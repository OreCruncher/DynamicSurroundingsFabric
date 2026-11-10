# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Cleans a source recording before sound-cut.py cuts it: filtering, spectral noise removal, late-reverb removal and a
speed change, written as a mono 32-bit float WAV (keep it outside the repository, as the sources are).

Usage: python scripts/sound-clean.py IN OUT [--highpass HZ] [--lowpass HZ] [--denoise FLOOR_DB]
       [--dereverb T60 FLOOR_DB] [--tempo X]

Needs numpy (pip install numpy) and ffmpeg (with Rubber Band for --tempo).
  --highpass, --lowpass  4-pole high-pass and 2-pole low-pass, first: rumble under a call, hiss over it. Try these
           before --denoise where the noise sits outside the call's band: it costs the call nothing
  --denoise  a noise profile per frequency bin (its 15th percentile power over the recording, which moving calls barely
           touch), taken out by a smoothed Wiener-style gain no lower than FLOOR_DB (-10 light, -20 strong), so what
           is left of the noise stays natural. Where the "noise" is quiet calling (a pack's chorus) it takes the calls
           down with it: filter instead
  --dereverb  the late reverb at each frame predicted from the signal 50 ms before, decayed at the room's T60 in
           seconds (Lebart et al.), and taken out the same way, no lower than FLOOR_DB (-15 keeps a little room)
  --tempo  faster (over 1) or slower, pitch kept (Rubber Band), after the rest
The recipe's source notes should give the command, so the clips can be cut again from the original download.
"""
import argparse
import os
import subprocess
import tempfile

import numpy as np

p = argparse.ArgumentParser()
p.add_argument("src")
p.add_argument("out")
p.add_argument("--highpass", type=float, default=0)
p.add_argument("--lowpass", type=float, default=0)
p.add_argument("--denoise", type=float, default=None, metavar="FLOOR_DB")
p.add_argument("--dereverb", type=float, nargs=2, default=None, metavar=("T60", "FLOOR_DB"))
p.add_argument("--tempo", type=float, default=1.0)
a = p.parse_args()

rate = int(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=sample_rate",
                           "-of", "csv=p=0", a.src], capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout)
af = ([f"highpass=f={a.highpass}:p=2,highpass=f={a.highpass}:p=2"] if a.highpass else []) + ([f"lowpass=f={a.lowpass}:p=2"] if a.lowpass else [])
cmd = ["ffmpeg", "-v", "error", "-i", a.src, "-ac", "1"] + (["-af", ",".join(af)] if af else []) + ["-f", "f32le", "-"]
x = np.frombuffer(subprocess.run(cmd, capture_output=True, check=True, stdin=subprocess.DEVNULL).stdout,
                  dtype=np.float32).astype(np.float64)

N = 2048
H = N // 4
win = np.hanning(N)


def stft(x):
    pad = np.concatenate([np.zeros(N), x, np.zeros(N)])
    frames = np.lib.stride_tricks.sliding_window_view(pad, N)[::H] * win
    return np.fft.rfft(frames, axis=1)


def istft(X, length):
    frames = np.fft.irfft(X, N, axis=1) * win
    out = np.zeros(H * (len(frames) - 1) + N)
    norm = np.zeros_like(out)
    for i, fr in enumerate(frames):
        out[i * H:i * H + N] += fr
        norm[i * H:i * H + N] += win ** 2
    out /= np.maximum(norm, 1e-8)
    return out[N:N + length]


def smooth_time(P, alpha):
    S = np.empty_like(P)
    S[0] = P[0]
    for t in range(1, len(P)):
        S[t] = alpha * S[t - 1] + (1 - alpha) * P[t]
    return S


def gain(P, D, floor_db, over=1.5):
    g = np.sqrt(np.clip(1 - over * D / np.maximum(P, 1e-20), 0, 1))
    g = np.maximum(g, 10 ** (floor_db / 20))
    # smooth across frequency (3 bins) and time (attack fast, release slower), against "musical noise"
    g = (np.roll(g, 1, axis=1) + g + np.roll(g, -1, axis=1)) / 3
    out = np.empty_like(g)
    out[0] = g[0]
    for t in range(1, len(g)):
        rise = g[t] > out[t - 1]
        out[t] = np.where(rise, 0.5 * out[t - 1] + 0.5 * g[t], 0.8 * out[t - 1] + 0.2 * g[t])
    return out


X = stft(x)
P = np.abs(X) ** 2
report = []
if a.dereverb:
    t60, floor_db = a.dereverb
    nd = max(1, round(0.05 * rate / H))          # late reverb from 50 ms on
    delta = 3 * np.log(10) / t60
    Ps = smooth_time(P, 0.5)
    late = np.zeros_like(Ps)
    late[nd:] = np.exp(-2 * delta * nd * H / rate) * Ps[:-nd]
    G = gain(Ps, late, floor_db, over=1.0)
    X = X * G
    P = np.abs(X) ** 2
    report.append(f"dereverb T60 {t60} s, floor {floor_db} dB: mean gain {20 * np.log10(G.mean()):.1f} dB")
if a.denoise is not None:
    noise = np.percentile(P, 15, axis=0)
    G = gain(smooth_time(P, 0.5), noise, a.denoise)
    X = X * G
    report.append(f"denoise floor {a.denoise} dB: mean gain {20 * np.log10(G.mean()):.1f} dB")
y = istft(X, len(x)).astype(np.float32)

with tempfile.TemporaryDirectory() as tmp:
    raw = os.path.join(tmp, "y.f32")
    y.tofile(raw)
    chain = [f"rubberband=tempo={a.tempo}:pitchq=quality:transients=smooth:detector=soft:window=long"] if a.tempo != 1.0 else []
    cmd = ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(rate), "-ac", "1", "-i", raw]
    cmd += (["-af", ",".join(chain)] if chain else []) + ["-c:a", "pcm_f32le", a.out]
    subprocess.run(cmd, check=True, stdin=subprocess.DEVNULL)
print(f"{os.path.basename(a.src)} -> {os.path.basename(a.out)}: " + "; ".join(report + ([f"tempo x{a.tempo}"] if a.tempo != 1 else [])))
