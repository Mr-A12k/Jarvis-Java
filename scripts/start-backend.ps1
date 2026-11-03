param([int]$HeapMb = 512)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/java.ps1"
. "$PSScriptRoot/load-env.ps1"
if ($env:JARVIS_AUTH_MODE -eq 'token' -and -not $env:JARVIS_API_TOKEN) { throw 'Token mode requires JARVIS_API_TOKEN in .env.' }
if ($HeapMb -lt 128 -or $HeapMb -gt 2048) { throw 'HeapMb must be between 128 and 2048.' }
$JarvisJar = Join-Path $JarvisRoot 'backend/target/jarvis-backend-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $JarvisJar)) { throw 'Run ./scripts/build.ps1 first.' }

# Automatically terminate any stale backend process on port 18080 before binding
$conflicts = Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
if ($conflicts) {
    $pids = $conflicts | Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -gt 0 }
    foreach ($p in $pids) {
        Write-Host "Releasing port 18080 by stopping process PID $p..." -ForegroundColor Yellow
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Milliseconds 600
}

& $JarvisJava '-Xms64m' "-Xmx${HeapMb}m" '-XX:MaxDirectMemorySize=128m' -jar $JarvisJar
if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with an error.' }
  
