#!/usr/bin/env python3
"""Check F-Droid description limits in Unicode characters, including newlines.

Run from any directory. An optional metadata directory supports other locales
and disposable validation fixtures. No Android tooling or dependencies required.
"""

import argparse
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "metadata_dir",
        nargs="?",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "fastlane/metadata/android/en-US",
        help="metadata directory (default: repository en-US listing)",
    )
    args = parser.parse_args()
    failed = False
    for name, limit in (("short_description.txt", 80), ("full_description.txt", 4000)):
        path = args.metadata_dir / name
        try:
            with path.open(encoding="utf-8", newline="") as source:
                count = len(source.read())
        except (OSError, UnicodeError) as error:
            print(f"ERROR {path}: {error}", file=sys.stderr)
            failed = True
            continue
        valid = count <= limit
        print(f"{'OK' if valid else 'ERROR'} {path}: {count}/{limit} characters")
        failed |= not valid
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
