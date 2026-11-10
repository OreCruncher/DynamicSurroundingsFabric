# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Cuts a sound's clips from a source recording, as a recipe says, with ffmpeg.

Usage: python scripts/sound-cut.py RECIPE --source FILE [--out DIR] [--install]

A recipe (scripts/sound-recipes/<name>.json) names the source (link, author, license), the settings and each clip's
start and end in the source, in seconds; a clip with "loop": SECONDS is an ambience that repeats, its end crossfaded
into its start over that many seconds of the recording after it. Each clip is cut in 32-bit float with a short lead-in and tail, made mono,
high-passed, faded in and out, resampled, brought to the target level (the loudest 50 ms window's RMS, as
sound-survey.py measures it: the measure for short sounds), kept under the true peak ceiling, and encoded to Ogg Vorbis
once, at the end. The clips go to build/sound-rework/<name>/ for review with a report: each clip's level, true peak
and length against the file it replaces, if there is one; --install then copies them over the mod's files. The
source itself never changes, and isn't kept in the repository: give its path with --source.
"""
import argparse
import csv
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir))
SOUNDS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "dsurround", "sounds")
DEFAULTS = {
    # Hz; mono for positional sounds (Minecraft only places mono ones)
    "rate": 44100,
    "channel": "mix",           # mix, left or right
    "highpass": 80,             # Hz: rumble and DC offset out; 0 for none
    "lowpass": 0,               # Hz: 0 for none
    "pad_before": 0.005,        # seconds of the source kept before a clip's start, faded in
    "pad_after": 0.03,          # and after its end, faded out
    "fade_in": 0.003,
    "fade_out": 0.03,
    "target_rms_peak": -22.0,   # dB, the loudest 50 ms window
    # dB, the whole clip's RMS instead, when set: for an ambience, whose loudest moment (a bird close by) says little of
    # how loud it plays
    "target_rms": None,
    "max_true_peak": -1.0,      # dBTP: Vorbis can push peaks up
    # Spiky clips (sparks, clicks) can't reach the target level under the ceiling: a fast limiter shaves the spikes,
    # a few milliseconds each, so the rest can. Only what would cross the ceiling is touched. False leaves them quieter
    "limit": True,
    "quality": 5,               # libvorbis -q:a
}


def load_survey():
    spec = importlib.util.spec_from_file_location("sound_survey", os.path.join(ROOT, "scripts", "sound-survey.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def run(cmd):
    result = subprocess.run(cmd, capture_output=True, text=True, errors="replace", stdin=subprocess.DEVNULL)
    if result.returncode != 0:
        sys.exit(f"ffmpeg failed: {' '.join(cmd)}\n{result.stderr[-2000:]}")


def cut(source, clip, s, work):
    out = os.path.join(work, clip["file"].replace("/", "_") + ".wav")
    pan = {"mix": "pan=mono|c0=0.5*c0+0.5*c1", "left": "pan=mono|c0=c0", "right": "pan=mono|c0=c1"}[s["channel"]]
    filters = []
    if s["highpass"]:
        filters.append(f"highpass=f={s['highpass']}")
    if s["lowpass"]:
        filters.append(f"lowpass=f={s['lowpass']}")
    if clip.get("loop"):
        return cut_loop(source, clip, s, out, pan, filters)
    start = max(0.0, clip["start"] - s["pad_before"])
    end = clip["end"] + s["pad_after"]
    length = end - start
    chain = [f"atrim=start={start:.4f}:end={end:.4f}", "asetpts=PTS-STARTPTS", pan] + filters
    chain += [f"afade=t=in:d={s['fade_in']}", f"afade=t=out:st={length - s['fade_out']:.4f}:d={s['fade_out']}",
              f"aresample={s['rate']}:resampler=soxr"]
    run(["ffmpeg", "-v", "error", "-y", "-i", source, "-af", ",".join(chain), "-c:a", "pcm_f32le", out])
    return out


def cut_loop(source, clip, s, out, pan, filters):
    """A clip that plays over and over (a biome's ambience): start to end, with the "loop" seconds of the recording
    after its end crossfaded into its start (equal power, as the two are unrelated sound). Played again, its end runs
    on into what followed it in the recording, so the seam has no gap, fade or dip. No fades or pads."""
    xf = clip["loop"]
    start, end = clip["start"], clip["end"]
    length = end - start
    if xf <= 0 or xf * 2 > length:
        sys.exit(f"{clip['file']}: loop crossfade {xf} s doesn't fit a {length:.2f} s clip")
    pre = ",".join([f"atrim=start={start:.4f}:end={end + xf:.4f}", "asetpts=PTS-STARTPTS", pan] + filters)
    graph = (f"[0:a]{pre},asplit=3[a][b][c];"
             f"[a]atrim=end={xf:.4f},afade=t=in:d={xf:.4f}:curve=qsin[head];"
             f"[b]atrim=start={length:.4f},asetpts=PTS-STARTPTS,afade=t=out:d={xf:.4f}:curve=qsin[tail];"
             f"[head][tail]amix=inputs=2:normalize=0:duration=first[seam];"
             f"[c]atrim=start={xf:.4f}:end={length:.4f},asetpts=PTS-STARTPTS[body];"
             f"[seam][body]concat=n=2:v=0:a=1,aresample={s['rate']}:resampler=soxr[out]")
    run(["ffmpeg", "-v", "error", "-y", "-i", source, "-filter_complex", graph, "-map", "[out]", "-c:a", "pcm_f32le",
         out])
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("recipe")
    parser.add_argument("--source", required=True, help="the source recording (kept outside the repository)")
    parser.add_argument("--out", help="where the clips go (default build/sound-rework/<name>)")
    parser.add_argument("--install", action="store_true", help="copy the clips over the mod's files")
    args = parser.parse_args()

    with open(args.recipe, encoding="utf-8") as f:
        recipe = json.load(f)
    s = dict(DEFAULTS, **recipe.get("settings", {}))
    name = recipe["name"]
    out_dir = args.out or os.path.join(ROOT, "build", "sound-rework", name)
    os.makedirs(out_dir, exist_ok=True)
    survey = load_survey()

    rows = []
    with tempfile.TemporaryDirectory() as work:
        for clip in recipe["clips"]:
            wav = cut(args.source, clip, s, work)
            raw = survey.measure(wav)
            # To the target level, the spikes over the ceiling limited; or, not limiting, never over the ceiling
            measure = "rms" if s["target_rms"] is not None else "rms_peak"
            target = s["target_rms"] if s["target_rms"] is not None else s["target_rms_peak"]
            gain = target - raw[measure]
            limited = raw["true_peak"] is not None and raw["true_peak"] + gain > s["max_true_peak"] - 0.5
            if limited and not s["limit"]:
                gain = s["max_true_peak"] - 0.5 - raw["true_peak"]
            limit = limited and s["limit"]
            clip["limited"] = limit
            ogg = os.path.join(out_dir, os.path.basename(clip["file"]) + ".ogg")
            # Limiting lowers the level a little too: the gain into the limiter is raised by what was missed, until
            # within half a dB
            for _ in range(4 if limit else 1):
                chain = [f"volume={gain:.2f}dB"]
                if limit:
                    # Sample peaks a dB under the ceiling, as true peaks sit a little over them; at 4x, so they're caught
                    ceiling = 10 ** ((s["max_true_peak"] - 1.0) / 20)
                    chain += ["aresample=176400", f"alimiter=limit={ceiling:.4f}:attack=1:release=20:level=disabled",
                              f"aresample={s['rate']}:resampler=soxr"]
                run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-af", ",".join(chain), "-c:a", "libvorbis",
                     "-q:a", str(s["quality"]), ogg])
                new = survey.measure(ogg)
                short = target - new[measure]
                if not limit or abs(short) <= 0.5:
                    break
                gain += short
            old_path = os.path.join(SOUNDS, clip["file"] + ".ogg")
            old = survey.measure(old_path) if os.path.exists(old_path) else None
            rows.append((clip, new, old, gain))

    report = os.path.join(out_dir, "report.csv")
    with open(report, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["file", "start", "end", "gain_db", "seconds", "rms_peak", "true_peak", "old_seconds",
                    "old_rms_peak", "old_true_peak", "source", "license"])
        for clip, new, old, gain in rows:
            w.writerow([clip["file"], clip["start"], clip["end"], f"{gain:.2f}", new["seconds"], new["rms_peak"],
                        new["true_peak"], old and old["seconds"], old and old["rms_peak"], old and old["true_peak"],
                        recipe["source"]["url"], recipe["source"]["license"]])

    print(f"{name}: {len(rows)} clips from {recipe['source']['title']} ({recipe['source']['license']}) -> {out_dir}")
    measure = "rms" if s["target_rms"] is not None else "rms_peak"
    label = "rms" if measure == "rms" else "rms peak"
    print(f"  {'file':34} {'length':>7} {label:>9} {'true pk':>8} {'size':>7}   was {'length':>7} {label:>9} "
          f"{'true pk':>8} {'size':>7}")
    problems = 0
    for clip, new, old, gain in rows:
        kb = os.path.getsize(os.path.join(out_dir, os.path.basename(clip["file"]) + ".ogg")) / 1024
        line = f"  {clip['file']:34} {new['seconds']:6.2f}s {new[measure]:8.1f} {new['true_peak']:8.1f} {kb:5.0f}kB"
        line += " L" if clip.get("limited") else "  "
        if old:
            old_kb = os.path.getsize(os.path.join(SOUNDS, clip["file"] + ".ogg")) / 1024
            line += f"     {old['seconds']:6.2f}s {old[measure]:8.1f} {old['true_peak']:8.1f} {old_kb:5.0f}kB"
        print(line)
        if new["true_peak"] is not None and new["true_peak"] > s["max_true_peak"]:
            print(f"    true peak over {s['max_true_peak']} dBTP")
            problems += 1
        # A recipe's long fade-in (a swell) reads as silence at its start: only what goes past it counts
        # A loop has no start to hear (its start is the crossfaded seam), and a quiet ambience reads as silence there
        if not clip.get("loop") and new["lead_silence"] >= max(0.05, s["fade_in"]):
            print(f"    {new['lead_silence']:.2f} s of leading silence")
            problems += 1
    if any(c.get("limited") for c, *_ in rows):
        print("  L: spikes limited to reach the level")
    levels = [n[measure] for _, n, _, _ in rows if n[measure] is not None]
    if levels and max(levels) - min(levels) >= 6:
        print(f"  variants differ by {max(levels) - min(levels):.1f} dB (sound-survey.py flags 6 or more)")
        problems += 1
    print(f"  report: {report}")

    if args.install:
        if problems:
            sys.exit("not installed: fix the problems above first")
        for clip, *_ in rows:
            shutil.copyfile(os.path.join(out_dir, os.path.basename(clip["file"]) + ".ogg"),
                            os.path.join(SOUNDS, clip["file"] + ".ogg"))
        print(f"  installed {len(rows)} files under {SOUNDS}")


if __name__ == "__main__":
    main()
