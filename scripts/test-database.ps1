param([switch]$Backend)
$ErrorActionPreference = 'Stop'
$JarvisRoot = Split-Path $PSScriptRoot -Parent
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
$testDir = Join-Path $JarvisRoot ('.tools/pg-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir | Out-Null
$passwordFile = Join-Path $testDir 'password.txt'
$previousPassword = $env:PGPASSWORD
$randomBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randomBytes)
$env:PGPASSWORD = [Convert]::ToBase64String($randomBytes)
Set-Content -LiteralPath $passwordFile -Value $env:PGPASSWORD
$started = $false
try {
    & "$pgBin/initdb.exe" -D "$testDir/data" -U jarvis_test -A scram-sha-256 --pwfile=$passwordFile --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Test database initialization failed' }
    $launcher = Start-Process -FilePath "$pgBin/pg_ctl.exe" -ArgumentList @('-D', "$testDir/data", '-l', "$testDir/server.log", '-o', '"-h 127.0.0.1 -p 15432"', '-w', 'start') -WindowStyle Hidden -PassThru
    $launcher.WaitForExit()
    if ($launcher.ExitCode -ne 0) { throw 'Test database startup failed' }
    $started = $true
    foreach ($file in @('001_tables_and_functions.sql', '002_smoke_test_rollback.sql')) {
        & "$pgBin/psql.exe" -X -w -h 127.0.0.1 -p 15432 -U jarvis_test -d postgres -v ON_ERROR_STOP=1 -f "$JarvisRoot/database/$file"
        if ($LASTEXITCODE -ne 0) { throw "SQL validation failed: $file" }
    }
    if ($Backend) {
        . "$PSScriptRoot/java.ps1"
        $previousTest = $env:JARVIS_DATABASE_TEST
        $env:JARVIS_DATABASE_TEST = 'true'
        Push-Location "$JarvisRoot/backend"
        try {
            & mvn.cmd -B -ntp "-Dmaven.repo.local=$JarvisRoot/.tools/m2" '-Dtest=LiveDatabaseTest,DatabaseApiTest' test
            if ($LASTEXITCODE -ne 0) { throw 'Backend database tests failed' }
        } finally { Pop-Location; $env:JARVIS_DATABASE_TEST = $previousTest }
    }
}
finally {
    if ($started) { & "$pgBin/pg_ctl.exe" -D "$testDir/data" -m fast -w stop }
    Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue
    $env:PGPASSWORD = $previousPassword
}
