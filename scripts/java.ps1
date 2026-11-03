$ErrorActionPreference = 'Stop'
$JarvisRoot = Split-Path $PSScriptRoot -Parent
if (Test-Path -LiteralPath 'D:\Tools\jdk-21\bin\java.exe') { $env:JAVA_HOME = 'D:\Tools\jdk-21' }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Install JDK 21 and set JAVA_HOME to its installation folder.'
}
$JarvisJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$prevEA = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$JarvisVersion = (& $JarvisJava -version 2>&1 | Out-String)
$ErrorActionPreference = $prevEA
if ($JarvisVersion -notmatch 'version "21\.') { throw 'JARVIS requires Java 21. Your selected JAVA_HOME is another version.' }
