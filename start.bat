@echo off
title JARVIS AI Assistant
echo ========================================================
echo               Starting J.A.R.V.I.S. AI
echo ========================================================
echo.
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run.ps1"
pause
