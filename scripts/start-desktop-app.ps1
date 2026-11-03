param(
    [ValidateSet("coding", "graph", "assistant")]
    [string]$View = "coding",
    [int]$Width = 1600,
    [int]$Height = 980,
    [switch]$ForceRestart
)

$ErrorActionPreference = 'Stop'
$JarvisRoot = Split-Path $PSScriptRoot -Parent
$TargetUrl = "http://127.0.0.1:18080/#$View"
$ProfileDir = Join-Path $JarvisRoot "data\desktop-coding-profile"

if (-not (Test-Path -LiteralPath $ProfileDir)) {
    New-Item -ItemType Directory -Path $ProfileDir -Force | Out-Null
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "       JARVIS Desktop Studio (View: $View)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Health check backend on port 18080
$backendReady = $false
try {
    $conn = New-Object System.Net.Sockets.TcpClient("127.0.0.1", 18080)
    $conn.Close()
    $backendReady = $true
} catch {
    $backendReady = $false
}

if (-not $backendReady -or $ForceRestart) {
    Write-Host "[*] Launching JARVIS backend in background..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot/run.ps1`"" -WindowStyle Minimized
    
    Write-Host "[*] Waiting for backend to bind port 18080..." -ForegroundColor Yellow
    $retries = 30
    while ($retries -gt 0) {
        Start-Sleep -Seconds 1
        try {
            $conn = New-Object System.Net.Sockets.TcpClient("127.0.0.1", 18080)
            $conn.Close()
            $backendReady = $true
            break
        } catch {
            $retries--
        }
    }
    
    if (-not $backendReady) {
        Write-Warning "Backend did not respond in time. Proceeding with launch attempt..."
    } else {
        Write-Host "[+] JARVIS Backend online!" -ForegroundColor Green
    }
} else {
    Write-Host "[+] JARVIS Backend is already active on port 18080." -ForegroundColor Green
}

# 2. Locate Chromium binary
$browserCandidates = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles}\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "${env:LocalAppData}\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles}\BraveSoftware\Brave-Browser\Application\brave.exe"
)

$browserExe = $browserCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

if ($browserExe) {
    Write-Host "[+] Launching standalone Chromium App window: $browserExe" -ForegroundColor Cyan
    $appArgs = @(
        "--app=$TargetUrl",
        "--window-size=$Width,$Height",
        "--window-position=60,40",
        "--user-data-dir=$ProfileDir",
        "--disable-features=Translate,OptimizationHints",
        "--disable-component-update",
        "--enable-features=OverlayScrollbar"
    )
    Start-Process -FilePath $browserExe -ArgumentList $appArgs
} else {
    Write-Host "[!] Launching in default web browser..." -ForegroundColor Yellow
    Start-Process -FilePath $TargetUrl
}

Write-Host "[+] Desktop Application active." -ForegroundColor Green
