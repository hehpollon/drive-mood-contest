#!/usr/bin/env python3
"""Run installed-APK checks; --live injects only into an Android emulator.

Requires the Pleos APK and its androidTest APK already installed. Live mode
uses the official localhost Virtual Console and restores motion inputs.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--action", choices=["flow", "live", "snapshot", "ui", "rhythm-ui", "route-ui", "verify-persistence"], default="flow")
args = parser.parse_args()
adb = str(Path(os.environ.get("ANDROID_HOME", ROOT / ".tools/android-sdk")) / "platform-tools/adb")
package = "io.github.hehpollon.drivemood"
log_dir = ROOT / ".tools/logs"
log_dir.mkdir(parents=True, exist_ok=True)
log_path = log_dir / f"runtime-{args.action}.txt"
stop = threading.Event()


def adb_run(*command):
    return subprocess.check_output([adb, "-s", args.serial, *command], timeout=20).decode()


def console(path, method="GET", **values):
    query = urllib.parse.urlencode(values)
    request = urllib.request.Request("http://127.0.0.1:8088/" + path + ("?" + query if query else ""), method=method)
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


def heartbeat():
    while not stop.is_set():
        try:
            console("browser/session", "POST", client="drive-mood-runtime-checks")
        except OSError:
            pass
        stop.wait(1)


def motion(speed=0, steering=0, brake=0):
    console("vehicle/controls", "PUT", speed=speed, accelerator=0, steering=steering, brake=brake)


original = None
process = None
try:
    if args.action == "live":
        if not args.serial.startswith("emulator-") or adb_run("shell", "getprop", "ro.kernel.qemu").strip() != "1":
            raise RuntimeError("Live injection is restricted to an Android emulator.")
        adb_run("forward", "tcp:8088", "tcp:8088")
        original = console("vehicle/state")
        threading.Thread(target=heartbeat, daemon=True).start()
        deadline = time.monotonic() + 20
        while not console("vehicle/state")["controls"]["dds_ready"]["all"]:
            if time.monotonic() > deadline:
                raise RuntimeError("Virtual Console DDS bridge did not become ready.")
            time.sleep(0.5)
        motion()
        console("vehicle/gear", "PUT", gear="Park")
        time.sleep(2)
    with log_path.open("w") as output:
        process = subprocess.Popen([adb, "-s", args.serial, "shell", "am", "instrument", "--user", "current", "-w", "-e", "action", args.action,
                                    package + ".test/" + package + ".verification.RuntimeChecks"], stdout=output, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 200
        driving = False
        varying = False
        parked = False
        guide_moved = False
        restored = False
        step = 0
        previous_check = ""
        while process.poll() is None:
            if time.monotonic() > deadline:
                raise TimeoutError("Runtime checks exceeded 200 seconds.")
            current = log_path.read_text()
            checks = [line for line in current.splitlines() if line.startswith("INSTRUMENTATION_STATUS: check=")]
            if checks and checks[-1] != previous_check:
                previous_check = checks[-1]
                print(previous_check.removeprefix("INSTRUMENTATION_STATUS: check="), flush=True)
            if args.action == "live":
                if "check=LIVE_READY" in current and not driving:
                    motion(speed=80, steering=0)
                    console("vehicle/gear", "PUT", gear="Drive")
                    driving = True
                if "check=real SDK steady rhythm" in current and not parked:
                    varying = True
                if varying and not parked:
                    motion(speed=80, steering=7 if step % 2 else -7, brake=25 if step % 5 == 0 else 0)
                    step += 1
                if "check=real SDK changed rhythm" in current and not parked:
                    motion()
                    console("vehicle/gear", "PUT", gear="Park")
                    parked = True
                if "check=GUIDE_READY_FOR_MOVEMENT" in current and not guide_moved:
                    console("vehicle/gear", "PUT", gear="Drive")
                    motion(speed=15)
                    guide_moved = True
                if "check=actual movement cancels demo and guide" in current and not restored:
                    motion()
                    console("vehicle/gear", "PUT", gear="Park")
                    restored = True
            time.sleep(1)
    result = log_path.read_text()
    if "INSTRUMENTATION_RESULT: result=PASS" not in result:
        raise RuntimeError(f"Runtime checks failed. See {log_path}\n{result[-6000:]}")
    print(f"PASS · {log_path}")
finally:
    if process is not None and process.poll() is None:
        process.terminate()
        process.wait(timeout=10)
        adb_run("shell", "am", "force-stop", "--user", "current", package)
    if original is not None:
        try:
            values = original["controls"]
            console("vehicle/controls", "PUT", speed=values.get("speed_kph", 0), accelerator=values.get("accelerator_pct", 0),
                    brake=values.get("brake_pct", 0), steering=values.get("steering_deg", 0))
            gear = original.get("gear", {}).get("actual")
            if isinstance(gear, dict):
                gear = gear.get("value")
            console("vehicle/gear", "PUT", gear=gear if gear in {"Park", "Drive", "Reverse", "Neutral"} else "Park")
        finally:
            stop.set()
            console("browser/session", "POST", client="drive-mood-runtime-checks", closing=1)
