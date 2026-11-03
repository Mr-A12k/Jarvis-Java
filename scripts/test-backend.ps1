param([string[]]$Tests)
$ErrorActionPreference = 'Stop'
$JarvisRoot = Split-Path $PSScriptRoot -Parent
. "$PSScriptRoot/java.ps1"

Push-Location (Join-Path $JarvisRoot 'backend')
try {
    $mvnArgs = @('-B', '-ntp', "-Dmaven.repo.local=$JarvisRoot/.tools/m2", 'test')
    if ($Tests -and $Tests.Length -gt 0) {
        $testFilter = $Tests -join ','
        $mvnArgs += "-Dtest=$testFilter"
    }
    & mvn.cmd @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
}
finally {
    Pop-Location
}
