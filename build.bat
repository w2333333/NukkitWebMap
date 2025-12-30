@echo off
echo === NukkitWebMap Build ===

set GRADLE_CMD=

for /d %%i in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.5-bin\*") do (
    if exist "%%i\gradle-8.5\bin\gradle.bat" set "GRADLE_CMD=%%i\gradle-8.5\bin\gradle.bat"
)

if "%GRADLE_CMD%"=="" (
    where gradle >nul 2>&1
    if %errorlevel% equ 0 set GRADLE_CMD=gradle
)

if "%GRADLE_CMD%"=="" (
    echo ERROR: Gradle not found
    echo Run: gradle build
    pause
    exit /b 1
)

echo Using: %GRADLE_CMD%
call "%GRADLE_CMD%" build --no-daemon

if exist build\libs\NukkitWebMap-1.0.0.jar (
    echo.
    echo SUCCESS!
    echo Output: build\libs\NukkitWebMap-1.0.0.jar
) else (
    echo BUILD FAILED
)
pause
