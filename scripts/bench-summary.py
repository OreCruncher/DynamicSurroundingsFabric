#!/usr/bin/env python3
"""
Summarizes a bench results folder (scripts/bench/README.md): each run's recording through jfr-summary.py, the runs of
one label averaged over the scenario's repeats, as tables to compare the settings: the frame (fps, GPU and CPU time
drawing it), each render timer (GPU and CPU ms a frame), each client tick handler (ms a tick), with the client tick,
and enhanced sound processing where a recording has it (scripts/bench/sound.json).

    python scripts/bench-summary.py <results folder>          (default: the newest, from bench-results/latest.txt)
    python scripts/bench-summary.py <folder> --spread          also each label's lowest and highest run

A timer or handler a run doesn't have counts as 0 there (the effect wasn't drawn, the handler did nothing).
"""

import argparse
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
DRAWING = re.compile(r"drawing a frame .*mean: GPU ([\d.]+), CPU ([\d.]+)")
FPS = re.compile(r"^\s+(\d+) fps; frame mean ([\d.]+)")
# A render timer row: name, then gpu mean, gpu end, gpu peak, cpu mean, ...
TIMER = re.compile(r"^  (\S.*?)\s+([\d.]+)\s+[\d.]+\s+[\d.]+\s+([\d.]+)\s+[\d.]+\s+[\d.]+\s+\d+\s+\S+\s+\S+\s+\S+$")
# A handler row: name, ran %, p50, p95, p99, max, mean a tick, share
HANDLER = re.compile(r"^  (\S.*?)\s+\d+%\s+[\d.]+\s+[\d.]+\s+[\d.]+\s+[\d.]+\s+([\d.]+)\s+[\d.]+%$")
# Enhanced sounds: a row of work (a second, mean, peak, ms a second), the sources and the traces
SOUND_ROW = re.compile(r"^  (start|update|pass|upload|trace)\s+([\d.]+)\s+([\d.]+)\s+(?:[\d.]+|-)\s+([\d.]+)$")
SOUND_SOURCES = re.compile(r"^  sources: mean ([\d.]+)")
SOUND_KIND = re.compile(r"(traced|shared|reused|skipped) ([\d.]+) a second")
WHOLE_TICK = re.compile(r"^  whole tick\s+[\d.]+\s+[\d.]+\s+[\d.]+\s+[\d.]+\s+([\d.]+)$")


def summarize(recording):
    """The numbers of one recording, from jfr-summary.py's text"""
    text = subprocess.run([sys.executable, str(SCRIPTS / "jfr-summary.py"), str(recording)],
                          capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    numbers = {"frame": {}, "timers": {}, "handlers": {}, "sound": {}}
    section = None
    for line in text.splitlines():
        if line.startswith("== "):
            section = "timers" if line.startswith("== Render timers") else \
                "handlers" if line.startswith("== Client tick handlers") else \
                "sound" if line.startswith("== Enhanced sounds") else None
            continue
        if m := FPS.match(line):
            numbers["frame"]["fps"] = float(m.group(1))
        elif m := DRAWING.search(line):
            numbers["frame"]["gpu"] = float(m.group(1))
            numbers["frame"]["cpu"] = float(m.group(2))
        elif section == "timers" and (m := TIMER.match(line)) and m.group(1) != "effect":
            numbers["timers"][m.group(1).strip()] = (float(m.group(2)), float(m.group(3)))
        elif section == "sound" and (m := SOUND_ROW.match(line)):
            numbers["sound"][m.group(1)] = (float(m.group(2)), float(m.group(3)), float(m.group(4)))
        elif section == "sound" and (m := SOUND_SOURCES.match(line)):
            numbers["sound"]["sources"] = float(m.group(1))
        elif section == "sound" and line.startswith("  calculations:"):
            for kind, rate in SOUND_KIND.findall(line):
                numbers["sound"][kind] = float(rate)
        elif section == "handlers" and (m := WHOLE_TICK.match(line)):
            numbers["handlers"]["(whole tick)"] = float(m.group(1))
        elif section == "handlers" and (m := HANDLER.match(line)):
            numbers["handlers"][m.group(1).strip()] = float(m.group(2))
    return numbers


def mean(values):
    return sum(values) / len(values) if values else 0.0


def spread(values, digits):
    return f" ({min(values):.{digits}f}-{max(values):.{digits}f})" if len(values) > 1 else ""


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", nargs="?")
    parser.add_argument("--spread", action="store_true")
    args = parser.parse_args()

    if args.folder:
        folder = Path(args.folder)
    else:
        latest = next(Path(".").glob("*/run-bench/bench-results/latest.txt"), None)
        if latest is None:
            sys.exit("no results folder given, and no */run-bench/bench-results/latest.txt")
        folder = Path(latest.read_text(encoding="utf-8").strip())
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    print(f"{folder.name}: {'finished' if manifest.get('finished') else 'NOT finished'}"
          + (f", error: {manifest['error']}" if manifest.get("error") else ""))
    if manifest.get("modChangesSinceScenes"):
        print(f"  mods changed since the scenes were found: {', '.join(manifest['modChangesSinceScenes'])}")

    # Each label's runs, in the order the labels first ran
    runs = defaultdict(list)
    scenes = {}
    for run in manifest.get("runs", []):
        if "recording" not in run:
            continue
        runs[run["label"]].append(summarize(folder / run["recording"]))
        scenes[run["label"]] = run["scene"]
        flags = [f for f in ("terrainNotSettled", "error", "soundsMuted") if run.get(f)]
        if run.get("enhancedSounds") is False:
            flags.append("enhanced sounds not running")
        if flags:
            print(f"  {run['index']:02d} {run['label']}: {', '.join(flags)}")

    # By scene, so each table compares one scene's settings
    for scene in dict.fromkeys(scenes.values()):
        labels = [label for label in runs if scenes[label] == scene]
        print(f"\n== {scene} ({len(runs[labels[0]])} run(s) a label)")
        width = max(len(label) for label in labels) + 2

        print(f"  {'frame':{width}} {'fps':>6} {'GPU ms':>8} {'CPU ms':>8}")
        for label in labels:
            frames = [r["frame"] for r in runs[label]]
            gpu = [f.get("gpu", 0.0) for f in frames]
            line = (f"  {label:{width}} {mean([f.get('fps', 0.0) for f in frames]):6.0f} {mean(gpu):8.3f} "
                    f"{mean([f.get('cpu', 0.0) for f in frames]):8.3f}")
            print(line + (spread(gpu, 3) if args.spread else ""))

        timers = list(dict.fromkeys(t for label in labels for r in runs[label] for t in r["timers"]))
        if timers:
            print(f"\n  {'render timers, GPU/CPU ms a frame':{width}}" + "".join(f" {t[:18]:>20}" for t in timers))
            for label in labels:
                cells = []
                for t in timers:
                    values = [r["timers"].get(t, (0.0, 0.0)) for r in runs[label]]
                    cells.append(f"{mean([v[0] for v in values]):.3f}/{mean([v[1] for v in values]):.3f}")
                print(f"  {label:{width}}" + "".join(f" {c:>20}" for c in cells))

        sounded = [label for label in labels if any(r["sound"] for r in runs[label])]
        if sounded:
            # Starts are on the sound engine's thread, which waits for them; updates and passes on the workers'. A trace
            # is the rays of one calculation that traced, inside a start or an update
            print(f"\n  {'enhanced sounds, ms':{width}} {'sources':>8} {'starts/s':>9} {'start':>8} {'start ms/s':>11} "
                  f"{'update':>8} {'update ms/s':>12} {'pass':>8} {'trace':>8} {'traced/s':>9} {'shared/s':>9} "
                  f"{'reused/s':>9} {'skipped/s':>10}")
            for label in sounded:
                sound = [r["sound"] for r in runs[label]]
                row = lambda key, i: mean([s.get(key, (0.0, 0.0, 0.0))[i] for s in sound])
                value = lambda key: mean([s.get(key, 0.0) for s in sound])
                print(f"  {label:{width}} {value('sources'):8.1f} {row('start', 0):9.1f} {row('start', 1):8.3f} "
                      f"{row('start', 2):11.3f} {row('update', 1):8.3f} {row('update', 2):12.3f} {row('pass', 1):8.3f} "
                      f"{row('trace', 1):8.3f} {value('traced'):9.1f} {value('shared'):9.1f} {value('reused'):9.1f} "
                      f"{value('skipped'):10.1f}")

        handlers = list(dict.fromkeys(h for label in labels for r in runs[label] for h in r["handlers"]))
        # The costliest first, the whole tick last
        handlers.sort(key=lambda h: (h == "(whole tick)", -max(mean([r["handlers"].get(h, 0.0) for r in runs[l]]) for l in labels)))
        if handlers:
            print(f"\n  {'handlers, ms a tick':{width}}" + "".join(f" {h[:16]:>17}" for h in handlers[:7]))
            for label in labels:
                cells = [f"{mean([r['handlers'].get(h, 0.0) for r in runs[label]]):.3f}" for h in handlers[:7]]
                print(f"  {label:{width}}" + "".join(f" {c:>17}" for c in cells))


if __name__ == "__main__":
    main()
