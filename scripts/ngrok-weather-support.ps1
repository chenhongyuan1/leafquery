# Pure helpers shared by the launcher and offline checks. Never print token values.
function New-NgrokWeatherConfig([string]$Token) {
    $trimmed = $Token.Trim()
    if ($trimmed.Length -lt 16 -or $trimmed -match '\s') { throw 'Invalid ngrok Authtoken. Copy only the token value from the dashboard.' }
    $quoted = ConvertTo-Json -InputObject $trimmed -Compress
    return @('version: "3"', 'agent:', "  authtoken: $quoted", '  web_addr: "127.0.0.1:18788"', '  console_ui: false') -join [Environment]::NewLine
}

function ConvertTo-NgrokBaseUrl([string]$Value) {
    $parsed = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsed) -or
        $parsed.Scheme -ne 'https' -or -not $parsed.Host -or $parsed.UserInfo -or
        $parsed.Query -or $parsed.Fragment -or $parsed.AbsolutePath -ne '/') {
        throw 'Expected an HTTPS ngrok base URL without credentials, path, query or fragment.'
    }
    return $parsed.GetLeftPart([UriPartial]::Authority)
}

function Select-NgrokWeatherUrl($ApiResult, [string]$ExpectedUrl) {
    $candidates = @()
    foreach ($endpoint in @($ApiResult.endpoints)) {
        if ($endpoint -and $endpoint.upstream) {
            $candidates += @{ Public = [string]$endpoint.url; Target = [string]$endpoint.upstream.url }
        }
    }
    foreach ($candidate in @($ApiResult.tunnels)) {
        if (-not $candidate -or -not $candidate.config) { continue }
        $candidates += @{ Public = [string]$candidate.public_url; Target = [string]$candidate.config.addr }
    }
    foreach ($candidate in $candidates) {
        if ($candidate.Target -notmatch '^(?:http://)?127\.0\.0\.1:18787/?$') { continue }
        try { $base = ConvertTo-NgrokBaseUrl $candidate.Public } catch { continue }
        if ($ExpectedUrl -and $base -ne (ConvertTo-NgrokBaseUrl $ExpectedUrl)) { continue }
        return $base
    }
    return $null
}
