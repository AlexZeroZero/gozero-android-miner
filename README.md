# Gozero Android Miner

Android 原生 BNT / JTM 挖矿应用，当前版本 **1.10**。

[下载安装包](https://github.com/AlexZeroZero/gozero-android-miner/releases/latest) · [构建说明](docs/BUILD.md)

## 功能

- 中文、English、Русский、日本語四种语言，在“配置 → 语言”即时切换并保存。
- BNT / JTM 切换，矿池、收款地址与线程配置分别保存。
- 实时算力、份额、线程数、核心内存、CPU 和电池温度显示。
- JTM 自适应线程与工作区，BNT 每线程约 2 GiB 内存。
- 后台通知与悬浮窗、温控暂停与恢复、深色 / 浅色主题、紧凑日志。
- 1% 开发费用。

## 安装与使用

从 Releases 下载 `Gozero-1.10-arm64-v8a.apk`，在手机上安装。最低 Android 9（API 28），正式安装包面向 ARM64 手机；真机验证设备为 realme GT5（RMX3823 / SM8550）。三星 S24 等其他设备的性能取决于芯片、散热及系统限制。

进入“配置”，选择币种，填写自己的公开收款地址、矿池和矿工名称，保存后启动。预填的地址是项目测试地址，请核对并换成你的地址；不要输入私钥或助记词。默认“仅充电时挖矿”需要接通电源。

CPU 温度读取依赖设备支持，可通过 USB 调试授予诊断权限：

```sh
adb shell pm grant trade.gozero.bnt android.permission.DUMP
```

## 源码结构

| 路径 | 内容 |
| --- | --- |
| `app/src/main/java/trade/gozero/bnt` | Android 界面、配置、服务与统计 |
| `app/src/test` | JVM 单元测试 |
| `app/src/androidTest` | Android 集成测试 |
| `native/seine` | BNT Seine 核心及 Gozero Android 修改 |
| `native/towerminer` | JTM towerminer 核心、验证器和 vendored 依赖 |
| `tools/build.py` | 可移植构建入口 |

## 版本验证

1.10 将四语言版本统一为新的版本号，功能与 1.1.0 相同，支持覆盖升级。1.1.0 通过 41 项 JVM 测试、Android Debug / Release 构建与 lint，并在 GT5 检查两个币种界面。新增四种界面语言；两个 native 核心与 1.0.0 保持一致。切换语言保留未保存的配置输入，运行中的核心继续工作。原始核心诊断日志和许可证保留原文。

上游组件的许可与版权声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。上游目录中的说明适用于对应原项目；本应用的使用及构建以本页和构建说明为准。
