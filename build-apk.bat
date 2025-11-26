@echo off
REM Build Ivy Wallet Debug APK for sharing
REM This script builds a debug APK that can be shared and installed on any device

echo ========================================
echo Building Ivy Wallet Debug APK
echo ========================================
echo.

REM Navigate to project directory
cd /d "%~dp0"

REM Clean previous builds
echo Cleaning previous builds...
call gradlew.bat clean

REM Build debug APK
echo.
echo Building APK (this may take a few minutes)...
call gradlew.bat assembleDebug

REM Check if build was successful
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    pause
    exit /b 1
)

REM Copy APK to root directory with a clear name
echo.
echo Build successful! Copying APK...

REM Get current date for filename
for /f "tokens=2-4 delims=/ " %%a in ('date /t') do (set mydate=%%c-%%a-%%b)
set FILENAME=IvyWallet-Debug-%mydate%.apk

copy /Y "app\build\outputs\apk\debug\app-debug.apk" "%FILENAME%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS!
    echo ========================================
    echo.
    echo APK created: %FILENAME%
    echo Location: %~dp0%FILENAME%
    echo.
    echo You can now:
    echo 1. Send this APK file to your wife's phone via WhatsApp/Email/Drive
    echo 2. On her phone, enable "Install from unknown sources" in Settings
    echo 3. Open the APK file to install
    echo.
    echo Note: This is a debug APK, perfect for personal use between devices.
    echo Both phones must have the same version installed to sync properly.
    echo.
) else (
    echo.
    echo Failed to copy APK file!
)

pause
