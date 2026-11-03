param (
    [switch]$Force,
    [switch]$StatusOnly
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$Workspace = Split-Path -Parent $ScriptDir

$cmd = Get-Command python -ErrorAction SilentlyContinue
if ($cmd) {
    $PythonExe = $cmd.Source
} else {
    $PythonExe = "python"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  JARVIS Realtime Research Model Autonomous Training      " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($StatusOnly) {
    & $PythonExe "$ScriptDir\train_facts_model.py" --status
    exit 0
}

Write-Host "[*] Executing autonomous model update..." -ForegroundColor Yellow
& $PythonExe "$ScriptDir\train_facts_model.py"

if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] Model jarvis-researcher:latest is fully trained and ready for inference." -ForegroundColor Green
} else {
    Write-Host "[!] Training encountered an issue. See logs above." -ForegroundColor Red
}
