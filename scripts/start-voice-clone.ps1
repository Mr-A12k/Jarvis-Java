# scripts/start-voice-clone.ps1
# Starts the JARVIS Local Neural Voice Cloning Server
[CmdletBinding()]
param(
    [int]$Port = 18088
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$venvPython = Join-Path $projectRoot ".venv\Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
    $pythonCmd = "python"
} else {
    $pythonCmd = $venvPython
}

# Clean up any previously running voice clone instances
Get-CimInstance Win32_Process | Where-Object { $_.Name -eq "python.exe" -and $_.CommandLine -like "*voice_clone_server.py*" } | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Milliseconds 300

$serverScript = Join-Path $projectRoot "scripts\voice_clone_server.py"

Write-Host "Starting JARVIS Voice Clone Server on port $Port..." -ForegroundColor Cyan
& $pythonCmd $serverScript --port $Port
