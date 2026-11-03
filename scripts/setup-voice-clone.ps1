# scripts/setup-voice-clone.ps1
# Automated installer for JARVIS Local Neural Voice Cloning (100% Free & Open-Source)
[CmdletBinding()]
param(
    [switch]$ForceReinstall
)

$ErrorActionPreference = "Stop"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   JARVIS AI - Local Voice Cloning Setup (F5-TTS)         " -ForegroundColor Cyan
Write-Host "   100% Free, Open-Source & Private (Zero Cloud Telemetry)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $PSScriptRoot
$venvDir = Join-Path $projectRoot ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"

# 1. Ensure .venv exists
if (-not (Test-Path $venvPython)) {
    Write-Host "[1/4] Creating virtual environment at .venv..." -ForegroundColor Yellow
    python -m venv $venvDir
} else {
    Write-Host "[1/4] Virtual environment found at .venv" -ForegroundColor Green
}

# 2. Upgrade pip & wheel
Write-Host "[2/4] Ensuring latest pip and packaging tools..." -ForegroundColor Yellow
& $venvPython -m pip install --upgrade pip setuptools wheel --quiet

# 3. Install PyTorch with CUDA support if available
Write-Host "[3/4] Installing PyTorch, Torchaudio & Soundfile..." -ForegroundColor Yellow
& $venvPython -m pip install torch torchaudio soundfile

# 4. Install F5-TTS
Write-Host "[4/4] Installing F5-TTS Neural Voice Cloning Model..." -ForegroundColor Yellow
& $venvPython -m pip install f5-tts

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " Voice Cloning Engine Installed Successfully!            " -ForegroundColor Green
Write-Host " To start the service manually:                           " -ForegroundColor White
Write-Host "   powershell -File scripts/start-voice-clone.ps1        " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Green
