"""Publish only a runner-owned emulator's authenticated discovery record."""

import argparse
import os
from pathlib import Path
import re
import stat


def private_directory(path, create=False):
    if create:
        path.mkdir(mode=0o700, exist_ok=True)
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise PermissionError(f"Discovery directory must be private and owned by this user: {path}")


def shared_directory(runtime_dir, create=False):
    path = Path(runtime_dir).absolute()
    private_directory(path)
    for part in ("avd", "running"):
        path /= part
        private_directory(path, create)
    return path


def publish(work_dir, runtime_dir, serial):
    work = Path(work_dir).absolute()
    private_directory(work)
    source_dir = work / "runtime/avd/running"
    sources = []
    for source in source_dir.glob("pid_*.ini"):
        match = re.fullmatch(r"pid_(\d+)(?:_info)?\.ini", source.name)
        info = source.lstat()
        if not match or not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid():
            continue
        try:
            os.kill(int(match[1]), 0)
        except ProcessLookupError:
            continue
        values = dict(line.split("=", 1) for line in source.read_text().splitlines() if "=" in line)
        if values.get("port.serial", "").strip() == serial.removeprefix("emulator-"):
            sources.append(source)
    if not sources:
        raise ValueError("No live discovery record matches this run's emulator")
    shared = shared_directory(runtime_dir, create=True)
    for source in sources:
        link = shared / source.name
        try:
            link.symlink_to(source)
        except FileExistsError:
            if not link.is_symlink() or link.readlink() != source:
                raise


def remove(work_dir, runtime_dir):
    try:
        shared = shared_directory(runtime_dir)
    except FileNotFoundError:
        return
    source_dir = Path(work_dir).absolute() / "runtime/avd/running"
    for link in shared.glob("pid_*.ini"):
        if link.is_symlink() and link.readlink().parent == source_dir:
            link.unlink()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["publish", "remove"])
    parser.add_argument("work_dir", type=Path)
    parser.add_argument("runtime_dir", type=Path)
    parser.add_argument("serial", nargs="?")
    args = parser.parse_args()
    if args.action == "publish":
        if not args.serial:
            parser.error("publish requires the owned emulator serial")
        publish(args.work_dir, args.runtime_dir, args.serial)
    else:
        remove(args.work_dir, args.runtime_dir)
