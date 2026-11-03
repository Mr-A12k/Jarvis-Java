$ErrorActionPreference = 'Stop'
$JarvisRoot = Split-Path $PSScriptRoot -Parent
$JarvisOllama = 'D:\Tools\ollama\ollama.exe'
if (-not (Test-Path -LiteralPath $JarvisOllama)) {
    $JarvisInstalled = Get-Command ollama.exe -ErrorAction SilentlyContinue
    if (-not $JarvisInstalled) { throw 'Install Ollama from https://ollama.com/download/windows first.' }
    $JarvisOllama = $JarvisInstalled.Source
}
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_NUM_PARALLEL = '1'
$env:OLLAMA_MAX_LOADED_MODELS = '1'
$env:OLLAMA_CONTEXT_LENGTH = '2048'
$env:OLLAMA_NO_CLOUD = '1'
$env:LLAMA_ARG_CACHE_RAM = '0'
$JarvisSharedModels = [Environment]::GetEnvironmentVariable('OLLAMA_MODELS','User')
if ($JarvisSharedModels) { $env:OLLAMA_MODELS = $JarvisSharedModels }
Write-Host 'Starting local Ollama with one loaded model. Ctrl+C stops this runtime.'
& $JarvisOllama serve
if ($LASTEXITCODE -ne 0) { throw 'Ollama stopped with an error. Check whether another instance already owns port 11434.' }
