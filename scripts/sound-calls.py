# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Where the calls are in a recording, for picking clips to cut: its noise floor and where that noise lies, then each
call (sound 10 dB over the floor, joined across gaps shorter than GAP seconds) with its start, end, length, band (10-90%
of its energy) and how far it stands over the floor.

Usage: python scripts/sound-calls.py FILE [GAP]

Needs numpy and ffmpeg. A smaller GAP splits a run of calls into phrases (0.1-0.15 s for chittering or song). A call's
start here is where it crosses the 10 dB line, so a clip cut from it may still open on a little near-silence: trim to
what sound-cut.py reports. A floor far below -100 dBFS means digital silence between calls (cut out by the uploader).
"""
import os
import subprocess
import sys

import numpy as np

SR = 16000
path = sys.argv[1]
gap = float(sys.argv[2]) if len(sys.argv) > 2 else 0.3
info = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=sample_rate,channels", "-of", "csv=p=0", path],
                      capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout.split()
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                     capture_output=True, stdin=subprocess.DEVNULL).stdout
x = np.frombuffer(raw, dtype=np.int16).astype(np.float64)
W, H = 1024, SR // 50
frames = np.lib.stride_tricks.sliding_window_view(x, W)[::H] * np.hanning(W)
P = np.abs(np.fft.rfft(frames, axis=1)) ** 2
f = np.fft.rfftfreq(W, 1 / SR)
level = 10 * np.log10(P.sum(axis=1) / W ** 2 * 4 + 1e-12) - 84.3
floor = np.percentile(level, 10)
quiet = P[level <= floor + 1].mean(axis=0)
total = quiet.sum() or 1
bands = [(20, 100), (100, 300), (300, 1000), (1000, 3000), (3000, 8000)]
print(f"{os.path.basename(path)}: {len(x) / SR:.1f} s, {' '.join(info)}, floor {floor:.0f} dBFS; noise by band "
      + " ".join(f"{lo}-{hi}:{100 * quiet[(f >= lo) & (f < hi)].sum() / total:.0f}%" for lo, hi in bands))
loud = level > floor + 10
runs, start, silent, end = [], None, 0, 0
for i, on in enumerate(loud):
    if on:
        if start is None:
            start = i
        silent, end = 0, i
    elif start is not None:
        silent += 1
        if silent > gap * 50:
            runs.append((start, end))
            start = None
if start is not None:
    runs.append((start, end))
for lo, hi in runs:
    if hi - lo < 10:
        continue
    seg = P[lo:hi + 1].sum(axis=0)
    c = np.cumsum(seg) / seg.sum()
    print(f"   {lo / 50:6.2f}-{hi / 50:6.2f}  {(hi - lo) / 50:4.1f} s  band {f[np.searchsorted(c, 0.1)]:5.0f}-"
          f"{f[np.searchsorted(c, 0.9)]:<5.0f} Hz  peak {level[lo:hi + 1].max():5.1f}  over floor "
          f"{level[lo:hi + 1].max() - floor:3.0f} dB")
