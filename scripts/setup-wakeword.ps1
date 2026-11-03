# scripts/setup-wakeword.ps1
# Automated installer for JARVIS Native System Tray & Background Wake-Word Engine
[CmdletBinding()]
param(
    [switch]$ForceReinstall
)

$ErrorActionPreference = "Stop"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   JARVIS AI - Background Wake-Word Engine Setup          " -ForegroundColor Cyan
Write-Host "   openWakeWord, Silero VAD, PyStray Native Immersion    " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $PSScriptRoot
$venvDir = Join-Path $projectRoot ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"

# 1. Ensure .venv exists
if (-not (Test-Path $venvPython)) {
    Write-Host "[1/3] Creating virtual environment at .venv..." -ForegroundColor Yellow
    python -m venv $venvDir
} else {
    Write-Host "[1/3] Virtual environment found at .venv" -ForegroundColor Green
}

# 2. Install Tray and Audio packages
Write-Host "[2/3] Installing System Tray & Audio capture libraries (pystray, pillow, sounddevice, numpy)..." -ForegroundColor Yellow
& $venvPython -m pip install pystray pillow sounddevice numpy requests

# 3. Install Neural Wake-Word Engine (openWakeWord, ONNXRuntime)
Write-Host "[3/3] Installing Neural Wake-Word & Silero VAD models (openwakeword, onnxruntime)..." -ForegroundColor Yellow
& $venvPython -m pip install openwakeword onnxruntime

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " Background Wake-Word Engine Setup Complete!             " -ForegroundColor Green
Write-Host " To start the tray listener:                             " -ForegroundColor White
Write-Host "   powershell -File scripts/start-tray.ps1               " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Green
