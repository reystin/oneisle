#!/usr/bin/env python3
"""25x25 chunk-generation timing for OneIsle over RCON.

Runs one dedicated server per (variant, round), force-loads chunks x 28..52,
z -12..12 and measures wall-clock time until every chunk is confirmed loaded.
Packs are produced by PerfHarnessPacksTest (ONEISLE_PERF_DIR=<workdir>).

Example:
  python3 tools/perf/chunkgen_25x25.py \
      --packs-dir /tmp/oneisle-perf/packs \
      --out docs/evidence/perf/chunkgen_25x25_1.0.0.csv \
      --rounds 3
"""
import argparse
import csv
import os
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DEFAULT_JAVA_HOME = ("/Users/reystin/Library/Application Support/PrismLauncher/java/"
                     "java-runtime-epsilon/jre.bundle/Contents/Home")
RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
SERVER_PORT = 25585
RCON_PASSWORD = os.environ.get("ONEISLE_PERF_RCON_PASSWORD", "oneisleperf2")
VIEW_DISTANCE = 4
X_CHUNKS = list(range(28, 53))
Z_CHUNKS = list(range(-12, 13))
STRIPS = [(28, 32), (33, 37), (38, 42), (43, 47), (48, 52)]
PROBE_LIMIT = 1400
MAX_POLLS = 600

SERVER_PROPERTIES = """#Minecraft server properties
#OneIsle perf harness template
accepts-transfers=false
allow-flight=true
allow-nether=false
broadcast-console-to-ops=true
broadcast-rcon-to-ops=false
difficulty=easy
enable-code-of-conduct=false
enable-jmx-monitoring=false
enable-query=false
enable-rcon=true
enable-status=false
enforce-secure-profile=false
enforce-whitelist=false
entity-broadcast-range-percentage=100
force-gamemode=false
function-permission-level=2
gamemode=survival
generate-structures=true
hardcore=false
hide-online-players=false
initial-disabled-packs=
initial-enabled-packs=vanilla,fabric-convention-tags-v2,fabric-gametest-api-v1,oneisle
level-name={level}
level-seed=424242
level-type=oneisle\\:oneisle
max-chained-neighbor-updates=1000000
max-players=1
max-tick-time=-1
motd=OneIsle perf harness
network-compression-threshold=256
online-mode=false
op-permission-level=4
pause-when-empty-seconds=0
player-idle-timeout=0
prevent-proxy-connections=false
query.port=25565
rate-limit=0
rcon.password={password}
rcon.port={rcon_port}
region-file-compression=deflate
require-resource-pack=false
server-ip=
server-port={server_port}
simulation-distance={view_distance}
spawn-protection=0
sync-chunk-writes=false
use-native-transport=true
view-distance={view_distance}
white-list=false
"""


class RconError(RuntimeError):
    pass


class Rcon:
    def __init__(self, host, port, password, timeout=10.0):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.next_id = 1
        self._send(3, password)
        request_id, _, _ = self._recv_packet()
        if request_id == -1:
            raise RconError("RCON authentication failed")

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass

    def command(self, command):
        self._send(2, command)
        chunks = []
        self.sock.settimeout(180.0)
        request_id, packet_type, body = self._recv_packet()
        chunks.append(body)
        self.sock.settimeout(2.0)
        while len(chunks[-1]) >= 4000:
            try:
                request_id, packet_type, body = self._recv_packet()
            except socket.timeout:
                break
            chunks.append(body)
        self.sock.settimeout(180.0)
        return "".join(chunks)

    def _send(self, packet_type, body):
        request_id = self.next_id
        self.next_id += 1
        payload = struct.pack("<ii", request_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv_packet(self):
        length = struct.unpack("<i", self._read_exact(4))[0]
        if length < 10 or length > 2_000_000:
            raise RconError("invalid RCON packet length %d" % length)
        data = self._read_exact(length)
        request_id, packet_type = struct.unpack("<ii", data[:8])
        return request_id, packet_type, data[8:-2].decode("utf-8", "replace")

    def _read_exact(self, size):
        data = b""
        while len(data) < size:
            part = self.sock.recv(size - len(data))
            if not part:
                raise RconError("RCON connection closed")
            data += part
        return data


def connect_rcon(process, timeout):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RconError("server exited during startup (code %s)" % process.returncode)
        try:
            return Rcon(RCON_HOST, RCON_PORT, RCON_PASSWORD)
        except (OSError, RconError) as error:
            last_error = error
            time.sleep(0.25)
    raise RconError("RCON did not become ready in %ss: %s" % (timeout, last_error))


def port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.settimeout(0.2)
        return probe.connect_ex(("127.0.0.1", port)) == 0


def write_server_properties(run_dir, level_name):
    path = run_dir / "server.properties"
    if path.exists() and not (run_dir / "server.properties.pre-perf-harness").exists():
        shutil.copy2(path, run_dir / "server.properties.pre-perf-harness")
    path.write_text(SERVER_PROPERTIES.format(
        level=level_name,
        password=RCON_PASSWORD,
        rcon_port=RCON_PORT,
        server_port=SERVER_PORT,
        view_distance=VIEW_DISTANCE,
    ), encoding="utf-8")


def prepare_world(universe, level_name, pack):
    if not level_name.startswith("perf2_"):
        raise ValueError("refusing to touch level directory %r" % level_name)
    level_dir = universe / level_name
    if level_dir.exists():
        shutil.rmtree(level_dir)
    datapacks = level_dir / "datapacks"
    datapacks.mkdir(parents=True)
    shutil.copy2(pack, datapacks / "oneisle-generated.zip")
    return level_dir


def start_server(level_name, universe, log_path, java_home):
    env = os.environ.copy()
    env["JAVA_HOME"] = java_home
    args = "--nogui --universe %s" % universe
    log = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(
        [str(REPO / "gradlew"), "runServer", "--args=" + args, "--console=plain"],
        cwd=str(REPO), env=env, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
    )
    return process, log


def probe_command(row):
    z = Z_CHUNKS[row]
    conditions = " ".join("if loaded %d 64 %d" % (x * 16, z * 16) for x in X_CHUNKS)
    command = "execute %s run scoreboard players set row%d perf 1" % (conditions, row)
    if len(command) >= PROBE_LIMIT:
        raise RuntimeError("probe command is %d bytes, over the RCON limit" % len(command))
    return command


def land_probe_command(index, x, z):
    point = "%d 300 %d" % (x, z)
    return ("execute unless biome %s #minecraft:is_ocean run "
            "scoreboard players set land%d perf 1" % (point, index))


def count_value(rcon, name):
    response = rcon.command("scoreboard players get %s perf" % name)
    if " has " not in response:
        return 0
    try:
        return int(response.split(" has ", 1)[1].split()[0])
    except (IndexError, ValueError):
        return 0


def time_run(rcon):
    rcon.command("scoreboard objectives remove perf")
    rcon.command("scoreboard objectives add perf dummy")
    probes = [probe_command(row) for row in range(len(Z_CHUNKS))]
    start = time.perf_counter()
    for x1, x2 in STRIPS:
        rcon.command("forceload add %d %d %d %d" % (x1 * 16, -12 * 16, x2 * 16 + 15, 12 * 16 + 15))
    polls = 0
    while True:
        polls += 1
        if polls > MAX_POLLS:
            raise RconError("chunks still not loaded after %d probe rounds" % polls)
        complete = True
        for row, probe in enumerate(probes):
            response = rcon.command(probe)
            if "Unknown" in response or "Incorrect" in response or "Expected" in response:
                raise RconError("probe rejected: %r" % response[:200])
            if "Test failed" in response or count_value(rcon, "row%d" % row) != 1:
                complete = False
        if complete:
            break
    elapsed = time.perf_counter() - start
    forceload = rcon.command("forceload query")
    match = re.match(r"(\d+) force loaded chunks? were found", forceload)
    loaded = int(match.group(1)) if match else forceload.count("is marked for force loading")
    if loaded != len(X_CHUNKS) * len(Z_CHUNKS):
        raise RconError("forceload query reports %d chunks, expected 625" % loaded)
    land = 0
    for index, (x, z) in enumerate(((x * 16 + 8, z * 16 + 8) for z in (Z_CHUNKS[0], Z_CHUNKS[6],
                                                                         Z_CHUNKS[12], Z_CHUNKS[18],
                                                                         Z_CHUNKS[24])
                                    for x in (X_CHUNKS[0], X_CHUNKS[6], X_CHUNKS[12],
                                              X_CHUNKS[18], X_CHUNKS[24]))):
        rcon.command(land_probe_command(index, x, z))
        land += 1 if count_value(rcon, "land%d" % index) == 1 else 0
    info = " | ".join(line.strip() for line in rcon.command("oneisle info").splitlines() if line.strip())
    return elapsed, polls, loaded, land, info


def read_manifest(packs_dir):
    manifest = packs_dir.parent / "manifest.csv"
    if not manifest.exists():
        return {}
    with manifest.open(encoding="utf-8") as handle:
        return {row["variant"]: row for row in csv.DictReader(handle)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--packs-dir", default="/tmp/oneisle-perf/packs",
                        help="directory with one subdirectory per variant")
    parser.add_argument("--variants", nargs="+", default=["v1", "v2", "v3-neutral", "v3-weighted"])
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--start-round", type=int, default=1,
                        help="first round number, for appending extra interleaved rounds")
    parser.add_argument("--out", required=True, help="CSV output path")
    parser.add_argument("--universe", default="/tmp/oneisle-perf/worlds")
    parser.add_argument("--logs", default="/tmp/oneisle-perf/logs")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME", DEFAULT_JAVA_HOME))
    parser.add_argument("--startup-timeout", type=float, default=300.0)
    parser.add_argument("--keep-worlds", action="store_true")
    args = parser.parse_args(argv)

    packs_dir = Path(args.packs_dir).resolve()
    universe = Path(args.universe).resolve()
    logs = Path(args.logs).resolve()
    out = Path(args.out).resolve()
    universe.mkdir(parents=True, exist_ok=True)
    logs.mkdir(parents=True, exist_ok=True)
    out.parent.mkdir(parents=True, exist_ok=True)
    manifest = read_manifest(packs_dir)
    for port in (SERVER_PORT, RCON_PORT):
        if port_in_use(port):
            sys.exit("port %d is already in use" % port)
    config = REPO / "run" / "config" / "oneisle.json"
    if config.exists():
        sys.exit("%s exists; remove it so the harness pack is not overridden" % config)

    fields = ["variant", "generation_version", "cherry_weight", "round", "level", "chunks",
              "seconds", "seconds_per_chunk", "polls", "forceload_after", "land_probes", "info"]
    existing = []
    if out.exists():
        with out.open(encoding="utf-8", newline="") as handle:
            existing = list(csv.DictReader(handle))
    with out.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        writer.writerows(existing)
        handle.flush()

        for round_number in range(args.start_round, args.start_round + args.rounds):
            for variant in args.variants:
                pack = packs_dir / variant / "oneisle-generated.zip"
                if not pack.exists():
                    sys.exit("missing pack %s" % pack)
                level_name = "perf2_%s_r%d" % (variant.replace("-", ""), round_number)
                print("[%s] %s round %d" % (time.strftime("%H:%M:%S"), level_name, round_number),
                      flush=True)
                prepare_world(universe, level_name, pack)
                write_server_properties(REPO / "run", level_name)
                log_path = logs / (level_name + ".server.log")
                process, log = start_server(level_name, universe, log_path, args.java_home)
                rcon = None
                try:
                    rcon = connect_rcon(process, args.startup_timeout)
                    elapsed, polls, loaded, land, info = time_run(rcon)
                    row = manifest.get(variant, {})
                    record = {
                        "variant": variant,
                        "generation_version": row.get("generation_version", ""),
                        "cherry_weight": row.get("cherry_weight", ""),
                        "round": round_number,
                        "level": level_name,
                        "chunks": loaded,
                        "seconds": "%.3f" % elapsed,
                        "seconds_per_chunk": "%.4f" % (elapsed / loaded),
                        "polls": polls,
                        "forceload_after": loaded,
                        "land_probes": "%d/25" % land,
                        "info": info,
                    }
                    writer.writerow(record)
                    handle.flush()
                    print("    %.3f s  (%d polls, land %d/25)" % (elapsed, polls, land), flush=True)
                finally:
                    if rcon is not None:
                        try:
                            rcon.command("stop")
                        except (OSError, RconError):
                            pass
                        rcon.close()
                    try:
                        process.wait(timeout=180)
                    except subprocess.TimeoutExpired:
                        process.terminate()
                        try:
                            process.wait(timeout=30)
                        except subprocess.TimeoutExpired:
                            process.kill()
                            process.wait(timeout=30)
                    log.close()
                    if process.returncode not in (0, None):
                        sys.exit("server for %s exited with %s" % (level_name, process.returncode))
                    if not args.keep_worlds:
                        shutil.rmtree(universe / level_name, ignore_errors=True)
    print("Wrote %s" % out, flush=True)


if __name__ == "__main__":
    main()
