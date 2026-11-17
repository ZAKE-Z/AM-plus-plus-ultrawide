# AM++ 布局可视化设计工程（Android Studio）

这是给 **Android Studio 布局设计器（Design / Split）** 用的独立单模块工程，
与 AM++ 模块本体（`:app`、`:host-applemusic` 等）无关，只用来拖动、调整布局。

## 为什么不能直接用主工程？

1. 主工程是多模块（10+ 个 Gradle 子工程），在 **Windows 映射的网络共享盘**上，Android Studio
   的 Gradle 同步会因为 `:backdrop`、`:layout-preview` 等目录判定失败而中断；
2. 共享名 `共享` 含中文，Java 侧会把路径解码成乱码，进一步加剧判断失败；
3. 真正决定界面的是运行期注入代码（`TabletArtworkLayoutPolicy`），本项目只是它的可视化等价物。

## 使用方式（本机磁盘）

1. 把本目录整体复制到本机磁盘，例如 `C:\AMLayoutEditor`（**不要**在网络盘上直接打开）；
2. 用 Android Studio 打开 `C:\AMLayoutEditor`；
3. 等 Gradle Sync 完成（首次会下载 AGP 依赖，需要代理）；
4. 打开 `app/src/main/res/layout/preview_tablet_player_v1.xml` 或 `preview_tablet_player_v2.xml`；
5. 右上角切到 **Design**，用鼠标拖拽封面、进度条、按钮组、分界线（Guideline），
   顶部可切换预览设备（XML 里已内置 `tools:device` 2560×1600 / 400dpi / 横屏）。

## 代理

`gradle.properties` 里默认使用本机 Clash 代理 `127.0.0.1:7897`。
如果代理端口不同，改成自己的；没有代理可删除 `systemProp.*` 四行。

`org.gradle.java.home` 指向 `C:/Program Files/Java/jdk-24`，如果本机没有该 JDK，
删除这一行，改用 Android Studio 自带的 Gradle JDK。

## 调整完成后

把调好的 XML 发给助手即可；助手会把坐标换算写回
`core/.../TabletArtworkLayoutPolicy.kt`（SongColumn 几何）并重新打包 APK。
