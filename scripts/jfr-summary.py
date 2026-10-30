#!/usr/bin/env python3
"""
Summarizes a `/dsprofile` recording (config/dsurround/profiles/*.zip or the .jfr inside): what it was made on (and
the optional hooks, conflicts and settings that aren't as they are out of the box), the frames (how long they took, and
drawing one on the GPU and CPU), the mod's render timers (each as a share of drawing a frame), the
client tick's handlers (and what running them in parallel could gain), what random block effects did by distance (each
rate against what the sampler means it to be), where each busy thread spends its time (by
method and by mod package), what allocates, and GC.

  jfr-summary.py RECORDING [--thread NAME] [--top N] [--jfr PATH] [--dispatch-us US] [--worth-ms MS] [--shell-width N]

  --thread   only this thread's samples for the method tables (default: the busiest few threads)
  --top      rows per table (default 15)
  --jfr      the JDK's jfr tool (default: newest Adoptium JDK, else `jfr` on the path)
  --callers  METHOD (a substring, e.g. BiomeManager.getBiome): only who calls it, on --thread or every thread;
             the first mod frame above it and the nearest caller outside it
  --dispatch-us  for the handler ticks: what handing the handlers to other threads and waiting for them would cost a
             tick, in microseconds (default 50); measure it on the machine to trust the gain
  --worth-ms the gain a tick must have for running the handlers in parallel to be worth it (default 0.5)
  --shell-width  blocks of distance to a row for random block effects (default 2)

Reads `jfr print` text output, which is far smaller than its JSON. A zip is unpacked next to itself.
"""
import argparse
import datetime
import glob
import math
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


def started(value):
    # jfr prints a start time as "10:11:14.829 (2026-10-25)": the date first, so a recording past midnight sorts right
    match = re.match(r"([\d:.]+) \(([\d-]+)\)", value or "")
    return (match.group(2), match.group(1)) if match else ("", value or "")


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


# How the client tick's handlers could run in parallel: these first, as now, then everything else at once; a chain
# stays in order on one thread (lightning hands the cloud handler its strikes). Names are AbstractClientHandler's
# Render timers measured once a tick rather than once a frame, in recordings from before the events said so (perTick)
TICK_TIMERS = ("- Field",)

FIRST_HANDLERS = ("Scanners",)
HANDLER_CHAINS = (("Lightning Handler", "Cloud Handler"),)


def percentile(values, share):
    # Nearest rank, of values already sorted
    if not values:
        return 0.0
    return values[min(len(values) - 1, max(0, math.ceil(share * len(values)) - 1))]


def spread(values):
    values = sorted(values)
    return (f"{percentile(values, 0.5):7.3f} {percentile(values, 0.95):7.3f} {percentile(values, 0.99):7.3f} "
            f"{(values[-1] if values else 0.0):7.3f}")


def clock_ms(value):
    # A start time as milliseconds since the epoch's day: enough to compare times within a recording
    date, time = started(value)
    parts = time.split(":")
    if len(parts) != 3:
        return 0.0
    hours, minutes, seconds = parts
    day = datetime.date.fromisoformat(date).toordinal() if date else 0
    return ((day * 24 + int(hours)) * 60 + int(minutes)) * 60_000 + float(seconds) * 1000


def handler_ticks(jfr, recording, args):
    ticks = defaultdict(dict)
    ran = defaultdict(dict)
    spans = []
    for _, fields, _ in events(jfr, recording, ["dsurround.HandlerTick"], 1):
        tick = int(number(fields.get("tick")))
        handler = fields.get("handler", "?").strip('"')
        time = duration_ms(fields.get("time"))
        ticks[tick][handler] = time
        ran[tick][handler] = fields.get("ran") == "true"
        # Written once the handler is done: it ran for its time up to then
        end = clock_ms(fields.get("startTime"))
        spans.append((end - time, end, tick))
    if not ticks:
        return

    # A GC pause stops the handler it lands in, which then looks slow: those ticks are left out
    pauses = jvm_pauses(jfr, recording)
    paused = {tick for begin, end, tick in spans
              if any(start < end and start + length > begin for start, length in pauses)}
    worst = max((sum(ticks[t].values()) for t in paused), default=0.0)
    for tick in paused:
        del ticks[tick]
        del ran[tick]

    handlers = sorted({h for t in ticks.values() for h in t})
    totals = {tick: sum(times.values()) for tick, times in ticks.items()}
    whole = sum(totals.values())
    print(f"\n== Client tick handlers ({len(ticks)} ticks; ms; p50 p95 p99 max of the ticks it ran in; "
          f"share of all handler time)")
    if paused:
        print(f"  {len(paused)} tick{'s' if len(paused) > 1 else ''} with a GC pause or other JVM stop in "
              f"{'them' if len(paused) > 1 else 'it'} left out (the longest {worst:.3f} ms)")
    print(f"  {'handler':24} {'ran':>5} {'p50':>7} {'p95':>7} {'p99':>7} {'max':>7} {'mean/tick':>9} {'share':>6}")
    rows = []
    for handler in handlers:
        times = [t[handler] for tick, t in ticks.items() if handler in t and ran[tick].get(handler)]
        spent = sum(t.get(handler, 0.0) for t in ticks.values())
        rows.append((spent, handler, times))
    for spent, handler, times in sorted(rows, reverse=True):
        print(f"  {handler:24} {100 * len(times) / len(ticks):4.0f}% {spread(times)} {spent / len(ticks):9.3f} "
              f"{100 * spent / max(whole, 1e-9):5.1f}%")
    print(f"  {'whole tick':24} {'':5} {spread(totals.values())} {whole / len(ticks):9.3f}")
    p95 = percentile(sorted(totals.values()), 0.95)
    print(f"  p95 tick against a frame: {100 * p95 / 16.7:.0f}% of 60 fps (16.7 ms), {100 * p95 / 6.94:.0f}% of 144 fps "
          f"(6.94 ms); the tick runs on the render thread, inside a frame")

    # The ideal parallel tick: what can't move, then the longest of the rest, plus what handing them out costs
    chained = {h for chain in HANDLER_CHAINS for h in chain}
    dispatch = args.dispatch_us / 1000.0
    gains, paths, critical = [], [], Counter()
    for times in ticks.values():
        first = sum(times.get(h, 0.0) for h in FIRST_HANDLERS)
        units = {h: v for h, v in times.items() if h not in FIRST_HANDLERS and h not in chained}
        for chain in HANDLER_CHAINS:
            units[" > ".join(chain)] = sum(times.get(h, 0.0) for h in chain)
        longest = max(units, key=units.get) if units else "-"
        path = first + (units[longest] if units else 0.0) + dispatch
        critical[longest] += 1
        paths.append(path)
        gains.append(sum(times.values()) - path)
    worth = sum(1 for g in gains if g >= args.worth_ms)
    print(f"\n== Handlers in parallel, ideally ({', '.join(FIRST_HANDLERS)} first; "
          f"{'; '.join(' > '.join(c) for c in HANDLER_CHAINS)} in order; {args.dispatch_us:g} us to hand out and wait)")
    print(f"  {'':24} {'':5} {'p50':>7} {'p95':>7} {'p99':>7} {'max':>7}")
    print(f"  {'parallel tick':24} {'':5} {spread(paths)}")
    print(f"  {'gain (negative: slower)':24} {'':5} {spread(gains)}")
    print(f"  ticks gaining {args.worth_ms:g} ms or more: {worth} of {len(ticks)} ({100 * worth / len(ticks):.1f}%)")
    print("  longest after the first, the tick's floor:")
    for unit, count in critical.most_common(args.top):
        print(f"    {100 * count / len(ticks):5.1f}%  {unit}")


# What RandomBlockSampler means each block to get, to set the measured rates against; keep in step with it
NEAR_RANGE, FAR_RANGE, PAIRS, TAIL_RATE = 16, 32, 667, 1.2e-3


def sampler_spread(offset, reach):
    weight = reach + 1 - abs(offset)
    return weight / (reach + 1) ** 2 if weight > 0 else 0.0


def sampler_rates(effect_range, shells):
    """Per 1-block shell: the blocks in it, and the mean rate (samples a tick) RandomBlockSampler means them to get"""
    blocks, rates = [0] * shells, [0.0] * shells
    reach = shells - 1
    for x in range(reach + 1):
        for y in range(reach + 1):
            for z in range(reach + 1):
                shell = int(math.sqrt(x * x + y * y + z * z))
                if shell >= shells:
                    continue
                # One octant, counted for each sign it stands for
                copies = (1 if x == 0 else 2) * (1 if y == 0 else 2) * (1 if z == 0 else 2)
                inner = PAIRS * (sampler_spread(x, NEAR_RANGE) * sampler_spread(y, NEAR_RANGE) * sampler_spread(z, NEAR_RANGE)
                                 + sampler_spread(x, FAR_RANGE) * sampler_spread(y, FAR_RANGE) * sampler_spread(z, FAR_RANGE))
                distance = math.sqrt(x * x + y * y + z * z)
                tail = 0.0
                if FAR_RANGE < effect_range and distance < effect_range:
                    tail = TAIL_RATE if distance <= FAR_RANGE else TAIL_RATE * (effect_range - distance) / (effect_range - FAR_RANGE)
                blocks[shell] += copies
                rates[shell] += copies * max(inner, tail)
    return blocks, [r / b if b else 0.0 for r, b in zip(rates, blocks)]


def random_block_samples(jfr, recording, args):
    shells = defaultdict(lambda: Counter())
    ranges = Counter()
    for _, fields, _ in events(jfr, recording, ["dsurround.RandomBlockSamples"], 1):
        shell = int(number(fields.get("shell")))
        row = shells[shell]
        for key in ("ticks", "samples", "tailSamples", "effectBlocks", "starts", "particles", "sounds"):
            row[key] += int(number(fields.get(key)))
        ranges[int(number(fields.get("range")))] += 1
    if not shells:
        return
    # Each second's events carry that second's ticks; a shell sampled every second has them all
    ticks = max(row["ticks"] for row in shells.values())
    effect_range = ranges.most_common(1)[0][0]
    last = max(max(shells), effect_range)
    blocks, expected = sampler_rates(effect_range, last + 1)
    width = args.shell_width
    print(f"\n== Random block effects by distance ({ticks} ticks; block effect range {effect_range}"
          f"{', changed while recording: ' + ', '.join(map(str, sorted(ranges))) if len(ranges) > 1 else ''}; "
          f"rates a block a tick x 1000, effect blocks a tick; starts (effects kept running), particles (spawned outright) and "
          f"sounds over the whole recording, being rare)")
    print(f"  {'blocks out':>10} {'blocks':>7} {'rate':>7} {'meant':>7} {'tail':>6} {'effect blocks':>13} "
          f"{'starts':>7} {'particles':>9} {'sounds':>7}")
    previous = None
    for begin in range(0, last + 1, width):
        group = range(begin, min(begin + width, last + 1))
        count = sum(blocks[s] for s in group)
        if not count:
            continue
        rows = [shells.get(s, Counter()) for s in group]
        taken = sum(r["samples"] + r["tailSamples"] for r in rows)
        tail = sum(r["tailSamples"] for r in rows)
        rate = taken / (ticks * count)
        meant = sum(expected[s] * blocks[s] for s in group) / count
        found = sum(r["effectBlocks"] for r in rows) / ticks
        starts = sum(r["starts"] for r in rows)
        particles = sum(r["particles"] for r in rows)
        sounds = sum(r["sounds"] for r in rows)
        # Denser than nearer in is the band the sampler is meant not to have; off from what is meant, a sampler change
        # this script hasn't caught up with, or too short a recording
        notes = []
        if previous and rate > previous * 1.1:
            notes.append("denser than nearer")
        if meant and abs(rate - meant) > 0.1 * meant and taken >= 1000:
            notes.append("off from meant")
        previous = rate
        label = f"{group[0]}-{group[-1] + 1}"
        print(f"  {label:>10} {count:7d} {1000 * rate:7.3f} {1000 * meant:7.3f} "
              f"{(100 * tail / taken if taken else 0):5.0f}% {found:13.2f} {starts:7d} {particles:9d} {sounds:7d}"
              f"{'  ' + '; '.join(notes) if notes else ''}")


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


def jvm_pauses(jfr, recording):
    """
    When every thread was stopped, as (start, length) in ms, sorted: GC pauses, and any other stop of a millisecond or
    more (JFR instrumenting an event class, say, at its first event). Whatever was being timed then looks slow.
    """
    pauses = [(clock_ms(f.get("startTime")), duration_ms(f.get("duration")))
              for _, f, _ in events(jfr, recording, ["jdk.GCPhasePause"], 1)]
    pauses += [(clock_ms(f.get("startTime")), duration_ms(f.get("duration")))
               for _, f, _ in events(jfr, recording, ["jdk.ExecuteVMOperation"], 1)
               if f.get("safepoint") == "true" and duration_ms(f.get("duration")) >= 1.0]
    pauses.sort()
    return pauses


def paused_in(pauses, at, peak, window_ms=2000.0):
    """
    The longest JVM pause, in ms, that could be in a render timer's peak written at {at}: in the window the peak covers
    (the longest of the last second or two, TimerEMA), and no longer than the peak, with a little for rounding
    """
    return max((length for start, length in pauses
                if start < at and start + length > at - window_ms and length <= peak * 1.05 + 0.05), default=0.0)


def frame_times(jfr, recording):
    """
    Prints the frames (dsurround.Frames, a second's each), and gives the mean time drawing a frame took on the GPU and
    CPU, for the render timers' shares, and the mean frame's length; zeros for a recording from before frames were
    recorded
    """
    seconds = [f for _, f, _ in events(jfr, recording, ["dsurround.Frames"], 1)]
    if not seconds:
        return 0.0, 0.0, 0.0
    count = sum(int(number(f.get("frames"))) for f in seconds)
    # Each second's mean weighed by its frames: the recording's mean frame
    mean = sum(number(f.get("mean")) * int(number(f.get("frames"))) for f in seconds) / count
    medians = sorted(number(f.get("p50")) for f in seconds)
    p95s = sorted(number(f.get("p95")) for f in seconds)
    gpu = sum(number(f.get("gpu")) for f in seconds) / len(seconds)
    cpu = sum(number(f.get("cpu")) for f in seconds) / len(seconds)
    print(f"\n== Frames ({len(seconds)} seconds, {count} frames; ms)")
    print(f"  {1000 / mean:.0f} fps; frame mean {mean:.2f}, median second's median {percentile(medians, 0.5):.2f}")
    print(f"  a second's 95th percentile: median {percentile(p95s, 0.5):.2f}, worst {p95s[-1]:.2f}; "
          f"longest frame {max(number(f.get('max')) for f in seconds):.2f}")
    print(f"  drawing a frame (the world and the screen over it), mean: GPU {gpu:.3f}, CPU {cpu:.3f}")
    return gpu, cpu, mean


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("recording")
    parser.add_argument("--thread")
    parser.add_argument("--top", type=int, default=15)
    parser.add_argument("--jfr")
    parser.add_argument("--callers")
    parser.add_argument("--dispatch-us", type=float, default=50.0)
    parser.add_argument("--worth-ms", type=float, default=0.5)
    parser.add_argument("--shell-width", type=int, default=2)
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
    features = defaultdict(list)
    for _, fields, _ in events(jfr, recording, ["dsurround.Feature"], 1):
        features[fields.get("kind", "?").strip('"')].append(fields)
    if features:
        # Only what isn't as it is out of the box: a hook that didn't apply, a feature standing aside, a changed setting
        for kind, title in (("hook", "optional hooks not applied"), ("conflict", "features standing aside for a mod"),
                            ("setting", "settings changed from the default")):
            rows = features.get(kind, [])
            unusual = [r for r in rows if r.get("usual") != "true"]
            print(f"  {title}: {len(unusual)} of {len(rows)}")
            for row in unusual:
                print(f"    {row.get('name', '?').strip(chr(34))}: {row.get('state', '?').strip(chr(34))}")

    frame_gpu, frame_cpu, frame_ms = frame_times(jfr, recording)

    timers = defaultdict(list)
    for _, fields, _ in events(jfr, recording, ["dsurround.RenderTimer"], 1):
        timers[fields.get("effect", "?").strip('"')].append(fields)
    if timers:
        # Each average is a running one, written once a second. Their mean covers the whole recording, but carries a
        # slow start for a timer that measures rarely (once a second or less), whose average takes minutes to
        # forget it; the last is the running average at the end, the last few seconds for a timer measured every frame
        # A CPU peak is the longest of the last second or two, as the clock on the wall has it: a JVM pause landing
        # while it was timed is in it. A peak a pause in its window could make half of or more is left out; one shorter
        # than the pause can't have had it land in it. The GPU's aren't stopped by the JVM
        pauses = jvm_pauses(jfr, recording)
        print("\n== Render timers (ms; mean of the periodic averages, the running average at the end, highest peak; "
              "cpu peak leaves out those a JVM pause could make half of, and paused says how many; "
              "count: how many there were to draw a frame, mean, where the effect counts; gpu %, cpu %: the means' share "
              "of drawing a frame, where the recording has the frames; * timed once a tick, not a frame: its ms are a tick's, "
              "its shares those of its time a frame, 20 ticks a second spread over the frames)")
        print(f"  {'effect':28} {'gpu mean':>8} {'gpu end':>8} {'gpu peak':>9} "
              f"{'cpu mean':>8} {'cpu end':>8} {'cpu peak':>9} {'paused':>6} {'count':>8} {'gpu %':>6} {'cpu %':>6}")
        share = lambda part, whole: f"{100 * part / whole:6.1f}" if whole else f"{'-':>6}"
        for effect, rows in timers.items():
            rows.sort(key=lambda r: started(r.get("startTime", "")))
            # Recordings from before the flag have the clouds' field as the only timer measured on the tick
            per_tick = rows[-1].get("perTick") == "true" if "perTick" in rows[-1] else effect in TICK_TIMERS
            # A tick's time as a frame's: a tick is 50 ms, so ticks a frame is the frame's length over 50
            frame_share = (lambda part, whole: share(part * frame_ms / 50, whole)) if per_tick else share
            mean = lambda key: sum(number(r.get(key)) for r in rows) / len(rows)
            last = lambda key: number(rows[-1].get(key))
            peak = lambda key: max(number(r.get(key)) for r in rows)
            cpu_peaks = [(number(r.get("cpuPeak")), paused_in(pauses, clock_ms(r.get("startTime")), number(r.get("cpuPeak"))))
                         for r in rows]
            kept = [p for p, pause in cpu_peaks if pause < p / 2]
            paused = len(cpu_peaks) - len(kept)
            # Recordings from before the count have none; an effect that doesn't count writes NaN, which jfr prints as N/A
            counts = [number(r["count"]) for r in rows if r.get("count") and r["count"] not in ("N/A", "NaN")]
            count = f"{sum(counts) / len(counts):8.0f}" if counts else f"{'-':>8}"
            label = effect + " *" if per_tick else effect
            print(f"  {label:28} {mean('gpuAverage'):8.3f} {last('gpuAverage'):8.3f} {peak('gpuPeak'):9.3f} "
                  f"{mean('cpuAverage'):8.3f} {last('cpuAverage'):8.3f} {max(kept, default=0.0):9.3f} {paused:6} {count} "
                  f"{frame_share(mean('gpuAverage'), frame_gpu)} {frame_share(mean('cpuAverage'), frame_cpu)}")

    handler_ticks(jfr, recording, args)
    random_block_samples(jfr, recording, args)

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
    # A thread's first sample carries the weight of everything it allocated before the recording began (gigabytes, for
    # the render thread): left out, and how much that was said
    samples = sorted(events(jfr, recording, ["jdk.ObjectAllocationSample"], 32),
                     key=lambda e: started(e[1].get("startTime", "")))
    seen = set()
    before = 0
    for _, fields, frames in samples:
        weight = size_bytes(fields.get("weight"))
        thread = fields.get("eventThread", "?")
        if thread not in seen:
            seen.add(thread)
            before += weight
            continue
        classes[fields.get("objectClass", "?").split(" ")[0]] += weight
        if frames:
            sites[next((o for o in map(owner, frames) if o), "(game / JDK)")] += weight
        mine = next((f for f in frames if "orecruncher" in f), None)
        if mine:
            ours_alloc[mine] += weight
    allocated = sum(classes.values())
    print(f"\n== Allocation (sampled weight {allocated / 1024 ** 3:.1f} GB; each thread's first sample, "
          f"{before / 1024 ** 3:.1f} GB from before the recording, left out)")
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
