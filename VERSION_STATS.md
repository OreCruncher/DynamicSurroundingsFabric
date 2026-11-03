# Minecraft version popularity (modded, 1.21+)

**Generated:** 2026-10-23, from the Modrinth public API (no API key needed). Previous refresh: 2026-10-09.

**Purpose:** decide which Minecraft versions are worth targeting, and which have little following.

**License:** this text and its tables are © OreCruncher, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/):
reuse them, with credit and a link to this file. The download counts themselves are Modrinth's public figures. The
script that gathers them (`scripts/modrinth-version-stats.py`) is MIT.

## Summary

Share of 1.21+ downloads by game version, for mods that nearly every player on a version installs (all downloads
to date):

| Version | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 1.21.1 | **20.1%** | **19.4%** | **19.6%** |
| 1.21.11 | **21.3%** | **21.5%** | **20.8%** |
| 26.2 | **11.6%** | 10.1% | 10.0% |
| 1.21.4 | 7.1% | 6.7% | 5.8% |
| 1.21.10 | 6.1% | 3.4% | 3.5% |
| 1.21 | 5.6% | 10.2% | 11.3% |
| 1.21.8 | 5.1% | 2.3% | 2.2% |
| 26.3 | 4.7% | 3.8% | 3.9% |
| 1.21.5 | 4.4% | 4.7% | 3.9% |
| 26.1 / 26.1.1 / 26.1.2 | 4.1 / 3.2 / 2.8% | 1.4 / 1.4 / 6.6% | 3.7 / 2.9 / 2.9% |
| 1.21.2, .3, .6, .7, .9 | 0.2–1.0% each | 0.5–3.1% | 0.3–3.7% |

### Where new downloads went (2026-10-09 to 2026-10-23)

The share of the downloads made since the previous refresh, worked out from the difference between the two runs.
This shows where players are now, which the totals above, built up over years, hide:

| Version | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 26.3 | **40.9%** | **34.9%** | **36.5%** |
| 1.21.11 | 19.4% | 20.8% | 18.6% |
| 26.2 | 14.2% | 13.1% | 13.6% |
| 1.21.1 | 12.8% | 18.1% | 18.3% |
| All others | 12.7% | 13.1% | 13.0% |

New downloads in the two weeks: Fabric API 13.5 million, Sodium 11.2 million, Iris 8.0 million. Dynamic Surroundings
gained 45,211 (all 1.21.1; 84% Fabric, 16% NeoForge).

## Analysis

- **26.3 is where new players are going.** In two weeks it took over a third of all new downloads of Fabric API,
  Sodium and Iris, more than any other version, and more than 26.2 and 1.21.1 together on Fabric API. Its total share
  doubled (2.3% to 4.7% on Fabric API) in that time. This matters for the 26.2 port: the 26.2 build doesn't run on 26.3
  (its rendering API moved), so the port should target 26.3 as well, or instead.
- **26.2 is still growing, but has been overtaken.** It takes about 13–14% of new downloads, against 26.3's 35–41%.
  It is the version a 26.x build would have reached first, but players are moving on from it quickly.
- **1.21.1 is a long-lived target.** It still takes 13–18% of new downloads, nearly as much as 26.2. Large NeoForge
  modpacks (e.g. All the Mods 10) are built on it, and it has by far the highest NeoForge share (about a third of
  Sodium and Iris downloads for that version). All of Dynamic Surroundings' 1.21+ Modrinth downloads are on 1.21.1.
- **1.21.11 is the other anchor.** It is the last 1.x release, and holds about a fifth of new downloads.
- **Middle tier:** 1.21.4, 1.21.8 and 1.21.10. Each takes 1–2% of new downloads. Worth supporting only if the port is
  cheap.
- **Skip:** 1.21.2, 1.21.3, 1.21.6, 1.21.7, 1.21.9 and the 26.1.x line. Players treated these as stepping stones.
- **NeoForge** is a small share everywhere except 1.21.1 (roughly 3–8% elsewhere on Sodium/Iris, 8–17% on JEI).

### Caveats

- The totals are cumulative, so older versions have had longer to build up downloads. A newer version with a
  similar share is the more popular one *now*. The "new downloads" table avoids this, but covers only two weeks, so a
  burst (a release, a modpack update) shows more there.
- When a single file covers several game versions, its downloads are split evenly between them. That's why Sodium
  shows identical numbers for 1.21.6, 1.21.7 and 1.21.8, and JEI for 1.21 and 1.21.1.
- These are Modrinth numbers only. CurseForge is larger, especially for NeoForge and modpack users. Its official
  API (`api.curseforge.com/v1/mods/{id}/files`, `downloadCount` per file) needs a key. The keyless CFWidget mirror
  had stale or zero counts for recent files when checked, so it isn't usable for this. The CurseForge author dashboard
  shows the mod's own downloads.
- JEI is distributed mainly through CurseForge, so its Modrinth numbers are skewed (e.g. almost nothing for 1.21.2–1.21.9).
  It's included as a NeoForge-leaning reference only.

## How to refresh

1. Run `python scripts/modrinth-version-stats.py > tables.md` (Python 3, standard library only). Pass Modrinth slugs
   as arguments to use other projects; the defaults are the ones below.
2. Before replacing anything, subtract the previous raw data from the new output, per project and version, for the
   "Where new downloads went" table (the share of the downloads made since the last refresh).
3. Replace the "Raw data" section with the output. Update the summary tables, the analysis and the dates at the top.

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

## Raw data (2026-10-23)

### dynamicsurroundingsfabric

1.21+ downloads: 3,096,378

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21.1 | 3,096,378 | 100.0% | fabric 84%, neoforge 16% |

### fabric-api

1.21+ downloads: 215,901,726

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 12,081,618 | 5.6% | fabric 100% |
| 1.21.1 | 43,332,283 | 20.1% | fabric 100% |
| 1.21.2 | 385,045 | 0.2% | fabric 100% |
| 1.21.3 | 1,842,090 | 0.9% | fabric 100% |
| 1.21.4 | 15,243,649 | 7.1% | fabric 100% |
| 1.21.5 | 9,522,769 | 4.4% | fabric 100% |
| 1.21.6 | 2,151,152 | 1.0% | fabric 100% |
| 1.21.7 | 1,967,357 | 0.9% | fabric 100% |
| 1.21.8 | 11,092,106 | 5.1% | fabric 100% |
| 1.21.9 | 1,899,505 | 0.9% | fabric 100% |
| 1.21.10 | 13,235,946 | 6.1% | fabric 100% |
| 1.21.11 | 46,026,276 | 21.3% | fabric 100% |
| 26.1 | 8,932,018 | 4.1% | fabric 100% |
| 26.1.1 | 6,883,079 | 3.2% | fabric 100% |
| 26.1.2 | 6,013,212 | 2.8% | fabric 100% |
| 26.2 | 25,141,730 | 11.6% | fabric 100% |
| 26.3 | 10,151,891 | 4.7% | fabric 100% |

### sodium

1.21+ downloads: 199,135,506

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 20,222,594 | 10.2% | fabric 100% |
| 1.21.1 | 38,563,576 | 19.4% | fabric 66%, neoforge 34% |
| 1.21.2 | 1,063,600 | 0.5% | fabric 98%, neoforge 2% |
| 1.21.3 | 1,160,718 | 0.6% | fabric 89%, neoforge 11% |
| 1.21.4 | 13,245,092 | 6.7% | fabric 94%, neoforge 6% |
| 1.21.5 | 9,279,090 | 4.7% | fabric 95%, neoforge 5% |
| 1.21.6 | 4,603,571 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.7 | 4,603,571 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.8 | 4,603,571 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.9 | 6,182,245 | 3.1% | fabric 100% |
| 1.21.10 | 6,728,385 | 3.4% | fabric 92%, neoforge 8% |
| 1.21.11 | 42,790,492 | 21.5% | fabric 96%, neoforge 4% |
| 26.1 | 2,742,645 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.1 | 2,742,645 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.2 | 13,086,191 | 6.6% | fabric 94%, neoforge 6% |
| 26.2 | 20,019,805 | 10.1% | fabric 94%, neoforge 6% |
| 26.3 | 7,497,716 | 3.8% | fabric 94%, neoforge 6% |

### iris

1.21+ downloads: 151,393,887

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 17,134,346 | 11.3% | fabric 50%, quilt 49%, neoforge 1% |
| 1.21.1 | 29,708,234 | 19.6% | fabric 40%, neoforge 32%, quilt 27% |
| 1.21.2 | 400,518 | 0.3% | fabric 70%, quilt 27%, neoforge 3% |
| 1.21.3 | 1,044,052 | 0.7% | fabric 54%, quilt 38%, neoforge 8% |
| 1.21.4 | 8,718,651 | 5.8% | fabric 47%, quilt 47%, neoforge 6% |
| 1.21.5 | 5,874,832 | 3.9% | fabric 48%, quilt 48%, neoforge 5% |
| 1.21.6 | 3,873,346 | 2.6% | fabric 59%, quilt 37%, neoforge 3% |
| 1.21.7 | 3,356,734 | 2.2% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.8 | 3,356,734 | 2.2% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.9 | 5,564,134 | 3.7% | fabric 94%, quilt 6% |
| 1.21.10 | 5,327,188 | 3.5% | fabric 93%, neoforge 6%, quilt 1% |
| 1.21.11 | 31,537,377 | 20.8% | fabric 72%, quilt 24%, neoforge 3% |
| 26.1 | 5,638,172 | 3.7% | fabric 95%, neoforge 5% |
| 26.1.1 | 4,415,795 | 2.9% | fabric 94%, neoforge 6% |
| 26.1.2 | 4,415,795 | 2.9% | fabric 94%, neoforge 6% |
| 26.2 | 15,184,601 | 10.0% | fabric 95%, neoforge 5% |
| 26.3 | 5,843,379 | 3.9% | fabric 96%, neoforge 4% |

### jei

1.21+ downloads: 35,625,155

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 12,445,154 | 34.9% | fabric 60%, neoforge 38%, forge 2% |
| 1.21.1 | 11,903,866 | 33.4% | fabric 59%, neoforge 39%, forge 2% |
| 1.21.4 | 140,671 | 0.4% | neoforge 100% |
| 1.21.5 | 65,438 | 0.2% | neoforge 100% |
| 1.21.7 | 29,606 | 0.1% | neoforge 100% |
| 1.21.8 | 122,416 | 0.3% | neoforge 100% |
| 1.21.9 | 35,312 | 0.1% | neoforge 100% |
| 1.21.10 | 1,027,984 | 2.9% | fabric 88%, neoforge 12% |
| 1.21.11 | 3,787,696 | 10.6% | fabric 88%, neoforge 12% |
| 26.1 | 806,735 | 2.3% | fabric 83%, neoforge 17% |
| 26.1.1 | 762,803 | 2.1% | fabric 83%, neoforge 17% |
| 26.1.2 | 687,796 | 1.9% | fabric 83%, neoforge 17% |
| 26.2 | 2,602,383 | 7.3% | fabric 89%, neoforge 11% |
| 26.3 | 1,207,296 | 3.4% | fabric 92%, neoforge 8% |
