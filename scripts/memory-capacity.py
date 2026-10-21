#!/usr/bin/env python3
"""Read-only cgroup v2 admission probe. Exit 75 means insufficient capacity."""

import argparse
import json
from pathlib import Path
import re
import sys

MIB = 1024 * 1024


def number(value):
    if not re.fullmatch(r"[0-9]+", value):
        raise ValueError(f"invalid byte count: {value!r}")
    return int(value)


def unescape(value):
    return re.sub(r"\\([0-7]{3})", lambda m: chr(int(m[1], 8)), value)


def capacity(proc, required_mb, host_mb):
    meminfo = (proc / "meminfo").read_text()
    available = re.search(r"^MemAvailable:\s+([0-9]+) kB$", meminfo, re.M)
    if not available:
        raise ValueError("MemAvailable missing or malformed")
    host = int(available[1]) * 1024
    membership = (proc / "self/cgroup").read_text().splitlines()
    paths = [line[3:] for line in membership if line.startswith("0::")]
    if len(paths) != 1:
        raise ValueError("expected one unified cgroup v2 membership")
    group = Path(paths[0])
    if not group.is_absolute() or ".." in group.parts:
        raise ValueError("invalid cgroup membership path")
    mounts = []
    for line in (proc / "self/mountinfo").read_text().splitlines():
        before, separator, after = line.partition(" - ")
        if separator and after.split()[0] == "cgroup2":
            fields = before.split()
            root, mount = Path(unescape(fields[3])), Path(unescape(fields[4]))
            # A subtree mount hides ancestor limits. Never treat those as unlimited.
            if root == Path("/"):
                mounts.append(mount)
    if not mounts:
        raise ValueError("no full cgroup2 mount; ancestor limits are not visible")
    mount = mounts[0]
    directory = mount / group.relative_to("/")
    rows, blockers = [], []
    if host < host_mb * MIB:
        blockers.append(f"host MemAvailable={host} bytes < {host_mb} MiB")
    while True:
        row = {"path": str(directory)}
        # The hierarchy root has no memory controller files on Linux.
        if directory != mount or (directory / "memory.current").exists():
            current = number((directory / "memory.current").read_text().strip())
            row["memory.current"] = current
            for name in ("memory.high", "memory.max"):
                value = (directory / name).read_text().strip()
                row[name] = value
                if value != "max":
                    headroom = max(0, number(value) - current)
                    row[name + ".headroom"] = headroom
                    if headroom < required_mb * MIB:
                        blockers.append(f"{directory}/{name}: headroom={headroom} bytes < {required_mb} MiB")
            for name in ("memory.events", "memory.pressure", "memory.swap.current", "memory.swap.max"):
                try:
                    row[name] = (directory / name).read_text().strip()
                except FileNotFoundError:
                    row[name] = "unavailable"
        rows.append(row)
        if directory == mount:
            break
        directory = directory.parent
    return {"host_available_bytes": host, "host_floor_mb": host_mb,
            "required_cgroup_mb": required_mb, "ancestors": rows,
            "blockers": blockers, "admitted": not blockers}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--proc-root", type=Path, default=Path("/proc"),
                        help="proc tree, replace with fixture files only in tests")
    parser.add_argument("--required-mb", type=int, default=5000)
    parser.add_argument("--host-mb", type=int, default=5000)
    args = parser.parse_args()
    if args.required_mb < 5000 or args.host_mb < 5000:
        parser.error("host and cgroup floors must be at least 5000 MiB")
    try:
        report = capacity(args.proc_root, args.required_mb, args.host_mb)
    except (OSError, ValueError, IndexError) as error:
        print(json.dumps({"admitted": False, "error": str(error)}))
        return 2
    print(json.dumps(report))
    return 0 if report["admitted"] else 75


if __name__ == "__main__":
    sys.exit(main())
