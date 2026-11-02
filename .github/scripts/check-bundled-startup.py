"""Launch a packaged distribution, load Pizza, and check for startup errors.

Run after `mvn package`, with a display available (Xvfb on Linux).
This checks startup only; the process tree is forcibly stopped after the check.
"""

import argparse
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import tarfile
import time
import zipfile


def read_log(path):
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=("linux", "win"))
    parser.add_argument("--launcher", choices=("script", "native"), default="script")
    args = parser.parse_args()
    windows = args.platform == "win"
    if windows != (os.name == "nt"):
        parser.error("Run this check on the distribution's operating system")

    root = Path(__file__).resolve().parents[2]
    target = root / "protege-desktop/target"
    extension = "zip" if windows else "tar.gz"
    archives = list(target.glob(f"protege-*-{args.platform}.{extension}"))
    if len(archives) != 1:
        raise RuntimeError(f"Expected one distribution archive, found {archives}")
    output = target / "startup-check" / args.launcher
    output.mkdir(parents=True, exist_ok=True)
    # Refuse to reuse a previous extraction or preference directory.
    extracted = output / "distribution"
    extracted.mkdir()
    if windows:
        with zipfile.ZipFile(archives[0]) as archive:
            archive.extractall(extracted)
    else:
        with tarfile.open(archives[0]) as archive:
            archive.extractall(extracted, filter="data")
    if args.launcher == "native":
        launcher_name = "Protege.exe" if windows else "protege"
    else:
        launcher_name = "run.bat" if windows else "run.sh"
    launchers = list(extracted.glob(f"*/{launcher_name}"))
    if len(launchers) != 1:
        raise RuntimeError(f"Expected one packaged launcher, found {launchers}")
    distribution = launchers[0].parent
    test_home = output / "home"
    test_home.mkdir()
    ontology = output / "pizza.owl"
    shutil.copyfile(root / "protege-editor-owl/src/test/resources/ontologies/pizza.owl", ontology)

    env = os.environ.copy()
    for key in ("JAVA_HOME", "PROTEGE_JAVA_HOME", "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CMD_OPTIONS"):
        env.pop(key, None)
    java = distribution / "jre/bin" / ("java.exe" if windows else "java")
    version = subprocess.run([str(java), "-XshowSettings:properties", "-version"],
                             env=env, capture_output=True, text=True, timeout=30, check=True)
    (output / "java-version.log").write_text(version.stdout + version.stderr, encoding="utf-8")
    if not re.search(r"java.specification.version = 25\s", version.stderr):
        raise RuntimeError("The packaged runtime is not Java 25")

    # The launchers accept JVM properties through JAVA_TOOL_OPTIONS. Forward
    # the ontology this way so paths with spaces do not depend on shell quoting.
    env["JAVA_TOOL_OPTIONS"] = (f'-Duser.home="{test_home.as_posix()}" '
                                f'-Dcommand.line.arg.0="{ontology.as_posix()}"')
    if windows and args.launcher == "script":
        command = ["cmd.exe", "/d", "/c", launcher_name]
    else:
        command = [str(launchers[0])]
    print(f"Checking packaged launcher: {launchers[0]}", flush=True)
    console_log = output / "console.log"
    application_log = test_home / ".Protege/logs/protege.log"
    ready_since = None
    with console_log.open("w", encoding="utf-8") as console:
        process = subprocess.Popen(command, cwd=distribution, env=env,
                                   stdout=console, stderr=subprocess.STDOUT,
                                   start_new_session=not windows)
        try:
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                text = read_log(console_log) + read_log(application_log)
                text = re.sub(r"\x1b\[[0-9;]*m", "", text)
                if re.search(r"\bERROR\b|Exception in thread|Exception:|\n\s+at [\w.$/]+\(", text):
                    raise RuntimeError("Startup reported an error; see the startup logs")
                if process.poll() is not None:
                    raise RuntimeError(f"Launcher exited early with status {process.returncode}")
                ready = ("Java: JVM 25." in text
                         and "The OSGi framework has been started" in text
                         and re.search(r"Finished loading file:[^\r\n]*pizza\.owl", text)
                         and "Loading for ontology and imports closure successfully completed" in text)
                if ready and ready_since is None:
                    ready_since = time.monotonic()
                if ready_since is not None and time.monotonic() - ready_since >= 10:
                    print(f"PASS: {launcher_name} started packaged Java 25, loaded Pizza, "
                          "and remained running for 10 seconds")
                    return
                time.sleep(1)
            raise TimeoutError("Pizza did not finish loading within 120 seconds")
        finally:
            # Stop only this test's process tree. Shutdown behavior is a separate test.
            if windows:
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                               capture_output=True, timeout=15)
            else:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            process.wait(timeout=15)
            if application_log.exists():
                shutil.copyfile(application_log, output / "protege.log")
            print(read_log(console_log))


if __name__ == "__main__":
    main()
