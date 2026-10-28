"""Loudness survey of the mod's OGG sounds, measured with ffmpeg.

Usage: python scripts/sound-survey.py [--csv out.csv] [--sounds-root DIR]

Per file: duration, channels, sample rate, true peak, RMS peak (loudest 50 ms window: the measure for short sounds),
integrated loudness (LUFS, only meaningful from 0.4 s), leading silence, and the sounds.json volume(s) it is played at.
Prints the loudest/quietest files, sounds.json events whose variants differ a lot, stereo files, clipping and
leading silence. Needs ffmpeg on the path.
"""
import argparse
import csv
import json
import math
import os
import re
import subprocess
import sys
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir)
ASSETS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "dsurround")
FILTERS = "astats=measure_perchannel=none,ebur128=peak=true,silencedetect=n=-50dB:d=0.02"


def last_float(pattern, text):
    found = re.findall(pattern, text)
    if not found:
        return None
    try:
        return float(found[-1])
    except ValueError:
        return None


def measure(path):
    out = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", path, "-af", FILTERS, "-f", "null", "-"],
                         capture_output=True, text=True, errors="replace").stderr
    stream = re.search(r"Audio: \w+, (\d+) Hz, (\w+)", out)
    duration = re.search(r"Duration: (\d+):(\d+):([\d.]+)", out)
    seconds = None
    if duration:
        h, m, s = duration.groups()
        seconds = int(h) * 3600 + int(m) * 60 + float(s)
    lufs = last_float(r"I:\s+(-?[\d.]+) LUFS", out)
    starts = [float(x) for x in re.findall(r"silence_start: (-?[\d.]+)", out)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", out)]
    lead = ends[0] if starts and starts[0] <= 0.001 and ends else 0.0
    return {
        "seconds": seconds,
        "rate": int(stream.group(1)) if stream else None,
        "layout": stream.group(2) if stream else "?",
        "true_peak": last_float(r"Peak:\s+(-?[\d.]+|-inf) dBFS", out),
        "peak": last_float(r"Peak level dB: (-?[\d.]+|-inf)", out),
        "rms": last_float(r"RMS level dB: (-?[\d.]+|-inf)", out),
        "rms_peak": last_float(r"RMS peak dB: (-?[\d.]+|-inf)", out),
        "lufs": lufs if lufs is not None and lufs > -69 and seconds and seconds >= 0.4 else None,
        "lead_silence": lead,
    }


def sound_events(sounds_root):
    """Maps a file id (path under sounds/, no extension) to [(event, volume)]."""
    with open(os.path.join(sounds_root, os.pardir, "sounds.json"), encoding="utf-8") as f:
        data = json.load(f)
    uses = defaultdict(list)
    for event, body in data.items():
        for entry in body.get("sounds", []):
            if isinstance(entry, str):
                entry = {"name": entry}
            if entry.get("type", "file") != "file":
                continue
            name = entry["name"].split(":", 1)[-1]
            uses[name].append((event, float(entry.get("volume", 1.0))))
    return uses


def event_levels(rows):
    """Maps each sounds.json event to [(level as played, row)]. One measure per event: integrated LUFS when every
    file has it, else RMS peak for all (the two don't compare)."""
    events = defaultdict(list)
    for r in rows:
        for event, vol in r["uses"]:
            events[event].append((vol, r))
    levels = {}
    for event, items in events.items():
        key = "lufs" if all(r["lufs"] is not None for _, r in items) else "rms_peak"
        measured = [(r[key] + 20 * math.log10(vol), r) for vol, r in items
                    if r[key] is not None and r[key] != float("-inf")]
        if measured:
            levels[event] = sorted(measured, key=lambda x: x[0])
    return levels


def survey(sounds_root):
    files = []
    for folder, _, names in os.walk(sounds_root):
        files += [os.path.join(folder, n) for n in names if n.endswith(".ogg")]
    files.sort()
    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
        results = list(pool.map(measure, files))

    uses = sound_events(sounds_root)
    rows = []
    for path, m in zip(files, results):
        sound_id = os.path.relpath(path, sounds_root)[:-4].replace(os.sep, "/")
        m["id"] = sound_id
        m["path"] = path
        m["uses"] = uses.get(sound_id, [])
        # The level a listener gets at the source: loudness plus the sounds.json volume (the loudest use).
        level = m["lufs"] if m["lufs"] is not None else m["rms_peak"]
        m["level"] = level
        vols = [v for _, v in m["uses"]] or [1.0]
        m["played"] = level + 20 * math.log10(max(vols)) if level is not None else None
        rows.append(m)
    return rows


def fmt(value, width=6):
    return f"{value:{width}.1f}" if value is not None else " " * (width - 1) + "-"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv")
    parser.add_argument("--sounds-root", default=os.path.join(ASSETS, "sounds"))
    args = parser.parse_args()

    rows = survey(args.sounds_root)

    if args.csv:
        with open(args.csv, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["id", "seconds", "rate", "layout", "true_peak", "peak", "rms", "rms_peak", "lufs",
                        "lead_silence", "events", "played_level"])
            for r in rows:
                w.writerow([r["id"], r["seconds"], r["rate"], r["layout"], r["true_peak"], r["peak"], r["rms"],
                            r["rms_peak"], r["lufs"], r["lead_silence"],
                            ";".join(f"{e}@{v}" for e, v in r["uses"]), r["played"]])

    print(f"{len(rows)} files. Level = integrated LUFS from 0.4 s, else RMS peak (50 ms window), dB.")
    print("'played' adds the sounds.json volume.\n")

    measured = [r for r in rows if r["level"] is not None]
    by_level = sorted(measured, key=lambda r: r["level"])
    print("Quietest files:")
    for r in by_level[:12]:
        print(f"  {fmt(r['level'])}  played {fmt(r['played'])}  peak {fmt(r['true_peak'])}  {r['id']}")
    print("Loudest files:")
    for r in by_level[-12:][::-1]:
        print(f"  {fmt(r['level'])}  played {fmt(r['played'])}  peak {fmt(r['true_peak'])}  {r['id']}")

    print("\nEvents whose variants differ by 6 dB or more (level of each file, as played):")
    spread = sorted(((items[-1][0] - items[0][0], event, items) for event, items in event_levels(rows).items()
                     if len(items) > 1), key=lambda s: -s[0])
    for diff, event, items in spread:
        if diff < 6:
            break
        print(f"  {diff:5.1f} dB  {event}: quietest {items[0][1]['id']} ({items[0][0]:.1f}), "
              f"loudest {items[-1][1]['id']} ({items[-1][0]:.1f})")

    stereo = [r["id"] for r in rows if r["layout"] not in ("mono", "?")]
    print(f"\nNot mono ({len(stereo)}; positional sounds need mono to fade with distance):")
    for s in stereo:
        print(f"  {s}")
    clipping = [r for r in rows if r["true_peak"] is not None and r["true_peak"] > -0.1]
    print(f"\nTrue peak above -0.1 dBFS ({len(clipping)}; may clip after decoding):")
    for r in clipping:
        print(f"  {r['true_peak']:5.1f}  {r['id']}")
    lead = [r for r in rows if r["lead_silence"] >= 0.05]
    print(f"\nLeading silence of 50 ms or more ({len(lead)}; delays the sound):")
    for r in sorted(lead, key=lambda r: -r["lead_silence"]):
        print(f"  {r['lead_silence']:5.2f} s  {r['id']}")
    unused = [r["id"] for r in rows if not r["uses"]]
    print(f"\nNot referenced by sounds.json ({len(unused)}):")
    for s in unused:
        print(f"  {s}")
    rates = defaultdict(int)
    for r in rows:
        rates[r["rate"]] += 1
    print(f"\nSample rates: {dict(rates)}")


if __name__ == "__main__":
    sys.exit(main())
