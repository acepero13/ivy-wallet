@echo off
REM Quick Build - Ivy Wallet Debug APK (no clean step)
REM Use this for faster builds when testing changes

echo ========================================
echo Quick Building Ivy Wallet Debug APK
echo ========================================
echo.

REM Navigate to project directory
cd /d "%~dp0"

REM Build debug APK (without cleaning)
echo Building APK (this should be faster)...
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
    echo File is ready to share!
    echo.
) else (
    echo.
    echo Failed to copy APK file!
)

pause
