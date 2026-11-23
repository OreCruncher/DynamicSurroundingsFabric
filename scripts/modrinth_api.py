# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""
What the scripts that read Modrinth share (modpack-fetch.py, item-mod-survey.py, biome-mod-survey.py,
modrinth-version-stats.py): its API, one User-Agent, a search past a download count, a download cache checked against
the file's hash, and the mod ids a jar declares. Imported, not run: a script puts its own folder on sys.path first, so
it works whatever folder the script is run from.
"""

import hashlib
import json
import re
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "build" / "modpack-cache"
API = "https://api.modrinth.com/v2"
USER_AGENT = "OreCruncher/DynamicSurroundings scripts (github.com/OreCruncher/DynamicSurroundingsFabric)"

MODS_TOML_ID = re.compile(r'^\s*modId\s*=\s*"([^"]+)"', re.MULTILINE)
# A mods.toml table header (a comment may follow it): only [[mods]] tables declare the jar's mods; [[dependencies.x]]
# name the ones it needs
TOML_TABLE = re.compile(r"^\s*\[\[?([^\]]+)\]\]?\s*(?:#.*)?$", re.MULTILINE)


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def get_json(url, **params):
    """A JSON answer; a list parameter is sent as JSON, as Modrinth takes its facets and versions"""
    if params:
        url += "?" + urllib.parse.urlencode({k: json.dumps(v) if isinstance(v, list) else v for k, v in params.items()})
    return json.loads(get(url))


def search(facets, min_downloads, query=None, leave_out=()):
    """
    Projects matching the facets (a list of lists, as Modrinth takes them), by downloads, down to min_downloads,
    less those in any of the categories in leave_out
    """
    found, offset = [], 0
    while True:
        params = {"facets": facets, "index": "downloads", "limit": 100, "offset": offset}
        if query:
            params["query"] = query
        page = get_json(API + "/search", **params)
        for hit in page["hits"]:
            if hit["downloads"] < min_downloads:
                return found
            if not set(leave_out) & set(hit["categories"]):
                found.append(hit)
        offset += len(page["hits"])
        if not page["hits"] or offset >= page["total_hits"]:
            return found


def cached(url, sha512, name):
    """A download, from the cache if a file with that hash is there; checked against the hash either way"""
    target = CACHE / sha512[:2] / sha512 / name
    if target.exists():
        return target
    data = get(url)
    if hashlib.sha512(data).hexdigest() != sha512:
        sys.exit(f"{name}: downloaded file doesn't match its hash")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def toml_mod_ids(text):
    ids = []
    tables = list(TOML_TABLE.finditer(text))
    for n, table in enumerate(tables):
        if table.group(1).strip() != "mods":
            continue
        end = tables[n + 1].start() if n + 1 < len(tables) else len(text)
        ids += MODS_TOML_ID.findall(text[table.end():end])
    return ids


def normal_id(mod_id):
    """Fabric ids use '-' where NeoForge's can't: compared with it as '_', given on the command line either way"""
    return mod_id.replace("-", "_")


def mod_ids(jar, normalize=True):
    """
    The mod ids a jar declares: NeoForge's (or Forge's) mods.toml, and fabric.mod.json. Normalized (see normal_id) for
    comparing ids across loaders; as written for matching an assets folder, which is named by the id as it is
    """
    ids = set()
    try:
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            for toml in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
                if toml in names:
                    ids.update(toml_mod_ids(z.read(toml).decode("utf-8", "replace")))
            if "fabric.mod.json" in names:
                try:
                    ids.add(json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)["id"])
                except (ValueError, KeyError):
                    pass
    except zipfile.BadZipFile:
        pass
    return {normal_id(i) for i in ids} if normalize else ids
