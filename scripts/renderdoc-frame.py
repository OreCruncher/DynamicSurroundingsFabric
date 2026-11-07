#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

"""
Captures a frame of a bench scene with RenderDoc and reads it from a script: what each draw did, any target at any
point in the frame, and a pass redrawn with an edited shader. For what a frame drew, not what it cost: replay times
aren't the game's, so compare costs with the bench (scripts/bench/README.md, "RenderDoc", has the how and the limits).

    python scripts/renderdoc-frame.py capture <scenario> [--platform fabric] [--backend vulkan] [--run LABEL] [--wait 10]
    python scripts/renderdoc-frame.py list <capture> [--shader TEXT]
    python scripts/renderdoc-frame.py save <capture> (--event N | --shader TEXT) [--next] [--name march]
    python scripts/renderdoc-frame.py swap <capture> (--event N | --shader TEXT) --edits edits.json

capture starts the scenario's game under RenderDoc and takes a frame once the run is recording, closing the game when
it has it; captures go in <platform>/run-bench/renderdoc. list prints each draw (event, ms in replay, vertices, its
shader's number, its target) and writes the fragment shaders beside the capture, <capture>-shaders/psN.glsl, as the
capture has them: generated source, which is what edits must match. --shader finds a draw by text in that source
(a function's name, say ordered_dither). save writes the draw's target as it stands after it; --next also the next
draw's, as a pass and what lays it over the screen. swap (OpenGL captures) redraws with edits, each variant's images
named after it:

    {"no-dither": [["float start = ordered_dither(param);", "float start = 0.5;"]]}

A variant "base" (no edits) comes first: it should match the frame, which shows a difference is the edit's.
This file also runs inside RenderDoc's own Python (qrenderdoc --python), for the steps that read a capture.
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import traceback
from pathlib import Path

try:
    import renderdoc as rd
except ImportError:
    rd = None

# qrenderdoc has no __file__ for what it runs
SCRIPT = Path(__file__).resolve() if rd is None else None
ROOT = SCRIPT.parent.parent if rd is None else None
RENDERDOC = Path(os.environ.get("RENDERDOC_HOME", r"C:\Program Files\RenderDoc"))
CLIENT = "dsurround.devRun=clientBench"
SETTLE_SECONDS = 240
LOAD_SECONDS = 300
REPLAY_SECONDS = 170


# The steps run inside qrenderdoc. They take a job from a file and write their output to another, as it has no console.

def replay_job(job, out):
    cap = rd.OpenCaptureFile()
    if cap.OpenFile(job["capture"], "", None) != rd.ResultCode.Succeeded:
        raise RuntimeError("Can't open " + job["capture"])
    result, controller = cap.OpenCapture(rd.ReplayOptions(), None)
    if result != rd.ResultCode.Succeeded:
        raise RuntimeError("Can't replay it: " + str(result))
    try:
        Reader(controller, job, out).run()
    finally:
        controller.Shutdown()
        cap.Shutdown()


class Reader:
    def __init__(self, controller, job, out):
        self.c = controller
        self.job = job
        self.out = out
        self.folder = Path(job["folder"])
        self.textures = {int(t.resourceId): t for t in controller.GetTextures()}
        self.draws = []
        self.shaders = {}
        self.walk(controller.GetRootActions())

    def walk(self, actions):
        for action in actions:
            if action.flags & rd.ActionFlags.Drawcall:
                self.draws.append(action)
            self.walk(action.children)

    def run(self):
        getattr(self, "do_" + self.job["command"])()

    def source(self, action):
        self.c.SetFrameEvent(action.eventId, False)
        reflection = self.c.GetPipelineState().GetShaderReflection(rd.ShaderStage.Pixel)
        if reflection is None or not len(reflection.debugInfo.files):
            return ""
        return reflection.debugInfo.files[0].contents

    def find(self):
        if self.job.get("event") is not None:
            for action in self.draws:
                if action.eventId == self.job["event"]:
                    return action
            raise RuntimeError("No draw at event %d" % self.job["event"])
        for action in self.draws:
            if self.job["shader"] in self.source(action):
                return action
        raise RuntimeError("No draw's fragment shader has " + self.job["shader"])

    def following(self, action):
        later = [a for a in self.draws if a.eventId > action.eventId]
        return later[0] if later else None

    def target(self, action):
        return next(o for o in action.outputs if int(o))

    def describe(self, resource):
        texture = self.textures.get(int(resource))
        if texture is None:
            return "%d -" % int(resource)
        return "%d %dx%d %s" % (int(resource), texture.width, texture.height, texture.format.Name())

    def durations(self):
        return {x.eventId: x.value.d * 1000 for x in self.c.FetchCounters([rd.GPUCounter.EventGPUDuration])}

    def do_list(self):
        times = self.durations()
        folder = self.folder / (Path(self.job["capture"]).stem + "-shaders")
        folder.mkdir(exist_ok=True)
        print("api", self.c.GetAPIProperties().pipelineType, file=self.out)
        print("event  replay ms  vertices  shader  target", file=self.out)
        for action in self.draws:
            text = self.source(action)
            number = self.shaders.setdefault(text, len(self.shaders))
            (folder / ("ps%d.glsl" % number)).write_text(text)
            if self.job.get("shader") and self.job["shader"] not in text:
                continue
            print("%5d  %9.4f  %8d  ps%-4d  %s" % (action.eventId, times.get(action.eventId, 0), action.numIndices,
                                                   number, self.describe(self.target(action))), file=self.out)
        print("shaders in", folder, file=self.out)

    def save(self, action, name):
        self.c.SetFrameEvent(action.eventId, True)
        save = rd.TextureSave()
        save.resourceId = self.target(action)
        save.destType = rd.FileType.PNG
        save.alpha = rd.AlphaMapping.Discard
        path = self.folder / (name + ".png")
        print(path, self.c.SaveTexture(save, str(path)), file=self.out)

    def save_pair(self, action, name):
        self.save(action, name)
        after = self.following(action)
        if self.job.get("next") and after is not None:
            self.save(after, name + "-next")

    def do_save(self):
        self.save_pair(self.find(), self.job.get("name") or "event")

    def do_swap(self):
        action = self.find()
        print("event", action.eventId, self.describe(self.target(action)), file=self.out)
        text = self.source(action)
        self.c.SetFrameEvent(action.eventId, True)
        original = self.c.GetPipelineState().GetShader(rd.ShaderStage.Pixel)
        if rd.ShaderEncoding.GLSL not in self.c.GetTargetShaderEncodings():
            raise RuntimeError("Only an OpenGL capture can take edited shaders here")
        for name, edits in [("base", [])] + list(self.job["edits"].items()):
            edited = text
            for old, new in edits:
                if edited.count(old) != 1:
                    raise RuntimeError("%s: not exactly once in the shader: %s" % (name, old))
                edited = edited.replace(old, new)
            shader, errors = self.c.BuildTargetShader("main", rd.ShaderEncoding.GLSL, edited.encode(),
                                                      rd.ShaderCompileFlags(), rd.ShaderStage.Pixel)
            if int(shader) == 0:
                print(name, "didn't build:", errors, file=self.out)
                continue
            self.c.ReplaceResource(original, shader)
            self.job["next"] = True
            self.save_pair(action, name)
            self.c.RemoveReplacement(original)
            self.c.FreeTargetResource(shader)


def inside_renderdoc():
    with open(os.environ["DSURROUND_RENDERDOC_OUT"], "w", encoding="utf-8") as out:
        try:
            with open(os.environ["DSURROUND_RENDERDOC_JOB"], encoding="utf-8") as file:
                job = json.load(file)
            if job["command"] == "trigger":
                trigger(job, out)
            else:
                replay_job(job, out)
        except Exception:
            print(traceback.format_exc(), file=out)
    os._exit(0)


def trigger(job, out):
    ident, target = 0, None
    for _ in range(60):
        ident = rd.EnumerateRemoteTargets("", ident)
        target = rd.CreateTargetControl("", ident, "dsurround", True) if ident else None
        if target is not None:
            break
        time.sleep(1)
    if target is None:
        raise RuntimeError("No game to capture from")
    time.sleep(2)
    target.TriggerCapture(1)
    end = time.time() + 120
    while time.time() < end:
        message = target.ReceiveMessage(None)
        if message.type == rd.TargetControlMessageType.NewCapture:
            print(message.newCapture.path, file=out)
            break
        if message.type == rd.TargetControlMessageType.Disconnected:
            raise RuntimeError("The game went away")
    target.Shutdown()


# The rest runs in the system's Python

def in_renderdoc(job, seconds=REPLAY_SECONDS):
    """Runs this file inside qrenderdoc, which waits for its first-run screen to be answered, and returns its output."""
    with tempfile.TemporaryDirectory() as folder:
        job_file, out_file = Path(folder, "job.json"), Path(folder, "out.txt")
        job_file.write_text(json.dumps(job), encoding="utf-8")
        env = dict(os.environ, DSURROUND_RENDERDOC_JOB=str(job_file), DSURROUND_RENDERDOC_OUT=str(out_file))
        process = subprocess.Popen([str(RENDERDOC / "qrenderdoc.exe"), "--python", str(SCRIPT)], env=env)
        try:
            process.wait(seconds)
        except subprocess.TimeoutExpired:
            process.kill()
            sys.exit("RenderDoc took too long. Is its first-run screen waiting for an answer?")
        return out_file.read_text(encoding="utf-8") if out_file.exists() else ""


def powershell(command):
    return subprocess.run(["powershell", "-NoProfile", "-Command", command], capture_output=True, text=True).stdout


def client_command():
    return powershell("(Get-CimInstance Win32_Process -Filter \"Name='java.exe' or Name='javaw.exe'\" | Where-Object "
                      "{ $_.CommandLine -match '%s' } | Select-Object -First 1).CommandLine" % CLIENT).strip()


def stop_client():
    powershell("Get-CimInstance Win32_Process -Filter \"Name='java.exe' or Name='javaw.exe'\" | Where-Object "
               "{ $_.CommandLine -match '%s' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" % CLIENT)


def display_timeout():
    text = subprocess.run(["powercfg", "/query", "SCHEME_CURRENT", "SUB_VIDEO", "VIDEOIDLE"],
                          capture_output=True, text=True).stdout
    match = re.search(r"Current AC Power Setting Index: (0x[0-9a-f]+)", text)
    return int(match[1], 16) // 60 if match else None


def set_display_timeout(minutes):
    subprocess.run(["powercfg", "/change", "monitor-timeout-ac", str(minutes)])


def game_command(platform, scenario, backend):
    """Gradle builds the game and starts it; what it started is the command line to start under RenderDoc"""
    args = [str(ROOT / "gradlew.bat"), ":%s:runClientBench" % platform, "-Pscenario=%s" % scenario]
    if backend:
        args.append("-PgraphicsBackend=" + backend)
    gradle = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    end = time.time() + SETTLE_SECONDS
    command = ""
    while not command and time.time() < end and gradle.poll() is None:
        time.sleep(1)
        command = client_command()
    stop_client()
    try:
        gradle.wait(60)
    except subprocess.TimeoutExpired:
        gradle.kill()
    if not command:
        sys.exit("The game didn't start: see what ./gradlew :%s:runClientBench says" % platform)
    return command


def wait_for_recording(log, process, run, since):
    pattern = re.compile(r"Bench: %s: record" % (r"\d+-" + re.escape(run) if run else r"\S+"))
    end = time.time() + LOAD_SECONDS
    while time.time() < end:
        if process.poll() is not None:
            sys.exit("The game closed before recording")
        if log.exists() and log.stat().st_mtime > since:
            if pattern.search(log.read_text(encoding="utf-8", errors="replace")):
                return
        time.sleep(1)
    sys.exit("It never got to recording" + (" " + run if run else ""))


def capture(args):
    platform = args.platform
    run_folder = ROOT / platform / "run-bench"
    if client_command():
        sys.exit("A bench client is already running")
    folder = run_folder / "renderdoc"
    folder.mkdir(exist_ok=True)
    timeout = display_timeout()
    set_display_timeout(0)
    try:
        command = game_command(platform, Path(args.scenario).resolve(), args.backend)
        since = time.time()
        template = folder / (Path(args.scenario).stem + "-" + (args.backend or "opengl"))
        # As it stands: quoting it again for renderdoccmd's own parsing loses the path to java
        line = '"%s" capture -w -d "%s" -c "%s" %s' % (RENDERDOC / "renderdoccmd.exe", run_folder, template, command)
        process = subprocess.Popen(line, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        wait_for_recording(run_folder / "logs" / "latest.log", process, args.run, since)
        time.sleep(args.wait)
        print(in_renderdoc({"command": "trigger"}).strip())
    finally:
        stop_client()
        if timeout is not None:
            set_display_timeout(timeout)


def read(args, **job):
    path = Path(args.capture).resolve()
    job.update(capture=str(path), folder=str(path.parent), event=getattr(args, "event", None),
               shader=getattr(args, "shader", None))
    print(in_renderdoc(job).rstrip())


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    take = commands.add_parser("capture")
    take.add_argument("scenario")
    take.add_argument("--platform", choices=["fabric", "neoforge"], default="fabric")
    take.add_argument("--backend", choices=["opengl", "vulkan"])
    take.add_argument("--run", help="the run's label: the first to record if not given")
    take.add_argument("--wait", type=int, default=10, help="seconds into its recording")

    draws = commands.add_parser("list")
    draws.add_argument("capture")
    draws.add_argument("--shader")

    for name in ["save", "swap"]:
        command = commands.add_parser(name)
        command.add_argument("capture")
        which = command.add_mutually_exclusive_group(required=True)
        which.add_argument("--event", type=int)
        which.add_argument("--shader")
        if name == "save":
            command.add_argument("--next", action="store_true")
            command.add_argument("--name")
        else:
            command.add_argument("--edits", required=True)

    args = parser.parse_args()
    if args.command == "capture":
        capture(args)
    elif args.command == "list":
        read(args, command="list")
    elif args.command == "save":
        read(args, command="save", next=args.next, name=args.name)
    else:
        read(args, command="swap", edits=json.loads(Path(args.edits).read_text(encoding="utf-8")))


if rd is not None:
    inside_renderdoc()
elif __name__ == "__main__":
    main()
