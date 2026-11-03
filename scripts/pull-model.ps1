param([string]$Model = 'qwen3:1.7b')
$ErrorActionPreference = 'Stop'
if ($Model -notmatch '^[a-zA-Z0-9._:/-]{1,120}$' -or $Model -match 'cloud') { throw 'Provide a local Ollama model tag.' }
Write-Host "Downloading $Model into the model directory of the running Ollama service."
$JarvisPullBody = @{model = $Model; stream = $false } | ConvertTo-Json
$JarvisPullResult = Invoke-RestMethod http://127.0.0.1:11434/api/pull -Method Post -ContentType 'application/json' -Body $JarvisPullBody -TimeoutSec 1800
if ($JarvisPullResult.status -ne 'success') { throw 'Ollama did not confirm model installation.' }
Write-Host 'Model installed successfully.'
