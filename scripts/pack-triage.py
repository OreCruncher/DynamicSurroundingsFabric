#!/usr/bin/env python3
"""
Triage of a modpack run (scripts/bench/README.md, "Modpacks"): what in its log, crash reports and bench results needs
a look, most likely ours first.

    python scripts/pack-triage.py neoforge/run-pack/smp-aesthia     a pack's run folder
    python scripts/pack-triage.py smp-aesthia                       the same, found under either platform
    python scripts/pack-triage.py smp-aesthia --all                 every warning and error, not only the first few

Reports:
  - the pack (pack.json: version, mods, left out) and whether the game started and the scenario finished
  - our startup lines: features off for another mod or a changed hook, shaders loaded or not, drawing that failed
  - mixin trouble, anyone's: failed injections, conflicts, missing targets
  - exceptions, ours (a stack through org.orecruncher) apart from the rest, each kind once with how often
  - warnings and errors by logger, the noisiest first
  - crash reports written during the run
  - the bench results: each run finished or not, and the screenshots to compare, on beside off
"""

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LINE = re.compile(r"^\[(\d\d:\d\d:\d\d)\] \[([^\]]*?)/(\w+)\] \(([^)]*)\) (.*)$")
OURS = "org.orecruncher"
MIXIN = re.compile(r"InvalidInjection|MixinApplyError|MixinTransformerError|InjectionError|@Mixin target .* was not found|"
                   r"Critical injection failure|conflict|Mixin apply .* failed|Error loading class", re.IGNORECASE)
EXCEPTION = re.compile(r"^(?:Caused by: )?([\w.$]+(?:Exception|Error|Throwable))(?::\s*(.*))?$")
OUR_STARTUP = [
    (re.compile(r": off \(.*installed\)$"), "feature off for another mod"),
    (re.compile(r": off \(the game code it hooks was changed by another mod\)$"), "optional hook didn't apply"),
    (re.compile(r"^Unable to load the .* shader"), "shader failed"),
    (re.compile(r"^Drawing .* failed"), "drawing failed"),
    (re.compile(r"^Unable to tell whether the optional game hooks applied"), "hooks unknown"),
    # Not the profile summary's Display line, which names a shader pack in use
    (re.compile(r"^(?!Display:).*Iris"), "shaders"),
]


def find_run_folder(spec):
    path = Path(spec)
    if path.is_dir():
        return path
    for platform in ("neoforge", "fabric"):
        candidate = ROOT / platform / "run-pack" / spec
        if candidate.is_dir():
            return candidate
    sys.exit(f"{spec}: no such run folder")


def read_log(path):
    """The log's entries: (time, thread, level, logger, message, following lines: a stack trace)"""
    entries = []
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = LINE.match(raw)
        if m:
            entries.append([*m.groups(), []])
        elif entries:
            entries[-1][5].append(raw.strip())
    return entries


def exceptions(entries):
    """Each exception kind: (type, first line of ours or the top frame) -> count, example, whether ours"""
    found = {}
    for time, thread, level, logger, message, rest in entries:
        lines = [message] + rest
        for i, line in enumerate(lines):
            m = EXCEPTION.match(line)
            if not m:
                continue
            frames = [l for l in lines[i + 1:i + 40] if l.startswith("at ")]
            ours = next((f for f in frames if OURS in f), None)
            where = ours or (frames[0] if frames else "")
            key = (m.group(1), where)
            if key not in found:
                found[key] = {"count": 0, "ours": ours is not None, "message": (m.group(2) or "")[:200],
                              "logged": f"{time} [{level}] ({logger}) {message[:160]}"}
            found[key]["count"] += 1
            break
    return found


def bench_results(folder):
    latest = folder / "bench-results" / "latest.txt"
    if not latest.exists():
        return None
    results = Path(latest.read_text(encoding="utf-8").strip())
    if not results.is_absolute():
        results = folder / "bench-results" / results
    manifest = results / "manifest.json"
    return results, json.loads(manifest.read_text(encoding="utf-8")) if manifest.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("run", help="a pack's run folder, or its slug")
    parser.add_argument("--all", action="store_true", help="every warning and error line")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    folder = find_run_folder(args.run)
    pack_file = folder / "pack.json"
    if pack_file.exists():
        pack = json.loads(pack_file.read_text(encoding="utf-8"))
        mods = sum(1 for f in pack["fetched"] if f.startswith("mods/") and f.endswith(".jar"))
        print(f"== {pack.get('name')} {pack.get('version')} ({pack['pack']}): {mods} mods, shaders "
              f"{'on' if pack.get('shaders') else 'off'}; made for {pack.get('dependencies')}")
        for e in pack.get("excluded", []):
            print(f"   left out {e['file']} ({e['why']})")

    log = folder / "logs" / "latest.log"
    if not log.exists():
        sys.exit(f"{log}: no log")
    entries = read_log(log)
    messages = [e[4] for e in entries]
    started = any("Bench: " in m for m in messages) or any("Loaded the dsurround:" in m for m in messages)
    print(f"\n== Start: {'reached the game' if started else 'did NOT reach the game'}; {len(entries)} log entries")
    loaded = sum(1 for m in messages if m.startswith("Loaded the dsurround:"))
    print(f"   our shaders loaded: {loaded}")
    if not started:
        # Why: the mod loader's errors (missing or wrong versions of mods, two copies of one), each kind once
        seen = set()
        for time, thread, level, logger, message, rest in entries:
            if level in ("ERROR", "FATAL") and logger in ("FMLLoader", "ModSorter", "Entrypoint", "LoadingModList"):
                text = " ".join([message] + rest[:2])[:220]
                if text not in seen:
                    seen.add(text)
                    print(f"   {time} ({logger}) {text}")
            if len(seen) >= (1000 if args.all else 12):
                break

    print("\n== Ours at startup")
    shown = 0
    for time, thread, level, logger, message, rest in entries:
        if logger not in ("dsurround", "Dynamic Surroundings"):
            continue
        for pattern, what in OUR_STARTUP:
            if pattern.search(message):
                print(f"   {time} [{level}] {what}: {message[:200]}")
                shown += 1
                break
    if not shown:
        print("   nothing off, failed or unusual")

    print("\n== Mixin trouble")
    mixin = [(e[0], e[2], e[3], e[4]) for e in entries if MIXIN.search(e[4]) and e[2] in ("WARN", "ERROR", "FATAL")]
    for time, level, logger, message in mixin[: None if args.all else 30]:
        print(f"   {time} [{level}] ({logger}) {message[:220]}")
    if not mixin:
        print("   none")
    elif len(mixin) > 30 and not args.all:
        print(f"   ... {len(mixin) - 30} more (--all)")

    found = exceptions(entries)
    for label, ours in (("Exceptions through our code", True), ("Other exceptions", False)):
        rows = sorted(((k, v) for k, v in found.items() if v["ours"] == ours), key=lambda kv: -kv[1]["count"])
        print(f"\n== {label}: {len(rows)} kinds")
        for (kind, where), v in rows[: None if args.all or ours else 15]:
            print(f"   {v['count']:>4}x {kind}: {v['message']}")
            print(f"         {where[:200]}")
            print(f"         logged: {v['logged']}")
        if not ours and len(rows) > 15 and not args.all:
            print(f"   ... {len(rows) - 15} more (--all)")

    print("\n== Warnings and errors by logger")
    by_logger = defaultdict(Counter)
    for e in entries:
        if e[2] in ("WARN", "ERROR", "FATAL"):
            by_logger[e[3]][e[2]] += 1
    for logger, counts in sorted(by_logger.items(), key=lambda kv: -sum(kv[1].values()))[: None if args.all else 20]:
        print(f"   {sum(counts.values()):>5}  {logger:<40} " + ", ".join(f"{k} {v}" for k, v in counts.items()))

    # The same warning over and over (worldgen mods, a missing translation): numbers and names in quotes made alike
    print("\n== Most repeated warnings and errors")
    repeated = Counter()
    for e in entries:
        if e[2] in ("WARN", "ERROR", "FATAL"):
            repeated[(e[2], e[3], re.sub(r"-?\d+(\.\d+)?", "N", e[4])[:150])] += 1
    for (level, logger, text), count in repeated.most_common(None if args.all else 12):
        if count < 3 and not args.all:
            break
        print(f"   {count:>5}x [{level}] ({logger}) {text}")

    crashes = sorted((folder / "crash-reports").glob("*.txt")) if (folder / "crash-reports").is_dir() else []
    after = log.stat().st_mtime - 6 * 3600
    crashes = [c for c in crashes if c.stat().st_mtime >= after]
    print(f"\n== Crash reports: {len(crashes)}")
    for c in crashes:
        text = c.read_text(encoding="utf-8", errors="replace").splitlines()
        description = next((l for l in text if l.startswith("Description:")), "")
        print(f"   {c.name}: {description}")

    bench = bench_results(folder)
    print("\n== Bench")
    if not bench:
        print("   no results")
        return
    results, manifest = bench
    if not manifest:
        print(f"   {results}: no manifest")
        return
    print(f"   {results}: {'finished' if manifest.get('finished') else 'NOT finished'}"
          + (f", error: {manifest['error']}" if manifest.get("error") else "")
          + (f", opened over {manifest['openedOver']}" if manifest.get("openedOver") else ""))
    shots = results / "screenshots"
    pairs = defaultdict(dict)
    for run in manifest.get("runs", []):
        label = run.get("label", "")
        state = "finished" if "finished" in run else "NOT finished"
        loc = run.get("location", {})
        print(f"   {run.get('index', 0):>2} {label:<20} {state:<13} {run.get('how') or '':<10} {loc.get('biome', '')}")
        scene, _, side = label.rpartition("-")
        if side in ("on", "off"):
            shot = shots / f"{run.get('index', 0):02d}-{label}.png"
            if shot.exists():
                pairs[scene][side] = shot
    if pairs:
        print("   screenshots, on beside off:")
        for scene, sides in pairs.items():
            print(f"     {scene:<12} {sides.get('on', '-')}  |  {sides.get('off', '-')}")


if __name__ == "__main__":
    main()
