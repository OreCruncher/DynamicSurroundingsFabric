# Minecraft version popularity (modded, 1.21+)

**Generated:** 2026-10-09, from the Modrinth public API (no API key needed).
**Purpose:** decide which Minecraft versions are worth targeting, and which have little following.

## Summary

Share of 1.21+ downloads by game version, for mods that nearly every player on a version installs:

| Version | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 1.21.1 | **20.6%** | **19.4%** | **19.7%** |
| 1.21.11 | **21.4%** | **21.5%** | **21.0%** |
| 26.2 | **11.5%** | 9.9% | 9.8% |
| 1.21.4 | 7.4% | 6.9% | 6.0% |
| 1.21.10 | 6.5% | 3.5% | 3.7% |
| 1.21 | 5.9% | 10.6% | 11.8% |
| 1.21.8 | 5.4% | 2.4% | 2.3% |
| 1.21.5 | 4.6% | 4.9% | 4.1% |
| 26.1 / 26.1.1 / 26.1.2 | 4.3 / 3.3 / 2.9% | 1.4 / 1.4 / 6.7% | 3.8 / 3.0 / 3.0% |
| 26.3 | 2.3% | 1.9% | 2.0% |
| 1.21.2, .3, .6, .7, .9 | 0.2–1.1% each | 0.6–3.2% | 0.3–3.8% |

## Analysis

- **1.21.1 is a long-lived target.** Large NeoForge modpacks (e.g. All the Mods 10) are built on it, and it has
  by far the highest NeoForge share (about a third of Sodium and Iris downloads for that version). All of
  Dynamic Surroundings' 1.21+ Modrinth downloads are on 1.21.1 (85% Fabric, 15% NeoForge).
- **1.21.11 is the other anchor.** It is the last 1.x release, and the Fabric ecosystem settled there.
- **26.2 is the current version and catching up fast.** It already has about half of 1.21.11's downloads within a few
  months of release. This fits the existing 26.2 port (`OreCruncher/26.2`).
- **Middle tier:** 1.21.4, 1.21.8 and 1.21.10. Worth supporting only if the port is cheap.
- **Skip:** 1.21.2, 1.21.3, 1.21.6, 1.21.7, 1.21.9 and the 26.1.x line. Players treated these as stepping stones.
- **26.3** looks small mostly because it is new. Check it again at the next refresh.
- **NeoForge** is a small share everywhere except 1.21.1 (roughly 3–8% elsewhere on Sodium/Iris, 8–17% on JEI).

### Caveats

- The counts are cumulative, so older versions have had longer to build up downloads. A newer version with a
  similar share is the more popular one *now*.
- When a single file covers several game versions, its downloads are split evenly between them. That's why Sodium
  shows identical numbers for 1.21.6, 1.21.7 and 1.21.8.
- These are Modrinth numbers only. CurseForge is larger, especially for NeoForge and modpack users. Its official
  API (`api.curseforge.com/v1/mods/{id}/files`, `downloadCount` per file) needs a key. The keyless CFWidget mirror
  had stale or zero counts for recent files when checked, so it isn't usable for this. The CurseForge author dashboard
  shows the mod's own downloads.
- JEI is distributed mainly through CurseForge, so its Modrinth numbers are skewed (e.g. almost nothing for 1.21.2–1.21.9).
  It's included as a NeoForge-leaning reference only.

## How to refresh

1. Save the script below (e.g. to a temp folder; it's not part of the build).
2. Run `python modrinth_version_stats.py > tables.md` (Python 3, standard library only). Pass Modrinth slugs as
   arguments to use other projects; the defaults are the ones below.
3. Replace the "Raw data" section with the output. Update the summary table, the analysis and the date at the top.

Projects used (Modrinth slugs):

| Slug | Why |
|---|---|
| `dynamicsurroundingsfabric` | This mod (Fabric and NeoForge builds) |
| `fabric-api` | Required by almost every Fabric mod; the best proxy for Fabric players |
| `sodium` | Widely installed; Fabric and NeoForge |
| `iris` | Widely installed; Fabric, NeoForge, Quilt |
| `jei` | NeoForge-leaning reference (mainly on CurseForge, see caveats) |

Data source: `GET https://api.modrinth.com/v2/project/{slug}/version` returns every file with `downloads`,
`game_versions` and `loaders`. Only release versions 1.21.x and 26.x+ are counted.

```python
"""Download share by Minecraft version (1.21+) for a set of Modrinth projects.

Usage: python modrinth_version_stats.py [slug ...]
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
```

## Raw data (2026-10-09)

### dynamicsurroundingsfabric

1.21+ downloads: 3,051,167

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21.1 | 3,051,167 | 100.0% | fabric 85%, neoforge 15% |

### fabric-api

1.21+ downloads: 202,353,646

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 11,917,277 | 5.9% | fabric 100% |
| 1.21.1 | 41,599,449 | 20.6% | fabric 100% |
| 1.21.2 | 374,166 | 0.2% | fabric 100% |
| 1.21.3 | 1,821,751 | 0.9% | fabric 100% |
| 1.21.4 | 14,923,912 | 7.4% | fabric 100% |
| 1.21.5 | 9,407,174 | 4.6% | fabric 100% |
| 1.21.6 | 2,125,991 | 1.1% | fabric 100% |
| 1.21.7 | 1,954,798 | 1.0% | fabric 100% |
| 1.21.8 | 10,974,285 | 5.4% | fabric 100% |
| 1.21.9 | 1,876,106 | 0.9% | fabric 100% |
| 1.21.10 | 13,054,639 | 6.5% | fabric 100% |
| 1.21.11 | 43,400,704 | 21.4% | fabric 100% |
| 26.1 | 8,661,374 | 4.3% | fabric 100% |
| 26.1.1 | 6,643,788 | 3.3% | fabric 100% |
| 26.1.2 | 5,782,510 | 2.9% | fabric 100% |
| 26.2 | 23,219,366 | 11.5% | fabric 100% |
| 26.3 | 4,616,356 | 2.3% | fabric 100% |

### sodium

1.21+ downloads: 187,952,429

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 19,989,894 | 10.6% | fabric 100% |
| 1.21.1 | 36,537,358 | 19.4% | fabric 66%, neoforge 34% |
| 1.21.2 | 1,052,922 | 0.6% | fabric 98%, neoforge 2% |
| 1.21.3 | 1,148,767 | 0.6% | fabric 89%, neoforge 11% |
| 1.21.4 | 13,025,398 | 6.9% | fabric 94%, neoforge 6% |
| 1.21.5 | 9,187,147 | 4.9% | fabric 95%, neoforge 5% |
| 1.21.6 | 4,561,957 | 2.4% | fabric 94%, neoforge 6% |
| 1.21.7 | 4,561,957 | 2.4% | fabric 94%, neoforge 6% |
| 1.21.8 | 4,561,957 | 2.4% | fabric 94%, neoforge 6% |
| 1.21.9 | 6,106,672 | 3.2% | fabric 100% |
| 1.21.10 | 6,643,192 | 3.5% | fabric 92%, neoforge 8% |
| 1.21.11 | 40,460,842 | 21.5% | fabric 96%, neoforge 4% |
| 26.1 | 2,683,643 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.1 | 2,683,643 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.2 | 12,592,983 | 6.7% | fabric 94%, neoforge 6% |
| 26.2 | 18,558,797 | 9.9% | fabric 94%, neoforge 6% |
| 26.3 | 3,595,298 | 1.9% | fabric 94%, neoforge 6% |

### iris

1.21+ downloads: 143,381,611

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 16,916,010 | 11.8% | fabric 50%, quilt 49%, neoforge 1% |
| 1.21.1 | 28,244,885 | 19.7% | fabric 40%, neoforge 32%, quilt 29% |
| 1.21.2 | 397,635 | 0.3% | fabric 70%, quilt 27%, neoforge 3% |
| 1.21.3 | 1,033,022 | 0.7% | fabric 54%, quilt 38%, neoforge 8% |
| 1.21.4 | 8,589,377 | 6.0% | fabric 47%, quilt 47%, neoforge 6% |
| 1.21.5 | 5,831,499 | 4.1% | fabric 48%, quilt 48%, neoforge 5% |
| 1.21.6 | 3,844,414 | 2.7% | fabric 59%, quilt 37%, neoforge 3% |
| 1.21.7 | 3,329,309 | 2.3% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.8 | 3,329,309 | 2.3% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.9 | 5,502,750 | 3.8% | fabric 94%, quilt 6% |
| 1.21.10 | 5,261,090 | 3.7% | fabric 93%, neoforge 6%, quilt 1% |
| 1.21.11 | 30,049,263 | 21.0% | fabric 71%, quilt 25%, neoforge 3% |
| 26.1 | 5,482,250 | 3.8% | fabric 95%, neoforge 5% |
| 26.1.1 | 4,277,746 | 3.0% | fabric 94%, neoforge 6% |
| 26.1.2 | 4,277,746 | 3.0% | fabric 94%, neoforge 6% |
| 26.2 | 14,098,607 | 9.8% | fabric 95%, neoforge 5% |
| 26.3 | 2,916,699 | 2.0% | fabric 97%, neoforge 3% |

### jei

1.21+ downloads: 33,101,509

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 11,786,718 | 35.6% | fabric 61%, neoforge 37%, forge 2% |
| 1.21.1 | 11,246,788 | 34.0% | fabric 59%, neoforge 38%, forge 2% |
| 1.21.4 | 137,725 | 0.4% | neoforge 100% |
| 1.21.5 | 64,772 | 0.2% | neoforge 100% |
| 1.21.7 | 29,448 | 0.1% | neoforge 100% |
| 1.21.8 | 120,915 | 0.4% | neoforge 100% |
| 1.21.9 | 35,140 | 0.1% | neoforge 100% |
| 1.21.10 | 1,011,876 | 3.1% | fabric 88%, neoforge 12% |
| 1.21.11 | 3,579,338 | 10.8% | fabric 87%, neoforge 13% |
| 26.1 | 776,572 | 2.3% | fabric 83%, neoforge 17% |
| 26.1.1 | 733,127 | 2.2% | fabric 83%, neoforge 17% |
| 26.1.2 | 658,297 | 2.0% | fabric 84%, neoforge 16% |
| 26.2 | 2,372,078 | 7.2% | fabric 88%, neoforge 12% |
| 26.3 | 548,715 | 1.7% | fabric 92%, neoforge 8% |
