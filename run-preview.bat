@echo off
chcp 65001 >nul
title AM++ 布局预览启动器

echo ===================================================
echo           AM++ 平板布局实时预览启动器
echo ===================================================
echo.

cd /d "%~dp0"

echo [1/3] 加载便携版 Java 环境...

rem 优先使用项目自带的便携版 Windows JDK
for /d %%D in ("%~dp0.jdk\jdk-*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        goto :jdk_found
    )
)

rem 如果没有自带便携 JDK，检查系统环境
where java >nul 2>nul
if %errorlevel% neq 0 (
    if "%JAVA_HOME%"=="" (
        echo [错误] 未找到 Java 环境！
        echo 请确保安装了 JDK 17 或 JDK 21。
        echo.
        pause
        exit /b 1
    ) else (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    )
)

:jdk_found
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    echo [OK] 使用便携版 JDK: %JAVA_HOME%
)

echo Java 运行时检测通过：
java.exe -version
echo.

echo [2/3] 配置网络环境 (支持自定义 GRADLE_OPTS)...
rem 若在国内网络环境下需要配置代理，可在执行前设置 set GRADLE_OPTS=...
rem 示例: set "GRADLE_OPTS=-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dorg.gradle.vfs.watch=false"

echo.
echo [3/3] 正在启动布局预览应用 (首次运行需下载依赖，请稍候)...
echo ---------------------------------------------------
call gradlew.bat :layout-preview:run --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo ---------------------------------------------------
    echo [启动失败] 请查看上方控制台报错信息。
    echo 常见原因：
    echo 1. 无法连接外网下载依赖 (请检查代理端口 27890 是否正常)
) else (
    echo.
    echo [正常退出] 预览已关闭。
)

echo.
pause
