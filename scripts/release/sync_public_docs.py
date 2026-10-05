#!/usr/bin/env python3
"""Sync README / changelog documentation into the public distribution repository."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.parse import quote


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


DEFAULT_SYNC_FILES = ("README.md", "update.md", "USER_AGREEMENT.md")
GIT_PUSH_ATTEMPTS = 3


def run_command(command: list[str], cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=cwd, check=True, text=True)


def read_source_sha(source_dir: Path) -> str:
    result = subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd=source_dir,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync documentation files into the public repository")
    parser.add_argument("--target-repo", required=True, help="Public repository, e.g. laststudio/eklose")
    parser.add_argument("--target-branch", required=True, help="Branch to push in the public repository")
    parser.add_argument("--files", nargs="+", default=list(DEFAULT_SYNC_FILES), help="Files to sync")
    parser.add_argument("--commit-prefix", default="docs: sync from private repo", help="Commit message prefix")
    args = parser.parse_args()

    token = os.getenv("PUBLIC_RELEASE_TOKEN", "").strip()
    if not token:
        print("PUBLIC_RELEASE_TOKEN is not configured.", file=sys.stderr)
        return 1

    source_dir = Path.cwd()
    missing = [name for name in args.files if not (source_dir / name).is_file()]
    if missing:
        print(f"Source files not found: {', '.join(missing)}", file=sys.stderr)
        return 1

    source_sha = read_source_sha(source_dir)
    remote_url = f"https://x-access-token:{quote(token, safe='')}@github.com/{args.target_repo}.git"

    for attempt in range(1, GIT_PUSH_ATTEMPTS + 1):
        with tempfile.TemporaryDirectory(prefix="public-docs-sync-") as temp_dir:
            repo_dir = Path(temp_dir) / "public-repo"
            run_command([
                "git",
                "clone",
                "--branch",
                args.target_branch,
                "--depth",
                "1",
                remote_url,
                str(repo_dir),
            ])

            changed_files: list[str] = []
            for name in args.files:
                source = source_dir / name
                target = repo_dir / name
                if target.is_file() and target.read_bytes() == source.read_bytes():
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
                changed_files.append(name)

            if not changed_files:
                print(f"{args.target_repo} documentation is already up to date.")
                return 0

            run_command(["git", "config", "user.name", "github-actions[bot]"], cwd=repo_dir)
            run_command(["git", "config", "user.email", "github-actions[bot]@users.noreply.github.com"], cwd=repo_dir)
            run_command(["git", "add", *changed_files], cwd=repo_dir)
            run_command(["git", "commit", "-m", f"{args.commit_prefix} ({source_sha})"], cwd=repo_dir)
            try:
                run_command(["git", "push", "origin", f"HEAD:{args.target_branch}"], cwd=repo_dir)
            except subprocess.CalledProcessError:
                if attempt == GIT_PUSH_ATTEMPTS:
                    raise
                print(
                    f"Push to {args.target_repo} failed (attempt {attempt}/{GIT_PUSH_ATTEMPTS}); retrying.",
                    file=sys.stderr,
                )
                time.sleep(attempt)
                continue

            print(f"{args.target_repo} documentation updated: {', '.join(changed_files)}")
            return 0

    raise AssertionError("Git push retry loop ended unexpectedly.")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"Documentation sync failed: command exited with {exc.returncode}", file=sys.stderr)
        raise SystemExit(exc.returncode or 1)
