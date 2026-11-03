# Parse only the documented application keys. Never execute .env contents as shell code.
$JarvisEnvPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (Test-Path -LiteralPath $JarvisEnvPath) {
    foreach ($JarvisEnvLine in Get-Content -LiteralPath $JarvisEnvPath) {
        if ($JarvisEnvLine -match '^\s*(JARVIS_[A-Z_]+|OLLAMA_BASE_URL)\s*=(.*)$') {
            $JarvisEnvKey = $Matches[1]
            $JarvisEnvValue = $Matches[2].Trim()
            if ($JarvisEnvValue.Length -ge 2 -and (($JarvisEnvValue.StartsWith('"') -and $JarvisEnvValue.EndsWith('"')) -or ($JarvisEnvValue.StartsWith("'") -and $JarvisEnvValue.EndsWith("'")))) {
                $JarvisEnvValue = $JarvisEnvValue.Substring(1, $JarvisEnvValue.Length - 2)
            }
            if ($JarvisEnvValue.Length -gt 0) { [Environment]::SetEnvironmentVariable($JarvisEnvKey, $JarvisEnvValue, 'Process') }
        }
    }
    Remove-Variable JarvisEnvValue, JarvisEnvLine -ErrorAction SilentlyContinue
}
