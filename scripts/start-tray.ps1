# scripts/start-tray.ps1
# Starts the JARVIS Native Windows System Tray & Background Wake-Word Listener
[CmdletBinding()]
param(
    [int]$Port = 18080,
    [float]$Sensitivity = 0.50,
    [switch]$Dev,
    [switch]$Headless
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$venvPython = Join-Path $projectRoot ".venv\Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
    $pythonCmd = "python"
} else {
    $pythonCmd = $venvPython
}

# Clean up any previously running tray wakeword instances to prevent duplicate microphone listeners
Get-CimInstance Win32_Process | Where-Object { $_.Name -eq "python.exe" -and $_.CommandLine -like "*tray_wakeword.py*" } | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Milliseconds 300

$trayScript = Join-Path $projectRoot "scripts\tray_wakeword.py"

$argsList = @("`"$trayScript`"", "--port", $Port, "--sensitivity", $Sensitivity)
if ($Dev) { $argsList += "--dev" }
if ($Headless) { $argsList += "--headless" }

Write-Host "Starting JARVIS Background System Tray Wake-Word Listener..." -ForegroundColor Cyan
& $pythonCmd @argsList
