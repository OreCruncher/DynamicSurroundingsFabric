# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Generates a droning ambience loop, such as the hush heard underground, from noise: the mod's own sound, so there is
nothing to credit.

Usage: python scripts/drone-synth.py OUT.ogg [--seconds S] [--center HZ] [--width OCT] [--resonance DB]
       [--rumble DB] [--drift DB] [--pulse HZ DEPTH_DB] [--lufs L] [--rate HZ] [--quality Q] [--seed N]

Needs numpy and ffmpeg (with libvorbis). The noise is made in the frequency domain over the whole loop, so it repeats
without a seam, and everything that moves (drift, pulse) moves a whole number of times a loop:
  --center, --width  a band of noise around HZ, WIDTH octaves wide (a bell on a log scale: 0.6 is narrow, 1.5 broad)
  --resonance  a narrower peak on the center, DB over the band (0 for none), the hum the ear hears in quiet
  --rumble  low noise under 120 Hz, DB against the band (-60 for none)
  --drift  three bands around the center (0.8, 1 and 1.25 of it) rise and fall slowly against each other, by up to DB,
           so the color shifts over the loop instead of holding still
  --pulse  a slow swell in level, HZ (rounded to a whole number a loop) by DEPTH_DB, as of blood in the ear
  --lufs  loudness of the result (match the sound it replaces), --rate and --quality the encoding (content stops near
          1 kHz, so 16 kHz is plenty)
The same arguments and seed give the same file; the command goes in the notes of what it made.
"""
import argparse
import os
import subprocess
import sys
import tempfile

import numpy as np

p = argparse.ArgumentParser()
p.add_argument("out")
p.add_argument("--seconds", type=float, default=30.0)
p.add_argument("--center", type=float, default=200.0)
p.add_argument("--width", type=float, default=1.0)
p.add_argument("--resonance", type=float, default=0.0)
p.add_argument("--rumble", type=float, default=-12.0)
p.add_argument("--drift", type=float, default=4.0)
p.add_argument("--pulse", type=float, nargs=2, default=None, metavar=("HZ", "DEPTH_DB"))
p.add_argument("--lufs", type=float, default=-30.0)
p.add_argument("--rate", type=int, default=16000)
p.add_argument("--quality", type=float, default=2)
p.add_argument("--seed", type=int, default=1)
a = p.parse_args()

rng = np.random.default_rng(a.seed)
sr = a.rate
n = int(round(a.seconds * sr))
f = np.fft.rfftfreq(n, 1 / sr)
t = np.arange(n) / n                     # 0..1 over the loop


def noise(shape):
    """Periodic noise of the loop's length with the given magnitude per frequency bin"""
    spec = shape * (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f)))
    spec[0] = 0
    y = np.fft.irfft(spec, n)
    return y / (np.sqrt((y ** 2).mean()) or 1)


def bell(center, octaves):
    lf = np.log2(np.maximum(f, 1e-3) / center)
    return np.exp(-0.5 * (lf / (octaves / 2.355)) ** 2)    # width is the bell's full width at half height


def lfo(cycles, phase):
    return 0.5 + 0.5 * np.sin(2 * np.pi * (cycles * t + phase))


# Three bands drifting against each other, each its own noise, so the color moves
y = np.zeros(n)
for i, ratio in enumerate((0.8, 1.0, 1.25)):
    band = noise(bell(a.center * ratio, a.width))
    if a.resonance > 0 and ratio == 1.0:
        band = band + 10 ** (a.resonance / 20) * 0.25 * noise(bell(a.center, 0.12))
    swing = 10 ** (-a.drift * lfo(i + 1, rng.random()) / 20)
    y += band * swing
if a.rumble > -60:
    shape = np.where(f > 0, 1 / np.sqrt(np.maximum(f, 1)), 0) * (1 / (1 + (f / 120) ** 4))
    y += 10 ** (a.rumble / 20) * noise(shape)
if a.pulse:
    hz, depth = a.pulse
    cycles = max(1, round(hz * a.seconds))
    y *= 10 ** (-depth * lfo(cycles, 0.0) ** 3 / 20)       # a quick rise and a long fall, more beat than wobble
y /= np.abs(y).max()

with tempfile.TemporaryDirectory() as tmp:
    wav = os.path.join(tmp, "drone.wav")
    raw = os.path.join(tmp, "drone.f32")
    y.astype(np.float32).tofile(raw)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", raw, "-c:a", "pcm_f32le",
                    wav], check=True, stdin=subprocess.DEVNULL)
    # Loudness as ffmpeg measures it (EBU R128), over the loop played twice so its start is measured like the rest
    probe = subprocess.run(["ffmpeg", "-v", "info", "-stream_loop", "1", "-i", wav, "-af", "ebur128", "-f", "null", "-"],
                           capture_output=True, text=True, stdin=subprocess.DEVNULL).stderr
    measured = float([line for line in probe.splitlines() if line.strip().startswith("I:")][-1].split()[1])
    gain = a.lufs - measured
    if 20 * np.log10(np.abs(y).max()) + gain > -1:
        sys.exit(f"{a.lufs} LUFS would clip ({gain:+.1f} dB on a peak at full scale): ask for less")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-af", f"volume={gain:.2f}dB", "-c:a", "libvorbis", "-q:a",
                    str(a.quality), a.out], check=True, stdin=subprocess.DEVNULL)
print(f"{a.out}: {a.seconds:g} s at {sr} Hz, {os.path.getsize(a.out) / 1024:.0f} kB, {a.lufs} LUFS")
