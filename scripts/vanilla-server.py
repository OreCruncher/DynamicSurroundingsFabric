#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""
A client on a vanilla server, which sends no `c:` tags, to check the tag fallback (ServerResourceFinder).

  vanilla-server.py server [--accept-eula] [--port N]    sets up build/vanilla-server and starts the server there,
                                                         returning once it is ready; it logs to server.log. Mojang's
                                                         EULA is the user's to accept: pass --accept-eula only when
                                                         they have agreed, once for the folder
  vanilla-server.py client neoforge|fabric [--port N]    runs the client without the dev client's own mods, joining
                                                         the server; run it in the background
  vanilla-server.py check neoforge|fabric [--against F]  reads the /dsdump biomes and /dsdump tags made in that client:
                                                         what only a c: tag can give our tags, and what is missing;
                                                         --against compares biomes.txt with a dump (single player's)
  vanilla-server.py stop                                 stops the server this script started

--folder uses another server folder with any of them. Exits 1 if anything is missing.

The plain dev client can't join: Biomes O' Plenty and the others `-Ppack` leaves out need NeoForge on the server. The
client's run folder is <platform>/run-pack/none, so the dev settings and dumps are left alone.
"""
import argparse
import glob
import json
import os
import re
import socket
import subprocess
import sys
import time
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FOLDER = os.path.join(ROOT, "build", "vanilla-server")
LOOM = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "fabric-loom")
TAGS = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "dsurround", "dsconfigs", "tags")
START_SECONDS = 120

# Without these the server turns the dev client away (its account is offline, and 26.3 defaults the whitelist on)
PROPERTIES = {
    "server-ip": "127.0.0.1",
    "online-mode": "false",
    "white-list": "false",
    "enforce-whitelist": "false",
    "enforce-secure-profile": "false",
    "level-seed": "20261030",
    "gamemode": "creative",
    "spawn-protection": "0",
    "view-distance": "6",
    "simulation-distance": "6",
}

# Only clientPack: it inherits client's arguments, and on both the option arrives twice, which crashes the launch
INIT_SCRIPT = """allprojects {
    afterEvaluate { p ->
        if (p.name == 'neoforge' || p.name == 'fabric')
            p.loom.runs.matching { it.name == 'clientPack' }.configureEach {
                programArgs '--quickPlayMultiplayer', '127.0.0.1:%d'
            }
    }
}
"""

TAG = re.compile(r"Tag: TagKey\[(\S+) / (\S+)\]")
MEMBER = re.compile(r"^  (\S+)$", re.M)
BIOME = re.compile(r"^Biome \[.*?/(\S+?)\]\nTags: (.*)\nTraits: (.*)$", re.M)
REGISTRIES = {
    "minecraft:block": "block",
    "minecraft:item": "item",
    "minecraft:fluid": "fluid",
    "minecraft:entity_type": "entity_type",
    "minecraft:worldgen/biome": "worldgen/biome",
}


def gradle_property(name):
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            key, _, value = line.partition("=")
            if key.strip() == name:
                return value.strip()
    sys.exit(f"{name} isn't in gradle.properties")


def game_file(name):
    path = os.path.join(LOOM, gradle_property("minecraft_version"), name)
    if not os.path.isfile(path):
        sys.exit(f"{path} isn't there: run a Gradle build first")
    return path


def listening(port):
    with socket.socket() as s:
        s.settimeout(1)
        return s.connect_ex(("127.0.0.1", port)) == 0


def write_properties(path, port):
    wanted = dict(PROPERTIES, **{"server-port": str(port)})
    lines = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            lines = f.read().splitlines()
    for i, line in enumerate(lines):
        key = line.partition("=")[0]
        if key in wanted:
            lines[i] = f"{key}={wanted.pop(key)}"
    lines += [f"{key}={value}" for key, value in wanted.items()]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def eula_accepted(path):
    if not os.path.exists(path):
        return False
    with open(path, encoding="utf-8") as f:
        return any(line.strip() == "eula=true" for line in f)


def tail(path, lines=8):
    with open(path, encoding="utf-8", errors="replace") as f:
        return "\n".join(f.read().splitlines()[-lines:])


def start_server(args):
    if listening(args.port):
        sys.exit(f"Something is already listening on port {args.port}")
    os.makedirs(args.folder, exist_ok=True)
    eula = os.path.join(args.folder, "eula.txt")
    if args.accept_eula:
        with open(eula, "w", encoding="ascii") as f:
            f.write("eula=true\n")
    elif not eula_accepted(eula):
        sys.exit("The server won't start without Mojang's EULA (https://aka.ms/MinecraftEULA). Ask the user, and if "
                 "they agree run this again with --accept-eula.")
    write_properties(os.path.join(args.folder, "server.properties"), args.port)

    log_path = os.path.join(args.folder, "server.log")
    flags = subprocess.DETACHED_PROCESS if os.name == "nt" else 0
    with open(log_path, "w", encoding="utf-8") as log:
        server = subprocess.Popen(["java", "-Xmx2G", "-jar", game_file("minecraft-server.jar"), "--nogui"],
                                  cwd=args.folder, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                  creationflags=flags)
    with open(os.path.join(args.folder, "server.pid"), "w", encoding="ascii") as f:
        f.write(str(server.pid))

    deadline = time.time() + START_SECONDS
    while time.time() < deadline:
        with open(log_path, encoding="utf-8", errors="replace") as f:
            if "Done (" in f.read():
                print(f"Server ready on 127.0.0.1:{args.port}, logging to {log_path}")
                return 0
        if server.poll() is not None:
            break
        time.sleep(0.5)
    print(f"The server didn't start:\n{tail(log_path)}", file=sys.stderr)
    return 1


def stop_server(args):
    pid_path = os.path.join(args.folder, "server.pid")
    if not os.path.exists(pid_path):
        print("No server was started from this folder")
        return 0
    with open(pid_path, encoding="ascii") as f:
        pid = int(f.read())
    os.remove(pid_path)
    # A pid left from an earlier session may be something else's by now
    if not listening(args.port):
        print("The server isn't running")
        return 0
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(pid), "/F"], check=True, stdout=subprocess.DEVNULL)
    else:
        os.kill(pid, 15)
    print(f"Stopped the server ({pid})")
    return 0


def run_client(args):
    if not listening(args.port):
        sys.exit(f"No server on port {args.port}: start it with `vanilla-server.py server`")
    os.makedirs(args.folder, exist_ok=True)
    init_script = os.path.join(args.folder, "join.gradle")
    with open(init_script, "w", encoding="ascii") as f:
        f.write(INIT_SCRIPT % args.port)
    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    return subprocess.run([gradlew, "-I", init_script, f":{args.platform}:runClientPack", "-Ppack=none"],
                          cwd=ROOT).returncode


def run_folder(platform):
    return os.path.join(ROOT, platform, "run-pack", "none")


def convention_tags(platform):
    """The jar holding the loader's c: tags. Older Fabric API versions stay in Gradle's cache: the client's log names
    the one it ran."""
    if platform == "neoforge":
        return os.path.join(LOOM, gradle_property("minecraft_version"), "neoforge", gradle_property("neoforge_version"),
                            "forge-universal.jar")
    name = "fabric-convention-tags-v2"
    cache = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1",
                         "net.fabricmc.fabric-api", name)
    log = os.path.join(run_folder(platform), "logs", "latest.log")
    version = "*"
    if os.path.exists(log):
        with open(log, encoding="utf-8", errors="replace") as f:
            found = re.search(re.escape(name) + r" (\S+)", f.read())
        if found:
            version = glob.escape(found[1])
    jars = glob.glob(os.path.join(cache, version, "*", f"{name}-{version}.jar"))
    jars = [jar for jar in jars if not jar.endswith("-sources.jar")]
    if not jars:
        sys.exit(f"No {name} jar in {cache}")
    return max(jars, key=os.path.getmtime)


class Resolver:
    """A tag's members from the files, with or without the c: tags"""

    def __init__(self, platform):
        self.game = zipfile.ZipFile(game_file("minecraft-extracted_server.jar"))
        self.convention = zipfile.ZipFile(convention_tags(platform))

    def values(self, folder, namespace, path):
        try:
            if namespace == "minecraft":
                text = self.game.read(f"data/minecraft/tags/{folder}/{path}.json")
            elif namespace == "c":
                text = self.convention.read(f"data/c/tags/{folder}/{path}.json")
            elif namespace == "dsurround":
                with open(os.path.join(TAGS, *folder.split("/"), *path.split("/")) + ".json", "rb") as f:
                    text = f.read()
            else:
                return []
        except (KeyError, OSError):
            return []
        return [v if isinstance(v, str) else v["id"] for v in json.loads(text).get("values", [])]

    def members(self, folder, tag, with_c, seen=None):
        seen = set() if seen is None else seen
        namespace, _, path = tag.partition(":")
        if tag in seen or (namespace == "c" and not with_c):
            return set()
        seen.add(tag)
        result = set()
        for value in self.values(folder, namespace, path):
            result |= self.members(folder, value[1:], with_c, seen) if value.startswith("#") else {value}
        return result


def read_biomes(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        text = f.read()
    return {m[1]: ({t.strip() for t in m[2].split(",") if t.strip().startswith("dsurround:")}, m[3])
            for m in BIOME.finditer(text)}


def read_tags(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        text = f.read()
    tags = {}
    for block in re.split(r"\n(?=Tag: )", text):
        match = TAG.match(block)
        if match:
            tags[(match[1], match[2])] = set(MEMBER.findall(block))
    return tags


def report(folder, tags, resolver):
    """Prints a registry's members only c: tags can give, and returns how many are missing"""
    expected = found = 0
    missing = []
    for tag, members in tags.items():
        only_c = resolver.members(folder, tag, True) - resolver.members(folder, tag, False)
        only_c = {member for member in only_c if member.startswith("minecraft:")}
        expected += len(only_c)
        found += len(only_c & members)
        missing += [f"{tag}: {member}" for member in sorted(only_c - members)]
    print(f"{folder}: {found} of {expected} members only c: tags give")
    for line in missing:
        print(f"  MISSING {line}")
    return len(missing)


def check(args):
    dumps = os.path.join(run_folder(args.platform), "config", "dsurround", "dumps")
    biomes_path, tags_path = os.path.join(dumps, "biomes.txt"), os.path.join(dumps, "tags.txt")
    resolver = Resolver(args.platform)
    missing = 0

    if os.path.exists(tags_path):
        tags = read_tags(tags_path)
        print(f"{len(tags)} tags, {sum(not members for members in tags.values())} empty")
        for registry, folder in REGISTRIES.items():
            members = {tag: m for (r, tag), m in tags.items() if r == registry and tag.startswith("dsurround:")}
            missing += report(folder, members, resolver)
    else:
        print("No tags.txt: run /dsdump tags. Blocks and items aren't checked.")

    if not os.path.exists(biomes_path):
        print("No biomes.txt: run /dsdump biomes")
        return 1
    biomes = read_biomes(biomes_path)
    print(f"{len(biomes)} biomes in biomes.txt")
    if not os.path.exists(tags_path):
        members = {}
        for biome, (tags, _) in biomes.items():
            for tag in tags:
                members.setdefault(tag, set()).add(biome)
        missing += report("worldgen/biome", members, resolver)

    if args.against:
        other = read_biomes(args.against)
        differ = [biome for biome, value in biomes.items() if biome in other and other[biome] != value]
        print(f"Against {args.against}: {len(biomes) - len(differ)} of {len(biomes)} the same")
        for biome in differ:
            print(f"  DIFFERS {biome}")
        missing += len(differ)
    return 1 if missing else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--folder", default=FOLDER, help="the server's folder")
    parser.add_argument("--port", type=int, default=25565)
    commands = parser.add_subparsers(dest="command", required=True)

    server = commands.add_parser("server")
    server.add_argument("--accept-eula", action="store_true")
    server.set_defaults(run=start_server)

    client = commands.add_parser("client")
    client.add_argument("platform", choices=["neoforge", "fabric"])
    client.set_defaults(run=run_client)

    dumps = commands.add_parser("check")
    dumps.add_argument("platform", choices=["neoforge", "fabric"])
    dumps.add_argument("--against", help="a biomes.txt to compare with")
    dumps.set_defaults(run=check)

    commands.add_parser("stop").set_defaults(run=stop_server)

    args = parser.parse_args()
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
