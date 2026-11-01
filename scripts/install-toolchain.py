#!/usr/bin/env python3
"""Install the Linux Android toolchain inside this checkout, without sudo."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / ".tools"
DOWNLOADS = TOOLS / "downloads"


def fetch_text(url):
    request = urllib.request.Request(url, headers={"User-Agent": "DriveMood-Setup/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode()


def download(url, name, checksum):
    destination = DOWNLOADS / name
    if not destination.exists():
        partial = destination.with_suffix(destination.suffix + ".part")
        print(f"Downloading {name}...", flush=True)
        subprocess.run(
            ["curl", "--fail", "--location", "--retry", "3", "--connect-timeout", "20",
             "--max-time", "1800", "--silent", "--show-error", "--output", str(partial), url],
            check=True,
        )
        partial.rename(destination)
    with destination.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != checksum:
        raise RuntimeError(f"Checksum mismatch for {name}; remove {destination} and retry")
    print(f"Verified {name}", flush=True)
    return destination


def unpack(archive, destination, inner_name=None):
    with tempfile.TemporaryDirectory(dir=DOWNLOADS, prefix="extract-") as staging:
        staging = Path(staging)
        if archive.name.endswith(".zip"):
            subprocess.run(["unzip", "-q", str(archive), "-d", str(staging)], check=True)
        else:
            with tarfile.open(archive) as package:
                package.extractall(staging, filter="data")
        source = staging / inner_name if inner_name else next(staging.iterdir())
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(source), str(destination))


def install_jdk():
    destination = TOOLS / "jdk"
    if (destination / "bin/java").exists():
        return
    url = "https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.tar.gz"
    checksum_text = fetch_text(
        "https://corretto.aws/downloads/latest_sha256/amazon-corretto-21-x64-linux-jdk.tar.gz"
    )
    checksum = re.search(r"\b[0-9a-fA-F]{64}\b", checksum_text).group().lower()
    archive = download(url, "amazon-corretto-21-linux-x64.tar.gz", checksum)
    unpack(archive, destination)


def install_command_line_tools():
    destination = TOOLS / "android-sdk/cmdline-tools/latest"
    if (destination / "bin/sdkmanager").exists():
        return
    archive = download(
        "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip",
        "commandlinetools-linux-15859902_latest.zip",
        "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583",
    )
    unpack(archive, destination, "cmdline-tools")


def install_studio():
    destination = TOOLS / "android-studio"
    if (destination / "bin/studio.sh").exists():
        return
    archive = download(
        "https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.2.1.8/android-studio-rabbit1-linux.tar.gz",
        "android-studio-rabbit1-linux.tar.gz",
        "f8775c67cf899d9133712a2d7ee3ef7231e07fb16dbe0544c47e5f5babd5931b",
    )
    unpack(archive, destination, "android-studio")


def install_wrapper():
    wrapper = ROOT / "gradle/wrapper"
    wrapper.mkdir(parents=True, exist_ok=True)
    checksum = fetch_text("https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256").strip()
    archive = download(
        "https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar",
        "gradle-8.13-wrapper.jar", checksum,
    )
    shutil.copy2(archive, wrapper / "gradle-wrapper.jar")
    for name in ("gradlew", "gradlew.bat"):
        target = ROOT / name
        if not target.exists():
            target.write_text(fetch_text(f"https://raw.githubusercontent.com/gradle/gradle/v8.13.0/{name}"))
    (ROOT / "gradlew").chmod(0o755)
    distribution_checksum = fetch_text("https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256").strip()
    (wrapper / "gradle-wrapper.properties").write_text(
        "distributionBase=GRADLE_USER_HOME\n"
        "distributionPath=wrapper/dists\n"
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip\n"
        f"distributionSha256Sum={distribution_checksum}\n"
        "networkTimeout=60000\n"
        "validateDistributionUrl=true\n"
        "zipStoreBase=GRADLE_USER_HOME\n"
        "zipStorePath=wrapper/dists\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--with-studio", action="store_true", help="Also install Android Studio")
    args = parser.parse_args()
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise SystemExit("This installer supports Linux x86_64; configure the Android toolchain for your OS instead.")
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    jobs = [install_jdk, install_command_line_tools, install_wrapper]
    if args.with_studio:
        jobs.append(install_studio)
    with ThreadPoolExecutor(max_workers=len(jobs)) as pool:
        futures = [pool.submit(job) for job in jobs]
        failures = []
        for future in futures:
            try:
                future.result()
            except Exception as error:
                failures.append(str(error))
        if failures:
            raise SystemExit("\n".join(failures))
    (ROOT / "local.properties").write_text(f"sdk.dir={TOOLS / 'android-sdk'}\n")
    gradle_local = ROOT / ".gradle"
    gradle_local.mkdir(exist_ok=True)
    (gradle_local / "config.properties").write_text(f"java.home={TOOLS / 'jdk'}\n")
    print("Toolchain ready. Next: scripts/setup-android.sh", flush=True)


if __name__ == "__main__":
    main()
