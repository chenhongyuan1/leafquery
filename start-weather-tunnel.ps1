# Expose only the weather callback to Dify Cloud through a temporary HTTPS tunnel.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'status', 'stop', 'restart-gateway')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$Runtime = Join-Path $PSScriptRoot '.leafquery-runtime\weather-tunnel'
$StateFile = Join-Path $Runtime 'processes.json'
$ConnectionFile = Join-Path $Runtime 'dify-connection.txt'
$TunnelLog = Join-Path $Runtime 'pinggy.out.log'
$GatewayScript = Join-Path $PSScriptRoot 'scripts\weather-gateway.mjs'
$Utf8 = New-Object Text.UTF8Encoding($false)

function Read-State {
    if (Test-Path -LiteralPath $StateFile) { return Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json }
    return $null
}

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

function Test-LocalPort([int]$Port) {
    $probe = New-Object Net.Sockets.TcpClient
    try { return ($probe.ConnectAsync('127.0.0.1', $Port).Wait(500) -and $probe.Connected) }
    catch { return $false } finally { $probe.Dispose() }
}

function Stop-Managed($State) {
    if (-not $State) { return }
    foreach ($entry in @($State.Tunnel, $State.Gateway)) {
        $managed = Get-ManagedProcess $entry
        if ($managed) { Stop-Process -Id $managed.Id -ErrorAction SilentlyContinue }
    }
}

function Find-PublicUrl {
    if (Test-Path -LiteralPath $TunnelLog) {
        $stream = [IO.File]::Open($TunnelLog, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        $reader = New-Object IO.StreamReader($stream)
        try { $logText = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $urlMatch = [regex]::Match($logText, 'https://[a-z0-9.-]+\.(?:pinggy-free|pinggy)\.link')
        if ($urlMatch.Success) { return $urlMatch.Value }
    }
    return $null
}

function Start-Gateway {
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
    } catch {
        $managed = Get-ManagedProcess $entry
        if ($managed) { Stop-Process -Id $managed.Id -ErrorAction SilentlyContinue }
        throw
    }
}

function Show-Connection([string]$PublicUrl) {
    if (-not $PublicUrl) { Write-Host '[WAIT] The public URL is not ready. Check pinggy.out.log and pinggy.err.log.'; return }
    $endpoint = "$PublicUrl/api/weather/features"
    $details = @(
        'LeafQuery - Dify HTTP node configuration',
        "URL: $endpoint", 'Method: GET', 'Query key: locationId',
        'Query value: select the workflow location_id variable (test: 101010100)',
        'Authentication: None', 'Headers: leave empty', 'Body: none', '',
        'Keep the computer and backend running. Pinggy free tunnels expire after 60 minutes.',
        'A newly created tunnel has a new URL. Restart with stop, then start.',
        'Stop: start-weather-tunnel.cmd stop'
    ) -join [Environment]::NewLine
    [IO.File]::WriteAllText($ConnectionFile, $details, $Utf8)
    [IO.File]::WriteAllText((Join-Path $Runtime 'public-url.txt'), $endpoint, $Utf8)
    Write-Host "[OK] URL: $endpoint"
    Write-Host "[INFO] Dify configuration: $ConnectionFile"
    Write-Host '[INFO] Use GET with query parameter locationId. Leave headers empty.'
}

$started = $null
try {
    $state = Read-State
    if ($Action -eq 'stop') {
        Stop-Managed $state
        if (Test-Path -LiteralPath $StateFile) { Remove-Item -LiteralPath $StateFile }
        foreach ($file in @($ConnectionFile, (Join-Path $Runtime 'public-url.txt'))) {
            if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
        }
        Write-Host '[OK] Weather tunnel stopped. The Java backend was left running.'
        exit 0
    }
    $gateway = if ($state) { Get-ManagedProcess $state.Gateway } else { $null }
    $tunnel = if ($state) { Get-ManagedProcess $state.Tunnel } else { $null }
    if ($Action -eq 'restart-gateway') {
        if (-not $tunnel) { throw 'No active SSH tunnel. Use start-weather-tunnel.cmd start.' }
        if ($gateway) { Stop-Process -Id $gateway.Id; [void]$gateway.WaitForExit(2000) }
        $started = @{ Gateway = (Start-Gateway); Tunnel = $null }
        $state.Gateway = $started.Gateway
        [IO.File]::WriteAllText($StateFile, ($state | ConvertTo-Json), $Utf8)
        Show-Connection (Find-PublicUrl)
        exit 0
    }
    if ($Action -eq 'status' -or ($gateway -and $tunnel)) {
        if ($gateway -and $tunnel) { Show-Connection (Find-PublicUrl); exit 0 }
        Write-Host '[INFO] Weather tunnel is stopped or incomplete. Use stop, then start.'
        exit 1
    }
    if ($gateway -or $tunnel) { throw 'An incomplete weather tunnel is running. Use start-weather-tunnel.cmd stop first.' }
    foreach ($port in @(18787)) {
        if (Test-LocalPort $port) { throw "Local tunnel port $port is occupied by another process." }
    }
    $tcp = New-Object Net.Sockets.TcpClient
    try { if (-not $tcp.ConnectAsync('127.0.0.1', 8080).Wait(1500) -or -not $tcp.Connected) { throw 'Backend is not listening on 127.0.0.1:8080. Start it with start-all.cmd.' } }
    finally { $tcp.Dispose() }
    $ssh = (Get-Command ssh.exe -ErrorAction Stop).Source
    [void][IO.Directory]::CreateDirectory($Runtime)
    $identity = Join-Path $Runtime 'client_ed25519'
    if (-not (Test-Path -LiteralPath $identity)) {
        $keygen = (Get-Command ssh-keygen.exe -ErrorAction Stop).Source
        $keygenProcess = Start-Process -FilePath $keygen -ArgumentList @('-q', '-t', 'ed25519', '-N', '""', '-C', 'leafquery-weather-tunnel', '-f', ('"' + $identity + '"')) -WindowStyle Hidden -Wait -PassThru
        if ($keygenProcess.ExitCode -ne 0) { throw 'Could not generate the dedicated local SSH identity.' }
    }
    $started = @{ Gateway = $null; Tunnel = $null }
    $started.Gateway = Start-Gateway
    [IO.File]::WriteAllText($StateFile, ($started | ConvertTo-Json), $Utf8)
    $knownHosts = (Join-Path $Runtime 'known_hosts').Replace('\', '/')
    $sshArguments = @('-F', 'NUL', '-T', '-p', '443', '-i', ('"' + $identity + '"'), '-o', 'BatchMode=yes', '-o', 'PreferredAuthentications=publickey,none', '-o', 'IdentitiesOnly=yes', '-o', 'IdentityAgent=none', '-o', 'StrictHostKeyChecking=accept-new', '-o', ('UserKnownHostsFile="' + $knownHosts + '"'), '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=3', '-o', 'ExitOnForwardFailure=yes', '-R', '0:127.0.0.1:18787', 'free@free.pinggy.io')
    $tunnel = Start-Process -FilePath $ssh -ArgumentList $sshArguments -WorkingDirectory $Runtime -WindowStyle Hidden -PassThru -RedirectStandardOutput $TunnelLog -RedirectStandardError (Join-Path $Runtime 'pinggy.err.log')
    $started.Tunnel = Process-Entry $tunnel
    [IO.File]::WriteAllText($StateFile, ($started | ConvertTo-Json), $Utf8)
    for ($i = 0; $i -lt 100; $i++) {
        if (-not (Get-ManagedProcess $started.Tunnel)) { throw 'SSH tunnel exited. Check pinggy.err.log.' }
        $publicUrl = Find-PublicUrl
        if ($publicUrl) { Show-Connection $publicUrl; exit 0 }
        Start-Sleep -Milliseconds 500
    }
    throw 'No public URL received within 50 seconds. Check pinggy.out.log and pinggy.err.log.'
} catch {
    if ($started) {
        Stop-Managed $started
        if (Test-Path -LiteralPath $StateFile) { Remove-Item -LiteralPath $StateFile }
    }
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
