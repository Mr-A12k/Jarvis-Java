# Dot-source this file so environment variables remain available in the current shell.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/load-env.ps1"
if (-not $env:JARVIS_API_TOKEN) {
    $JarvisTokenBytes = New-Object byte[] 32
    $JarvisRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $JarvisRng.GetBytes($JarvisTokenBytes) } finally { $JarvisRng.Dispose() }
    $env:JARVIS_API_TOKEN = [Convert]::ToBase64String($JarvisTokenBytes)
}
if ($env:JARVIS_API_TOKEN.Length -lt 32) { throw 'JARVIS_API_TOKEN must contain at least 32 characters.' }
if (-not $env:JARVIS_CHAT_MODEL) { $env:JARVIS_CHAT_MODEL = 'qwen3:1.7b' }
Write-Host 'JARVIS session configured. Token stays in this shell and is not printed or saved.'
