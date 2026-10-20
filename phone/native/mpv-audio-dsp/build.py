"""Rebuild FFmpeg 9.0.1 audio filters against the exact libmpv 1.0.4 AAR.

Windows: Python 3, Git Bash, Android NDK r28b. No system install is required.
Example: python phone/native/mpv-audio-dsp/build.py --ndk C:/Android/Sdk/ndk/28.2.13676358
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
AAR_SHA256 = "22c7e16003f59a03fd430063eb78036eb8cfb1844f9eb900387ee72c5fe7ce76"
SOURCE_SHA256 = "cf38e0e28c7e5605942c4a77755349b0145804a397af37eb1fb4c77cb237f635"
HEADER_SHA256 = "3c073236a09cb456c6e80587d5523c8771c32fb22ca014e0f99cc3d43905b75c"
ABIS = {
    "arm64-v8a": ("aarch64", "aarch64-linux-android", "armv8-a"),
    "armeabi-v7a": ("arm", "armv7a-linux-androideabi", "armv7-a"),
    "x86": ("x86", "i686-linux-android", "i686"),
    "x86_64": ("x86_64", "x86_64-linux-android", "generic"),
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def download(url: str, destination: Path) -> None:
    if not destination.exists():
        print(f"Download {url}", flush=True)
        with urllib.request.urlopen(url, timeout=90) as response, destination.open("wb") as output:
            shutil.copyfileobj(response, output)


def exported_symbols(nm: Path, library: Path) -> set[str]:
    output = subprocess.check_output([str(nm), "-D", "--defined-only", str(library)], text=True)
    return {line.split()[-1].replace("@@", "@") for line in output.splitlines() if line.split()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--aar", type=Path)
    parser.add_argument("--bash", type=Path, default=Path("C:/Program Files/Git/bin/bash.exe"))
    parser.add_argument("--abis", default=",".join(ABIS))
    parser.add_argument("--jobs", type=int, default=min(8, os.cpu_count() or 4))
    parser.add_argument("--reuse-configure", action="store_true", help="Reuse a completed per-ABI configure for a link-only retry")
    args = parser.parse_args()
    work = ROOT / "artifacts/mpv-audio-dsp-native"
    work.mkdir(parents=True, exist_ok=True)
    aar = args.aar or next((Path.home() / ".gradle/caches/modules-2/files-2.1/org.jellycine.mpv/libmpv/1.0.4").rglob("*.aar"))
    if sha256(aar) != AAR_SHA256:
        raise SystemExit("Unsupported AAR: update and verify the native wrapper layout before rebuilding.")
    archive = work / "ffmpeg-9.0.1.tar.xz"
    download("https://ffmpeg.org/releases/ffmpeg-9.0.1.tar.xz", archive)
    if sha256(archive) != SOURCE_SHA256:
        raise SystemExit("FFmpeg source checksum mismatch")
    source = work / "ffmpeg-9.0.1"
    if not (source / "configure").exists():
        with tarfile.open(archive) as stream:
            stream.extractall(work, filter="data")
    header = work / "client.h"
    download("https://raw.githubusercontent.com/mpv-player/mpv/v0.41.0/include/mpv/client.h", header)
    if sha256(header) != HEADER_SHA256:
        raise SystemExit("mpv client header checksum mismatch")
    prebuilt = args.ndk.resolve() / "toolchains/llvm/prebuilt/windows-x86_64"
    toolbin = prebuilt / "bin"
    make = args.ndk.resolve() / "prebuilt/windows-x86_64/bin/make.exe"
    env = os.environ.copy()
    env["PATH"] = str(toolbin) + os.pathsep + env["PATH"]
    env["LC_ALL"] = "C"
    results = {}
    for abi in args.abis.split(","):
        arch, triple, cpu = ABIS[abi]
        build = source / ("_jellycine_" + abi)
        build.mkdir(exist_ok=True)
        dependencies = work / "original" / abi
        dependencies.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(aar) as original:
            for name in ["libavutil.so", "libswresample.so", "libmpv.so"]:
                (dependencies / name).write_bytes(original.read(f"jni/{abi}/{name}"))
        compiler = str(toolbin / "clang.exe").replace("\\", "/")
        flags = [
            "../configure", "--target-os=android", "--enable-cross-compile",
            f"--arch={arch}", f"--cpu={cpu}",
            f"--cc={compiler} --target={triple}26",
            # These filters need no host code generators. The configure-only
            # host C11 compile probe can use the same available NDK compiler.
            f"--host-cc={compiler} --target={triple}26",
            f"--ar={str(toolbin / 'llvm-ar.exe').replace(chr(92), '/')}",
            f"--nm={str(toolbin / 'llvm-nm.exe').replace(chr(92), '/')}",
            f"--ranlib={str(toolbin / 'llvm-ranlib.exe').replace(chr(92), '/')}",
            "--disable-autodetect", "--disable-everything", "--disable-programs",
            "--disable-doc", "--disable-network", "--disable-avcodec", "--disable-avformat",
            "--disable-avdevice", "--disable-swscale", "--enable-avfilter",
            "--enable-swresample", "--enable-avutil", "--disable-shared", "--enable-static",
            "--enable-pic", "--disable-asm", "--enable-gpl", "--enable-version3",
            "--disable-debug", "--enable-filter=abuffer,abuffersink,aresample,aformat,anull,volume,alimiter,dynaudnorm,astats",
        ]
        configure = " ".join(shlex.quote(value) for value in flags)
        make_command = " ".join(shlex.quote(value) for value in [
            str(make).replace("\\", "/"), f"-j{args.jobs}", "libavfilter/libavfilter.a",
            "SHELL=C:/Program Files/Git/bin/sh.exe",
        ])
        script = build / "jellycine-build.sh"
        script.write_text("set -e\n" + configure + "\n", encoding="utf-8", newline="\n")
        print(f"Build {abi}", flush=True)
        with (work / f"{abi}.log").open("w", encoding="utf-8") as log:
            if not args.reuse_configure:
                subprocess.run([str(args.bash), script.name], cwd=build, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
            elif not (build / "config.h").exists() or f"--target={triple}26" not in (build / "ffbuild/config.mak").read_text():
                raise SystemExit(f"No matching completed configure for {abi}")
            # NDK make.exe is a native Windows executable; unlike Git Bash it
            # cannot resolve /e/... drive paths generated by configure.
            source_windows = source.as_posix()
            source_msys = "/" + source_windows[0].lower() + source_windows[2:]
            for generated in [build / "Makefile", build / "ffbuild/config.mak"]:
                generated.write_text(generated.read_text().replace(source_msys, source_windows), newline="\n")
            make_script = build / "jellycine-make.sh"
            make_script.write_text("set -e\n" + make_command + "\n", encoding="utf-8", newline="\n")
            subprocess.run([str(args.bash), make_script.name], cwd=build, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        output = HERE / "jni" / abi
        output.mkdir(parents=True, exist_ok=True)
        common = [str(toolbin / "clang.exe"), f"--target={triple}26", "-shared", "-fPIC", "-O2",
                  "-Wl,--no-undefined", "-Wl,-z,max-page-size=16384", "-Wl,--build-id=sha1"]
        # libmpv imports versioned LIBAVFILTER_12 symbols. Matching the soname
        # alone is insufficient: preserve FFmpeg's exact public symbol map.
        version_script = build / "libavfilter/libavfilter.ver"
        version_script.write_text((source / "libavfilter/libavfilter.v").read_text().replace("MAJOR", "12"))
        subprocess.run(common + ["-Wl,-soname,libavfilter.so", "-Wl,-Bsymbolic",
                       f"-Wl,--version-script,{version_script}", "-Wl,--whole-archive", str(build / "libavfilter/libavfilter.a"),
                       "-Wl,--no-whole-archive", f"-L{dependencies}", "-lavutil", "-lswresample", "-lm",
                       "-o", str(output / "libavfilter.so")], check=True, env=env)
        # JNI C++ wrappers need no C++ runtime here; the bridge uses only fixed
        # arrays and C library calls. Avoid packaging or exporting another STL.
        subprocess.run([str(toolbin / "clang++.exe"), f"--target={triple}26", "-shared", "-fPIC", "-O2",
                        "-fno-exceptions", "-fno-rtti", "-nostdlib++", "-Wl,--gc-sections",
                        "-Wl,--no-undefined", "-Wl,-z,max-page-size=16384",
                        "-Wl,-soname,libjellycine_mpv_audio.so", f"-I{work}", str(HERE / "mpv_audio_bridge.cpp"),
                        f"-L{dependencies}", "-lmpv", "-o", str(output / "libjellycine_mpv_audio.so")], check=True, env=env)
        subprocess.run([str(toolbin / "llvm-strip.exe"), "--strip-unneeded", str(output / "libavfilter.so"),
                        str(output / "libjellycine_mpv_audio.so")], check=True)
        original_filter = dependencies / "libavfilter.so"
        with zipfile.ZipFile(aar) as original:
            original_filter.write_bytes(original.read(f"jni/{abi}/libavfilter.so"))
        missing = exported_symbols(toolbin / "llvm-nm.exe", original_filter) - exported_symbols(
            toolbin / "llvm-nm.exe", output / "libavfilter.so")
        if missing:
            raise SystemExit(f"Lost original {abi} versioned filter exports: {sorted(missing)}")
        results[abi] = {p.name: {"sha256": sha256(p), "bytes": p.stat().st_size} for p in output.glob("*.so")}
    manifest = {
        "base_dependency": "org.jellycine.mpv:libmpv:1.0.4", "base_aar_sha256": AAR_SHA256,
        "ffmpeg": "9.0.1", "source_sha256": SOURCE_SHA256,
        "mpv_client_header_sha256": HEADER_SHA256, "ndk": args.ndk.name,
        "bridge_source_sha256": sha256(HERE / "mpv_audio_bridge.cpp"),
        "build_script_sha256": sha256(Path(__file__)),
        "filters": ["aformat", "anull", "aresample", "volume", "alimiter", "dynaudnorm", "astats"],
        "jni": results,
    }
    # Aggregate all finished outputs rather than merging partial process-local
    # manifests. This also repairs manifests after parallel per-ABI builds.
    manifest["jni"] = {
        abi: {p.name: {"sha256": sha256(p), "bytes": p.stat().st_size}
              for p in sorted((HERE / "jni" / abi).glob("*.so"))}
        for abi in ABIS if (HERE / "jni" / abi / "libavfilter.so").exists()
    }
    manifest_path = HERE / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("Built " + ", ".join(results), flush=True)


if __name__ == "__main__":
    main()
