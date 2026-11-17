"""Create verified StudyFlow source/app archives from a clean checkout (standard-library Python)."""

import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from zipfile import ZIP_DEFLATED, ZipFile


def digest(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def staged_archive(destination, generate):
    with tempfile.NamedTemporaryFile(dir=destination.parent, suffix=".zip", delete=False) as handle:
        temporary = Path(handle.name)
    try:
        generate(temporary)
        with ZipFile(temporary) as archive:
            bad_entry = archive.testzip()
            if bad_entry is not None:
                raise RuntimeError(f"Archive failed integrity check: {bad_entry}")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--windows-image", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    destination = args.destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    java = args.jar.resolve(strict=True)
    windows = args.windows_image.resolve(strict=True)

    def git(*parts):
        return subprocess.check_output(["git", "-C", str(repo), *parts])

    if git("status", "--porcelain"):
        raise RuntimeError("Commit and verify the release checkout before archiving it.")
    version = ET.parse(repo / "pom.xml").findtext("{*}version")
    source_id = git("rev-parse", "HEAD").decode("ascii").strip()
    tracked = git("ls-tree", "-r", "--name-only", "-z", source_id).split(b"\0")
    expected = {"StudyFlow/" + name.decode("utf-8") for name in tracked if name}
    standalone = destination / "StudyFlow.jar"
    portable = destination / f"StudyFlow-{version}-Project.zip"
    bundled = destination / f"StudyFlow-{version}-Windows-x64.zip"

    image_jar = windows / "app" / "StudyFlow.jar"
    if not all(item.is_file() for item in (
        windows / "StudyFlow.exe", image_jar, windows / "runtime" / "bin" / "server" / "jvm.dll"
    )):
        raise RuntimeError("The Windows app image is incomplete.")
    if digest(java) != digest(image_jar):
        raise RuntimeError("Both packages must contain the same verified JAR.")
    shutil.copyfile(java, standalone)

    def project_archive(temporary):
        subprocess.run(["git", "-C", str(repo), "archive", "--format=zip", "--prefix=StudyFlow/",
                        f"--output={temporary}", source_id], check=True)
        with ZipFile(temporary, "a", compression=ZIP_DEFLATED) as archive:
            archive.write(standalone, "StudyFlow/StudyFlow.jar")
            archive.writestr("StudyFlow/START_HERE.txt", f"""StudyFlow {version}
Extract the entire ZIP. Open this StudyFlow folder in VS Code to work on the source.

Run with Java 21, from a terminal in this folder:
  java -version
  java -jar StudyFlow.jar

For the runtime-bundled Windows version, use the separate Windows x64 ZIP.
See README.md and docs/user-manual.md for profiles, features and recovery.
Source commit: {source_id}
""")
        with ZipFile(temporary) as archive:
            entries = {info.filename for info in archive.infolist() if not info.is_dir()}
            if entries != expected | {"StudyFlow/StudyFlow.jar", "StudyFlow/START_HERE.txt"}:
                raise RuntimeError("Archive contents do not match the verified checkout.")
            if hashlib.sha256(archive.read("StudyFlow/StudyFlow.jar")).hexdigest() != digest(java):
                raise RuntimeError("The archived JAR does not match the source artifact.")

    def windows_archive(temporary):
        if not (windows / "runtime" / "legal").is_dir():
            raise RuntimeError("The runtime image is missing its legal notices.")
        if (windows / "data").exists():
            raise RuntimeError("Do not package a profile directory.")
        with ZipFile(temporary, "w", compression=ZIP_DEFLATED, compresslevel=6) as archive:
            for item in sorted(windows.rglob("*")):
                if item.is_symlink():
                    raise RuntimeError(f"Unexpected symbolic link in app image: {item.name}")
                if item.is_file():
                    archive.write(item, "StudyFlow/" + item.relative_to(windows).as_posix())
            archive.writestr("StudyFlow/START_HERE.txt", f"""StudyFlow {version} for Windows x64

Extract all files and open StudyFlow.exe. Keep the app and runtime folders beside it.
This app image is unsigned and includes a Java 21 runtime.
New profiles live under %LOCALAPPDATA%\\StudyFlow\\data.
To open an existing profile, launch StudyFlow.exe with --data-dir "C:\\your profile".
See runtime/legal for the bundled runtime notices and release for its build information.
Source commit: {source_id}
""")
            archive.writestr("StudyFlow/RUNTIME-NOTICE.txt", """Runtime: Microsoft Build of OpenJDK.
The version information is in runtime/release; licenses and notices are in runtime/legal.
Vendor source project: https://github.com/microsoft/openjdk
Vendor documentation: https://learn.microsoft.com/en-us/java/openjdk/support
""")
        with ZipFile(temporary) as archive:
            if hashlib.sha256(archive.read("StudyFlow/app/StudyFlow.jar")).hexdigest() != digest(java):
                raise RuntimeError("The bundled app does not match the source artifact.")

    staged_archive(portable, project_archive)
    staged_archive(bundled, windows_archive)
    checksums = destination / "StudyFlow-SHA256SUMS.txt"
    checksums.write_text("".join(f"{digest(path)}  {path.name}\n"
                                 for path in (standalone, portable, bundled)), encoding="ascii")
    for path in (standalone, portable, bundled, checksums):
        print(f"{path.name}: {path.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
