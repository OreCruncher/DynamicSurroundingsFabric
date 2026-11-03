#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Download share by Minecraft version (1.21+) for a set of Modrinth projects.

Usage: python scripts/modrinth-version-stats.py [slug ...] > tables.md
Prints one Markdown table per project. No API key needed.
"""
import json
import re
import sys
import urllib.request
from collections import defaultdict

DEFAULT_SLUGS = ['dynamicsurroundingsfabric', 'fabric-api', 'sodium', 'iris', 'jei']
LOADERS = ('fabric', 'neoforge', 'forge', 'quilt')


def key(v):
    return [int(x) for x in re.findall(r'\d+', v)]


def wanted(v):
    # Release versions only (no snapshots/pre-releases), 1.21.x and the year-based 26.x+
    if not re.fullmatch(r'[\d.]+', v):
        return False
    k = key(v)
    return k[0] >= 26 or k[:2] == [1, 21]


def fetch(slug):
    req = urllib.request.Request(f'https://api.modrinth.com/v2/project/{slug}/version',
                                 headers={'User-Agent': 'OreCruncher/DynamicSurroundings version-stats'})
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def report(slug):
    total = defaultdict(float)
    loaders = defaultdict(lambda: defaultdict(float))
    for ver in fetch(slug):
        gvs = [g for g in ver['game_versions'] if wanted(g)]
        if not gvs:
            continue
        # A file listing several game versions has its downloads split evenly between them
        share = ver['downloads'] / len(gvs)
        lds = [l for l in ver['loaders'] if l in LOADERS] or ['other']
        for g in gvs:
            total[g] += share
            for l in lds:
                loaders[g][l] += share / len(lds)
    s = sum(total.values())
    print(f'### {slug}\n\n1.21+ downloads: {s:,.0f}\n')
    print('| Version | Downloads | Share | Loaders |')
    print('|---|---:|---:|---|')
    for g in sorted(total, key=key):
        ld = ', '.join(f'{l} {v / total[g]:.0%}' for l, v in sorted(loaders[g].items(), key=lambda x: -x[1]))
        print(f'| {g} | {total[g]:,.0f} | {total[g] / s:.1%} | {ld} |')
    print()


if __name__ == '__main__':
    for slug in sys.argv[1:] or DEFAULT_SLUGS:
        report(slug)
