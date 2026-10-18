#!/usr/bin/env python3
"""
Helps merge a 1.21.1 branch (OreCruncher/0.4.7) into the 26.2 branch, and move code between the two by hand.

  port-merge.py merge [--dry-run] [--show]
                                    after `git merge OreCruncher/0.4.7` stops on conflicts: renames the 1.21.1 side's
                                    names (port-renames.txt) and merges each conflicted file again. Files that then
                                    merge cleanly are written and staged; the rest keep fewer conflict markers, listed
                                    with their line numbers. Files merged cleanly by git but carrying 1.21.1 names are
                                    renamed and staged too. --show prints each remaining conflict.
  port-merge.py convert --to {26.2,1.21.1} FILE...
                                    renames the names in files in place (for copying code across by hand); going to
                                    1.21.1 only the whole-word renames apply, and lines the others would have made are
                                    listed.
  port-merge.py check-table         checks no rule matches the 26.2 branch's own Java (run on OreCruncher/26.2).

The merge assumes "ours" is 26.2 and "theirs" is 1.21.1. Compile afterwards: a rename can't see types.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile

TABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "port-renames.txt")
MARKER = re.compile(r"^<<<<<<< ", re.M)


class Rule:
    def __init__(self, pattern, replacement, reversible, old, new):
        self.pattern = re.compile(pattern)
        self.replacement = replacement
        self.reversible = reversible
        self.old = old
        self.new = new


def load_rules():
    rules = []
    with open(TABLE, encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("re:"):
                pattern, _, replacement = line[3:].partition("=>")
                if not _:
                    sys.exit(f"{TABLE}:{number}: expected 're: <regex> => <replacement>'")
                rules.append(Rule(pattern.strip(), replacement.strip(), False, None, None))
            else:
                old, _, new = line.partition("=")
                if not _:
                    sys.exit(f"{TABLE}:{number}: expected 'Old = New'")
                old, new = old.strip(), new.strip()
                rules.append(Rule(r"\b" + re.escape(old) + r"\b", new, True, old, new))
    return rules


def to_26_2(text, rules):
    for rule in rules:
        text = rule.pattern.sub(rule.replacement, text)
    return text


def to_1_21_1(text, rules):
    for rule in reversed(rules):
        if rule.reversible:
            text = re.sub(r"\b" + re.escape(rule.new) + r"\b", rule.old, text)
    return text


def git(*args, check=True):
    result = subprocess.run(["git", *args], capture_output=True, check=False)
    if check and result.returncode != 0:
        sys.exit(f"git {' '.join(args)} failed: {result.stderr.decode(errors='replace').strip()}")
    return result


def read_text(path):
    with open(path, "rb") as f:
        return f.read().decode("utf-8", "surrogateescape")


def write_text(path, text, crlf):
    text = text.replace("\r\n", "\n")
    if crlf:
        text = text.replace("\n", "\r\n")
    with open(path, "wb") as f:
        f.write(text.encode("utf-8", "surrogateescape"))


def uses_crlf(path):
    try:
        return b"\r\n" in open(path, "rb").read()
    except OSError:
        return False


def stages():
    """Unmerged paths: {path: {stage: blob}}"""
    result = {}
    out = git("ls-files", "-u", "-z").stdout.decode("utf-8", "surrogateescape")
    for entry in filter(None, out.split("\0")):
        info, path = entry.split("\t", 1)
        _, blob, stage = info.split()
        result.setdefault(path, {})[int(stage)] = blob
    return result


def blob_text(blob):
    return git("cat-file", "blob", blob).stdout.decode("utf-8", "surrogateescape").replace("\r\n", "\n")


def conflict_lines(text):
    lines = []
    for number, line in enumerate(text.split("\n"), 1):
        if line.startswith("<<<<<<< "):
            lines.append(number)
    return lines


def merge_file(ours, base, theirs):
    with tempfile.TemporaryDirectory() as folder:
        paths = []
        for name, text in (("ours", ours), ("base", base), ("theirs", theirs)):
            path = os.path.join(folder, name)
            with open(path, "wb") as f:
                f.write(text.encode("utf-8", "surrogateescape"))
            paths.append(path)
        result = git("merge-file", "-p", "-L", "ours (26.2)", "-L", "base", "-L", "theirs (1.21.1, renamed)", *paths,
                     check=False)
        if result.returncode < 0:
            sys.exit("git merge-file failed")
        return result.returncode, result.stdout.decode("utf-8", "surrogateescape")


def hunks(text):
    """The conflict blocks in merged text, each with its first line number"""
    result, current, start = [], None, 0
    for number, line in enumerate(text.split("\n"), 1):
        if line.startswith("<<<<<<< "):
            current, start = [], number
        if current is not None:
            current.append(line)
            if line.startswith(">>>>>>> "):
                result.append((start, current))
                current = None
    return result


def merge(dry_run, show):
    root = git("rev-parse", "--show-toplevel").stdout.decode().strip()
    os.chdir(root)
    if git("rev-parse", "-q", "--verify", "MERGE_HEAD", check=False).returncode != 0:
        sys.exit("no merge in progress")
    rules = load_rules()
    unmerged = stages()
    resolved, conflicted, by_hand, renamed = [], [], [], []

    for path in sorted(unmerged):
        entry = unmerged[path]
        if 2 not in entry or 3 not in entry:
            by_hand.append((path, "deleted on 26.2" if 2 not in entry else "deleted on 1.21.1"))
            continue
        if not os.path.exists(path) or not MARKER.search(read_text(path)):
            by_hand.append((path, "no conflict markers left (already being resolved?): left alone"))
            continue
        java = path.endswith(".java")
        ours = blob_text(entry[2])
        base = blob_text(entry[1]) if 1 in entry else ""
        theirs = blob_text(entry[3])
        if java:
            base, theirs = to_26_2(base, rules), to_26_2(theirs, rules)
        count, text = merge_file(ours, base, theirs)
        crlf = uses_crlf(path)
        if count == 0:
            resolved.append(path)
            if not dry_run:
                write_text(path, text, crlf)
                git("add", "--", path)
        else:
            conflicted.append((path, conflict_lines(text), 1 not in entry, text))
            if not dry_run:
                write_text(path, text, crlf)

    # Merged cleanly by git, but 0.4.7's lines may carry 1.21.1 names
    staged = git("diff", "--cached", "--name-only", "-z", "--diff-filter=AM", "HEAD").stdout.decode("utf-8").split("\0")
    for path in sorted(p for p in staged if p.endswith(".java") and p not in unmerged):
        text = read_text(path)
        new = to_26_2(text, rules)
        if new != text:
            renamed.append(path)
            if not dry_run:
                write_text(path, new, False)
                git("add", "--", path)

    prefix = "would be " if dry_run else ""
    print(f"{len(unmerged)} conflicted files")
    if resolved:
        print(f"\n{prefix}resolved after renaming ({len(resolved)}), 26.2's side kept where they agree:")
        for path in resolved:
            print(f"  {path}")
    if renamed:
        print(f"\n{prefix}renamed in files git merged cleanly ({len(renamed)}):")
        for path in renamed:
            print(f"  {path}")
    if conflicted:
        print(f"\nstill in conflict ({len(conflicted)}), conflicts start at lines:")
        for path, lines, added, text in conflicted:
            note = " (added on both sides: whole file compared)" if added else ""
            print(f"  {path}: {', '.join(map(str, lines))}{note}")
            if show:
                for start, block in hunks(text):
                    print(f"    --- line {start}")
                    for line in block:
                        print(f"    {line}")
    if by_hand:
        print(f"\nto resolve by hand ({len(by_hand)}):")
        for path, why in by_hand:
            print(f"  {path}: {why}")
    print("\nNext: compile (./gradlew :common:compileJava :common:compileTestJava :fabric:compileJava "
          ":neoforge:compileJava -q); a rename that missed a call shape shows there, and belongs in port-renames.txt.")


def convert(target, files):
    rules = load_rules()
    forward_only = [rule for rule in rules if not rule.reversible]
    for path in files:
        text = read_text(path)
        crlf = "\r\n" in text
        new = to_26_2(text, rules) if target == "26.2" else to_1_21_1(text, rules)
        if new != text:
            write_text(path, new, crlf)
            print(f"{path}: renamed")
        else:
            print(f"{path}: nothing to rename")
        if target == "1.21.1":
            for number, line in enumerate(new.split("\n"), 1):
                for rule in forward_only:
                    shape = re.sub(r"\\\d", "", rule.replacement)
                    if shape and shape in line:
                        print(f"  {path}:{number}: may need 1.21.1's name by hand: {line.strip()}")


def check_table():
    rules = load_rules()
    root = git("rev-parse", "--show-toplevel").stdout.decode().strip()
    os.chdir(root)
    hits = 0
    for path in git("ls-files", "-z", "*.java").stdout.decode("utf-8").split("\0"):
        if not path or not os.path.exists(path):
            continue
        for number, line in enumerate(read_text(path).split("\n"), 1):
            for rule in rules:
                if rule.pattern.search(line):
                    hits += 1
                    print(f"{path}:{number}: '{rule.pattern.pattern}' matches: {line.strip()}")
    print(f"{hits} matches" if hits else "no rule matches this branch's Java")
    sys.exit(1 if hits else 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    merge_command = commands.add_parser("merge", help="resolve what renaming resolves in a merge in progress")
    merge_command.add_argument("--dry-run", action="store_true", help="report only; change nothing")
    merge_command.add_argument("--show", action="store_true", help="print each remaining conflict")
    convert_command = commands.add_parser("convert", help="rename names in files in place")
    convert_command.add_argument("--to", required=True, choices=["26.2", "1.21.1"])
    convert_command.add_argument("files", nargs="+")
    commands.add_parser("check-table", help="check no rule matches this branch's Java")
    args = parser.parse_args()
    if args.command == "merge":
        merge(args.dry_run, args.show)
    elif args.command == "convert":
        convert(args.to, args.files)
    else:
        check_table()


if __name__ == "__main__":
    main()
