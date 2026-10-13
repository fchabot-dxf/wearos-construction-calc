#!/usr/bin/env python3
"""
build_and_deploy.py — Build & Deploy ConstructionCalc to Pixel Watch
"""

import sys
import os
import subprocess
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent
APK_PATH = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
ADB_PATH = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
if not ADB_PATH.exists():
    ADB_PATH = Path(r"C:\Users\danse\APPS\WearOS\tools\platform-tools\adb.exe")

def get_jdk_env():
    env = os.environ.copy()
    jdk_paths = [
        Path(r"C:\Program Files\Android\Android Studio\jbr"),
        Path(r"C:\Users\danse\AppData\Local\Android\jdk17\jdk-17.0.12+7")
    ]
    for jdk in jdk_paths:
        if jdk.exists():
            env["JAVA_HOME"] = str(jdk)
            env["PATH"] = f"{jdk / 'bin'}{os.pathsep}{env.get('PATH', '')}"
            break
    return env

def find_watch():
    res = subprocess.run([str(ADB_PATH), "devices"], capture_output=True, text=True)
    for line in res.stdout.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device" and not parts[0].startswith("emulator"):
            return parts[0]
    # Check mDNS services
    res = subprocess.run([str(ADB_PATH), "mdns", "services"], capture_output=True, text=True)
    for line in res.stdout.splitlines():
        if "_adb-tls-connect._tcp" in line or "_adb._tcp" in line:
            parts = line.split()
            addr = parts[-1]
            print(f"[DEPLOY] Found mDNS watch at {addr}, connecting...")
            subprocess.run([str(ADB_PATH), "connect", addr])
            time.sleep(1)
            return addr
    return None

def build():
    print("[BUILD] Compiling ConstructionCalc via Gradle...")
    env = get_jdk_env()
    gradle_cmd = str(REPO_ROOT / "gradlew.bat") if os.name == "nt" else str(REPO_ROOT / "gradlew")
    res = subprocess.run([gradle_cmd, "assembleDebug"], cwd=str(REPO_ROOT), env=env)
    if res.returncode != 0:
        print(f"[ERROR] Build failed with code {res.returncode}")
        sys.exit(res.returncode)
    print(f"[BUILD] Build successful! APK: {APK_PATH}")

def deploy():
    dev = find_watch()
    if not dev:
        print("[ERROR] No watch connected via ADB.")
        sys.exit(1)

    # Wake up device so it doesn't go offline
    subprocess.run([str(ADB_PATH), "-s", dev, "shell", "input", "keyevent", "KEYCODE_WAKEUP"])
    print(f"[DEPLOY] Installing {APK_PATH.name} to watch [{dev}]...")
    res = subprocess.run([str(ADB_PATH), "-s", dev, "install", "-r", str(APK_PATH)])
    if res.returncode != 0:
        print(f"[ERROR] Install failed with code {res.returncode}")
        sys.exit(res.returncode)
    print("[DEPLOY] Installation successful!")
    print("[DEPLOY] Installation successful!")

    print("[DEPLOY] Launching ConstructionCalcActivity...")
    subprocess.run([str(ADB_PATH), "-s", dev, "shell", "am", "start", "-n", "com.dansemur.constructioncalc/.ConstructionCalcActivity"])
    time.sleep(2)
    subprocess.run([str(ADB_PATH), "-s", dev, "shell", "screencap", "-p", "/sdcard/screen_construction_calc.png"])
    subprocess.run([str(ADB_PATH), "-s", dev, "pull", "/sdcard/screen_construction_calc.png", str(REPO_ROOT / "screen_construction_calc.png")])
    print(f"[DEPLOY] Captured screenshot to {REPO_ROOT / 'screen_construction_calc.png'}")

if __name__ == "__main__":
    build()
    deploy()
