$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ngrok-weather-support.ps1')
$checks = 0

function Assert-Equal($Actual, $Expected, [string]$Label) {
    if ($Actual -ne $Expected) { throw "Failed: $Label" }
    $script:checks++
}

$fixtures = @{ tunnels = @(
    @{ public_url = 'http://assigned.ngrok-free.app'; config = @{ addr = 'http://127.0.0.1:18787' } },
    @{ public_url = 'https://wrong.ngrok-free.app'; config = @{ addr = 'http://127.0.0.1:8080' } },
    @{ public_url = 'https://assigned.ngrok-free.app'; config = @{ addr = 'http://127.0.0.1:18787' } }
) }
Assert-Equal (Select-NgrokWeatherUrl $fixtures '') 'https://assigned.ngrok-free.app' 'Choose HTTPS for weather gateway, never the full backend'
Assert-Equal (Select-NgrokWeatherUrl $fixtures 'https://assigned.ngrok-free.app') 'https://assigned.ngrok-free.app' 'Keep the previously assigned domain'
Assert-Equal (Select-NgrokWeatherUrl $fixtures 'https://another.ngrok-free.app') $null 'Reject an unexpected domain change'
Assert-Equal (Select-NgrokWeatherUrl @{ tunnels = @() } '') $null 'No endpoint before authentication'

$modern = @{ endpoints = @(
    @{ url = 'https://backend.ngrok-free.app'; upstream = @{ url = 'http://127.0.0.1:8080' } },
    @{ url = 'https://assigned.ngrok-free.app'; upstream = @{ url = 'http://127.0.0.1:18787' } }
) }
Assert-Equal (Select-NgrokWeatherUrl $modern '') 'https://assigned.ngrok-free.app' 'Read current endpoints API schema'
Assert-Equal (Select-NgrokWeatherUrl $modern 'https://assigned.ngrok-free.app') 'https://assigned.ngrok-free.app' 'Pin current endpoints API domain'
Assert-Equal (Select-NgrokWeatherUrl $modern 'https://another.ngrok-free.app') $null 'Do not change a pinned domain with the new schema'
Assert-Equal (Select-NgrokWeatherUrl @{ tunnels = @(@{ public_url = 'https://assigned.ngrok-free.app'; config = @{ addr = '127.0.0.1:18787' } }) } '') 'https://assigned.ngrok-free.app' 'Accept legacy normalized upstream syntax'

foreach ($invalid in @('http://assigned.ngrok-free.app', 'https://user:secret@assigned.ngrok-free.app', 'https://assigned.ngrok-free.app/api/weather/features', 'https://assigned.ngrok-free.app/?token=ignored', 'https://assigned.ngrok-free.app/#fragment')) {
    $rejected = $false
    try { [void](ConvertTo-NgrokBaseUrl $invalid) } catch { $rejected = $true }
    Assert-Equal $rejected $true 'Reject non-base URLs and embedded credentials'
}

$config = New-NgrokWeatherConfig 'offline-test-token-not-a-real-secret'
Assert-Equal ($config -match 'web_addr: "127.0.0.1:18788"') $true 'Bind management API to loopback'
Assert-Equal ($config -match 'version: "3"') $true 'Generate current config format'
Assert-Equal ($config -match 'authtoken: "offline-test-token-not-a-real-secret"') $true 'Encode token as a YAML quoted scalar'
$rejected = $false
try { [void](New-NgrokWeatherConfig "invalid token`nother") } catch { $rejected = $true }
Assert-Equal $rejected $true 'Do not accept multiline or command input as token'
Write-Output "ngrok offline checks: $checks passed. No account or network was used."
