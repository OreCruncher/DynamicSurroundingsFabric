# Minecraft version popularity (modded, 1.21+)

**Generated:** 2026-11-17, from the Modrinth public API (no API key needed). Previous refresh: 2026-11-03.

**Purpose:** decide which Minecraft versions are worth targeting, and which have little following.

**License:** this text and its tables are © OreCruncher, under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/):
reuse them, with credit and a link to this file. The download counts themselves are Modrinth's public figures. The
script that gathers them (`scripts/modrinth-version-stats.py`) is MIT.

## Summary

Share of 1.21+ downloads by game version, for mods that nearly every player on a version installs (all downloads
to date):

| Version | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 1.21.11 | **21.3%** | **21.4%** | **20.8%** |
| 1.21.1 | **20.0%** | **19.4%** | **19.7%** |
| 26.2 | **11.4%** | 9.9% | 9.9% |
| 1.21.4 | 7.0% | 6.6% | 5.8% |
| 1.21.10 | 6.1% | 3.4% | 3.5% |
| 1.21 | 5.6% | 10.1% | 11.3% |
| 26.3 | 5.3% | 4.2% | 4.1% |
| 1.21.8 | 5.1% | 2.3% | 2.2% |
| 1.21.5 | 4.4% | 4.6% | 3.9% |
| 26.1 / 26.1.1 / 26.1.2 | 4.1 / 3.2 / 2.8% | 1.4 / 1.4 / 6.5% | 3.7 / 2.9 / 2.9% |
| 1.21.2, .3, .6, .7, .9 | 0.2–1.0% each | 0.5–3.1% | 0.3–3.7% |

### Where new downloads went

**Not measured this refresh.** Between 2026-11-03 and 2026-11-17 Modrinth lowered its counts: every version of every
project here has fewer downloads than two weeks ago, so the difference between the runs is negative everywhere. The
newer the version, the more was taken off: 26.3 lost 13-19% (Fabric API 13.46 to 11.46 million), 26.2 4.5-5.7%,
1.21.11 2.6-3.0%, 1.21.1 1.8-2.6%, the rest mostly under 2%. This mod's 1.21.1 count fell by 21,566 (0.7%). The project totals still match the
sum of their files, so it isn't files deleted. A Modrinth contributor called a similar drop on another mod a bug on
2026-11-07 ([modrinth/code#8250](https://github.com/modrinth/code/issues/8250)); nothing official was found.

The last period that could be measured, kept for reference:

| Version (2026-10-23 to 2026-11-03) | Fabric API | Sodium | Iris |
|---|---:|---:|---:|
| 26.3 | **43.7%** | **36.6%** | **38.5%** |
| 1.21.11 | 20.1% | 21.3% | 18.8% |
| 1.21.1 | 12.1% | 18.1% | 18.3% |
| 26.2 | 11.5% | 10.8% | 11.2% |
| All others | 12.7% | 13.1% | 13.2% |

As Modrinth's correction took the most from 26.3, that table likely overstates 26.3's share of the period. The next
refresh, measured from this one, should show where downloads go on the corrected counts.

Dynamic Surroundings now has downloads on all three lines: 1.21.1 3,100,365, 26.3 3,034 (5.1.0 and 5.2.0, since
2026-11-07), 1.21.11 1,088 (5.0.0).

## Analysis

- **26.3 is the newest release, and the one Dynamic Surroundings' 26.x line targets.** On 2026-11-03 it took the
  largest share of new downloads (37–44%), but Modrinth has since taken 13–19% off its counts, more than off any other
  version, so how far ahead it is can't be said from these numbers. Its total share is 4–5%, as it has been out only
  a few weeks. Measure the next period before reading much into it.
- **26.2 is fading.** It took about 11% of new downloads in the last measured period, down from 13–14% before it, and
  the 26.2 build doesn't run on 26.3 (its rendering API moved). 26.2 itself was never released for the mod.
- **1.21.1 is a long-lived target.** It took 12–18% of new downloads in the last measured period, more than 26.2.
  Large NeoForge modpacks (e.g. All the Mods 10) are built on it, and it has by far the highest NeoForge share (about
  a third of Sodium and Iris downloads for that version). Nearly all of Dynamic Surroundings' 1.21+ Modrinth downloads
  are on 1.21.1; the 26.3 and 1.21.11 builds are a few weeks old.
- **1.21.11 is the other anchor.** It is the last 1.x release, and holds about a fifth of new downloads.
- **Middle tier:** 1.21.4, 1.21.8 and 1.21.10. Each takes about 2% of new downloads or less. Worth supporting only if the port is
  cheap.
- **Skip:** 1.21.2, 1.21.3, 1.21.6, 1.21.7, 1.21.9 and the 26.1.x line. Players treated these as stepping stones.
- **NeoForge** is a small share everywhere except 1.21.1 (roughly 3–8% elsewhere on Sodium/Iris, 8–17% on JEI).

### Caveats

- Modrinth corrects its counts now and then, and a correction can make a refresh's difference meaningless (it did
  for 2026-11-17, see above). Compare totals only between runs with no correction in between.
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

## Raw data (2026-11-17)

### dynamicsurroundingsfabric

1.21+ downloads: 3,104,487

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21.1 | 3,100,365 | 99.9% | fabric 84%, neoforge 16% |
| 1.21.11 | 1,088 | 0.0% | fabric 87%, neoforge 13% |
| 26.3 | 3,034 | 0.1% | fabric 79%, neoforge 21% |

### fabric-api

1.21+ downloads: 217,231,277

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 12,090,543 | 5.6% | fabric 100% |
| 1.21.1 | 43,434,654 | 20.0% | fabric 100% |
| 1.21.2 | 386,284 | 0.2% | fabric 100% |
| 1.21.3 | 1,844,522 | 0.8% | fabric 100% |
| 1.21.4 | 15,283,024 | 7.0% | fabric 100% |
| 1.21.5 | 9,531,570 | 4.4% | fabric 100% |
| 1.21.6 | 2,153,733 | 1.0% | fabric 100% |
| 1.21.7 | 1,970,109 | 0.9% | fabric 100% |
| 1.21.8 | 11,103,474 | 5.1% | fabric 100% |
| 1.21.9 | 1,911,048 | 0.9% | fabric 100% |
| 1.21.10 | 13,236,854 | 6.1% | fabric 100% |
| 1.21.11 | 46,244,501 | 21.3% | fabric 100% |
| 26.1 | 8,932,561 | 4.1% | fabric 100% |
| 26.1.1 | 6,883,664 | 3.2% | fabric 100% |
| 26.1.2 | 6,013,773 | 2.8% | fabric 100% |
| 26.2 | 24,748,947 | 11.4% | fabric 100% |
| 26.3 | 11,462,016 | 5.3% | fabric 100% |

### sodium

1.21+ downloads: 200,220,338

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 20,234,310 | 10.1% | fabric 100% |
| 1.21.1 | 38,787,206 | 19.4% | fabric 66%, neoforge 34% |
| 1.21.2 | 1,065,496 | 0.5% | fabric 98%, neoforge 2% |
| 1.21.3 | 1,162,673 | 0.6% | fabric 89%, neoforge 11% |
| 1.21.4 | 13,271,143 | 6.6% | fabric 94%, neoforge 6% |
| 1.21.5 | 9,287,023 | 4.6% | fabric 95%, neoforge 5% |
| 1.21.6 | 4,607,329 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.7 | 4,607,329 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.8 | 4,607,329 | 2.3% | fabric 94%, neoforge 6% |
| 1.21.9 | 6,185,500 | 3.1% | fabric 100% |
| 1.21.10 | 6,732,285 | 3.4% | fabric 92%, neoforge 8% |
| 1.21.11 | 42,870,990 | 21.4% | fabric 96%, neoforge 4% |
| 26.1 | 2,747,467 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.1 | 2,747,467 | 1.4% | fabric 96%, neoforge 4% |
| 26.1.2 | 13,078,370 | 6.5% | fabric 94%, neoforge 6% |
| 26.2 | 19,757,663 | 9.9% | fabric 94%, neoforge 6% |
| 26.3 | 8,470,755 | 4.2% | fabric 93%, neoforge 7% |

### iris

1.21+ downloads: 151,576,673

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 17,118,100 | 11.3% | fabric 50%, quilt 49%, neoforge 1% |
| 1.21.1 | 29,813,796 | 19.7% | fabric 40%, neoforge 32%, quilt 27% |
| 1.21.2 | 400,797 | 0.3% | fabric 70%, quilt 27%, neoforge 3% |
| 1.21.3 | 1,045,029 | 0.7% | fabric 54%, quilt 38%, neoforge 8% |
| 1.21.4 | 8,727,237 | 5.8% | fabric 47%, quilt 47%, neoforge 6% |
| 1.21.5 | 5,876,928 | 3.9% | fabric 48%, quilt 48%, neoforge 5% |
| 1.21.6 | 3,875,262 | 2.6% | fabric 59%, quilt 37%, neoforge 3% |
| 1.21.7 | 3,358,556 | 2.2% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.8 | 3,358,556 | 2.2% | fabric 61%, quilt 35%, neoforge 4% |
| 1.21.9 | 5,564,354 | 3.7% | fabric 94%, quilt 6% |
| 1.21.10 | 5,327,076 | 3.5% | fabric 93%, neoforge 6%, quilt 1% |
| 1.21.11 | 31,514,573 | 20.8% | fabric 72%, quilt 24%, neoforge 3% |
| 26.1 | 5,635,512 | 3.7% | fabric 95%, neoforge 5% |
| 26.1.1 | 4,411,615 | 2.9% | fabric 94%, neoforge 6% |
| 26.1.2 | 4,411,615 | 2.9% | fabric 94%, neoforge 6% |
| 26.2 | 14,935,319 | 9.9% | fabric 95%, neoforge 5% |
| 26.3 | 6,202,349 | 4.1% | fabric 93%, neoforge 7% |

### jei

1.21+ downloads: 35,805,055

| Version | Downloads | Share | Loaders |
|---|---:|---:|---|
| 1.21 | 12,506,252 | 34.9% | fabric 60%, neoforge 38%, forge 2% |
| 1.21.1 | 11,964,888 | 33.4% | fabric 58%, neoforge 39%, forge 2% |
| 1.21.4 | 141,077 | 0.4% | neoforge 100% |
| 1.21.5 | 65,516 | 0.2% | neoforge 100% |
| 1.21.7 | 29,602 | 0.1% | neoforge 100% |
| 1.21.8 | 122,527 | 0.3% | neoforge 100% |
| 1.21.9 | 35,349 | 0.1% | neoforge 100% |
| 1.21.10 | 1,027,994 | 2.9% | fabric 88%, neoforge 12% |
| 1.21.11 | 3,795,062 | 10.6% | fabric 87%, neoforge 13% |
| 26.1 | 808,483 | 2.3% | fabric 83%, neoforge 17% |
| 26.1.1 | 764,467 | 2.1% | fabric 83%, neoforge 17% |
| 26.1.2 | 689,391 | 1.9% | fabric 83%, neoforge 17% |
| 26.2 | 2,553,885 | 7.1% | fabric 89%, neoforge 11% |
| 26.3 | 1,300,563 | 3.6% | fabric 89%, neoforge 11% |
