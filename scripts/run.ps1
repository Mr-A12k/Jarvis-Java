param([switch]$Build, [int]$HeapMb = 512)
$ErrorActionPreference = 'Stop'
$JarvisRoot = Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/java.ps1"

# 1. Stop any running backend process on port 18080 to prevent file lock
$running = Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
if ($running) {
    $pids = $running | Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -gt 0 }
    foreach ($p in $pids) {
        Write-Host "Stopping existing backend process (PID: $p)..." -ForegroundColor Yellow
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 600
}

# 2. Rebuild if requested or if jar doesn't exist
$jar = Join-Path $JarvisRoot 'backend/target/jarvis-backend-0.1.0-SNAPSHOT.jar'
if ($Build -or (-not (Test-Path -LiteralPath $jar))) {
    Write-Host "Packaging latest JAR..." -ForegroundColor Cyan
    Push-Location (Join-Path $JarvisRoot 'backend')
    try {
        & mvn.cmd -B -ntp "-Dmaven.repo.local=$JarvisRoot/.tools/m2" package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Maven package failed.' }
    } finally {
        Pop-Location
    }
}

# 3. Check Ollama
$ollamaListening = Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue
if (-not $ollamaListening) {
    Write-Host "Notice: Ollama is not detected on port 11434. Starting Ollama in background or launch it manually..." -ForegroundColor Yellow
}

# 3b. Check Local Voice Clone Engine (port 18088)
$voiceCloneListening = Get-NetTCPConnection -LocalPort 18088 -State Listen -ErrorAction SilentlyContinue
if (-not $voiceCloneListening) {
    Write-Host "Starting Local Voice Clone Engine on http://127.0.0.1:18088 ..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot/start-voice-clone.ps1`"" -WindowStyle Hidden
}

# 3c. Check Background System Tray Wake-Word Listener
$trayRunning = Get-CimInstance Win32_Process -Filter "CommandLine LIKE '%tray_wakeword.py%'" -ErrorAction SilentlyContinue
if (-not $trayRunning) {
    Write-Host "Starting Background System Tray Wake-Word Listener ('Hey Jarvis')..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot/start-tray.ps1`"" -WindowStyle Hidden
}

# 4. Launch backend
Write-Host "Starting JARVIS Assistant on http://127.0.0.1:18080 ..." -ForegroundColor Green
& powershell -File "$PSScriptRoot/start-backend.ps1" -HeapMb $HeapMb
