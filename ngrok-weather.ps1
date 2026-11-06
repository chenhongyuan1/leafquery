# Expose only the local weather gateway through the account's fixed ngrok dev domain.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('setup', 'start', 'status', 'stop', 'restart-gateway')]
    [string]$Action = 'start',
    [switch]$NoOpen
)

$ErrorActionPreference = 'Stop'
$Runtime = Join-Path $PSScriptRoot '.leafquery-runtime\weather-tunnel'
$StateFile = Join-Path $Runtime 'processes.json'
$ConfigFile = Join-Path $Runtime 'ngrok.yml'
$DomainFile = Join-Path $Runtime 'ngrok-domain.txt'
$ConnectionFile = Join-Path $Runtime 'dify-connection.txt'
$PublicUrlFile = Join-Path $Runtime 'public-url.txt'
$GatewayScript = Join-Path $PSScriptRoot 'scripts\weather-gateway.mjs'
$NgrokApi = 'http://127.0.0.1:18788/api'
$Utf8 = New-Object Text.UTF8Encoding($false)
. (Join-Path $PSScriptRoot 'scripts\ngrok-weather-support.ps1')

function Read-State {
    if (Test-Path -LiteralPath $StateFile) { return Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json }
    return $null
}
function Save-State($State) { [IO.File]::WriteAllText($StateFile, ($State | ConvertTo-Json -Depth 5), $Utf8) }
function Get-ManagedProcess($Entry) {
    if (-not $Entry) { return $null }
    $managed = Get-Process -Id $Entry.ProcessId -ErrorAction SilentlyContinue
    if ($managed -and $managed.StartTime.ToUniversalTime().Ticks.ToString() -eq $Entry.StartTicks) { return $managed }
    return $null
}
function Process-Entry($Child) {
    $Child.Refresh()
    return @{ ProcessId = $Child.Id; StartTicks = $Child.StartTime.ToUniversalTime().Ticks.ToString() }
}
function Stop-Entry($Entry) {
    $managed = Get-ManagedProcess $Entry
    if ($managed) { Stop-Process -Id $managed.Id -ErrorAction SilentlyContinue; [void]$managed.WaitForExit(2000) }
}
function Test-LocalPort([int]$Port) {
    $probe = New-Object Net.Sockets.TcpClient
    try { return ($probe.ConnectAsync('127.0.0.1', $Port).Wait(500) -and $probe.Connected) }
    catch { return $false } finally { $probe.Dispose() }
}
function Ensure-Ngrok {
    $tools = Join-Path $Runtime 'tools'
    $binary = Join-Path $tools 'ngrok.exe'
    if (-not (Test-Path -LiteralPath $binary)) {
        [void][IO.Directory]::CreateDirectory($tools)
        $zip = Join-Path $tools 'ngrok.zip'
        Write-Host '[INFO] Downloading the official ngrok Windows x64 client.'
        $curl = (Get-Command curl.exe -ErrorAction Stop).Source
        & $curl --fail --location --silent --show-error --max-time 180 --output $zip 'https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-windows-amd64.zip'
        if ($LASTEXITCODE -ne 0) { throw 'Official ngrok download failed.' }
        Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
    }
    $securityManifest = Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1'
    Import-Module -Name $securityManifest -ErrorAction Stop
    $signature = Get-AuthenticodeSignature -LiteralPath $binary
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'ngrok, Inc\.') {
        throw 'ngrok must have a valid official ngrok, Inc. digital signature.'
    }
    return $binary
}
function Save-Token([string]$Token) {
    $yaml = New-NgrokWeatherConfig $Token
    [void][IO.Directory]::CreateDirectory($Runtime)
    [IO.File]::WriteAllText($ConfigFile, $yaml, $Utf8)
}
function Setup-Ngrok {
    Write-Host '[1/2] Sign in to ngrok and copy the Authtoken value from this page:'
    Write-Host 'https://dashboard.ngrok.com/get-started/your-authtoken'
    Write-Host '[INFO] This token authenticates your local client. Dify headers remain empty.'
    if ($env:NGROK_AUTHTOKEN) { Save-Token $env:NGROK_AUTHTOKEN }
    else {
        if (-not $NoOpen) { Start-Process 'https://dashboard.ngrok.com/get-started/your-authtoken' }
        $secureToken = Read-Host '[2/2] Paste Authtoken (hidden; press Enter)' -AsSecureString
        $pointer = [IntPtr]::Zero
        try {
            $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
            $plainToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
            Save-Token $plainToken
        } finally {
            $plainToken = $null
            if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
            $secureToken.Dispose()
        }
    }
    Write-Host '[OK] Token saved in the ignored project runtime directory; value hidden.'
}
function Read-ExpectedDomain {
    if (Test-Path -LiteralPath $DomainFile) { return ConvertTo-NgrokBaseUrl ([IO.File]::ReadAllText($DomainFile).Trim()) }
    return ''
}
function Get-ActiveUrl([string]$Expected) {
    foreach ($resource in @('endpoints', 'tunnels')) {
        try {
            $result = Invoke-RestMethod -Uri "$NgrokApi/$resource" -TimeoutSec 1
            $url = Select-NgrokWeatherUrl $result $Expected
            if ($url) { return $url }
        } catch { }
    }
    return $null
}
function Start-Gateway {
    if (Test-LocalPort 18787) { throw 'Weather gateway port 18787 is occupied by an unmanaged process.' }
    $node = (Get-Command node.exe -ErrorAction Stop).Source
    $child = Start-Process -FilePath $node -ArgumentList @(('"' + $GatewayScript + '"')) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $Runtime 'gateway.out.log') -RedirectStandardError (Join-Path $Runtime 'gateway.err.log')
    $entry = Process-Entry $child
    try {
        for ($i = 0; $i -lt 20; $i++) {
            if (-not (Get-ManagedProcess $entry)) { throw 'Weather gateway exited. Check gateway.err.log.' }
            try { Invoke-WebRequest -Uri 'http://127.0.0.1:18787/api/weather/features' -UseBasicParsing -TimeoutSec 1 | Out-Null }
            catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 400) { return $entry } }
            Start-Sleep -Milliseconds 250
        }
        throw 'Weather gateway did not become ready.'
    } catch { Stop-Entry $entry; throw }
}
function Show-Connection([string]$BaseUrl) {
    $base = ConvertTo-NgrokBaseUrl $BaseUrl
    $endpoint = "$base/api/weather/features"
    $details = @(
        'LeafQuery - Dify HTTP node configuration (ngrok fixed dev domain)',
        "URL: $endpoint", 'Method: GET', 'Query key: locationId',
        'Query value: select the workflow location_id variable (test: 101010100)',
        'Authentication: None', 'Headers: leave empty', 'Body: none', '',
        'The domain is tied to this ngrok account and is reused on restart.',
        'Keep the computer, backend and ngrok client running. Free usage quotas apply.',
        'Stop: ngrok-weather.cmd stop'
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($DomainFile, $base, $Utf8)
    [IO.File]::WriteAllText($ConnectionFile, $details, $Utf8)
    [IO.File]::WriteAllText($PublicUrlFile, $endpoint, $Utf8)
    Write-Host "[OK] Fixed URL: $endpoint"
    Write-Host "[INFO] Dify configuration: $ConnectionFile"
    Write-Host '[INFO] Use GET with locationId. Leave headers empty.'
}

$createdGateway = $null
$createdTunnel = $null
try {
    $state = Read-State
    $gateway = if ($state) { Get-ManagedProcess $state.Gateway } else { $null }
    $tunnel = if ($state) { Get-ManagedProcess $state.Tunnel } else { $null }
    if ($Action -eq 'stop') {
        if ($state) { Stop-Entry $state.Tunnel; Stop-Entry $state.Gateway }
        foreach ($file in @($StateFile, $ConnectionFile, $PublicUrlFile)) {
            if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
        }
        Write-Host '[OK] Weather tunnel stopped. Account configuration and fixed domain were retained.'
        exit 0
    }
    if ($Action -eq 'setup') {
        [void](Ensure-Ngrok)
        Setup-Ngrok
        $Action = 'start'
    }
    $expected = Read-ExpectedDomain
    if ($Action -eq 'status') {
        if ($state -and $state.Provider -eq 'ngrok' -and $gateway -and $tunnel) {
            $url = Get-ActiveUrl $expected
            if ($url) { Show-Connection $url; exit 0 }
        }
        if ($state -and $state.Provider -ne 'ngrok' -and $tunnel) {
            Write-Host '[INFO] Legacy Pinggy tunnel is still running; use setup to switch to ngrok.'
        } elseif (-not (Test-Path -LiteralPath $ConfigFile)) {
            Write-Host '[ACTION] Configure your ngrok account: ngrok-weather.cmd setup'
        } else { Write-Host '[INFO] ngrok is stopped or not ready. Use ngrok-weather.cmd start.' }
        exit 1
    }
    if ($Action -eq 'restart-gateway') {
        if (-not $state -or $state.Provider -ne 'ngrok' -or -not $tunnel) { throw 'No active ngrok tunnel. Use setup or start first.' }
        Stop-Entry $state.Gateway
        $createdGateway = Start-Gateway
        $state.Gateway = $createdGateway
        Save-State $state
        $url = Get-ActiveUrl $expected
        if (-not $url) { throw 'ngrok endpoint is not ready.' }
        Show-Connection $url
        exit 0
    }
    if (-not (Test-Path -LiteralPath $ConfigFile)) {
        if ($env:NGROK_AUTHTOKEN) { Save-Token $env:NGROK_AUTHTOKEN }
        else {
            Write-Host '[ACTION] One-time account setup required: ngrok-weather.cmd setup'
            Write-Host '[INFO] Paste the token in the local hidden prompt, not into chat. Existing services were left running.'
            exit 2
        }
    }
    if (-not (Test-LocalPort 8080)) { throw 'Backend is not listening on 127.0.0.1:8080. Run start-all.cmd start.' }
    $ngrok = Ensure-Ngrok
    & $ngrok config check --config $ConfigFile 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'ngrok config is invalid. Run ngrok-weather.cmd setup again.' }
    if ($state -and $state.Provider -eq 'ngrok' -and $gateway -and $tunnel) {
        $url = Get-ActiveUrl $expected
        if ($url) { Show-Connection $url; exit 0 }
        throw 'Existing ngrok is not ready. Stop it, then start again.'
    }
    if ($state -and $state.Provider -eq 'ngrok' -and $tunnel) { throw 'Incomplete ngrok state. Use stop, then start.' }
    if (Test-LocalPort 18788) { throw 'ngrok management port 18788 is occupied by another process.' }
    [void][IO.Directory]::CreateDirectory($Runtime)
    $gatewayEntry = if ($gateway) { $state.Gateway } else { $createdGateway = Start-Gateway; $createdGateway }
    $arguments = @('http', 'http://127.0.0.1:18787', '--config', ('"' + $ConfigFile + '"'), '--log=false', '--inspect=false')
    if ($expected) { $arguments += @('--url', $expected) }
    # Keep native auth failures out of files/console; use the loopback API for readiness.
    $child = Start-Process -FilePath $ngrok -ArgumentList $arguments -WorkingDirectory $Runtime -WindowStyle Hidden -PassThru
    $createdTunnel = Process-Entry $child
    $deadline = [DateTime]::UtcNow.AddSeconds(50)
    do {
        if (-not (Get-ManagedProcess $createdTunnel)) { throw 'ngrok exited before connecting. Check your account token, verification and network; setup can replace the token.' }
        $url = Get-ActiveUrl $expected
        if ($url) {
            $newState = @{ Provider = 'ngrok'; Gateway = $gatewayEntry; Tunnel = $createdTunnel }
            Save-State $newState
            if ($state -and $state.Provider -ne 'ngrok') { Stop-Entry $state.Tunnel }
            Show-Connection $url
            exit 0
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'ngrok did not create the expected HTTPS endpoint within 50 seconds. Check account authentication and connectivity.'
} catch {
    Stop-Entry $createdTunnel
    Stop-Entry $createdGateway
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
