# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""An ambience recording's level over time, for picking a stretch to loop: its level in 5 s blocks, overall and by
band, then the short events that stand out from it (half a second 8 dB over a running 10 s median), each with its band.

Usage: python scripts/sound-ambience.py FILE

Needs numpy and ffmpeg. Events in a calls band (birds at 2-5 kHz) are the ambience itself; ones reaching down to the
low bands are knocks, handling or wind, and a band that rises and stays (an airplane, a boat, traffic) shows in the
blocks. A steady layer in one band that doesn't swell with the rest (a hum, an insect's trill, hiss) is a filter's
job, not denoising's. Pick a loop where the blocks at its two ends are about level, so the seam doesn't jump.
"""
import os
import subprocess
import sys

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view

SR = 22050
path = sys.argv[1]
raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"],
                     capture_output=True, stdin=subprocess.DEVNULL).stdout
x = np.frombuffer(raw, np.int16).astype(float)
W, H = 2048, SR // 20
P = np.abs(np.fft.rfft(sliding_window_view(x, W)[::H] * np.hanning(W), axis=1)) ** 2
f = np.fft.rfftfreq(W, 1 / SR)


def db(v):
    return 10 * np.log10(v + 1e-3) - 130


bands = [(20, 200), (200, 1000), (1000, 3000), (3000, 6000), (6000, 11000)]
B = np.stack([P[:, (f >= lo) & (f < hi)].sum(1) for lo, hi in bands], 1)
total = db(B.sum(1))
print(f"{os.path.basename(path)}: {len(x) / SR:.1f} s")
print("  5 s blocks: total dB, then " + " ".join(f"{lo}-{hi}" for lo, hi in bands))
block = 100
for i in range(0, len(total) - block + 1, block):
    print(f"  {i / 20:6.0f}s {db(B[i:i + block].sum(1).mean()):6.1f}  "
          + " ".join(f"{db(B[i:i + block, j].mean()):6.1f}" for j in range(len(bands))))
median = np.median(sliding_window_view(np.pad(total, 100, mode="edge"), 201), axis=1)[:len(total)]
over = total - median > 8
i = 0
while i < len(over):
    if over[i]:
        j = i
        while j < len(over) and over[j]:
            j += 1
        seg = P[i:j].sum(0)
        c = np.cumsum(seg) / seg.sum()
        print(f"  event {i / 20:6.1f}-{j / 20:6.1f}s +{(total[i:j] - median[i:j]).max():4.1f} dB  band "
              f"{f[np.searchsorted(c, .1)]:5.0f}-{f[np.searchsorted(c, .9)]:5.0f} Hz")
        i = j
    i += 1
