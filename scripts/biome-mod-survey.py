#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric
"""
Finds the biome mods worth tagging for a Minecraft version, fetches them, and runs block-effect-survey.py on each.

"Worth tagging" is a biome mod that adds blocks: Modrinth's worldgen mods for the version matching "biome", past a
download count (a million by default), less libraries and datapacks (no blocks of their own), and less any whose jar
has no blockstates once fetched (structure packs, terrain reshapers, tools). Each jar is checked against Modrinth's
SHA-1 and only ever read as a zip, never run. Jars are kept in build/biome-mods/<version>/<slug>/, and each survey is
written beside it as <slug>.txt, the stale entries first; the summary says which mods we have no config folder for.

    python scripts/biome-mod-survey.py                         26.3, a million downloads or more
    python scripts/biome-mod-survey.py --version 26.4 --min-downloads 500000
    python scripts/biome-mod-survey.py --slug wilder-wild --slug enderscape     those only, whatever their downloads
    python scripts/biome-mod-survey.py --list                  only list what would be fetched

Then judge each survey (blocks with no effect that should have one), and add the entries to that mod's folder,
common/src/main/resources/assets/<mod id>/dsconfigs/tags/block/effects/, each "required": false.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, 'common', 'src', 'main', 'resources', 'assets')
SURVEY = os.path.join(REPO, 'scripts', 'block-effect-survey.py')
API = 'https://api.modrinth.com/v2'
AGENT = 'OreCruncher/DynamicSurroundings biome-mod-survey'
# Loaders tried for a mod's file, in order: the survey reads blockstates and tags, the same in either
LOADERS = ('fabric', 'neoforge', 'quilt', 'forge')


def get(path, **params):
    url = API + path + ('?' + urllib.parse.urlencode(params) if params else '')
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': AGENT})) as r:
        return json.load(r)


def candidates(version, min_downloads):
    """Biome mods for a version by downloads, past the count, less libraries and datapacks"""
    facets = json.dumps([['versions:' + version], ['project_type:mod'], ['categories:worldgen']])
    found, offset = [], 0
    while True:
        page = get('/search', query='biome', facets=facets, index='downloads', limit=100, offset=offset)
        for hit in page['hits']:
            if hit['downloads'] < min_downloads:
                return found
            if not {'library', 'datapack'} & set(hit['categories']):
                found.append(hit)
        offset += len(page['hits'])
        if not page['hits'] or offset >= page['total_hits']:
            return found


def file_for(slug, version):
    """The mod's newest primary file for the version, on the first loader it has"""
    versions = get('/project/%s/version' % slug, game_versions=json.dumps([version]))
    for loader in LOADERS:
        for v in versions:
            if loader in v['loaders']:
                files = [f for f in v['files'] if f.get('primary')] or v['files']
                return v['version_number'], files[0]
    return None, None


def fetch(slug, version, folder):
    number, file = file_for(slug, version)
    if file is None:
        return None, 'no file for %s' % version
    target = os.path.join(folder, slug)
    os.makedirs(target, exist_ok=True)
    path = os.path.join(target, file['filename'])
    if not os.path.exists(path):
        req = urllib.request.Request(file['url'], headers={'User-Agent': AGENT})
        with urllib.request.urlopen(req) as r, open(path, 'wb') as out:
            out.write(r.read())
    with open(path, 'rb') as f:
        if hashlib.sha1(f.read()).hexdigest() != file['hashes']['sha1']:
            os.remove(path)
            return None, 'SHA-1 mismatch: removed'
    return path, number


def mod_ids(jar):
    """The mod ids a jar declares, for matching our config folders"""
    ids = []
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        if 'fabric.mod.json' in names:
            m = re.search(r'"id"\s*:\s*"([^"]+)"', z.read('fabric.mod.json').decode('utf-8', 'replace'))
            if m:
                ids.append(m.group(1))
        for toml in (n for n in names if n.endswith('mods.toml')):
            ids += [i for i in re.findall(r'modId\s*=\s*"([^"]+)"', z.read(toml).decode('utf-8', 'replace'))
                    if i not in ('neoforge', 'forge', 'minecraft') and i not in ids]
    return ids


def block_count(jar):
    with zipfile.ZipFile(jar) as z:
        return sum(1 for n in z.namelist() if re.match(r'^assets/[^/]+/blockstates/[^/]+\.json$', n)
                   and not n.startswith('assets/minecraft/'))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--version', default='26.3')
    parser.add_argument('--min-downloads', type=int, default=1_000_000)
    parser.add_argument('--slug', action='append', default=[], help='these mods only, whatever their downloads')
    parser.add_argument('--list', action='store_true', help='only list what would be fetched')
    parser.add_argument('--out', default=None, help='where jars and surveys go (build/biome-mods/<version>)')
    args = parser.parse_args()

    if args.slug:
        mods = [{'slug': s, 'title': s, 'downloads': None} for s in args.slug]
    else:
        mods = candidates(args.version, args.min_downloads)
    if not mods:
        sys.exit('No biome mods found for %s past %d downloads' % (args.version, args.min_downloads))
    if args.list:
        for m in mods:
            print('%-32s %12s  %s' % (m['slug'], m['downloads'] if m['downloads'] is not None else '-', m['title']))
        return

    folder = args.out or os.path.join(REPO, 'build', 'biome-mods', args.version)
    os.makedirs(folder, exist_ok=True)
    rows = []
    for m in mods:
        jar, note = fetch(m['slug'], args.version, folder)
        if jar is None:
            rows.append((m['slug'], '-', '-', note))
            continue
        blocks = block_count(jar)
        ids = mod_ids(jar)
        if blocks == 0:
            rows.append((m['slug'], note, 0, 'no blocks: nothing to tag'))
            continue
        report = os.path.join(folder, m['slug'] + '.txt')
        with open(report, 'w', encoding='utf-8') as out:
            subprocess.run([sys.executable, '-I', SURVEY, '--mod', jar], stdout=out, stderr=subprocess.DEVNULL, check=True)
        with open(report, encoding='utf-8') as f:
            stale = f.read().startswith('Entries naming')
        folders = [i for i in ids if os.path.isdir(os.path.join(ASSETS, i))]
        rows.append((m['slug'], note, blocks, '%s; ids %s; our folder: %s%s' % (
            os.path.relpath(report, REPO).replace(os.sep, '/'), ', '.join(ids) or '?',
            ', '.join(folders) or 'none yet', '; STALE ENTRIES' if stale else '')))
    for slug, number, blocks, what in rows:
        print('%-24s %-22s %6s  %s' % (slug, number, blocks, what))


if __name__ == '__main__':
    main()
