@echo off
setlocal enabledelayedexpansion
title JARVIS Coding Agent - Desktop Studio

echo ========================================================
echo        JARVIS AI Coding Agent - Desktop Studio
echo        Autonomous Long-Running Development Agent
echo ========================================================
echo.

set TARGET_VIEW=coding
if not "%~1"=="" set TARGET_VIEW=%~1

set APP_URL=http://127.0.0.1:18080/#%TARGET_VIEW%
set PROFILE_DIR=%~dp0data\desktop-coding-profile

if not exist "%PROFILE_DIR%" (
    mkdir "%PROFILE_DIR%" >nul 2>&1
)

:: 1. Check if backend is reachable on port 18080
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "try { $c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 18080); $c.Close(); exit 0 } catch { exit 1 }"
if %ERRORLEVEL% neq 0 (
    echo [!] JARVIS Backend is not active on port 18080.
    echo [*] Starting JARVIS backend in background...
    start "JARVIS Backend" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run.ps1"
    
    echo [*] Waiting for backend to initialize...
    :wait_loop
    timeout /t 2 /nobreak >nul
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "try { $c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 18080); $c.Close(); exit 0 } catch { exit 1 }"
    if %ERRORLEVEL% neq 0 goto wait_loop
    echo [+] JARVIS backend is ready!
)

:: 2. Find Chromium-based browser (Edge, Chrome, or Brave)
set BROWSER_EXE=
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    set "BROWSER_EXE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
) else if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
    set "BROWSER_EXE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
) else if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    set "BROWSER_EXE=%ProgramFiles%\Google\Chrome\Application\chrome.exe"
) else if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
    set "BROWSER_EXE=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
) else if exist "%LocalAppData%\Google\Chrome\Application\chrome.exe" (
    set "BROWSER_EXE=%LocalAppData%\Google\Chrome\Application\chrome.exe"
) else if exist "%ProgramFiles%\BraveSoftware\Brave-Browser\Application\brave.exe" (
    set "BROWSER_EXE=%ProgramFiles%\BraveSoftware\Brave-Browser\Application\brave.exe"
)

if defined BROWSER_EXE (
    echo [+] Launching desktop application via Chromium engine:
    echo     !BROWSER_EXE!
    echo [*] Target URL: %APP_URL%
    
    start "" "!BROWSER_EXE!" ^
        --app="%APP_URL%" ^
        --window-size=1600,980 ^
        --window-position=60,40 ^
        --user-data-dir="%PROFILE_DIR%" ^
        --disable-features=Translate,OptimizationHints ^
        --disable-component-update
) else (
    echo [!] No dedicated Chromium engine binary found in default locations.
    echo [*] Launching in default web browser...
    start "" "%APP_URL%"
)

echo.
echo ========================================================
echo  Desktop Studio is running. Keep this session open for
echo  long-running autonomous tasks (Cursor / Claude Code mode).
echo ========================================================
timeout /t 5
