param([switch]$Live, [switch]$BackendOnly)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/java.ps1"
$JarvisPreviousLive = $env:JARVIS_LIVE_TEST
if ($Live) { $env:JARVIS_LIVE_TEST = 'true' }
if (-not $BackendOnly -and (Test-Path (Join-Path $JarvisRoot 'frontend/package.json'))) {
    Push-Location (Join-Path $JarvisRoot 'frontend')
    try {
        if (-not (Test-Path 'node_modules')) {
            & npm.cmd ci --no-fund --no-audit
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependencies could not be installed.' }
        }
        & npm.cmd test
        if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    }
    finally { Pop-Location }
}
Push-Location (Join-Path $JarvisRoot 'backend')
try {
    & mvn.cmd -B -ntp "-Dmaven.repo.local=$JarvisRoot/.tools/m2" verify
    if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed. Review the Maven output.' }
}
finally {
    Pop-Location
    $env:JARVIS_LIVE_TEST = $JarvisPreviousLive
}
