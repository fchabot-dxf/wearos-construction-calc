#!/usr/bin/env python3
"""
tools/ota_deploy.py — Host-Side Pipeline for ConstructionCalc Wear OS OTA Updates

Workflow:
1. Bumps `versionCode` in app/build.gradle.kts.
2. Compiles the APK via gradlew.bat assembleDebug.
3. Computes the SHA-256 hash and size of the output APK.
4. Generates ota_manifest.json pointing to GitHub Release asset.
5. Publishes release to GitHub via `gh release create`.
6. Pushes git commits & tags to origin.
"""

import sys
import os
import re
import shutil
import hashlib
import json
import subprocess
import argparse
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
BUILD_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
APK_PATH = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
MANIFEST_PATH = REPO_ROOT / "ota_manifest.json"

GH_REPO = "fchabot-dxf/wearos-construction-calc"

def bump_version_code() -> tuple[int, str]:
    content = BUILD_GRADLE.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    if not code_match:
        print("[ERROR] Could not find 'versionCode' in app/build.gradle.kts", file=sys.stderr)
        sys.exit(1)
        
    old_code = int(code_match.group(1))
    new_code = old_code + 1
    
    content = re.sub(
        r"versionCode\s*=\s*\d+",
        f"versionCode = {new_code}",
        content
    )
    
    new_name = f"1.0.{new_code}"
    content = re.sub(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{new_name}"',
        content
    )
        
    BUILD_GRADLE.write_text(content, encoding="utf-8")
    print(f"[PIPELINE] Bumped versionCode: {old_code} -> {new_code} (versionName: {new_name})")
    return new_code, new_name


def build_apk():
    print("[PIPELINE] Compiling APK (gradlew.bat assembleDebug)...")
    env = os.environ.copy()
    jbr_path = Path(r"C:\Program Files\Android\Android Studio\jbr")
    if jbr_path.exists():
        env["JAVA_HOME"] = str(jbr_path)
        env["PATH"] = f"{jbr_path / 'bin'}{os.pathsep}{env.get('PATH', '')}"

    gradle_cmd = "gradlew.bat" if os.name == "nt" else "./gradlew"
    cmd = [str(REPO_ROOT / gradle_cmd), "assembleDebug"]
    
    result = subprocess.run(cmd, cwd=str(REPO_ROOT), env=env)
    if result.returncode != 0:
        print("[ERROR] Build failed!", file=sys.stderr)
        sys.exit(result.returncode)
        
    if not APK_PATH.exists():
        print(f"[ERROR] APK not found at {APK_PATH}", file=sys.stderr)
        sys.exit(1)
        
    print(f"[PIPELINE] Build successful: {APK_PATH} ({APK_PATH.stat().st_size:,} bytes)")


def compute_sha256(file_path: Path) -> str:
    hasher = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()


def write_manifest(version_code: int, version_name: str, sha256_hash: str) -> dict:
    apk_url = f"https://github.com/{GH_REPO}/releases/download/v{version_code}/app-debug.apk"
    manifest = {
        "versionCode": version_code,
        "versionName": version_name,
        "apkFile": "app-debug.apk",
        "apkSize": APK_PATH.stat().st_size,
        "sha256": sha256_hash,
        "apkUrl": apk_url,
        "releaseNotes": f"Wear OS Conversion Tile & Calculator build v{version_name}",
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
    
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"[PIPELINE] Wrote local OTA manifest to {MANIFEST_PATH}")
    return manifest


def publish_github_release(version_code: int, version_name: str):
    tag = f"v{version_code}"
    print(f"[PIPELINE] Creating GitHub Release {tag} via gh CLI on {GH_REPO}...")
    cmd = [
        "gh", "release", "create", tag,
        str(APK_PATH),
        str(MANIFEST_PATH),
        "-R", GH_REPO,
        "--title", f"ConstructionCalc OTA {tag} ({version_name})",
        "--notes", f"Automated OTA build deployed at {datetime.now(timezone.utc).isoformat()}"
    ]
    res = subprocess.run(cmd, cwd=str(REPO_ROOT), capture_output=True, text=True)
    if res.returncode == 0:
        print(f"[PIPELINE] Successfully published release {tag} to GitHub!")
    else:
        print(f"[WARN] gh release output: {res.stderr or res.stdout}", file=sys.stderr)


def git_commit_and_push(version_code: int):
    print("[PIPELINE] Committing and pushing OTA changes to GitHub...")
    subprocess.run(["git", "add", "."], cwd=str(REPO_ROOT))
    subprocess.run(["git", "commit", "-m", f"chore(ota): bump version to {version_code} and update manifest"], cwd=str(REPO_ROOT))
    subprocess.run(["git", "push", "-u", "origin", "main"], cwd=str(REPO_ROOT))


def main():
    parser = argparse.ArgumentParser(description="ConstructionCalc Wear OS OTA Deployment Pipeline")
    parser.add_argument("--no-bump", action="store_true", help="Do not bump versionCode")
    args = parser.parse_args()

    if args.no_bump:
        content = BUILD_GRADLE.read_text(encoding="utf-8")
        code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
        name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
        version_code = int(code_match.group(1)) if code_match else 1
        version_name = name_match.group(1) if name_match else "1.0.0"
    else:
        version_code, version_name = bump_version_code()

    build_apk()
    sha256 = compute_sha256(APK_PATH)
    manifest = write_manifest(version_code, version_name, sha256)

    git_commit_and_push(version_code)
    publish_github_release(version_code, version_name)

    print("\n" + "=" * 60)
    print("ConstructionCalc OTA Deployment Complete!")
    print(f"Manifest URL: https://raw.githubusercontent.com/{GH_REPO}/main/ota_manifest.json")
    print(f"Release URL:  https://github.com/{GH_REPO}/releases/tag/v{version_code}")
    print("=" * 60)


if __name__ == "__main__":
    main()
