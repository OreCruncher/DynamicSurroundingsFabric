#!/usr/bin/env python3
"""
Fetches a Modrinth modpack into a run folder of its own, for testing the mod alongside it
(scripts/bench/README.md, "Modpacks"): <platform>/run-pack/<slug>, which runClientPack -Ppack=<slug> runs.

    python scripts/modpack-fetch.py smp-aesthia                    the newest version for this branch's game and loader
    python scripts/modpack-fetch.py smp-aesthia --version 6.0.0-1  a version number (or id) of the pack
    python scripts/modpack-fetch.py path/to/pack.mrpack            a pack file already downloaded
    python scripts/modpack-fetch.py smp-aesthia --shaders          Iris on, with the pack's chosen shader pack
    python scripts/modpack-fetch.py <slug> --shader-pack BSL.zip    Iris on, with this shader pack (for a pack with none)
    python scripts/modpack-fetch.py smp-aesthia --keep dynamic_fps put back a mod left out by default
    python scripts/modpack-fetch.py <slug> --leave-out c2me         leave out a mod the development run can't load

Every file is checked against the pack's hash, and kept in build/modpack-cache so a fetch again downloads nothing
new. The folder's mods, resource packs, shader packs and config are replaced each time; its worlds, logs and bench
results are kept. Only the client's files are fetched.

Left out, by mod id (read from each jar, so a renamed file doesn't slip through):
  - mods the development run loads even for a pack (our own, and Architectury and Cloth Config, which it needs): two
    copies of a mod stop NeoForge loading. The mods the development client otherwise plays with (Biomes O' Plenty,
    Serene Seasons, Simple Voice Chat...) are left out of a pack run (-Ppack), so the pack's own, or none, are used.
  - mods that would hold an unattended run up: Crash Assistant (a window waiting for someone to close it, on a crash
    and on some exits) and Dynamic FPS (slows or stops drawing when the window isn't focused, which stalls a run and
    skews its timings); --keep <mod id> puts one back
What was fetched and left out, and why, is written to the folder's pack.json.
"""

import argparse
import hashlib
import json
import re
import shutil
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "build" / "modpack-cache"
API = "https://api.modrinth.com/v2"
USER_AGENT = "OreCruncher/DynamicSurroundings modpack-fetch (github.com/OreCruncher/DynamicSurroundingsFabric)"

# Loaded by a pack run too (each platform's build.gradle), so a pack's copy would be a second one
DEV_RUN_MODS = {
    "dsurround": "this mod",
    "architectury": "the development run loads its own",
    "cloth_config": "the development run loads its own",
}
# Would hold an unattended run up
UNATTENDED = {
    "crash_assistant": "opens a window that waits for someone to close it",
    "dynamic_fps": "slows or stops drawing while the window isn't focused",
}
# The folders a pack replaces each fetch; anything else in the run folder (saves, logs, bench results) is kept
REPLACED = ["mods", "resourcepacks", "shaderpacks", "config"]

MODS_TOML_ID = re.compile(r'^\s*modId\s*=\s*"([^"]+)"', re.MULTILINE)
# A mods.toml table header (a comment may follow it): only [[mods]] tables declare the jar's mods; [[dependencies.x]]
# name the ones it needs
TOML_TABLE = re.compile(r"^\s*\[\[?([^\]]+)\]\]?\s*(?:#.*)?$", re.MULTILINE)


def toml_mod_ids(text):
    ids = []
    tables = list(TOML_TABLE.finditer(text))
    for n, table in enumerate(tables):
        if table.group(1).strip() != "mods":
            continue
        end = tables[n + 1].start() if n + 1 < len(tables) else len(text)
        ids += MODS_TOML_ID.findall(text[table.end():end])
    return ids


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def get_json(url, **params):
    if params:
        url += "?" + urllib.parse.urlencode({k: json.dumps(v) if isinstance(v, list) else v for k, v in params.items()})
    return json.loads(get(url))


def gradle_properties():
    props = {}
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def pack_file(spec, version, game_version, loader):
    """The .mrpack (cached) and the slug to name its folder by"""
    path = Path(spec)
    if path.suffix == ".mrpack" and path.exists():
        return path, re.sub(r"[^a-z0-9_-]+", "-", path.stem.lower())
    versions = get_json(f"{API}/project/{spec}/version", loaders=[loader], game_versions=[game_version])
    # Modrinth's filter has let other loaders' versions through: checked here too
    versions = [v for v in versions if loader in v["loaders"] and game_version in v["game_versions"]]
    if version:
        versions = [v for v in versions if version in (v["version_number"], v["id"])]
    if not versions:
        sys.exit(f"{spec}: no {loader} version for {game_version}" + (f" named {version}" if version else ""))
    chosen = versions[0]
    file = next((f for f in chosen["files"] if f["primary"]), chosen["files"][0])
    print(f"{spec} {chosen['version_number']} ({chosen['date_published'][:10]})")
    return cached(file["url"], file["hashes"]["sha512"], file["filename"]), spec


def cached(url, sha512, name):
    """A download, from the cache if a file with that hash is there; checked against the hash either way"""
    target = CACHE / sha512[:2] / sha512 / name
    if target.exists():
        return target
    data = get(url)
    if hashlib.sha512(data).hexdigest() != sha512:
        sys.exit(f"{name}: downloaded file doesn't match the pack's hash")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def version_key(version):
    """A loader version's numbers, to compare: 26.2.0.90 > 26.2.0.82; 21.11.13-beta < 21.11.48"""
    return tuple(int(n) for n in re.findall(r"\d+", version.split("-")[0]))


def mod_ids(jar):
    """The mod ids a jar declares: NeoForge's mods.toml, or fabric.mod.json for one Connector loads"""
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
    # Fabric ids use '-' where NeoForge's can't
    return {i.replace("-", "_") for i in ids}


def left_out(ids, keep, leave_out):
    for i in sorted(ids):
        if i in leave_out:
            return f"{i}: left out by request"
        if i in DEV_RUN_MODS:
            return f"{i}: {DEV_RUN_MODS[i]}"
        if i in UNATTENDED and i not in keep:
            return f"{i}: {UNATTENDED[i]}"
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("pack", help="Modrinth slug or id, or a .mrpack file")
    parser.add_argument("--version", help="the pack version (number or id); default the newest")
    parser.add_argument("--platform", default="neoforge", choices=["neoforge", "fabric"])
    parser.add_argument("--game-version", help="default this branch's minecraft_version")
    parser.add_argument("--shaders", action="store_true", help="turn Iris shaders on, with the pack's shader pack")
    parser.add_argument("--shader-pack", metavar="ZIP", help="a shader pack to add and use (turns shaders on)")
    parser.add_argument("--leave-out", action="append", default=[], metavar="MOD_ID",
                        help="leave a mod out: one the development run can't load (on an obfuscated game, a mixin "
                             "into a lambda, whose name differs there)")
    parser.add_argument("--keep", action="append", default=[], metavar="MOD_ID",
                        help="fetch a mod left out by default for holding unattended runs up")
    args = parser.parse_args()

    props = gradle_properties()
    game_version = args.game_version or props["minecraft_version"]
    mrpack, slug = pack_file(args.pack, args.version, game_version, args.platform)

    with zipfile.ZipFile(mrpack) as z:
        index = json.loads(z.read("modrinth.index.json"))
        deps = index.get("dependencies", {})
        if args.platform == "neoforge" and "neoforge" not in deps:
            sys.exit(f"{slug} isn't a NeoForge pack ({deps})")
        if args.platform == "fabric" and "fabric-loader" not in deps:
            sys.exit(f"{slug} isn't a Fabric pack ({deps})")

        dest = ROOT / args.platform / "run-pack" / slug
        dest.mkdir(parents=True, exist_ok=True)
        for folder in REPLACED:
            shutil.rmtree(dest / folder, ignore_errors=True)

        fetched, excluded = [], []
        files = [f for f in index["files"] if f.get("env", {}).get("client", "required") != "unsupported"]
        print(f"  {len(files)} files")
        for entry in files:
            rel = Path(entry["path"])
            if rel.is_absolute() or ".." in rel.parts:
                sys.exit(f"{entry['path']}: a path outside the pack's folder")
            source = cached(entry["downloads"][0], entry["hashes"]["sha512"], rel.name)
            if rel.parts[0] == "mods":
                reason = left_out(mod_ids(source), args.keep, args.leave_out)
                if reason:
                    excluded.append({"file": str(rel).replace("\\", "/"), "why": reason})
                    continue
            (dest / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, dest / rel)
            fetched.append(str(rel).replace("\\", "/"))

        # The overrides, then the client's: config, options, mods the pack carries itself
        for prefix in ("overrides/", "client-overrides/"):
            for name in z.namelist():
                if not name.startswith(prefix) or name.endswith("/"):
                    continue
                rel = Path(name[len(prefix):])
                if rel.is_absolute() or ".." in rel.parts:
                    sys.exit(f"{name}: a path outside the pack's folder")
                target = dest / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(name))
                if rel.parts[0] == "mods" and rel.suffix == ".jar":
                    reason = left_out(mod_ids(target), args.keep, args.leave_out)
                    if reason:
                        target.unlink()
                        excluded.append({"file": str(rel).replace("\\", "/"), "why": reason})
                        continue
                fetched.append(str(rel).replace("\\", "/"))

    shader_pack = None
    if args.shader_pack:
        source = Path(args.shader_pack)
        if not source.is_file():
            sys.exit(f"{source}: no such shader pack")
        (dest / "shaderpacks").mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, dest / "shaderpacks" / source.name)
        shader_pack = source.name
        args.shaders = True
    iris = dest / "config" / "iris.properties"
    if args.shaders and not iris.exists() and shader_pack:
        # Iris writes the rest of its settings itself
        iris.parent.mkdir(parents=True, exist_ok=True)
        iris.write_text("enableShaders=true\nshaderPack=\n", encoding="utf-8")
    if iris.exists():
        text = iris.read_text(encoding="utf-8")
        text = re.sub(r"^enableShaders=.*$", f"enableShaders={'true' if args.shaders else 'false'}", text,
                      flags=re.MULTILINE)
        if shader_pack:
            text = re.sub(r"^shaderPack=.*$", f"shaderPack={shader_pack}", text, flags=re.MULTILINE)
        iris.write_text(text, encoding="utf-8")
        if args.shaders and not re.search(r"^shaderPack=.+$", text, flags=re.MULTILINE):
            print("warning: shaders on, but no shader pack is chosen (--shader-pack adds one)")
    elif args.shaders:
        print("warning: --shaders, but the pack has no config/iris.properties")

    loader_key = "neoforge" if args.platform == "neoforge" else "fabric-loader"
    ours = props.get("neoforge_version" if args.platform == "neoforge" else "fabric_loader_version")
    record = {
        "pack": slug,
        "source": str(mrpack),
        "name": index.get("name"),
        "version": index.get("versionId"),
        "dependencies": deps,
        "devRunLoader": ours,
        "shaders": args.shaders,
        "shaderPack": shader_pack,
        "fetched": fetched,
        "excluded": excluded,
    }
    (dest / "pack.json").write_text(json.dumps(record, indent=2), encoding="utf-8")

    mods = sum(1 for f in fetched if f.startswith("mods/") and f.endswith(".jar"))
    print(f"{slug}: {mods} mods in {dest}")
    for e in excluded:
        print(f"  left out {e['file']} ({e['why']})")
    wanted = deps.get(loader_key)
    if wanted not in (None, "*", ours) and version_key(wanted) > version_key(ours):
        print(f"  the pack is made for {loader_key} {wanted}, newer than the development run's {ours}"
              f" (-P{'neoforge_version' if args.platform == 'neoforge' else 'fabric_loader_version'}="
              f"{wanted} runs it with the pack's)")
    print(f"run it: ./gradlew :{args.platform}:runClientPack -Ppack={slug} [-Pscenario=scripts/bench/compat.json]")


if __name__ == "__main__":
    main()
