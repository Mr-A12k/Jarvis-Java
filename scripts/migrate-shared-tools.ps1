$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.tools'))
$sharedRoot = 'D:\Tools'
$jdkFolders = @(Get-ChildItem -LiteralPath "$sourceRoot/jdk21" -Directory)
if ($jdkFolders.Count -ne 1) { throw 'Expected one portable JDK. No changes made.' }
$moves = @(
    @{ Source=$jdkFolders[0].FullName; Target="$sharedRoot\jdk-21" },
    @{ Source="$sourceRoot\ollama"; Target="$sharedRoot\ollama" },
    @{ Source="$sourceRoot\models"; Target="$sharedRoot\ollama-models" }
)
foreach ($move in $moves) {
    $source = [IO.Path]::GetFullPath($move.Source)
    $target = [IO.Path]::GetFullPath($move.Target)
    if (-not $source.StartsWith($sourceRoot+'\',[StringComparison]::OrdinalIgnoreCase) -or
        -not $target.StartsWith($sharedRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected migration path.' }
    if (-not (Test-Path -LiteralPath $source) -or (Test-Path -LiteralPath $target)) { throw 'Source missing or destination exists. No migration performed.' }
}
# Stop only processes using the project-local runtimes that must be relocated.
Get-CimInstance Win32_Process | Where-Object {
    $_.ExecutablePath -and ($_.ExecutablePath.StartsWith($sourceRoot+'\jdk21\',[StringComparison]::OrdinalIgnoreCase) -or
    $_.ExecutablePath.StartsWith($sourceRoot+'\ollama\',[StringComparison]::OrdinalIgnoreCase))
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Path $sharedRoot -Force | Out-Null
foreach ($move in $moves) { Move-Item -LiteralPath $move.Source -Destination $move.Target }
[Environment]::SetEnvironmentVariable('JAVA_HOME',"$sharedRoot\jdk-21",'User')
[Environment]::SetEnvironmentVariable('OLLAMA_MODELS',"$sharedRoot\ollama-models",'User')
$oldPath = [Environment]::GetEnvironmentVariable('Path','User')
$entries = @("$sharedRoot\jdk-21\bin","$sharedRoot\ollama") + @($oldPath -split ';' | Where-Object { $_ -and $_ -notin @("$sharedRoot\jdk-21\bin","$sharedRoot\ollama") })
[Environment]::SetEnvironmentVariable('Path',($entries -join ';'),'User')
& "$sharedRoot\jdk-21\bin\java.exe" -version
if ($LASTEXITCODE -ne 0) { throw 'Shared JDK verification failed.' }
Write-Output 'Shared runtimes and existing models moved to D:\Tools. User JAVA_HOME, OLLAMA_MODELS and PATH configured.'
