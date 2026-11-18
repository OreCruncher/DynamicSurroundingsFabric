#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric
"""
Finds the item mods worth checking for a Minecraft version, runs them in the game for an item dump, and reports how
their armor, weapons and tools were sorted (class, armor family) and which items need a tag.

An item's sounds depend on its components (its equip sound, what its tool mines, whether it is a weapon), which a mod
sets in its code, so a jar alone can't say: unlike biome-mod-survey.py, this one runs the game. Three steps:

    python scripts/item-mod-survey.py fetch                    NeoForge mods for this branch's game in Modrinth's
                                                               equipment category, 250k downloads or more, with their
                                                               required dependencies, into neoforge/run-pack/item-survey
    ./gradlew :neoforge:runClientPack -Ppack=item-survey -Pscenario=scripts/bench/item-dump.json
                                                               opens a world, writes /dsdump items, closes the game
    python scripts/item-mod-survey.py report                   what each mod's items resolved to, and what needs a look

    python scripts/item-mod-survey.py fetch --list             only list what would be fetched
    python scripts/item-mod-survey.py fetch --min-downloads 100000 --slug some-mod   (--slug: these as well)
    python scripts/item-mod-survey.py fetch --leave-out some_mod_id                  a mod that breaks the run

Each file is checked against Modrinth's SHA-512 and cached in build/modpack-cache (modpack-fetch.py's). The mods
folder is replaced each fetch; the world and logs are kept. pack.json says what was fetched, left out and why. A mod
the development run can't load (a crash, or a newer NeoForge than gradle.properties has: -Pneoforge_version) is left
out with --leave-out, and fetched again.

The report (also written to build/item-mods/<version>/report.txt) gives each mod's worn armor with its family and
why, its classed items with why, and the dump's "needs a look" lines. Fix what is wrong in the mod's folder,
common/src/main/resources/assets/<mod id>/dsconfigs/tags/item/effects/: is_<family>_armor for armor, is_<class> for
weapons and tools, each entry "required": false.
"""
import argparse
import functools
import importlib.util
import json
import re
import shutil
import sys
import urllib.error
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'common' / 'src' / 'main' / 'resources' / 'assets'
PACK = 'item-survey'
DEST = ROOT / 'neoforge' / 'run-pack' / PACK
LOADER = 'neoforge'
DUMP = DEST / 'config' / 'dsurround' / 'dumps' / 'items.txt'

# modpack-fetch.py's downloads, cache, mod ids and the mods a pack run leaves out
_spec = importlib.util.spec_from_file_location('modpack_fetch', ROOT / 'scripts' / 'modpack-fetch.py')
mf = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mf)


def candidates(game_version, min_downloads):
    """Equipment mods for the game version on NeoForge by downloads, past the count, less libraries"""
    facets = json.dumps([['versions:' + game_version], ['project_type:mod'], ['categories:equipment'],
                         ['categories:' + LOADER]])
    found, offset = [], 0
    while True:
        page = mf.get_json(mf.API + '/search', facets=facets, index='downloads', limit=100, offset=offset)
        for hit in page['hits']:
            if hit['downloads'] < min_downloads:
                return found
            if 'library' not in hit['categories']:
                found.append(hit)
        offset += len(page['hits'])
        if not page['hits'] or offset >= page['total_hits']:
            return found


ITEM_DEFINITION = re.compile(r'^assets/([^/]+)/items/[^/]+\.json$')


def item_count(jar):
    """The items a jar defines (26.x's item definitions, assets/<ns>/items/), less vanilla's"""
    with zipfile.ZipFile(jar) as z:
        return sum(1 for n in z.namelist() if (m := ITEM_DEFINITION.match(n)) and m.group(1) != 'minecraft')


NEOFORGE_RANGE = re.compile(r'modId\s*=\s*"neoforge"[^\[]*?versionRange\s*=\s*"[\[(]([0-9.]+)')


def neoforge_needed(jar):
    """The lowest NeoForge version the jar's mods.toml accepts, or None"""
    with zipfile.ZipFile(jar) as z:
        if 'META-INF/neoforge.mods.toml' not in z.namelist():
            return None
        m = NEOFORGE_RANGE.search(z.read('META-INF/neoforge.mods.toml').decode('utf-8', 'replace'))
        return m.group(1) if m else None


def newest_version(project, game_version):
    """The project's newest NeoForge version for the game version, or None"""
    versions = mf.get_json(mf.API + '/project/%s/version' % project, loaders=[LOADER], game_versions=[game_version])
    # Modrinth's filter has let other loaders' versions through (modpack-fetch.py): checked here too
    versions = [v for v in versions if LOADER in v['loaders'] and game_version in v['game_versions']]
    return versions[0] if versions else None


def resolve(project, game_version, chosen, missing, trail):
    """Adds the project's version and its required dependencies' to chosen (by project id); False if one is missing"""
    if project in chosen:
        return True
    version = newest_version(project, game_version)
    if version is None:
        missing.append(' <- '.join([project] + trail))
        return False
    chosen[project] = version
    ok = True
    for dep in version.get('dependencies', []):
        if dep.get('dependency_type') != 'required' or not dep.get('project_id'):
            continue
        ok &= resolve(dep['project_id'], game_version, chosen, missing, [project] + trail)
    return ok


def jar_of(version):
    file = next((f for f in version['files'] if f.get('primary')), version['files'][0])
    return mf.cached(file['url'], file['hashes']['sha512'], file['filename'])


def requirements(jar):
    """The mod ids a jar's mods.toml requires on the client, and its Minecraft version range (or None)"""
    with zipfile.ZipFile(jar) as z:
        if 'META-INF/neoforge.mods.toml' not in z.namelist():
            return set(), None
        text = z.read('META-INF/neoforge.mods.toml').decode('utf-8', 'replace')
    required, minecraft = set(), None
    tables = list(mf.TOML_TABLE.finditer(text))
    for n, table in enumerate(tables):
        if not table.group(1).strip().startswith('dependencies'):
            continue
        body = text[table.end():tables[n + 1].start() if n + 1 < len(tables) else len(text)]
        for entry in re.split(r'(?=^\s*modId\s*=)', body, flags=re.MULTILINE):
            mod = re.search(r'^\s*modId\s*=\s*"([^"]+)"', entry, re.MULTILINE)
            if not mod:
                continue
            kind = re.search(r'^\s*type\s*=\s*"([^"]+)"', entry, re.MULTILINE | re.IGNORECASE)
            mandatory = re.search(r'^\s*mandatory\s*=\s*(true|false)', entry, re.MULTILINE)
            side = re.search(r'^\s*side\s*=\s*"([^"]+)"', entry, re.MULTILINE)
            needed = (kind.group(1).lower() == 'required') if kind else (mandatory is not None and mandatory.group(1) == 'true')
            if mod.group(1) == 'minecraft':
                found = re.search(r'^\s*versionRange\s*=\s*"([^"]+)"', entry, re.MULTILINE)
                minecraft = found.group(1) if found else None
            elif needed and not (side and side.group(1).upper() == 'SERVER'):
                required.add(mf.normal_id(mod.group(1)))
    return required, minecraft


def in_range(version, spec):
    """Whether a version is inside a Maven range such as [26.3,26.4) or [1.21.1,)"""
    def key(v):
        k = mf.version_key(v)
        return k + (0,) * (6 - len(k))
    v = key(version)
    for part in re.findall(r'[\[(][^\])]*[\])]', spec) or [spec]:
        if part[0] not in '[(':
            # A bare version is Maven's soft requirement: that version or any later
            return v >= key(part)
        low, _, high = part[1:-1].partition(',')
        if part[0] in '[(' and ',' not in part:
            high = low
        ok = True
        if low.strip():
            ok &= v >= key(low) if part[0] == '[' else v > key(low)
        if high.strip():
            ok &= v <= key(high) if part[-1] == ']' else v < key(high)
        if ok:
            return True
    return False


@functools.cache
def ids_of(jar):
    return frozenset(mf.mod_ids(jar))


@functools.cache
def requirements_of(jar):
    return requirements(jar)


# Provided whatever the pack holds: the game, the loader, and the mods the development run loads itself
ALWAYS_THERE = {'minecraft', 'neoforge', 'javafml', 'lowcodefml', 'mcp'} | set(mf.DEV_RUN_MODS)


def find_by_mod_id(mod_id, game_version):
    """The Modrinth project for a mod id (its slug is usually the id), whose jar declares it, and its version"""
    for slug in dict.fromkeys([mod_id, mod_id.replace('_', '-')]):
        try:
            project = mf.get_json(mf.API + '/project/' + slug)
        except urllib.error.HTTPError:
            continue
        version = newest_version(project['id'], game_version)
        if version and mod_id in mf.mod_ids(jar_of(version)):
            return project['id']
    return None


def settle(chosen, game_version):
    """
    What Modrinth's dependency lists miss, from each jar's own mods.toml: a required mod not fetched is looked for by
    its id and added, and a jar whose Minecraft range leaves the version out, or whose requirement can't be found, is
    dropped (and so is whatever then misses it). Returns the dropped projects, with why.
    """
    dropped, unknown = {}, set()
    while True:
        jars = {p: jar_of(v) for p, v in chosen.items()}
        provided = ALWAYS_THERE.union(*(ids_of(j) for j in jars.values()))
        changed = False
        for project, jar in jars.items():
            required, minecraft = requirements_of(jar)
            why = None
            if minecraft and not in_range(game_version, minecraft):
                why = 'made for Minecraft %s' % minecraft
            else:
                for mod_id in sorted(required - provided):
                    found = None if mod_id in unknown else find_by_mod_id(mod_id, game_version)
                    # Never a project already dropped, which would come back each pass
                    if found and found not in dropped:
                        trial = dict(chosen)
                        if resolve(found, game_version, trial, [], []) and not dropped.keys() & trial.keys():
                            chosen.clear()
                            chosen.update(trial)
                            changed = True
                            break
                    unknown.add(mod_id)
                    why = 'needs %s, not found for %s %s' % (mod_id, LOADER, game_version)
                    break
            if why:
                dropped[project] = why
                chosen.pop(project)
                changed = True
            if changed:
                break
        if not changed:
            return dropped


def fetch(args, game_version):
    mods = candidates(game_version, args.min_downloads)
    known = {m['slug'] for m in mods}
    for slug in args.slug:
        if slug not in known:
            project = mf.get_json(mf.API + '/project/' + slug)
            mods.append({'slug': project['slug'], 'project_id': project['id'], 'title': project['title'],
                         'downloads': project['downloads']})
    if not mods:
        sys.exit('No equipment mods for %s on %s past %d downloads' % (game_version, LOADER, args.min_downloads))
    if args.list:
        for m in mods:
            print('%-36s %10s  %s' % (m['slug'], m['downloads'], m['title']))
        return

    leave_out = {mf.normal_id(i) for i in args.leave_out}
    chosen, surveyed, skipped = {}, [], []
    for m in mods:
        # Modrinth's equipment category holds structures, tooltips and enchantments too: only a mod adding items
        version = newest_version(m['project_id'], game_version)
        if version is None:
            skipped.append({'mod': m['slug'], 'why': 'no %s %s version' % (LOADER, game_version)})
            continue
        file = next((f for f in version['files'] if f.get('primary')), version['files'][0])
        if item_count(mf.cached(file['url'], file['hashes']['sha512'], file['filename'])) == 0:
            skipped.append({'mod': m['slug'], 'why': 'adds no items'})
            continue
        trial, missing = dict(chosen), []
        if not resolve(m['project_id'], game_version, trial, missing, []):
            skipped.append({'mod': m['slug'], 'why': 'no %s %s version of %s' % (LOADER, game_version, '; '.join(missing))})
            continue
        chosen = trial
        surveyed.append(m)

    titles = {m['project_id']: m for m in surveyed}
    for project, why in settle(chosen, game_version).items():
        if project in titles:
            skipped.append({'mod': titles[project]['slug'], 'why': why})
            surveyed.remove(titles.pop(project))

    mods_dir = DEST / 'mods'
    shutil.rmtree(mods_dir, ignore_errors=True)
    mods_dir.mkdir(parents=True)
    fetched, excluded = [], []
    neoforge = None
    for project, version in chosen.items():
        file = next((f for f in version['files'] if f.get('primary')), version['files'][0])
        source = mf.cached(file['url'], file['hashes']['sha512'], file['filename'])
        ids = mf.mod_ids(source)
        reason = mf.left_out(ids, [], leave_out)
        name = titles[project]['slug'] if project in titles else project
        if reason:
            excluded.append({'file': file['filename'], 'project': name, 'why': reason})
            continue
        shutil.copyfile(source, mods_dir / file['filename'])
        needed = neoforge_needed(source)
        if needed and (neoforge is None or mf.version_key(needed) > mf.version_key(neoforge[0])):
            neoforge = (needed, name)
        fetched.append({'file': file['filename'], 'project': name, 'version': version['version_number'],
                        'ids': sorted(ids), 'surveyed': project in titles,
                        'title': titles[project]['title'] if project in titles else None,
                        'downloads': titles[project]['downloads'] if project in titles else None})

    record = {'pack': PACK, 'gameVersion': game_version, 'loader': LOADER, 'minDownloads': args.min_downloads,
              'devRunLoader': mf.gradle_properties().get('neoforge_version'),
              'fetched': fetched, 'excluded': excluded, 'skipped': skipped}
    (DEST / 'pack.json').write_text(json.dumps(record, indent=2), encoding='utf-8')

    print('%d mods surveyed, %d jars with dependencies, in %s' % (sum(f['surveyed'] for f in fetched), len(fetched),
                                                                  DEST.relative_to(ROOT).as_posix()))
    for f in fetched:
        print('  %-36s %-24s %s%s' % (f['project'], f['version'], ', '.join(f['ids']) or '?',
                                      '' if f['surveyed'] else '  (dependency)'))
    for e in excluded:
        print('  left out %s (%s)' % (e['file'], e['why']))
    no_items = [s['mod'] for s in skipped if s['why'] == 'adds no items']
    for s in skipped:
        if s['why'] != 'adds no items':
            print('  skipped %s (%s)' % (s['mod'], s['why']))
    if no_items:
        print('  skipped %d that add no items (pack.json lists them)' % len(no_items))
    ours = record['devRunLoader']
    flag = ''
    if neoforge and ours and mf.version_key(neoforge[0]) > mf.version_key(ours):
        flag = ' -Pneoforge_version=' + neoforge[0]
        print('  %s needs NeoForge %s, newer than the development run\'s %s' % (neoforge[1], neoforge[0], ours))
    print('Next: ./gradlew :neoforge:runClientPack -Ppack=%s%s -Pscenario=scripts/bench/item-dump.json' % (PACK, flag))


ITEM = re.compile(r'^([a-z0-9_.-]+):([a-z0-9_/.-]+)$')


def parse_dump(text):
    """The dump's needs-a-look lines by item, and each item's fields"""
    concerns = defaultdict(list)
    items = {}
    head, _, body = text.partition('\n\n')
    for line in head.splitlines():
        m = re.match(r'^  ([a-z0-9_.-]+:[a-z0-9_/.-]+): (.*)$', line)
        if m:
            concerns[m.group(1)].append(m.group(2))
    for block in body.split('\n\n'):
        lines = block.strip().splitlines()
        if not lines or not ITEM.match(lines[0]):
            continue
        fields = {}
        for line in lines[1:]:
            key, _, value = line.partition(': ')
            fields[key] = value
        items[lines[0]] = fields
    return concerns, items


def report(args, game_version):
    dump = Path(args.dump) if args.dump else DUMP
    if not dump.exists():
        sys.exit('%s: no dump; run the scenario first (see --help)' % dump)
    pack = json.loads((DEST / 'pack.json').read_text(encoding='utf-8')) if (DEST / 'pack.json').exists() else {}
    by_id = {}
    for f in pack.get('fetched', []):
        for i in f['ids']:
            by_id[i] = f

    concerns, items = parse_dump(dump.read_text(encoding='utf-8'))
    by_namespace = defaultdict(list)
    for item_id in items:
        by_namespace[item_id.split(':', 1)[0]].append(item_id)

    out = []
    total_concerns = 0
    for ns in sorted(by_namespace):
        if ns == 'minecraft':
            continue
        ids = by_namespace[ns]
        worn = [i for i in ids if items[i].get('Armor family', '').split(' ')[0] != 'none'
                or 'is in no family' in items[i].get('Armor family', '')]
        classed = [i for i in ids if not items[i].get('Class', 'none').startswith('none')]
        looks = [(i, c) for i in ids for c in concerns.get(i, [])]
        total_concerns += len(looks)
        if not worn and not classed and not looks:
            continue
        mod = by_id.get(ns)
        title = ' (%s, %s)' % (mod['project'], mod['version']) if mod else ''
        ours = ' - our folder: assets/%s/dsconfigs' % ns if (ASSETS / ns / 'dsconfigs').is_dir() else ''
        out.append('== %s%s: %d items, %d worn, %d classed, %d need a look%s' % (
            ns, title, len(ids), len(worn), len(classed), len(looks), ours))
        for item_id, concern in looks:
            out.append('  ! %s: %s' % (item_id, concern))
        for item_id in worn:
            out.append('  armor  %-44s %s' % (item_id, items[item_id].get('Armor family')))
        for item_id in classed:
            out.append('  class  %-44s %s' % (item_id, items[item_id].get('Class')))
        out.append('')

    header = ['Item survey, %s: %d items in %d mods besides vanilla; %d need a look' % (
        game_version, sum(len(v) for k, v in by_namespace.items() if k != 'minecraft'),
        sum(1 for k in by_namespace if k != 'minecraft'), total_concerns),
        'Namespaces with no worn, classed or flagged items are left out.', '']
    text = '\n'.join(header + out)
    target = ROOT / 'build' / 'item-mods' / game_version / 'report.txt'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text + '\n', encoding='utf-8')
    print(text)
    print('Written to %s' % target.relative_to(ROOT).as_posix())


def main():
    # Mod titles and item names aren't all in the console's code page
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='step', required=True)
    f = sub.add_parser('fetch', help='fetch the mods into the pack folder')
    f.add_argument('--min-downloads', type=int, default=250_000)
    f.add_argument('--slug', action='append', default=[], help='fetch this mod as well, whatever its downloads')
    f.add_argument('--leave-out', action='append', default=[], metavar='MOD_ID', help='a mod that breaks the run')
    f.add_argument('--list', action='store_true', help='only list what would be fetched')
    r = sub.add_parser('report', help='report on the dump the scenario wrote')
    r.add_argument('--dump', help='an items.txt other than the pack folder\'s')
    parser.add_argument('--game-version', help="default this branch's minecraft_version")
    args = parser.parse_args()

    game_version = args.game_version or mf.gradle_properties()['minecraft_version']
    if args.step == 'fetch':
        fetch(args, game_version)
    else:
        report(args, game_version)


if __name__ == '__main__':
    main()
