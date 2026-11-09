"""Build Gozero native cores and Android APKs without machine-specific paths."""
import argparse
import os
from pathlib import Path
import platform
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {"arm64-v8a": "aarch64-linux-android", "x86_64": "x86_64-linux-android"}

def run(args, cwd=ROOT, env=None):
    subprocess.run([str(x) for x in args], cwd=cwd, env=env, check=True)

def native(abis):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    ndk_override = os.environ.get("ANDROID_NDK_HOME")
    if not sdk and not ndk_override:
        raise SystemExit("Set ANDROID_HOME or ANDROID_NDK_HOME first")
    ndk = Path(ndk_override) if ndk_override else Path(sdk) / "ndk/28.2.13676358"
    host = {"Windows": "windows-x86_64", "Linux": "linux-x86_64", "Darwin": "darwin-x86_64"}[platform.system()]
    bin_dir = ndk / "toolchains/llvm/prebuilt" / host / "bin"
    windows = os.name == "nt"
    for abi in abis:
        target = TARGETS[abi]
        compiler = bin_dir / (target + "28-clang" + (".cmd" if windows else ""))
        if not compiler.is_file():
            raise SystemExit(f"Missing NDK compiler: {compiler}")
        env = os.environ.copy()
        env["PATH"] = str(bin_dir) + os.pathsep + env["PATH"]
        key = target.replace("-", "_")
        env["CARGO_TARGET_" + key.upper() + "_LINKER"] = str(compiler)
        env["CC_" + key] = str(compiler)
        env["AR_" + key] = str(bin_dir / ("llvm-ar.exe" if windows else "llvm-ar"))
        env["RUSTFLAGS"] = env.get("RUSTFLAGS", "") + " -C link-arg=-Wl,-z,max-page-size=16384"
        output = ROOT / "app/src/main/jniLibs" / abi
        output.mkdir(parents=True, exist_ok=True)
        for core in ("seine", "towerminer"):
            build_dir = ROOT / ".build/native" / core
            env["CARGO_TARGET_DIR"] = str(build_dir)
            args = ["cargo", "+1.93.0", "build", "--locked", "--release", "--target", target]
            args += ["--no-default-features", "--bin", "seine"] if core == "seine" else ["-p", "towerminer"]
            run(args, ROOT / "native" / core, env)
            dest = output / ("lib" + core + ".so")
            shutil.copy2(build_dir / target / "release" / core, dest)
            if core == "seine":
                run([bin_dir / ("llvm-strip.exe" if windows else "llvm-strip"), "--strip-debug", dest])

def apk(release):
    cores = ROOT / "app/src/main/jniLibs/arm64-v8a"
    if not all((cores / name).is_file() for name in ("libseine.so", "libtowerminer.so")):
        raise SystemExit("Build native cores first: python tools/build.py native")
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    tasks = [":app:assembleRelease"] if release else [":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug"]
    run([wrapper, "--no-daemon", *tasks])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    n = sub.add_parser("native")
    n.add_argument("--abi", action="append", choices=list(TARGETS))
    a = sub.add_parser("apk")
    a.add_argument("--release", action="store_true")
    args = parser.parse_args()
    if args.command == "native":
        native(args.abi or list(TARGETS))
    else:
        apk(args.release)

if __name__ == "__main__":
    main()
