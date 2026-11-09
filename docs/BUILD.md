# 从源码构建

## 依赖

- Python 3.10+、Git、JDK 17。
- Android SDK Platform 35、Build Tools 35.0.0、NDK 28.2.13676358。
- Rust / rustup；固定 toolchain 为 1.93.0。
- Gradle Wrapper 固定 8.11.1；首次构建需联网下载依赖。

设置 `JAVA_HOME` 和 `ANDROID_HOME`。可通过 `ANDROID_NDK_HOME` 指定其他位置的同版本 NDK。Windows 需要 PowerShell；Linux 需要标准 C/C++ 构建工具。Windows 请使用纯英文构建路径，例如 `D:/Projects/gozero-android-miner`，避免 JVM 测试在中文路径下无法加载测试类。

```sh
rustup toolchain install 1.93.0
rustup target add --toolchain 1.93.0 aarch64-linux-android x86_64-linux-android
python tools/build.py native
python tools/build.py apk
```

`native` 默认构建 ARM64 与 x86_64 两种架构，产物放入 `app/src/main/jniLibs`。只构建 ARM64 可使用 `python tools/build.py native --abi arm64-v8a`；此时只使用 ARM64 APK，不要分发缺少核心的其他架构包。

`apk` 执行 Debug 构建、34 项 JVM 单元测试及 lint。APK 位于 `app/build/outputs/apk/debug/`。也可直接运行 `gradlew.bat`（Windows）或 `./gradlew`（Linux）任务。

## Release 签名

```sh
python tools/build.py apk --release
```

正式发布需在环境变量配置 `BNT_KEYSTORE`（你自己的 PKCS12 路径）与 `BNT_STORE_PASSWORD`，key alias 为 `gozero-bnt`，key password 与 store password 相同。未配置时 Gradle 只生成未签名 Release 包。原发布签名密钥不随源码提供；自行签名的 APK 不能覆盖安装官方签名版本。

构建脚本不设置代理，可按自己的网络环境配置 Gradle、Cargo。源码不包含本机 SDK 路径、设备调试记录或签名凭据。
