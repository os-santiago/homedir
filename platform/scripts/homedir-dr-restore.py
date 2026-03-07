#!/usr/bin/env python3
"""Safe archive restore utility for HomeDir disaster recovery.

Supported formats:
- .zip (AdminBackupResource downloads)
- .tar, .tar.gz, .tgz

Security guardrails:
- Reject absolute paths and path traversal entries.
- Reject symlinks/hardlinks in tar archives.
"""

from __future__ import annotations

import argparse
import os
import shutil
import tarfile
import zipfile
from pathlib import Path


def safe_target(root: Path, entry_name: str) -> Path | None:
    normalized = entry_name.replace("\\", "/").strip()
    while normalized.startswith("/"):
        normalized = normalized[1:]

    if not normalized or normalized in {".", "./"}:
        return None
    if normalized.startswith("..") or "/../" in normalized:
        raise ValueError(f"path traversal detected: {entry_name}")
    if len(normalized) >= 2 and normalized[1] == ":" and normalized[0].isalpha():
        raise ValueError(f"absolute windows path detected: {entry_name}")

    target = (root / normalized).resolve()
    root_resolved = root.resolve()
    if target != root_resolved and root_resolved not in target.parents:
        raise ValueError(f"path outside restore root: {entry_name}")
    return target


def extract_zip(archive: Path, output_dir: Path) -> int:
    restored = 0
    with zipfile.ZipFile(archive, "r") as zf:
        for info in zf.infolist():
            target = safe_target(output_dir, info.filename)
            if target is None:
                continue
            if info.is_dir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with zf.open(info, "r") as src, open(target, "wb") as dst:
                shutil.copyfileobj(src, dst)
            restored += 1
    return restored


def extract_tar(archive: Path, output_dir: Path) -> int:
    restored = 0
    with tarfile.open(archive, "r:*") as tf:
        for member in tf.getmembers():
            target = safe_target(output_dir, member.name)
            if target is None:
                continue
            if member.issym() or member.islnk():
                raise ValueError(f"symlink/hardlink entries are not allowed: {member.name}")
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            if not member.isfile():
                # Skip device files and other special entries.
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            src = tf.extractfile(member)
            if src is None:
                continue
            with src, open(target, "wb") as dst:
                shutil.copyfileobj(src, dst)
            restored += 1
    return restored


def detect_format(archive: Path) -> str:
    name = archive.name.lower()
    if name.endswith(".zip"):
        return "zip"
    if name.endswith(".tar") or name.endswith(".tar.gz") or name.endswith(".tgz"):
        return "tar"
    raise ValueError(f"unsupported archive format: {archive}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Restore HomeDir backup archive safely.")
    parser.add_argument("--archive", required=True, help="Path to archive (.zip/.tar/.tar.gz/.tgz)")
    parser.add_argument("--output-dir", required=True, help="Directory where files will be extracted")
    args = parser.parse_args()

    archive = Path(args.archive).resolve()
    output_dir = Path(args.output_dir).resolve()

    if not archive.exists():
        raise FileNotFoundError(f"archive not found: {archive}")
    output_dir.mkdir(parents=True, exist_ok=True)

    fmt = detect_format(archive)
    restored = extract_zip(archive, output_dir) if fmt == "zip" else extract_tar(archive, output_dir)
    print(f"restored_files={restored}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

