# Minecraft version popularity (modded, 1.21+)

**Generated:** 2026-11-03, from the Modrinth public API (no API key needed). Previous refresh: 2026-10-23.

**Purpose:** decide which Minecraft versions are worth targeting, and which have little following.

**License:** this text and its tables are © OreCruncher, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/):
reuse them, with credit and a link to this file. The download counts themselves are Modrinth's public figures. The
script that gathers them (`scripts/modrinth-version-stats.py`) is MIT.

## Summary

Share of 1.21+ downloads by game version, for mods that nearly every player on a version installs (all downloads
to date):

| Version | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 1.21.11 | **21.3%** | **21.5%** | **20.8%** |
| 1.21.1 | **19.8%** | **19.3%** | **19.6%** |
| 26.2 | **11.6%** | 10.1% | 10.1% |
| 1.21.4 | 6.9% | 6.5% | 5.6% |
| 1.21.10 | 6.0% | 3.3% | 3.4% |
| 26.3 | 6.0% | 4.8% | 4.8% |
| 1.21 | 5.4% | 9.9% | 11.1% |
| 1.21.8 | 5.0% | 2.3% | 2.2% |
| 1.21.5 | 4.3% | 4.5% | 3.8% |
| 26.1 / 26.1.1 / 26.1.2 | 4.1 / 3.1 / 2.7% | 1.4 / 1.4 / 6.5% | 3.7 / 2.9 / 2.9% |
| 1.21.2, .3, .6, .7, .9 | 0.2–1.0% each | 0.5–3.0% | 0.3–3.6% |

### Where new downloads went (2026-10-23 to 2026-11-03)

The share of the downloads made since the previous refresh, worked out from the difference between the two runs.
This shows where players are now, which the totals above, built up over years, hide:

| Version | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 26.3 | **43.7%** | **36.6%** | **38.5%** |
| 1.21.11 | 20.1% | 21.3% | 18.8% |
| 1.21.1 | 12.1% | 18.1% | 18.3% |
| 26.2 | 11.5% | 10.8% | 11.2% |
| All others | 12.7% | 13.1% | 13.2% |

New downloads in the eleven days: Fabric API 7.6 million, Sodium 6.2 million, Iris 4.3 million. Dynamic Surroundings
gained 25,553 (all 1.21.1; 84% Fabric, 16% NeoForge overall).

## Analysis

- **26.3 is where new players are going.** In eleven days it took over a third of all new downloads of Fabric API,
  Sodium and Iris, more than any other version, and more than 26.2 and 1.21.1 together on all three. Its total share
  rose from 4.7% to 6.0% on Fabric API in that time. This matters for the 26.2 port: the 26.2 build doesn't run on 26.3
  (its rendering API moved), so the port should target 26.3 as well, or instead.
- **26.2 has fallen further behind.** It takes about 11% of new downloads, down from 13–14% in the previous period,
  against 26.3's 37–44%. It is the version a 26.x build would have reached first, but players are moving on from it
  quickly.
- **1.21.1 is a long-lived target.** It still takes 12–18% of new downloads, more than 26.2. Large NeoForge
  modpacks (e.g. All the Mods 10) are built on it, and it has by far the highest NeoForge share (about a third of
  Sodium and Iris downloads for that version). All of Dynamic Surroundings' 1.21+ Modrinth downloads are on 1.21.1.
- **1.21.11 is the other anchor.** It is the last 1.x release, and holds about a fifth of new downloads.
- **Middle tier:** 1.21.4, 1.21.8 and 1.21.10. Each takes about 2% of new downloads or less. Worth supporting only if the port is
  cheap.
- **Skip:** 1.21.2, 1.21.3, 1.21.6, 1.21.7, 1.21.9 and the 26.1.x line. Players treated these as stepping stones.
- **NeoForge** is a small share everywhere except 1.21.1 (roughly 3–8% elsewhere on Sodium/Iris, 8–17% on JEI).

### Caveats

- The totals are cumulative, so older versions have had longer to build up downloads. A newer version with a
  similar share is the more popular one *now*. The "new downloads" table avoids this, but covers only the days since the last refresh,
  so a burst (a release, a modpack update) shows more there.
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

## Raw data (2026-11-03)

### dynamicsurroundingsfabric

1.21+ downloads: 3,121,931

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21.1 | 3,121,931 | 100.0% | fabric 84%, neoforge 16% |

### fabric-api

1.21+ downloads: 223,487,672

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 12,174,103 | 5.4% | fabric 100% |
| 1.21.1 | 44,250,358 | 19.8% | fabric 100% |
| 1.21.2 | 391,221 | 0.2% | fabric 100% |
| 1.21.3 | 1,854,455 | 0.8% | fabric 100% |
| 1.21.4 | 15,424,129 | 6.9% | fabric 100% |
| 1.21.5 | 9,587,530 | 4.3% | fabric 100% |
| 1.21.6 | 2,165,502 | 1.0% | fabric 100% |
| 1.21.7 | 1,975,269 | 0.9% | fabric 100% |
| 1.21.8 | 11,158,787 | 5.0% | fabric 100% |
| 1.21.9 | 1,912,749 | 0.9% | fabric 100% |
| 1.21.10 | 13,335,053 | 6.0% | fabric 100% |
| 1.21.11 | 47,547,752 | 21.3% | fabric 100% |
| 26.1 | 9,079,174 | 4.1% | fabric 100% |
| 26.1.1 | 7,013,990 | 3.1% | fabric 100% |
| 26.1.2 | 6,139,488 | 2.7% | fabric 100% |
| 26.2 | 26,014,637 | 11.6% | fabric 100% |
| 26.3 | 13,463,476 | 6.0% | fabric 100% |

### sodium

1.21+ downloads: 205,318,583

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 20,351,078 | 9.9% | fabric 100% |
| 1.21.1 | 39,684,706 | 19.3% | fabric 66%, neoforge 34% |
| 1.21.2 | 1,069,967 | 0.5% | fabric 98%, neoforge 2% |
| 1.21.3 | 1,167,729 | 0.6% | fabric 89%, neoforge 11% |
| 1.21.4 | 13,370,649 | 6.5% | fabric 94%, neoforge 6% |
| 1.21.5 | 9,329,650 | 4.5% | fabric 95%, neoforge 5% |
| 1.21.6 | 4,627,460 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.7 | 4,627,460 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.8 | 4,627,460 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.9 | 6,223,664 | 3.0% | fabric 100% |
| 1.21.10 | 6,774,884 | 3.3% | fabric 92%, neoforge 8% |
| 1.21.11 | 44,105,559 | 21.5% | fabric 96%, neoforge 4% |
| 26.1 | 2,776,254 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.1 | 2,776,254 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.2 | 13,352,334 | 6.5% | fabric 94%, neoforge 6% |
| 26.2 | 20,689,816 | 10.1% | fabric 94%, neoforge 6% |
| 26.3 | 9,763,657 | 4.8% | fabric 95%, neoforge 5% |

### iris

1.21+ downloads: 155,736,888

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 17,248,361 | 11.1% | fabric 50%, quilt 49%, neoforge 1% |
| 1.21.1 | 30,502,227 | 19.6% | fabric 41%, neoforge 32%, quilt 27% |
| 1.21.2 | 402,152 | 0.3% | fabric 70%, quilt 27%, neoforge 3% |
| 1.21.3 | 1,050,356 | 0.7% | fabric 54%, quilt 38%, neoforge 8% |
| 1.21.4 | 8,792,795 | 5.6% | fabric 47%, quilt 47%, neoforge 6% |
| 1.21.5 | 5,899,144 | 3.8% | fabric 48%, quilt 48%, neoforge 5% |
| 1.21.6 | 3,890,277 | 2.5% | fabric 60%, quilt 37%, neoforge 3% |
| 1.21.7 | 3,372,749 | 2.2% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.8 | 3,372,749 | 2.2% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.9 | 5,597,738 | 3.6% | fabric 94%, quilt 6% |
| 1.21.10 | 5,363,190 | 3.4% | fabric 93%, neoforge 6%, quilt 1% |
| 1.21.11 | 32,355,050 | 20.8% | fabric 73%, quilt 24%, neoforge 3% |
| 26.1 | 5,723,092 | 3.7% | fabric 95%, neoforge 5% |
| 26.1.1 | 4,490,240 | 2.9% | fabric 94%, neoforge 6% |
| 26.1.2 | 4,490,240 | 2.9% | fabric 94%, neoforge 6% |
| 26.2 | 15,672,936 | 10.1% | fabric 95%, neoforge 5% |
| 26.3 | 7,513,595 | 4.8% | fabric 95%, neoforge 5% |

### jei

1.21+ downloads: 37,079,666

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 12,824,823 | 34.6% | fabric 60%, neoforge 38%, forge 2% |
| 1.21.1 | 12,282,781 | 33.1% | fabric 58%, neoforge 40%, forge 2% |
| 1.21.4 | 142,591 | 0.4% | neoforge 100% |
| 1.21.5 | 65,890 | 0.2% | neoforge 100% |
| 1.21.7 | 29,721 | 0.1% | neoforge 100% |
| 1.21.8 | 123,309 | 0.3% | neoforge 100% |
| 1.21.9 | 35,451 | 0.1% | neoforge 100% |
| 1.21.10 | 1,036,595 | 2.8% | fabric 88%, neoforge 12% |
| 1.21.11 | 3,911,395 | 10.5% | fabric 88%, neoforge 12% |
| 26.1 | 824,860 | 2.2% | fabric 83%, neoforge 17% |
| 26.1.1 | 780,600 | 2.1% | fabric 83%, neoforge 17% |
| 26.1.2 | 705,440 | 1.9% | fabric 83%, neoforge 17% |
| 26.2 | 2,709,594 | 7.3% | fabric 89%, neoforge 11% |
| 26.3 | 1,606,616 | 4.3% | fabric 91%, neoforge 9% |
