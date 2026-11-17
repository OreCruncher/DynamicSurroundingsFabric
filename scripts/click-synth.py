# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Generates a short "slide and lock" sound, such as a setting clicking into place: the mod's own sound, so there is
nothing to credit.

Usage: python scripts/click-synth.py OUT.ogg [--slide MS] [--sweep LOW_HZ HIGH_HZ] [--slide-level DB] [--tock HZ]
       [--ring MS] [--latch MS] [--level DB] [--seed N]

Needs numpy and ffmpeg (with libvorbis). Made of three parts:
  --slide, --sweep, --slide-level  noise swept from LOW_HZ up to HIGH_HZ over MS (0 for none), rising in level, DB
           against the click: something sliding home
  --tock, --ring  the lock: a sharp broadband snap of a few milliseconds and a damped tone at HZ that dies away over
           MS (a low HZ and long ring is wood, a high HZ and short ring is metal or plastic)
  --latch  a second, softer click MS after the first (0 for none), as a catch settling
  --level  the loudest 50 ms window's RMS in dB, as sound-cut.py measures it (the game's button click is about -21)
The same arguments and seed give the same file; the command goes in the notes of what it made.
"""
import argparse
import importlib.util
import os
import subprocess
import tempfile

import numpy as np

p = argparse.ArgumentParser()
p.add_argument("out")
p.add_argument("--slide", type=float, default=200.0)
p.add_argument("--sweep", type=float, nargs=2, default=(600.0, 3000.0), metavar=("LOW_HZ", "HIGH_HZ"))
p.add_argument("--slide-level", type=float, default=-14.0)
p.add_argument("--tock", type=float, default=1200.0)
p.add_argument("--ring", type=float, default=25.0)
p.add_argument("--latch", type=float, default=0.0)
p.add_argument("--level", type=float, default=-21.0)
p.add_argument("--seed", type=int, default=1)
a = p.parse_args()

sr = 44100
rng = np.random.default_rng(a.seed)
slide_n = int(a.slide / 1000 * sr)
tail_n = int(0.12 * sr) + int(a.latch / 1000 * sr)
y = np.zeros(slide_n + tail_n)

if slide_n > 0:
    # Noise in short frames, each band-passed around a center rising on a log scale, faded in as it closes
    frame, hop = 512, 128
    noise = rng.standard_normal(slide_n + frame)
    out = np.zeros(slide_n + frame)
    win = np.hanning(frame)
    f = np.fft.rfftfreq(frame, 1 / sr)
    for start in range(0, slide_n, hop):
        t = start / max(slide_n - 1, 1)
        center = a.sweep[0] * (a.sweep[1] / a.sweep[0]) ** t
        bell = np.exp(-0.5 * (np.log2(np.maximum(f, 1) / center) / 0.45) ** 2)
        seg = np.fft.irfft(np.fft.rfft(noise[start:start + frame] * win) * bell, frame)
        out[start:start + frame] += seg * win * (0.15 + 0.85 * t ** 1.5)
    out = out[:slide_n]
    out /= np.abs(out).max() or 1
    y[:slide_n] += out * 10 ** (a.slide_level / 20)


def click(at, scale):
    n = len(y) - at
    t = np.arange(n) / sr
    snap = rng.standard_normal(n) * np.exp(-t / 0.0015)               # the broadband snap
    tone = np.sin(2 * np.pi * a.tock * t) * np.exp(-t / (a.ring / 1000))  # the body that rings
    y[at:] += scale * (0.6 * snap / (np.abs(snap).max() or 1) + tone)


click(slide_n, 1.0)
if a.latch > 0:
    click(slide_n + int(a.latch / 1000 * sr), 0.35)
y[-int(0.02 * sr):] *= np.linspace(1, 0, int(0.02 * sr))
y /= np.abs(y).max()

# Level by the loudest 50 ms window's RMS, measured on the encoded file by sound-survey.py, as sound-cut.py does
spec = importlib.util.spec_from_file_location("sound_survey", os.path.join(os.path.dirname(os.path.abspath(__file__)), "sound-survey.py"))
survey = importlib.util.module_from_spec(spec)
spec.loader.exec_module(survey)


def encode(samples, out):
    with tempfile.TemporaryDirectory() as tmp:
        raw = os.path.join(tmp, "click.f32")
        samples.astype(np.float32).tofile(raw)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", raw, "-c:a",
                        "libvorbis", "-q:a", "4", out], check=True, stdin=subprocess.DEVNULL)
    return survey.measure(out)


y *= 0.5
for _ in range(3):
    measured = encode(y, a.out)
    gain = a.level - measured["rms_peak"]
    if abs(gain) < 0.3:
        break
    y *= 10 ** (gain / 20)
    if np.abs(y).max() > 10 ** (-1.5 / 20):
        y *= 10 ** (-1.5 / 20) / np.abs(y).max()                     # under the ceiling: as loud as it can be
measured = survey.measure(a.out)
print(f"{a.out}: {len(y) / sr:.2f} s, {os.path.getsize(a.out) / 1024:.0f} kB, {measured['rms_peak']:.1f} dB (loudest 50 ms)")
