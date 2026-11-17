# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Is a sound cut from a candidate recording? For tracing where one of the mod's sounds came from.

Usage: python scripts/sound-match.py SOUND CANDIDATE... [--excerpt S] [--speeds] [--contour] [--rate HZ]

Needs numpy and ffmpeg. Three ways to look, each giving the best match over every offset:
  default    three excerpts of SOUND (EXCERPT seconds each, from its start, middle and end; the whole sound if it is
             shorter than three), each slid along every candidate with a true Pearson r at every sample, either
             polarity. A real cut puts all three high and IN STEP (as far apart in the candidate as in the sound).
  --speeds   the candidate played from half to twice as fast, in half-semitone steps (pitch and time together), the
             sound as one piece: for a sound that may have been pitched or slowed
  --contour  the dominant pitch every 20 ms instead of the waveform, x0.60-1.60: for tonal calls (howls, hoots, whale
             song), where a quarter second of any tone matches any other at some speed
Reading the numbers: a re-encoded cut scores 0.9-1.0 (two lossy encodings, an MP3 preview, take off about 0.1);
unrelated sounds 0.1-0.4, short clicks up to about 0.3 by chance. Check the method with a control: cut a piece of a
candidate, alter it as the sound might have been, and see it score high (a pitched, filtered click scored 0.64 against
its source when unrelated clicks reached 0.34). Each candidate's own channels can be tried too: a mono mix scores
higher than either channel alone.
"""
import argparse
import os
import subprocess

import numpy as np

p = argparse.ArgumentParser()
p.add_argument("sound")
p.add_argument("candidates", nargs="+")
p.add_argument("--excerpt", type=float, default=3.0)
p.add_argument("--speeds", action="store_true")
p.add_argument("--contour", action="store_true")
p.add_argument("--rate", type=int, default=8000, help="analysis rate (8000; 22050 for bright, short sounds)")
a = p.parse_args()


def load(path, rate):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(rate), "-f", "s16le", "-"],
                         capture_output=True, stdin=subprocess.DEVNULL).stdout
    return np.frombuffer(raw, np.int16).astype(np.float64)


def ncc(long, piece):
    """The true Pearson r of piece against every same-length window of long, either polarity: (best r, offset)"""
    n = len(piece)
    if len(long) < n:
        long, piece = piece, long
        n = len(piece)
    c = piece - piece.mean()
    c /= np.linalg.norm(c) or 1
    size = 1 << int(np.ceil(np.log2(len(long) + n)))
    num = np.fft.irfft(np.fft.rfft(long, size) * np.conj(np.fft.rfft(c, size)), size)[:len(long) - n + 1]
    cs = np.concatenate([[0], np.cumsum(long)])
    cs2 = np.concatenate([[0], np.cumsum(long * long)])
    s, s2 = cs[n:] - cs[:-n], cs2[n:] - cs2[:-n]
    r = num / np.sqrt(np.maximum(s2 - s * s / n, 1e-9))
    i = int(np.argmax(np.abs(r)))
    return abs(r[i]), i


def name(path):
    return os.path.basename(path)[:40]


def by_excerpts():
    sr = a.rate
    sound = load(a.sound, sr)
    n = int(a.excerpt * sr)
    if len(sound) < 3 * n:
        starts, n = [0], len(sound)
    else:
        # half a second in from each end, past any fade
        margin = min(int(0.5 * sr), (len(sound) - 3 * n) // 2)
        starts = [margin, (len(sound) - n) // 2, len(sound) - n - margin]
    rows = []
    for path in a.candidates:
        long = load(path, sr)
        best = [ncc(long, sound[s:s + n]) for s in starts]
        in_step = all(abs((best[k][1] - best[0][1]) - (starts[k] - starts[0])) < sr * 0.05 for k in range(1, len(best)))
        rows.append((min(b[0] for b in best), path, [float(b[0]) for b in best], in_step, (best[0][1] - starts[0]) / sr))
    for r, path, each, in_step, at in sorted(rows, reverse=True):
        step = "" if len(each) == 1 else ("  IN STEP" if in_step else "  not in step")
        print(f"{name(path):40s} r {r:.2f} (each {' '.join(f'{e:.2f}' for e in each)}){step}  from {max(at, 0):.2f} s")


def by_speeds():
    sr = a.rate
    sound = load(a.sound, sr)
    speeds = np.round(2 ** np.arange(-1, 1.0001, 1 / 24), 4)
    rows = []
    for path in a.candidates:
        # decoded at sr * k and read as sr: the candidate slowed by k, so it plays at 1/k
        best = max((ncc(load(path, int(sr * k)), sound)[0], 1 / k) for k in speeds)
        rows.append((best[0], best[1], path))
    for r, speed, path in sorted(rows, reverse=True):
        print(f"{name(path):40s} r {r:.2f} with the candidate at x{speed:.2f}")


def contour(path):
    sr, hop, win = 8000, 160, 512
    x = load(path, sr)
    if len(x) < win:
        return np.array([]), np.array([], dtype=bool)
    frames = np.lib.stride_tricks.sliding_window_view(x, win)[::hop] * np.hanning(win)
    spec = np.abs(np.fft.rfft(frames, 4096, axis=1))
    f = np.fft.rfftfreq(4096, 1 / sr)
    band = (f >= 80) & (f <= 3500)
    spec[:, ~band] = 0
    i = np.argmax(spec, axis=1)
    peak = spec[np.arange(len(i)), i]
    energy = np.sum(frames ** 2, axis=1)
    # voiced: loud, with the peak well over the band's median (a tone, not noise)
    voiced = (energy > np.percentile(energy, 40)) & (peak / (np.median(spec[:, band], axis=1) + 1e-9) > 8)
    return np.log2(np.maximum(f[np.clip(i, 1, len(f) - 1)], 1)), voiced


def by_contour():
    t, tv = contour(a.sound)
    print(f"{name(a.sound)}: {int(tv.sum())} voiced frames (20 ms)")
    for path in a.candidates:
        c, cv = contour(path)
        best = (0.0, 1.0, 0)
        if tv.sum() >= 10:
            for k in np.arange(0.60, 1.6005, 0.01):
                # the candidate played k times as fast: its frame j*k at frame j, its pitch up by log2(k)
                idx = np.arange(0, len(c) - 1, k).astype(int)
                ck, cvk = c[idx] + np.log2(k), cv[idx]
                for o in range(0, len(ck) - len(t) + 1):
                    hit = np.count_nonzero(tv & cvk[o:o + len(t)] & (np.abs(t - ck[o:o + len(t)]) < 1 / 24))
                    if hit / tv.sum() > best[0]:
                        best = (hit / tv.sum(), k, o)
        s, k, o = best
        print(f"  {name(path):40s} {s:.2f} of its pitch within half a semitone, candidate at x{k:.2f}, from {o / 50 * k:.1f} s")


if a.contour:
    by_contour()
elif a.speeds:
    by_speeds()
else:
    by_excerpts()
