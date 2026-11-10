# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Brings sounds.json's ds_credits (shown in the sound settings and the now-playing toast) in line with CREDITS.md,
which is kept by hand and is the one to trust: each event a CREDITS.md heading names gets that heading's sources, in
order (title, author, the author's page or else the source's, the license without its link). Events CREDITS.md doesn't
name keep their own (the footsteps, credited there in one line). Prints what would change; --write writes it.

Usage: python scripts/credits-sync.py [--write]
"""
import json, re, sys
CRED = "CREDITS.md"
SJ = "common/src/main/resources/assets/dsurround/sounds.json"
LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")

def parse_entry(line):
    body = line.strip()[2:].strip()
    if body.startswith("S:"):
        body = body[2:].strip()
    parts = [p.strip() for p in body.split("|")]
    head, rest = parts[0], parts[1:]
    lic = next((p for p in rest if p.lower().startswith("license")), None)
    if lic is None:
        return None
    lic = re.sub(r"^License:?\s*", "", lic, flags=re.I)
    lic = LINK.sub(lambda m: m.group(1), lic).strip()
    links = LINK.findall(head)
    m = re.search(r"\bby\s+(.*)$", head)
    by = m.group(1) if m else ""
    title = links[0][0] if links else re.sub(r"\s*\bby\b.*$", "", head).strip()
    source = links[0][1] if links else None
    alinks = LINK.findall(by)
    if alinks:
        author, website = alinks[0][0], alinks[0][1]
    else:
        author = LINK.sub(lambda mm: mm.group(1), by).split(",")[0].strip() or title
        website = source
    if title.startswith("Made from") or not links and head.startswith("Made from"):
        title = head.split(" of ")[-1].split(" by ")[0] if " of " in head else head
    if head.startswith("Public domain recordings"):
        return {"name": "Public domain recording", "author": "Source not recorded", "license": lic}
    # the source's own page for a credit naming no author's page, the author's page otherwise
    return {"name": LINK.sub(lambda mm: mm.group(1), title).strip(), "author": author.strip(),
            **({"website": website.replace("https://www.freesound.org", "https://freesound.org")} if website else {}),
            "license": lic}

def parse_credits():
    out, heads, in_sounds = {}, None, False
    for line in open(CRED, encoding="utf-8"):
        if line.startswith("> ### Sound"):
            in_sounds = True
        if not in_sounds:
            continue
        s = line.strip()
        if s.startswith("*dsurround:") and s.endswith("*"):
            heads = [h.strip().split(":", 1)[1] for h in s.strip("*").split("/") if ":" in h]
            continue
        if heads and s.startswith("* "):
            e = parse_entry(s)
            if e:
                for h in heads:
                    out.setdefault(h, []).append(e)
    return out

credits = parse_credits()
sj = json.load(open(SJ, encoding="utf-8"))
text = open(SJ, encoding="utf-8", newline="").read()
nl = "\r\n" if "\r\n" in text else "\n"
missing = [h for h in credits if h not in sj]
uncovered = [k for k, v in sj.items() if v.get("ds_credits") and k not in credits]
changed = []
for ev, entries in credits.items():
    if ev not in sj:
        continue
    if sj[ev].get("ds_credits") == entries:
        continue
    changed.append((ev, sj[ev].get("ds_credits"), entries))
    m = re.search(r'\t"' + re.escape(ev) + r'": \{.*?\n\t\}', text, re.S)
    blk = m.group(0)
    new = re.sub(r'\t\t"ds_credits": \[.*?\n\t\t\],\r?\n', "", blk, flags=re.S)
    items = []
    for e in entries:
        fields = [f'\t\t\t\t"{k}": {json.dumps(v, ensure_ascii=False)}' for k, v in e.items()]
        items.append("\t\t\t{" + nl + ("," + nl).join(fields) + nl + "\t\t\t}")
    block = '\t\t"ds_credits": [' + nl + ("," + nl).join(items) + nl + "\t\t]," + nl
    i = new.index('\t\t"sounds": [')
    new = new[:i] + block + new[i:]
    text = text.replace(blk, new)
for ev, old, new in changed:
    print(f"{ev}:")
    print(f"   was: {[ (o.get('name'), o.get('author'), o.get('license')) for o in (old or [])]}")
    print(f"   now: {[ (n['name'], n['author'], n['license']) for n in new]}")
print(f"\n{len(changed)} events to update; CREDITS headings with no such event: {missing}; events credited only in sounds.json: {uncovered}")
if "--write" in sys.argv:
    json.loads(text)
    open(SJ, "w", encoding="utf-8", newline="").write(text)
    print("written")
