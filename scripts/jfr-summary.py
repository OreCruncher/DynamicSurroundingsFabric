#!/usr/bin/env python3
"""
Summarizes a `/dsprofile` recording (config/dsurround/profiles/*.zip or the .jfr inside): what it was made on, the
mod's render timers, where each busy thread spends its time (by method and by mod package), what allocates, and GC.

  jfr-summary.py RECORDING [--thread NAME] [--top N] [--jfr PATH]

  --thread   only this thread's samples for the method tables (default: the busiest few threads)
  --top      rows per table (default 15)
  --jfr      the JDK's jfr tool (default: newest Adoptium JDK, else `jfr` on the path)
  --callers  METHOD (a substring, e.g. BiomeManager.getBiome): only who calls it, on --thread or every thread;
             the first mod frame above it and the nearest caller outside it

Reads `jfr print` text output, which is far smaller than its JSON. A zip is unpacked next to itself.
"""
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import zipfile
from collections import Counter, defaultdict

# Frames that are nobody's mod: the JDK, the game, its libraries, the loaders
PLATFORM = ("java.", "jdk.", "sun.", "com.sun.", "net.minecraft.", "com.mojang.", "org.lwjgl.", "it.unimi.",
            "com.google.", "io.netty.", "org.slf4j.", "org.apache.", "net.neoforged.", "net.fabricmc.",
            "cpw.mods.", "org.spongepowered.", "org.objectweb.", "kotlin.", "kotlinx.", "javax.")
FRAME = re.compile(r"^\s+([\w$.<>/]+)\.([\w$<>]+)\((.*?)\)(?: line: (\d+))?")
FIELD = re.compile(r"^\s+(\w+) = (.*)$")


def find_jfr(given):
    if given:
        return given
    homes = sorted(glob.glob(r"C:/Program Files/Eclipse Adoptium/jdk-*/bin/jfr.exe"))
    if homes:
        return homes[-1]
    found = shutil.which("jfr")
    if not found:
        sys.exit("No jfr tool found: pass --jfr")
    return found


def unpack(path):
    if not path.lower().endswith(".zip"):
        return path
    with zipfile.ZipFile(path) as z:
        name = next(n for n in z.namelist() if n.endswith(".jfr"))
        target = os.path.join(os.path.dirname(os.path.abspath(path)), name)
        if not os.path.exists(target):
            z.extract(name, os.path.dirname(target))
    return target


def events(jfr, recording, types, depth=64):
    """Yields (type, fields, frames) from `jfr print`; frames innermost first, as 'pkg.Class.method'"""
    command = [jfr, "print", "--events", ",".join(types), "--stack-depth", str(depth), recording]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    kind, fields, frames, in_stack = None, {}, [], False
    for line in process.stdout:
        line = line.rstrip("\n")
        if kind is None:
            if line.endswith(" {") and not line.startswith(" "):
                kind, fields, frames, in_stack = line[:-2], {}, [], False
            continue
        if line == "}":
            yield kind, fields, frames
            kind = None
            continue
        if in_stack:
            if line.strip() == "]":
                in_stack = False
                continue
            match = FRAME.match(line)
            if match:
                frames.append(match.group(1) + "." + match.group(2))
            continue
        if line.strip() == "stackTrace = [":
            in_stack = True
            continue
        match = FIELD.match(line)
        if match and match.group(1) not in fields:
            fields[match.group(1)] = match.group(2)
    process.wait()


# On client threads JFR names the client's block reads ServerChunkCache.getChunk... in packs that mixin into it (c2me
# wraps it): vanilla's ClientLevel.animateTick shows it too. Not a server access; shown as what it is.
CLIENT_THREADS = ("Render thread",)
SERVER_CHUNKS = "net.minecraft.server.level.ServerChunkCache."
CLIENT_CHUNK_READ = "(client chunk read; JFR labels it ServerChunkCache.getChunk)"


def relabel(thread, frames):
    if thread in CLIENT_THREADS and frames and frames[0].startswith(SERVER_CHUNKS):
        index = next((i for i, f in enumerate(frames) if not f.startswith(SERVER_CHUNKS)), len(frames))
        return [CLIENT_CHUNK_READ] + frames[index:]
    return frames


def thread_name(value):
    match = re.match(r'"(.*?)"', value or "")
    return match.group(1) if match else (value or "?")


def owner(frame):
    """A frame's mod package (three segments), or None for the platform's"""
    if frame.startswith(PLATFORM):
        return None
    parts = frame.split(".")
    return ".".join(parts[:3]) if len(parts) > 4 else ".".join(parts[:-2])


def number(value):
    # jfr prints small doubles in scientific notation (1.0E-4); without the exponent they read thousands of times too big
    match = re.match(r"-?[\d.]+(?:[eE][-+]?\d+)?", value or "")
    return float(match.group(0)) if match else 0.0


def duration_ms(value):
    match = re.match(r"([\d.]+) (ns|us|ms|s)", value or "")
    if not match:
        return 0.0
    return float(match.group(1)) * {"ns": 1e-6, "us": 1e-3, "ms": 1.0, "s": 1000.0}[match.group(2)]


def size_bytes(value):
    match = re.match(r"([\d.]+) (bytes|kB|MB|GB)", value or "")
    if not match:
        return 0.0
    return float(match.group(1)) * {"bytes": 1, "kB": 1024, "MB": 1024 ** 2, "GB": 1024 ** 3}[match.group(2)]


def table(title, counter, total, top, unit=""):
    print(f"\n{title}")
    for key, value in counter.most_common(top):
        shown = f"{value / (1024 ** 2):9.1f} MB" if unit == "MB" else f"{value:9.0f}"
        print(f"  {shown} {100 * value / max(total, 1):5.1f}%  {key}")


def callers(jfr, recording, args):
    mods, nearest, ours, total = Counter(), Counter(), Counter(), 0
    for _, fields, frames in events(jfr, recording, ["jdk.ExecutionSample"]):
        thread = thread_name(fields.get("sampledThread"))
        if args.thread and thread != args.thread:
            continue
        frames = relabel(thread, frames)
        index = next((i for i, f in enumerate(frames) if args.callers in f), None)
        if index is None:
            continue
        total += 1
        above = frames[index + 1:]
        nearest[next((f for f in above if args.callers not in f), "(none)")] += 1
        mods[next((f for f in above if owner(f)), "(game / JDK)")] += 1
        mine = next((f for f in above if "orecruncher" in f), None)
        if mine:
            ours[mine] += 1
    print(f"== {total} samples in {args.callers}" + (f" on {args.thread}" if args.thread else ""))
    table("  nearest caller", nearest, total, args.top)
    table("  first mod frame above it", mods, total, args.top)
    if ours:
        table("  Dynamic Surroundings frames above it", ours, total, args.top)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("recording")
    parser.add_argument("--thread")
    parser.add_argument("--top", type=int, default=15)
    parser.add_argument("--jfr")
    parser.add_argument("--callers")
    args = parser.parse_args()
    jfr = find_jfr(args.jfr)
    recording = unpack(args.recording)
    if args.callers:
        callers(jfr, recording, args)
        return

    print("== Recorded on")
    for _, fields, _ in events(jfr, recording, ["dsurround.Environment"], 1):
        for key, value in fields.items():
            if key not in ("startTime", "eventThread"):
                print(f"  {key}: {value}")

    timers = defaultdict(list)
    for _, fields, _ in events(jfr, recording, ["dsurround.RenderTimer"], 1):
        timers[fields.get("effect", "?").strip('"')].append(fields)
    if timers:
        print("\n== Render timers (ms; mean of the periodic averages, highest peak; count: how many there were to draw "
              "a frame, mean, where the effect counts)")
        print(f"  {'effect':28} {'gpu avg':>8} {'gpu peak':>9} {'cpu avg':>8} {'cpu peak':>9} {'count':>8}")
        for effect, rows in timers.items():
            mean = lambda key: sum(number(r.get(key)) for r in rows) / len(rows)
            peak = lambda key: max(number(r.get(key)) for r in rows)
            # Recordings from before the count have none; an effect that doesn't count writes NaN, which jfr prints as N/A
            counts = [number(r["count"]) for r in rows if r.get("count") and r["count"] not in ("N/A", "NaN")]
            count = f"{sum(counts) / len(counts):8.0f}" if counts else f"{'-':>8}"
            print(f"  {effect:28} {mean('gpuAverage'):8.3f} {peak('gpuPeak'):9.3f} "
                  f"{mean('cpuAverage'):8.3f} {peak('cpuPeak'):9.3f} {count}")

    by_thread = Counter()
    methods = defaultdict(Counter)
    mods = defaultdict(Counter)
    ours = defaultdict(Counter)
    for _, fields, frames in events(jfr, recording, ["jdk.ExecutionSample"]):
        thread = thread_name(fields.get("sampledThread"))
        frames = relabel(thread, frames)
        by_thread[thread] += 1
        if frames:
            methods[thread][frames[0]] += 1
        mod = next((o for o in map(owner, frames) if o), "(game / JDK)")
        mods[thread][mod] += 1
        mine = next((f for f in frames if "orecruncher" in f), None)
        if mine:
            ours[thread][mine] += 1
    total = sum(by_thread.values())
    table(f"== CPU samples by thread ({total} samples)", by_thread, total, args.top)
    threads = [args.thread] if args.thread else [t for t, _ in by_thread.most_common(4)]
    for thread in threads:
        count = by_thread[thread]
        table(f"== {thread}: innermost method", methods[thread], count, args.top)
        table(f"== {thread}: innermost mod package (first non-platform frame)", mods[thread], count, args.top)
        if ours[thread]:
            share = sum(ours[thread].values())
            table(f"== {thread}: Dynamic Surroundings, innermost of its frames ({share} samples, "
                  f"{100 * share / max(count, 1):.1f}% of the thread)", ours[thread], count, args.top)
    mine_all = Counter()
    for thread, counter in ours.items():
        mine_all[thread] = sum(counter.values())
    if mine_all:
        table("== Samples with a Dynamic Surroundings frame, by thread", mine_all, total, args.top)

    classes, sites, ours_alloc = Counter(), Counter(), Counter()
    for _, fields, frames in events(jfr, recording, ["jdk.ObjectAllocationSample"], 32):
        weight = size_bytes(fields.get("weight"))
        classes[fields.get("objectClass", "?").split(" ")[0]] += weight
        if frames:
            sites[next((o for o in map(owner, frames) if o), "(game / JDK)")] += weight
        mine = next((f for f in frames if "orecruncher" in f), None)
        if mine:
            ours_alloc[mine] += weight
    allocated = sum(classes.values())
    print(f"\n== Allocation (sampled weight {allocated / 1024 ** 3:.1f} GB)")
    table("  by class", classes, allocated, args.top, "MB")
    table("  by mod package", sites, allocated, args.top, "MB")
    if ours_alloc:
        table(f"  Dynamic Surroundings sites ({100 * sum(ours_alloc.values()) / max(allocated, 1):.1f}%)",
              ours_alloc, allocated, args.top, "MB")

    pauses = [duration_ms(f.get("duration")) for _, f, _ in events(jfr, recording, ["jdk.GCPhasePause"], 1)]
    if pauses:
        pauses.sort()
        print(f"\n== GC pauses: {len(pauses)}, total {sum(pauses):.0f} ms, median {pauses[len(pauses) // 2]:.1f} ms,"
              f" longest {pauses[-1]:.1f} ms")
    ticks = [duration_ms(f.get("averageTickDuration")) for _, f, _ in
             events(jfr, recording, ["minecraft.ServerTickTime"], 1)]
    if ticks:
        print(f"== Server tick (integrated): mean {sum(ticks) / len(ticks):.1f} ms, worst second {max(ticks):.1f} ms")


if __name__ == "__main__":
    main()
