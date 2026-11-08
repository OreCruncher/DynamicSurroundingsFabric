#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric
"""
Which of the mod's block effects each block gets, for finding blocks that are missing one.

For every block in a game or mod jar (or only those added since an older game jar), lists the mod's block tags it is
in (dsconfigs/tags/block/..., all namespaces' files merged, as the mod merges them), the blocks.json rules that
match it, and the vanilla and c: tags it is in, which say what kind of block it is. Tags are resolved the way the
game resolves them: #references followed, {"id": ..., "required": false} entries kept.

    python scripts/block-effect-survey.py                          blocks new in 26.3 since 1.21.1
    python scripts/block-effect-survey.py --all                    every vanilla block
    python scripts/block-effect-survey.py --mod path/to/mod.jar    every block a mod adds (its own tags read too)
    python scripts/block-effect-survey.py --untagged               only blocks in none of the mod's effect tags

It also lists, first, the mod's block tag and blocks.json entries that name a block or tag the jars don't have, in
the namespaces surveyed (minecraft, or the mods'): a block renamed or removed (26.3's chain became iron_chain) drops
out of an effect with nothing said. Entries marked "required": false are left out.

The defaults are the jars in Gradle's cache (fabric-loom's merged game jars, the newest fabric-convention-tags-v2).
"""
import argparse
import glob
import json
import os
import re
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, 'common', 'src', 'main', 'resources', 'assets')
LOOM = os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'fabric-loom', 'minecraftMaven', 'net', 'minecraft')
GAME = os.path.join(LOOM, 'minecraft-merged-deobf', '26.3', 'minecraft-merged-deobf-26.3.jar')
SINCE = glob.glob(os.path.join(LOOM, 'minecraft-merged', '1.21.1-*', 'minecraft-merged-1.21.1-*.jar'))
CONVENTIONS = sorted(glob.glob(os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'modules-2', 'files-2.1',
                                            'net.fabricmc.fabric-api', 'fabric-convention-tags-v2', '*', '*',
                                            'fabric-convention-tags-v2-*[0-9a-f].jar')))

# Vanilla and c: tags worth showing beside a block: what kind of block it is, for judging which effects fit
TELLING = re.compile(r'^(minecraft:(leaves|logs|flowers|small_flowers|saplings|replaceable|replaceable_by_trees|'
                     r'sword_efficient|mineable/hoe|wool|wool_carpets|ice|snow|sand|dirt|mud|grass_blocks|moss_blocks|'
                     r'substrate_overworld|crops|bee_attractive|cave_vines|climbable|candles|campfires|fire|'
                     r'impermeable|glass_blocks|buttons|pressure_plates|planks|wooden_.*|trapdoors|doors|fences)|'
                     r'c:(glass_blocks.*|glass_panes.*|sands.*|flowers.*|ores|stones|chests.*|barrels.*|.*leaves.*))$')

COMMENT = re.compile(r'(?m)^\s*//.*$')


def read_json(text):
    return json.loads(COMMENT.sub('', text))


def tag_files(jar, registry='block'):
    """Every tag a jar defines for a registry: {tag id: [entries]}"""
    tags = {}
    prefix = re.compile(r'^data/([^/]+)/tags/%s/(.+)\.json$' % registry)
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            m = prefix.match(name)
            if m:
                try:
                    tags.setdefault('%s:%s' % (m.group(1), m.group(2)), []).extend(read_json(z.read(name).decode('utf-8'))['values'])
                except (ValueError, KeyError):
                    print('unreadable tag ' + name, file=sys.stderr)
    return tags


def our_tags():
    """The mod's block tags, each namespace folder's file merged into one dsurround: tag, as the mod does"""
    tags = {}
    for path in glob.glob(os.path.join(ASSETS, '*', 'dsconfigs', 'tags', 'block', '**', '*.json'), recursive=True):
        rel = os.path.relpath(path, os.path.join(os.path.dirname(path).split(os.sep + 'tags' + os.sep)[0], 'tags', 'block'))
        tag = 'dsurround:' + rel[:-5].replace(os.sep, '/')
        with open(path, encoding='utf-8') as f:
            tags.setdefault(tag, []).extend(read_json(f.read())['values'])
    return tags


def blocks_in(jar, namespace=None):
    pattern = re.compile(r'^assets/([^/]+)/blockstates/([^/]+)\.json$')
    found = set()
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            m = pattern.match(name)
            if m and (namespace is None or m.group(1) == namespace):
                found.add('%s:%s' % (m.group(1), m.group(2)))
    return found


class Resolver:
    def __init__(self, tags):
        self.tags = tags
        self.cache = {}

    def members(self, tag):
        if tag in self.cache:
            return self.cache[tag]
        self.cache[tag] = set()
        result = set()
        for entry in self.tags.get(tag, []):
            ref = entry['id'] if isinstance(entry, dict) else entry
            if ref.startswith('#'):
                result |= self.members(ref[1:])
            else:
                result.add(ref)
        self.cache[tag] = result
        return result


def stale(ours, blocks, tags, namespaces):
    """The mod's tag and blocks.json entries naming a block or tag that doesn't exist, in the namespaces surveyed"""
    found = []

    def check(where, ref):
        name = ref[1:] if ref.startswith('#') else ref.partition('[')[0]
        if name.split(':', 1)[0] not in namespaces:
            return
        if (ref.startswith('#') and name not in tags) or (not ref.startswith('#') and name not in blocks):
            found.append('%s: %s' % (where, ref))

    for tag, entries in sorted(ours.items()):
        for entry in entries:
            if isinstance(entry, dict):
                if entry.get('required', True):
                    check(tag, entry['id'])
            else:
                check(tag, entry)
    for path in glob.glob(os.path.join(ASSETS, '*', 'dsconfigs', 'blocks.json')):
        with open(path, encoding='utf-8') as f:
            for rule in read_json(f.read()):
                for selector in rule.get('blocks', []):
                    check(os.path.relpath(path, ASSETS).replace(os.sep, '/'), selector)
    return found


def block_rules(resolver):
    """For each block, what blocks.json rules give it: effects and sound factories"""
    given = {}
    for path in glob.glob(os.path.join(ASSETS, '*', 'dsconfigs', 'blocks.json')):
        with open(path, encoding='utf-8') as f:
            rules = read_json(f.read())
        for rule in rules:
            what = [e.get('effect') for e in rule.get('effects', [])] + [a.get('factory') for a in rule.get('acoustics', [])]
            what = [w for w in what if w]
            if not what:
                continue
            for selector in rule.get('blocks', []):
                state = ''
                if selector.startswith('#'):
                    members = resolver.members(selector[1:])
                else:
                    name, _, state = selector.partition('[')
                    members = {name}
                for block in members:
                    given.setdefault(block, set()).update(w + ('[' + state if state else '') for w in what)
    return given


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--game', default=GAME)
    parser.add_argument('--since', default=SINCE[0] if SINCE else None, help='older game jar: only blocks new since it')
    parser.add_argument('--all', action='store_true', help='every block of the game jar, not only new ones')
    parser.add_argument('--mod', action='append', default=[], help='a mod jar: its blocks, with its tags')
    parser.add_argument('--conventions', default=CONVENTIONS[-1] if CONVENTIONS else None)
    parser.add_argument('--untagged', action='store_true', help="only blocks in none of the mod's effect tags or rules")
    args = parser.parse_args()

    tags = tag_files(args.game)
    if args.conventions:
        for k, v in tag_files(args.conventions).items():
            tags.setdefault(k, []).extend(v)
    for jar in args.mod:
        for k, v in tag_files(jar).items():
            tags.setdefault(k, []).extend(v)
    ours = our_tags()
    for k, v in ours.items():
        tags.setdefault(k, []).extend(v)
    resolver = Resolver(tags)

    if args.mod:
        targets = set()
        for jar in args.mod:
            targets |= {b for b in blocks_in(jar) if not b.startswith('minecraft:')}
    else:
        targets = blocks_in(args.game, 'minecraft')
        if not args.all:
            if not args.since:
                sys.exit('No older game jar to compare with: give --since, or --all')
            targets -= blocks_in(args.since, 'minecraft')
    if not targets:
        sys.exit('No blocks found to survey')

    known = blocks_in(args.game)
    for jar in args.mod:
        known |= blocks_in(jar)
    namespaces = {b.split(':', 1)[0] for b in targets}
    missing = stale(ours, known, tags, namespaces)
    if missing:
        print('Entries naming what %s has no block or tag for:' % ', '.join(sorted(namespaces)))
        for line in missing:
            print('    ' + line)
        print()

    effect_tags = sorted(t for t in ours if t.startswith('dsurround:effects/'))
    sound_tags = sorted(t for t in ours if not t.startswith('dsurround:effects/'))
    membership = {t: resolver.members(t) for t in effect_tags + sound_tags}
    telling = sorted(t for t in tags if TELLING.match(t))
    kinds = {t: resolver.members(t) for t in telling}
    rules = block_rules(resolver)

    shown = 0
    for block in sorted(targets):
        effects = [t.split(':', 1)[1] for t in effect_tags if block in membership[t]]
        sounds = [t.split(':', 1)[1] for t in sound_tags if block in membership[t]]
        given = sorted(rules.get(block, []))
        if args.untagged and (effects or given):
            continue
        shown += 1
        kind = [t for t in telling if block in kinds[t]]
        print(block)
        print('    effects: ' + (', '.join(effects) or '-'))
        if sounds:
            print('    sound:   ' + ', '.join(sounds))
        if given:
            print('    rules:   ' + ', '.join(given))
        print('    kind:    ' + (', '.join(kind) or '-'))
    print('%d of %d blocks shown' % (shown, len(targets)), file=sys.stderr)


if __name__ == '__main__':
    main()
