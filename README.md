<a id="top"></a>

<p align="center">
  <img src="docs/images/b851cbb7f571c6666f1a41377baa778b.jpg" alt="AM++ icon" width="180">
</p>

<h1 align="center">AM++ Ultrawide (超宽屏适配版)</h1>

<p align="center">
  简体中文 | <a href="README.en.md">English</a>
</p>

<p align="center">
  Apple Music 的 Android 增强模块（AM++）超宽屏适配版本：针对车机带状屏、CarPlay / Car+ 车载投屏量身定制。
</p>

<p align="center">
  <a href="https://github.com/ZAKE-Z/AM-plus-plus-ultrawide/blob/main/LICENSE"><img src="https://img.shields.io/github/license/ZAKE-Z/AM-plus-plus-ultrawide" alt="GNU GPL v3.0"></a>
  <img src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white" alt="Android API 26+">
  <img src="https://img.shields.io/badge/libxposed-API%20102-7F52FF" alt="libxposed API 102">
  <a href="https://github.com/Zennmn/AM-plus-plus"><img src="https://img.shields.io/badge/Upstream-Zennmn%2FAM--plus--plus-blue" alt="Upstream Repository"></a>
</p>

> [!NOTE]
> 本项目分叉自原版开源项目 [AM++ (Zennmn/AM-plus-plus)](https://github.com/Zennmn/AM-plus-plus)。核心目标在于解决 Apple Music 在汽车车机长条超宽屏、手机车联投屏（如 OPPO Car+）等超宽横屏场景下的界面比例失调与布局受限问题，重构横屏播放界面的三栏交互结构。

---

### 📌 适用场景与免责声明

> **使用说明与场景实测提示**：  
> 本项目主要针对车载横向超宽屏幕场景进行定制调整与体验优化。由于 Android 生态中各类设备的屏幕长宽比、系统 DPI 与物理分辨率繁多，本项目**尚未在各类比例、各类 DPI 及各类分辨率的屏幕设备上进行全面实测**。  
> 目前在作者个人的主力使用场景（**1920×720 分辨率，通过 OPPO Car+ 车联投屏**）下，整体界面比例、控件排布与视觉呈现效果表现良好。非常欢迎在其他类似车机、折叠屏或平板设备上尝试并反馈使用体验。

---

### 🌟 超宽屏定制特性

1. **Apple TV / CarPlay 风格经典三栏布局**：
   - **左侧**：大尺寸专辑封面独立完整展现，比例优雅协调。
   - **中间**：歌曲标题与艺术家居中呈现；播放进度条两端延伸；底层全幅展开的播控按钮（上一曲居左、播放/暂停居中、下一曲居右）；精简无背景的辅助控制组（随机、循环、喜爱收藏、更多选项）。针对车载触控场景，移除冗余的屏幕音量条（直接由车机物理旋钮/按键接管）。
   - **右侧**：歌词与待播列表展示区域。
2. **双版本构建方案（Product Flavors）**：
   - **V1（经典独立歌词版）**：左封面、中播控、右侧固定显示实时滚动歌词。
   - **V2（歌词 / 待播列表快速切换版）**：播控区底部集成极简图标切换栏（[翻译]、[歌词]、[播放列表]），轻触即可在右侧面板随时切换实时歌词与待播队列，无需占用中栏或挤压封面。
3. **沉浸式横屏全屏**：展开横屏全屏播放器时，自动隐藏顶部状态栏与底部系统导航栏，充分利用超宽屏的所有有效像素。
4. **高度基准视口归一化**：以标准 720 高度为参考基准统一计算布局几何与字体比例，有效防止因非标车机 DPI 设置导致的进度时间文字折行与裁切。

---

<p align="center">
  没有lsposed和root？不妨试试<a href="https://github.com/Zennmn/AM-plus-plus/releases/tag/embedded">npatch 嵌入版</a>
</p>

<details>
<summary>目录</summary>

1. [项目简介](#项目简介)
2. [功能](#功能)
3. [效果展示](#效果展示)
4. [安装](#安装)
5. [使用](#使用)
6. [从源码构建](#从源码构建)
7. [项目结构](#项目结构)
8. [路线图](#路线图)
9. [贡献](#贡献)
10. [隐私与权限](#隐私与权限)
11. [许可证与致谢](#许可证与致谢)
12. [Star History](#star-history)
13. [支持项目](#支持项目)

</details>

## 项目简介

安装后，在 Apple Music → 设置 → **AM++ 模块设置** 中管理功能。

支持导入 ZIP 插件扩展功能，详见 [插件开发手册](docs/plugin-development.md)。

## 功能

| 功能 | 默认 | 说明 |
| --- | --- | --- |
| 平板双栏播放器 | 开启 | 横屏时并排显示播放控件与实时歌词。 |
| 强制显示蜂窝数据入口 | 关闭 | 在设置中显示原生蜂窝数据选项。 |
| 双向歌词模糊 | 开启 | 当前歌词保持清晰，前后歌词渐变模糊，支持调节强度（Android 12+）。 |
| CJK 长尾歌词动画 | 开启 | 为中日韩歌词加入辉光动画。 |
| 歌曲名显示修正 | 关闭 | 按歌曲原地区、中国大陆或日本显示歌曲名与相关信息。 |
| 自定义歌词 | 关闭 | 导入、编辑 TTML 歌词，支持在线获取。 |
| 自动实时补全 | 开启 | 启用自定义歌词后，为缺词、非逐字或缺翻译的歌曲自动查找歌词。 |
| 歌词字体 | 关闭 | 导入 TTF/OTF 字体，随时恢复原字体。 |
| 液态玻璃底栏 | 关闭 | 为导航栏和迷你播放器加入液态玻璃效果（Android 13+）。 |
| ios底栏动画 | 开启 | 手机端随启用ios同款底栏动画，实时生效。 |

## 效果展示

### 自定义歌词注入

<p align="center">
  <img src="docs/images/bf32d15a3519cef0051d8a208b58ab42.jpg" alt="自定义歌词注入示例一" width="48%">
  <img src="docs/images/918d640313475d68cf66fff0e63f4e19.jpg" alt="自定义歌词注入示例二" width="48%">
</p>

### 字体替换

<p align="center">
  <img src="docs/images/537369a74adc232f855165263c9ff1cc.jpg" alt="MiSans 字体替换前后对比" width="900">
</p>

### 平板双栏播放器与歌词模糊

<p align="center">
  <img src="docs/images/tablet-dual-pane-player-open-source-blur.jpg" alt="平板横屏双栏播放器与歌词模糊" width="900">
</p>

### 液态玻璃底栏

<p align="center">
  <img src="docs/images/liquid-glass-demo.gif" alt="液态玻璃底栏与迷你播放器动画演示" width="360">
</p>

## 安装

### Xposed 模块（推荐）

需要 Apple Music 和支持 libxposed API 102 的 Xposed 框架。

1. 从 [Releases](https://github.com/Zennmn/AM-plus-plus/releases/latest) 下载并安装 AM++ APK。
2. 在 LSPosed 或兼容的 Xposed 管理器中启用 **AM++**。
3. 作用域只勾选 Apple Music（`com.apple.android.music`）。
4. 强制停止并重新打开 Apple Music。
5. 打开 Apple Music → 设置 → “AM++ 模块设置”。

### npatch 嵌入版

没有lsposed和root？不妨试试[npatch 嵌入版](https://github.com/Zennmn/AM-plus-plus/releases/tag/embedded)，无法与官方版共存。

安装包文件名包含 AM++ 版本、Apple Music 版本和构建号，按需选择即可。

## 使用

部分设置需强制停止并重新打开 Apple Music 后生效。

### 自定义歌词

1. 打开“自定义歌词”，启用“自定义歌词替换”。
2. 点“获取 ID”读取当前歌曲，或手动填写 Apple Music ID。
3. 粘贴或导入 TTML，也可从 AMLL、AM-Lyrics、Lunabeat 在线获取。
4. 保存并启用该歌曲的歌词。
5. 强制停止并重新打开 Apple Music。

歌词支持搜索、编辑、删除和 ZIP 备份恢复；恢复时可选择覆盖或保留已有歌词。手动编写 TTML 可参考 [格式说明](docs/apple-music-ttml-format.md)。

### 歌词字体

1. 在“歌词字体”中选择 TTF 或 OTF 文件。
2. 导入完成后，强制停止并重新打开 Apple Music。
3. 点“恢复原字体”并重开 Apple Music 即可还原。

字体仅应用于播放器歌词。

### 液态玻璃底栏

打开“液态玻璃底栏”，强制停止并重新打开 Apple Music，即可为导航栏和迷你播放器应用玻璃效果。

可调节背景模糊强度；手机端还可开启“ios底栏动画”，让底栏随页面滚动自动收起、展开。

### 歌曲名显示修正

打开“歌曲名显示修正”，选择地区模式后重开 Apple Music。关闭时跟随 Apple Music 账号地区。

## 从源码构建

环境：JDK 21、Android SDK 37、Build Tools 37.0.0，使用项目自带的 Gradle Wrapper。

Windows：

```powershell
.\gradlew.bat test :app:lintDebug :app:lintVitalRelease :app:assembleRelease
```

Linux 或 macOS：

```bash
chmod +x gradlew
./gradlew test :app:lintDebug :app:lintVitalRelease :app:assembleRelease
```

生成的 Release APK 位于：

```text
app/build/outputs/apk/release/app-release.apk
```

构建 Release 前，将 `keystore.properties.example` 复制为 `keystore.properties` 并填写签名信息；无需签名配置可构建 Debug APK。

## 项目结构

```text
app/src/main/java/        模块入口、设置页与功能装配
app/src/main/resources/   libxposed 模块元数据
core/                     配置、歌词、网络来源与布局策略
host-api/                 宿主能力与事件接口
hook-runtime/             Xposed 封装与日志
host-applemusic/           Apple Music 版本适配
glass/                    液态玻璃渲染
backdrop/                 上游 Backdrop 库
docs/                     使用与开发文档
scripts/                  开发辅助脚本
```

## 路线图

- [x] 平板横屏双栏播放器
- [x] 双向歌词模糊
- [x] 自定义歌词注入与备份恢复
- [x] 歌词字体导入与恢复
- [x] 液态玻璃底栏与迷你播放器
- [x] 歌曲名显示修正
- [ ] 持续适配后续 Apple Music 版本

## 贡献

欢迎提交 Issue 和 Pull Request。代码改动请通过测试与构建；反馈界面问题时，请附设备型号、系统及 Apple Music 版本、截图或录屏。

开发与适配请参阅 [文档目录](docs/README.md) 和 [宿主适配手册](docs/host-adaptation-guide.md)。

## 隐私与权限

- 仅声明 `INTERNET` 权限，用于歌词获取、翻译补全和歌曲信息查询。
- 不申请存储或通知运行时权限，本地文件通过系统文件选择器导入。
- 不含分析服务，也没有独立启动图标。
- 设置、歌词与字体保存在 Apple Music 私有目录。

## 许可证与致谢

本项目以 [GNU General Public License v3.0](LICENSE) 开源。第三方许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

- [AMLyricBlur](https://github.com/a23bc/amlyricblur)：歌词模糊。
- [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)：液态玻璃效果与交互。

## Star History

<p align="center">
  <a href="https://www.star-history.com/?repos=Zennmn%2FAM-plus-plus&amp;type=date">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Zennmn/AM-plus-plus&amp;type=date&amp;theme=dark">
      <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Zennmn/AM-plus-plus&amp;type=date">
      <img src="https://api.star-history.com/chart?repos=Zennmn/AM-plus-plus&amp;type=date" alt="AM++ 的 GitHub Star 历史曲线" width="800">
    </picture>
  </a>
</p>

## 支持项目

AM++ 免费开源。如果这个项目对你有所帮助，欢迎请作者喝杯咖啡。赞赏完全自愿，不影响任何功能的使用。

<p align="center">
  <a href="docs/images/wechat-appreciation.png">
    <img src="docs/images/wechat-appreciation.png" alt="Zen 的微信赞赏码" width="360">
  </a>
</p>

<p align="right">(<a href="#top">返回顶部</a>)</p>
