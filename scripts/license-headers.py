#!/usr/bin/env python3
"""Puts an SPDX license header at the top of every tracked source file (Java, shaders, Python scripts).

The copyright line is taken from LICENSE, so after changing the years there, run this again to bring every header
up to date. Files that already name another copyright holder (third-party code) are left alone and listed.

    python scripts/license-headers.py [--check]

--check changes nothing and exits 1 if any file is missing its header or has an out-of-date one.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPDX = "SPDX-License-Identifier: MIT"
URL = "https://github.com/OreCruncher/DynamicSurroundingsFabric"

# Comment marker by file type
STYLES = {
    ".java": "//",
    ".fsh": "//",
    ".vsh": "//",
    ".glsl": "//",
    ".py": "#",
}


def copyright_line():
    for line in (ROOT / "LICENSE").read_text(encoding="utf-8").splitlines():
        if line.startswith("Copyright"):
            return line.strip()
    sys.exit("No Copyright line in LICENSE")


def tracked_files():
    out = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True).stdout
    for name in out.decode("utf-8").split("\0"):
        if name and Path(name).suffix in STYLES:
            yield ROOT / name


def header_lines(marker, copyright_text):
    return [f"{marker} {SPDX}", f"{marker} {copyright_text} - {URL}"]


def insert_at(lines, suffix):
    """Where the header goes: after a shader's #version (it must come first) or a script's shebang."""
    if suffix in (".fsh", ".vsh", ".glsl") and lines and lines[0].startswith("#version"):
        return 1
    if suffix == ".py" and lines and lines[0].startswith("#!"):
        return 1
    return 0


def process(path, copyright_text, check):
    """Returns 'ok', 'added', 'updated' or 'third-party'."""
    raw = path.read_bytes().decode("utf-8")
    newline = "\r\n" if "\r\n" in raw else "\n"
    lines = raw.split(newline)
    marker = STYLES[path.suffix]
    wanted = header_lines(marker, copyright_text)

    for i, line in enumerate(lines[:5]):
        if SPDX in line:
            if lines[i:i + 2] == wanted:
                return "ok"
            if not check:
                lines[i:i + 2] = wanted
                path.write_bytes(newline.join(lines).encode("utf-8"))
            return "updated"

    # Someone else's notice near the top: not ours to stamp
    if re.search(r"(?i)copyright", "\n".join(lines[:15])):
        return "third-party"

    if not check:
        at = insert_at(lines, path.suffix)
        block = list(wanted)
        # A blank line between the header and what follows, unless one is already there
        if at < len(lines) and lines[at].strip():
            block.append("")
        lines[at:at] = block
        path.write_bytes(newline.join(lines).encode("utf-8"))
    return "added"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="report only; exit 1 if anything needs changing")
    args = parser.parse_args()

    copyright_text = copyright_line()
    counts = {"ok": 0, "added": 0, "updated": 0, "third-party": 0}
    skipped = []
    for path in tracked_files():
        result = process(path, copyright_text, args.check)
        counts[result] += 1
        if result == "third-party":
            skipped.append(path.relative_to(ROOT).as_posix())

    verb = "need" if args.check else "got"
    print(f"{counts['ok']} up to date, {counts['added']} {verb} a header, {counts['updated']} {verb} an update")
    for name in skipped:
        print(f"skipped (another copyright holder): {name}")
    if args.check and (counts["added"] or counts["updated"]):
        sys.exit(1)


if __name__ == "__main__":
    main()
