# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""Freesound candidates for a sound, from its public web pages (no account or API key): each query's results, most
downloaded first (two pages), then each result's length, license, description and tags, so field recordings can be
told from synthesized sounds, remixes, imitations and pets.

Usage: python scripts/freesound-search.py "query" ["query" ...] [--license cc0|by] [--user NAME --previews DIR]

  --license  CC0 (the default) or Attribution (CC BY: needs a credit line)
  --user, --previews  every sound of one user matching the queries (pack grouping off, so each sound, not one a
             pack), with its HQ preview MP3 downloaded into DIR: for matching the mod's files against a recordist's
             work with sound-match.py. Previews are MP3s, so a true match scores a little under 1
Read each page yourself before choosing: the description says what the license field can't (a recording by someone
other than the uploader, a remix of others' sounds, a vintage library transfer). Downloads of the full files need a
login; the author downloads them.
"""
import argparse
import html
import os
import re
import sys
import time
import urllib.parse
import urllib.request

UA = {"User-Agent": "Mozilla/5.0 (dsurround-dev-check)"}
LICENSES = {"cc0": "Creative+Commons+0", "by": "Attribution"}

p = argparse.ArgumentParser()
p.add_argument("queries", nargs="+")
p.add_argument("--license", choices=LICENSES, default="cc0")
p.add_argument("--user")
p.add_argument("--previews")
a = p.parse_args()
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def get(url, binary=False):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        data = r.read()
        return data if binary else data.decode("utf-8", "replace")


seen = {}
for q in a.queries:
    for page in range(1, 15 if a.user else 3):
        url = "https://freesound.org/search/?q=" + urllib.parse.quote_plus(q)
        if a.user:
            url += "&f=username%3A%22" + urllib.parse.quote_plus(a.user) + "%22&g=0"
        else:
            url += "&f=license%3A%22" + LICENSES[a.license] + "%22&s=Downloads+%28most+first%29"
        if page > 1:
            url += f"&page={page}"
        try:
            t = get(url)
        except Exception:
            break
        found = re.findall(r'href="(/people/[^/"]+/sounds/(\d+)/)"', t)
        new = [s for _, s in found if s not in seen]
        for path, sid in found:
            seen.setdefault(sid, path)
        if not new:
            break
        time.sleep(1)

for sid, path in seen.items():
    try:
        t = get("https://freesound.org" + path)
    except Exception:
        continue
    title = html.unescape(re.search(r"<title>Freesound - ([^<]*)", t).group(1)) if "<title>" in t else path
    dur = re.search(r'data-duration="([\d.]+)"', t)
    desc = re.search(r'name="twitter:description" content="([^"]*)"', t)
    tags = re.findall(r'href="/browse/tags/([^/"]+)', t)
    lic = re.search(r"creativecommons\.org/(publicdomain/zero|licenses/[a-z-]+)/([\d.]+)", t)
    print(f"{float(dur.group(1)) if dur else 0:7.1f}s  {title}  https://freesound.org{path}  "
          f"[{lic.group(1) + ' ' + lic.group(2) if lic else '?'}]")
    print(f"          {html.unescape(desc.group(1))[:220] if desc else ''}")
    print(f"          tags: {', '.join(dict.fromkeys(tags))[:160]}")
    if a.previews:
        m = re.search(r'(https://cdn\.freesound\.org/previews/[^"]+-hq\.mp3)', t)
        if m:
            os.makedirs(a.previews, exist_ok=True)
            out = os.path.join(a.previews, f"{sid}.mp3")
            if not os.path.exists(out):
                with open(out, "wb") as fh:
                    fh.write(get(m.group(1), True))
    time.sleep(0.7)
