$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/load-env.ps1"
$headers = @{ 'X-Jarvis-Local' = '1' }
if ($env:JARVIS_AUTH_MODE -eq 'token') { $headers.Authorization = "Bearer $env:JARVIS_API_TOKEN" }
$base = 'http://127.0.0.1:18080/api/v1'
$sample = Join-Path (Split-Path $PSScriptRoot -Parent) 'samples/file-check.md'
function Send-JarvisTest($route, $body) {
    Invoke-RestMethod "$base/$route" -Headers $headers -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress) -TimeoutSec 180
}
$original = Invoke-RestMethod "$base/files/read?path=$([uri]::EscapeDataString($sample))" -Headers $headers
$proposal = Send-JarvisTest 'files/propose' @{ path=$sample; hash=$original.hash; content=($original.content + "`nVerification marker.") }
$before = Invoke-RestMethod "$base/files/read?path=$([uri]::EscapeDataString($sample))" -Headers $headers
if ($before.hash -ne $original.hash) { throw 'Proposal changed the source before apply.' }
try {
    $edited = Send-JarvisTest 'files/apply' @{ id=$proposal.id }
    if ($edited.content -notmatch 'Verification marker') { throw 'Edit was not applied.' }
} finally {
    $current = Invoke-RestMethod "$base/files/read?path=$([uri]::EscapeDataString($sample))" -Headers $headers
    if ($current.hash -ne $original.hash) {
        $restore = Send-JarvisTest 'files/propose' @{ path=$sample; hash=$current.hash; content=$original.content }
        $restored = Send-JarvisTest 'files/apply' @{ id=$restore.id }
        if ($restored.hash -ne $original.hash) { throw 'Sample restoration failed.' }
    }
}
Write-Output 'Reviewed edit and exact sample restoration: PASS'
$memory = Invoke-RestMethod "$base/files/memory" -Headers $headers
if (-not $memory.notes -or $memory.paths.Count -lt 1) { throw 'Expected saved memory and sample index.' }
Write-Output 'Persistent memory after restart: PASS'
$web = Invoke-WebRequest "$base/web/stream" -Headers $headers -Method Post -ContentType 'application/json' -Body '{"message":"PostgreSQL official documentation"}' -TimeoutSec 180
$result = [string]$web.Content
if ($result -match 'event:error' -or $result -notmatch 'event:sources' -or $result -notmatch 'event:done') { throw 'Web answer did not complete. Check provider/runtime availability.' }
Write-Output 'Live web search, source events and local answer: PASS'
