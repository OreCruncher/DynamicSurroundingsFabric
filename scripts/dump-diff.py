#!/usr/bin/env python3
"""
Compares two `/dsdump biomes` files (config/dsurround/dumps/biomes.txt): what changed, not everything.

  dump-diff.py OLD NEW [--sounds]   biomes added or gone, traits gained or lost, traits whose source changed
                                    (name -> definition), and the "Biomes to check" list before and after; --sounds
                                    also compares each biome's sound factories
  dump-diff.py --keep DUMP          copies a dump to build/dumps/<platform>-<time>.txt, as the next one overwrites it

Fabric's and NeoForge's dumps of the same world can be compared too.
"""
import argparse
import os
import re
import shutil
import sys
import time

BIOME = re.compile(r"^Biome \[(.*)/([^/\]]+)\]$")
GROUP = re.compile(r"^(.*) \(([^()]+)\)$")
CHECK = re.compile(r"^  (\S+:\S+): (.*)$")
FACTORY = re.compile(r"Factory \{loc=([^,}]+)")


class Biome:
    def __init__(self):
        self.traits = {}
        self.sounds = {}


def parse(path):
    biomes, checks = {}, {}
    current, section, in_checks = None, None, False
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.rstrip("\r\n")
            match = BIOME.match(line)
            if match:
                current = biomes.setdefault(match.group(2), Biome())
                section, in_checks = None, False
                continue
            if line.startswith("Biomes to check"):
                current, in_checks = None, True
                continue
            if in_checks:
                match = CHECK.match(line)
                if match:
                    checks[match.group(1)] = match.group(2)
                elif line.startswith("Biomes each dimension"):
                    in_checks = False
                continue
            if current is None:
                continue
            if line.startswith("Traits:"):
                for part in line[len("Traits:"):].strip().split("; "):
                    match = GROUP.match(part)
                    if not match:
                        continue
                    for trait in match.group(1).split(", "):
                        current.traits[trait.strip()] = match.group(2)
            elif re.match(r"^[A-Z]+ sounds \[$", line):
                section = line.split()[0]
            elif line == "]":
                section = None
            elif section:
                match = FACTORY.search(line)
                if match:
                    current.sounds.setdefault(section, set()).add(match.group(1))
    return biomes, checks


def describe(traits):
    return ", ".join(f"{trait} ({source})" for trait, source in sorted(traits.items()))


def diff(old_path, new_path, sounds):
    old, old_checks = parse(old_path)
    new, new_checks = parse(new_path)
    gone = sorted(set(old) - set(new))
    added = sorted(set(new) - set(old))
    changed = []
    for id in sorted(set(old) & set(new)):
        a, b = old[id], new[id]
        lines = []
        lost = {t: s for t, s in a.traits.items() if t not in b.traits}
        gained = {t: s for t, s in b.traits.items() if t not in a.traits}
        moved = {t: (a.traits[t], b.traits[t]) for t in a.traits if t in b.traits and a.traits[t] != b.traits[t]}
        if gained:
            lines.append(f"+ {describe(gained)}")
        if lost:
            lines.append(f"- {describe(lost)}")
        for trait, (was, now) in sorted(moved.items()):
            lines.append(f"~ {trait}: {was} -> {now}")
        if sounds:
            for section in sorted(set(a.sounds) | set(b.sounds)):
                was, now = a.sounds.get(section, set()), b.sounds.get(section, set())
                if now - was:
                    lines.append(f"+ {section}: {', '.join(sorted(now - was))}")
                if was - now:
                    lines.append(f"- {section}: {', '.join(sorted(was - now))}")
        if lines:
            changed.append((id, lines))

    print(f"{len(old)} -> {len(new)} biomes; {len(changed)} changed")
    if added:
        print(f"\nnew ({len(added)}):")
        for id in added:
            print(f"  {id}: {describe(new[id].traits)}")
    if gone:
        print(f"\ngone ({len(gone)}):")
        for id in gone:
            print(f"  {id}")
    if changed:
        print("\nchanged:")
        for id, lines in changed:
            print(f"  {id}")
            for line in lines:
                print(f"    {line}")

    print(f"\nbiomes to check: {len(old_checks)} -> {len(new_checks)}")
    for id in sorted(set(old_checks) - set(new_checks)):
        print(f"  settled: {id} (was: {old_checks[id]})")
    for id in sorted(set(new_checks) - set(old_checks)):
        print(f"  new:     {id}: {new_checks[id]}")
    for id in sorted(set(old_checks) & set(new_checks)):
        if old_checks[id] != new_checks[id]:
            print(f"  changed: {id}: {old_checks[id]} -> {new_checks[id]}")
        else:
            print(f"  still:   {id}: {new_checks[id]}")


def keep(path):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    parts = os.path.abspath(path).replace("\\", "/").split("/")
    platform = parts[parts.index("run") - 1] if "run" in parts and parts.index("run") > 0 else "dump"
    stamp = time.strftime("%Y%m%d-%H%M", time.localtime(os.path.getmtime(path)))
    folder = os.path.join(root, "build", "dumps")
    os.makedirs(folder, exist_ok=True)
    target = os.path.join(folder, f"{platform}-{stamp}.txt")
    shutil.copyfile(path, target)
    print(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dumps", nargs="*", metavar="DUMP")
    parser.add_argument("--sounds", action="store_true", help="also compare each biome's sound factories")
    parser.add_argument("--keep", metavar="DUMP", help="copy a dump to build/dumps")
    args = parser.parse_args()
    if args.keep:
        keep(args.keep)
    elif len(args.dumps) == 2:
        diff(args.dumps[0], args.dumps[1], args.sounds)
    else:
        parser.print_usage()
        sys.exit(2)


if __name__ == "__main__":
    main()
