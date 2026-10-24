"""Builds a resource pack of leveled copies of the mod's sounds, to compare with the originals in game.

Usage: python scripts/sound-level-pack.py OUT_DIR [--spread 6] [--tolerance 3] [--ceiling -1] [--quality 6]

For each sounds.json event whose variants differ by --spread dB or more (as sound-survey.py measures them), files
more than --tolerance dB from the event's median are brought to the median. Any boost stops where the true peak
would pass --ceiling dBTP, and files already peaking above -0.1 dBTP are turned down to it. Each changed file is
re-encoded with libvorbis at --quality and measured again. Writes OUT_DIR/<pack> and OUT_DIR/<pack>.zip; toggle the
pack in game (F3+T reloads) to switch between the two.
"""
import argparse
import importlib.util
import json
import os
import shutil
import statistics
import subprocess
import sys
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("sound_survey", os.path.join(HERE, "sound-survey.py"))
survey = importlib.util.module_from_spec(spec)
spec.loader.exec_module(survey)

PACK_NAME = "dsurround-leveled-sounds"
PACK_FORMAT = 34  # Minecraft 1.21.1


def plan(rows, spread_db, tolerance, ceiling):
    wanted = defaultdict(list)  # id -> [(gain, event)]
    for event, items in survey.event_levels(rows).items():
        if len(items) < 2 or items[-1][0] - items[0][0] < spread_db:
            continue
        median = statistics.median(level for level, _ in items)
        for level, r in items:
            if abs(level - median) > tolerance:
                wanted[r["id"]].append((median - level, event))

    changes = {}
    for r in rows:
        peak = r["true_peak"]
        if peak is None or peak == float("-inf"):
            continue
        gains = wanted.get(r["id"])
        note = ""
        if gains:
            gain = sum(g for g, _ in gains) / len(gains)
            if max(g for g, _ in gains) - min(g for g, _ in gains) > 2:
                note = "events disagree: " + ", ".join(f"{e} {g:+.1f}" for g, e in gains)
            reason = "level " + "/".join(e for _, e in gains)
        elif peak > -0.1:
            gain, reason = 0.0, "peak"
        else:
            continue
        if peak + gain > ceiling:
            capped = ceiling - peak
            if gains and capped < gain:
                note = (note + "; " if note else "") + f"wanted {gain:+.1f}, peak allows {capped:+.1f}"
            gain = capped
        if abs(gain) >= 0.5:
            changes[r["id"]] = (gain, reason, note, r)
    return changes


def encode(src, dst, gain, quality, rate):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", src, "-af", f"volume={gain:.2f}dB",
           "-c:a", "libvorbis", "-q:a", str(quality), "-map_metadata", "0"]
    if rate:
        cmd += ["-ar", str(rate)]
    subprocess.run(cmd + [dst], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("out_dir")
    parser.add_argument("--spread", type=float, default=6.0)
    parser.add_argument("--tolerance", type=float, default=3.0)
    parser.add_argument("--ceiling", type=float, default=-1.0)
    parser.add_argument("--quality", type=float, default=6)
    args = parser.parse_args()

    sounds_root = os.path.join(survey.ASSETS, "sounds")
    rows = survey.survey(sounds_root)
    changes = plan(rows, args.spread, args.tolerance, args.ceiling)

    pack = os.path.join(args.out_dir, PACK_NAME)
    shutil.rmtree(pack, ignore_errors=True)
    os.makedirs(pack)
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": PACK_FORMAT,
                            "description": f"Dynamic Surroundings: {len(changes)} sounds leveled"}}, f, indent=2)

    def make(item):
        sound_id, (gain, _, _, r) = item
        dst = os.path.join(pack, "assets", "dsurround", "sounds", *sound_id.split("/")) + ".ogg"
        encode(r["path"], dst, gain, args.quality, r["rate"])
        return sound_id, survey.measure(dst)

    with ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
        after = dict(pool.map(make, sorted(changes.items())))

    print(f"{len(changes)} files changed. Level (as sound-survey measures it) and true peak, before -> after.\n")
    print(f"{'gain':>6}  {'level':>13}  {'peak':>11}  file  (why)")
    for sound_id, (gain, reason, note, r) in sorted(changes.items(), key=lambda c: c[1][1] + c[0]):
        a = after[sound_id]
        key = "lufs" if r["lufs"] is not None else "rms_peak"
        print(f"{gain:+6.1f}  {r[key]:6.1f}>{a[key] if a[key] is not None else float('nan'):6.1f}  "
              f"{r['true_peak']:4.1f}>{a['true_peak']:5.1f}  {sound_id}  ({reason}){'  ' + note if note else ''}")

    shutil.make_archive(pack, "zip", pack)
    print(f"\nPack: {pack}.zip")


if __name__ == "__main__":
    sys.exit(main())
