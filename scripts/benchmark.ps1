param([string]$BaseUrl = 'http://127.0.0.1:11434', [string]$Model = 'qwen3:1.7b', [int]$Runs = 3)
$ErrorActionPreference = 'Stop'
$JarvisUri = [Uri]$BaseUrl
if ($JarvisUri.Scheme -ne 'http' -or $JarvisUri.Host -notin @('localhost', '127.0.0.1', '[::1]')) { throw 'Benchmark only permits a loopback Ollama endpoint.' }
if ($Runs -lt 1 -or $Runs -gt 10) { throw 'Runs must be between 1 and 10.' }
$JarvisTags = Invoke-RestMethod "$BaseUrl/api/tags" -TimeoutSec 5
if ($Model -notin $JarvisTags.models.name) { throw "Install the model first: ollama pull $Model" }
for ($JarvisIndex = 1; $JarvisIndex -le $Runs; $JarvisIndex++) {
    $JarvisBody = @{
        model = $Model; stream = $false; think = $false; keep_alive = '30s'
        messages = @(@{role = 'user'; content = 'In two sentences, explain what retrieval augmented generation does.' })
        options = @{num_ctx = 2048; num_predict = 128; temperature = 0 }
    } | ConvertTo-Json -Depth 5
    $JarvisWatch = [Diagnostics.Stopwatch]::StartNew()
    $JarvisResult = Invoke-RestMethod "$BaseUrl/api/chat" -Method Post -ContentType 'application/json' -Body $JarvisBody -TimeoutSec 180
    $JarvisWatch.Stop()
    if (-not $JarvisResult.message.content) { throw 'Ollama returned no answer. Benchmark failed.' }
    [pscustomobject]@{
        Run = $JarvisIndex; Model = $Model; WallMs = $JarvisWatch.ElapsedMilliseconds
        LoadMs = [math]::Round($JarvisResult.load_duration / 1e6)
        OutputTokens = $JarvisResult.eval_count
        TokensPerSecond = if ($JarvisResult.eval_duration -gt 0) { [math]::Round($JarvisResult.eval_count / ($JarvisResult.eval_duration / 1e9), 2) } else { $null }
    }
}
# Ollama reports actual processor offload and model residency here; do not infer it from file size.
Invoke-RestMethod "$BaseUrl/api/ps" -TimeoutSec 5 | Select-Object -ExpandProperty models | Select-Object name, size, size_vram
