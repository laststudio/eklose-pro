#!/usr/bin/env python3
"""Publish Eklose release config through the LastStudio API and legacy mirrors."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


DEFAULT_LOCAL_CONFIG_FILE = "config/config.json"
DEFAULT_CONFIG_FILE = "eklose_config.json"
DEFAULT_CHANGELOG_FILE = "eklose_update.md"
DEFAULT_DOWNLOAD_URL = "https://oplist.lastudio.cc/Eklose_release"
DEFAULT_API_URL = "https://api.lastudio.cc"
DEFAULT_GITHUB_REPO = "laststudio/Fe_config"
DEFAULT_GITHUB_BRANCH = "main"
DEFAULT_GITEE_REPO = "qiuqiqiuqid/fe_config"
DEFAULT_GITEE_BRANCH = "master"
PUBLISHER_CONFIG_PATH = "/publisher/software/eklose/config"
READ_ONLY_FIELDS = frozenset({"key", "releaseId", "changelogUrl"})
MIRROR_CONFIG_EXCLUDED_FIELDS = frozenset({"key", "releaseId", "version", "configRevision", "changelogContent"})
TRANSIENT_HTTP_STATUSES = frozenset({408, 429, 500, 502, 503, 504})
API_ATTEMPTS = 3
GIT_PUSH_ATTEMPTS = 3
_last_request_timestamp = 0


class PublisherApiError(RuntimeError):
    def __init__(self, message: str, transient: bool = False) -> None:
        super().__init__(message)
        self.transient = transient


def fresh_request_timestamp() -> str:
    global _last_request_timestamp
    _last_request_timestamp = max(int(time.time()), _last_request_timestamp + 1)
    return str(_last_request_timestamp)


def run_command(command: list[str], cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=cwd, check=True, text=True)


def clone_url(provider: str, repo: str, username: str | None, token: str) -> str:
    if provider == "github":
        return f"https://x-access-token:{quote(token, safe='')}@github.com/{repo}.git"
    if provider == "gitee":
        if not username:
            raise ValueError("Gitee sync requires a username.")
        return f"https://{quote(username, safe='')}:{quote(token, safe='')}@gitee.com/{repo}.git"
    raise ValueError(f"Unknown provider: {provider}")


def clone_repo(provider: str, repo: str, branch: str, username: str | None, token: str, target_dir: Path) -> Path:
    repo_dir = target_dir / f"{provider}-config"
    run_command([
        "git",
        "clone",
        "--branch",
        branch,
        "--depth",
        "1",
        clone_url(provider, repo, username, token),
        str(repo_dir),
    ])
    return repo_dir


def load_json_file(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object.")
    return data


def read_text(path: Path | None) -> str:
    if path is None:
        return ""
    return path.read_text(encoding="utf-8").strip()


def raw_url(provider: str, repo: str, branch: str, file_path: str) -> str:
    normalized_path = file_path.strip().lstrip("/")
    if provider == "github":
        return f"https://raw.githubusercontent.com/{repo}/{branch}/{normalized_path}"
    if provider == "gitee":
        return f"https://raw.giteeusercontent.com/{repo}/raw/{branch}/{normalized_path}"
    raise ValueError(f"Unknown provider: {provider}")


def write_if_changed(path: Path, content: str) -> bool:
    old_content = path.read_text(encoding="utf-8") if path.exists() else None
    if old_content == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    return True


def api_request(
    api_url: str,
    token: str,
    method: str,
    path: str,
    payload: dict | None = None,
    idempotency_key: str | None = None,
) -> dict:
    headers = {"Authorization": f"Publisher {token}", "Accept": "application/json"}
    body = None
    if payload is not None:
        if not idempotency_key:
            raise ValueError("Publisher PUT requires an Idempotency-Key.")
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        headers.update({
            "Content-Type": "application/json; charset=utf-8",
            "Idempotency-Key": idempotency_key,
            "X-Request-Timestamp": fresh_request_timestamp(),
            "X-Request-Nonce": uuid.uuid4().hex,
        })

    request = Request(
        f"{api_url.rstrip('/')}{path}",
        data=body,
        headers=headers,
        method=method,
    )
    try:
        with urlopen(request, timeout=15) as response:
            response_body = response.read().decode("utf-8")
    except HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace").strip()
        raise PublisherApiError(
            f"Publisher API {method} {path} failed ({exc.code}): {error_body or exc.reason}",
            transient=exc.code in TRANSIENT_HTTP_STATUSES,
        ) from exc
    except (URLError, TimeoutError, OSError) as exc:
        reason = getattr(exc, "reason", str(exc))
        raise PublisherApiError(f"Publisher API {method} {path} failed: {reason}", transient=True) from exc

    try:
        data = json.loads(response_body)
    except json.JSONDecodeError as exc:
        raise PublisherApiError(f"Publisher API {method} {path} returned invalid JSON.") from exc
    if not isinstance(data, dict):
        raise PublisherApiError(f"Publisher API {method} {path} must return a JSON object.")
    return data


def api_request_with_retry(
    api_url: str,
    token: str,
    method: str,
    path: str,
    payload: dict | None = None,
    idempotency_key: str | None = None,
) -> dict:
    for attempt in range(1, API_ATTEMPTS + 1):
        try:
            return api_request(api_url, token, method, path, payload, idempotency_key)
        except PublisherApiError as exc:
            if not exc.transient or attempt == API_ATTEMPTS:
                raise
            print(
                f"Publisher {method} transient failure (attempt {attempt}/{API_ATTEMPTS}); retrying.",
                file=sys.stderr,
            )
            time.sleep(attempt)
    raise AssertionError("Publisher API retry loop ended unexpectedly.")


def idempotency_key(version_code: int, version_name: str) -> str:
    release = f"eklose:{version_code}:{version_name}"
    digest = hashlib.sha256(release.encode("utf-8")).hexdigest()
    return f"eklose-config-{digest}"


def build_release_fields(
    local_config_file: Path,
    source_changelog_file: Path,
    version_code: int,
    version_name: str,
    apk_url: str,
    update_message: str,
) -> dict:
    local_config = load_json_file(local_config_file)
    return {
        "version": version_name,
        "latestVersionCode": version_code,
        "updateUrl": (local_config.get("updateUrl") or apk_url or DEFAULT_DOWNLOAD_URL).strip(),
        "isForce": local_config.get("isForce", False),
        "updateMessage": update_message,
        "changelogSummary": local_config.get("changelogSummary") or update_message,
        "changelogContent": source_changelog_file.read_text(encoding="utf-8"),
    }


def mirror_config(published_config: dict) -> dict:
    return {
        key: value
        for key, value in published_config.items()
        if key not in MIRROR_CONFIG_EXCLUDED_FIELDS
    }


def sync_files(
    provider: str,
    repo: str,
    branch: str,
    username: str | None,
    token: str,
    version_name: str,
    config_file: str,
    changelog_file: str,
    config: dict,
    changelog_content: str,
) -> None:
    provider_config = dict(config)
    provider_config["changelogUrl"] = raw_url(provider, repo, branch, changelog_file)
    config_content = json.dumps(provider_config, ensure_ascii=False, indent=2) + "\n"

    for attempt in range(1, GIT_PUSH_ATTEMPTS + 1):
        with tempfile.TemporaryDirectory(prefix=f"eklose-{provider}-config-") as temp_dir:
            repo_dir = clone_repo(provider, repo, branch, username, token, Path(temp_dir))
            changed_files: list[str] = []
            if write_if_changed(repo_dir / config_file, config_content):
                changed_files.append(config_file)
            if write_if_changed(repo_dir / changelog_file, changelog_content):
                changed_files.append(changelog_file)

            if not changed_files:
                print(f"{provider} config repository has no changes.")
                return

            run_command(["git", "config", "user.name", "github-actions[bot]"], cwd=repo_dir)
            run_command(["git", "config", "user.email", "github-actions[bot]@users.noreply.github.com"], cwd=repo_dir)
            run_command(["git", "add", *changed_files], cwd=repo_dir)
            run_command(["git", "commit", "-m", f"Update Eklose v{version_name} config"], cwd=repo_dir)
            try:
                run_command(["git", "push"], cwd=repo_dir)
            except subprocess.CalledProcessError:
                if attempt == GIT_PUSH_ATTEMPTS:
                    raise
                print(
                    f"{provider} config push failed (attempt {attempt}/{GIT_PUSH_ATTEMPTS}); retrying.",
                    file=sys.stderr,
                )
                time.sleep(attempt)
                continue

            print(f"{provider} config repository updated: {', '.join(changed_files)}")
            return

    raise AssertionError("Git push retry loop ended unexpectedly.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Publish Eklose config through the LastStudio API and legacy mirrors")
    parser.add_argument("--version-code", required=True, type=int, help="Android versionCode")
    parser.add_argument("--version-name", required=True, help="Android versionName")
    parser.add_argument("--apk-url", default=DEFAULT_DOWNLOAD_URL, help="Default APK download URL")
    parser.add_argument("--message-file", default="current_changelog.md", help="Current changelog text file")
    parser.add_argument("--local-config-file", default=os.getenv("LOCAL_CONFIG_FILE", DEFAULT_LOCAL_CONFIG_FILE))
    parser.add_argument("--source-changelog-file", default=os.getenv("SOURCE_CHANGELOG_FILE", DEFAULT_CHANGELOG_FILE))
    parser.add_argument("--config-file", default=os.getenv("REMOTE_CONFIG_FILE", DEFAULT_CONFIG_FILE))
    parser.add_argument("--changelog-file", default=os.getenv("REMOTE_CHANGELOG_FILE", DEFAULT_CHANGELOG_FILE))
    parser.add_argument("--api-url", default=os.getenv("LASTUDIO_API_URL", DEFAULT_API_URL), help="LastStudio API base URL")
    parser.add_argument("--github-repo", default=os.getenv("CONFIG_GITHUB_REPO", DEFAULT_GITHUB_REPO))
    parser.add_argument("--github-branch", default=os.getenv("CONFIG_GITHUB_BRANCH", DEFAULT_GITHUB_BRANCH))
    parser.add_argument("--github-token", default=os.getenv("CONFIG_GITHUB_TOKEN") or os.getenv("GH_CONFIG_TOKEN"))
    parser.add_argument("--gitee-repo", default=os.getenv("GITEE_CONFIG_REPO", DEFAULT_GITEE_REPO))
    parser.add_argument("--gitee-branch", default=os.getenv("GITEE_CONFIG_BRANCH", DEFAULT_GITEE_BRANCH))
    parser.add_argument("--gitee-username", default=os.getenv("GITEE_USERNAME"))
    parser.add_argument("--gitee-token", default=os.getenv("GITEE_TOKEN"))
    args = parser.parse_args()

    try:
        publisher_token = os.getenv("LASTUDIO_PUBLISHER_TOKEN", "").strip()
        if not publisher_token:
            raise ValueError("LASTUDIO_PUBLISHER_TOKEN is not configured.")
        if not args.github_token:
            raise ValueError("CONFIG_GITHUB_TOKEN is not configured.")
        if not args.gitee_username or not args.gitee_token:
            raise ValueError("GITEE_USERNAME and GITEE_TOKEN must be configured.")

        local_config_file = Path(args.local_config_file)
        source_changelog_file = Path(args.source_changelog_file)
        if not local_config_file.exists():
            raise FileNotFoundError(f"Local config file not found: {local_config_file}")
        if not source_changelog_file.exists():
            raise FileNotFoundError(f"Source changelog file not found: {source_changelog_file}")

        update_message = read_text(Path(args.message_file) if args.message_file else None)
        current_config = api_request_with_retry(args.api_url, publisher_token, "GET", PUBLISHER_CONFIG_PATH)
        if not isinstance(current_config.get("configRevision"), int):
            raise ValueError("Publisher API response is missing configRevision.")
        publish_payload = {
            key: value
            for key, value in current_config.items()
            if key not in READ_ONLY_FIELDS
        }
        publish_payload.update(build_release_fields(
            local_config_file=local_config_file,
            source_changelog_file=source_changelog_file,
            version_code=args.version_code,
            version_name=args.version_name,
            apk_url=args.apk_url,
            update_message=update_message,
        ))
        published_config = api_request_with_retry(
            args.api_url,
            publisher_token,
            "PUT",
            PUBLISHER_CONFIG_PATH,
            publish_payload,
            idempotency_key(args.version_code, args.version_name),
        )
        if not isinstance(published_config.get("configRevision"), int):
            raise ValueError("Publisher API response is missing configRevision.")
        published_changelog = published_config.get("changelogContent")
        if not isinstance(published_changelog, str):
            raise ValueError("Publisher API response is missing changelogContent.")
        published_mirror_config = mirror_config(published_config)
        sync_files(
            provider="github",
            repo=args.github_repo,
            branch=args.github_branch,
            username=None,
            token=args.github_token,
            version_name=args.version_name,
            config_file=args.config_file,
            changelog_file=args.changelog_file,
            config=published_mirror_config,
            changelog_content=published_changelog,
        )
        sync_files(
            provider="gitee",
            repo=args.gitee_repo,
            branch=args.gitee_branch,
            username=args.gitee_username,
            token=args.gitee_token,
            version_name=args.version_name,
            config_file=args.config_file,
            changelog_file=args.changelog_file,
            config=published_mirror_config,
            changelog_content=published_changelog,
        )
    except subprocess.CalledProcessError as exc:
        print(f"Remote config publish failed: command exited with {exc.returncode}", file=sys.stderr)
        return exc.returncode or 1
    except Exception as exc:
        print(f"Remote config publish failed: {exc}", file=sys.stderr)
        return 1

    print(f"Published and mirrored Eklose v{args.version_name} remote config.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
