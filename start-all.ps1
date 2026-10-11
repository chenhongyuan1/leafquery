# Windows development launcher. Supports both the workspace root and repository root.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'status', 'check', 'restart-backend')]
    [string]$Action = 'start',
    [switch]$NoOpen,
    [switch]$NoInstall,
    [string]$EnvFile,
    [ValidateRange(10, 1800)]
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$AppRoot = $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $AppRoot 'pom.xml'))) {
    $AppRoot = Join-Path $PSScriptRoot 'leafquery'
}
if (-not (Test-Path -LiteralPath (Join-Path $AppRoot 'pom.xml'))) {
    Write-Host '[ERROR] Cannot find leafquery/pom.xml.' -ForegroundColor Red
    exit 1
}
$RuntimeRoot = Join-Path $PSScriptRoot '.leafquery-runtime'
$StatePath = Join-Path $RuntimeRoot 'processes.json'
$ShellPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$Services = @(
    @{ Name = 'python'; Port = 5000; Url = 'http://localhost:5000/predict'; Method = 'POST'; Code = 400 },
    @{ Name = 'backend'; Port = 8080; Url = 'http://localhost:8080/api/trend/crops'; Method = 'GET'; Code = 200 },
    @{ Name = 'frontend'; Port = 5173; Url = 'http://localhost:5173/'; Method = 'GET'; Code = 200 },
    @{ Name = 'admin'; Port = 5174; Url = 'http://localhost:5174/'; Method = 'GET'; Code = 200 }
)

function Quote-Ps([string]$Value) { return "'" + $Value.Replace("'", "''") + "'" }

function Import-DotEnv([string]$Path, [hashtable]$OriginalKeys) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        if ($EnvFile) { throw 'The specified environment file does not exist.' }
        Write-Host '[INFO] No .env found. Using process environment variables; copy leafquery/.env.example to .env if needed.'
        return
    }
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8)) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -notmatch '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            throw "Invalid .env entry on line $lineNumber. Use KEY=VALUE; values are never executed."
        }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            $quote = $value.Substring(0, 1)
            if ($value.Length -lt 2 -or -not $value.EndsWith($quote)) {
                throw "Invalid .env quoting on line $lineNumber."
            }
            $value = $value.Substring(1, $value.Length - 2)
            if ($quote -eq '"') {
                $value = [regex]::Replace($value, '\\([nrt\\"])', {
                    param($match)
                    switch ($match.Groups[1].Value) {
                        'n' { return "`n" }; 'r' { return "`r" }; 't' { return "`t" }
                        default { return $match.Groups[1].Value }
                    }
                })
            }
        } else {
            $value = ($value -replace '\s+#.*$', '').TrimEnd()
        }
        # Explicit process environment settings take precedence over file values.
        if (-not $OriginalKeys.ContainsKey($key)) {
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
        }
    }
    Write-Host '[OK] Loaded local .env configuration (values hidden).'
}

function Test-Tcp([string]$HostName, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $pending = $client.ConnectAsync($HostName, $Port)
        return ($pending.Wait(1000) -and $client.Connected)
    } catch { return $false } finally { $client.Dispose() }
}

function Test-Service($Service) {
    try {
        $response = Invoke-WebRequest -Uri $Service.Url -Method $Service.Method -UseBasicParsing -TimeoutSec 2
        return ([int]$response.StatusCode -eq $Service.Code)
    } catch {
        if ($_.Exception.Response) {
            return ([int]$_.Exception.Response.StatusCode -eq $Service.Code)
        }
        return $false
    }
}

function Read-State {
    if (Test-Path -LiteralPath $StatePath) {
        $parsed = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
        foreach ($entry in $parsed) { Write-Output $entry }
        return
    }
    return @()
}

function Save-State($Entries) {
    if (@($Entries).Count) {
        ConvertTo-Json -InputObject @($Entries) -Depth 3 | Set-Content -LiteralPath $StatePath -Encoding UTF8
    } elseif (Test-Path -LiteralPath $StatePath) {
        Remove-Item -LiteralPath $StatePath
    }
}

function Get-OwnedProcess($Entry) {
    $process = Get-Process -Id $Entry.ProcessId -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq 'powershell' -and
        $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $Entry.StartTicks) {
        return $process
    }
    return $null
}

function Stop-OwnedProcess($Entry) {
    if (Get-OwnedProcess $Entry) {
        # Stop only this launcher's recorded process tree; never kill by port or image name.
        & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID $Entry.ProcessId /T /F | Out-Null
        if ($LASTEXITCODE -ne 0 -and (Get-OwnedProcess $Entry)) {
            throw "Cannot stop $($Entry.Name) (PID $($Entry.ProcessId))."
        }
    }
}

function Find-Tool([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) { throw "Missing $Name. Install it and reopen your terminal." }
    return $command.Source
}

function Test-PythonModules([string]$Executable) {
    & $Executable -c "import importlib.util, sys; sys.exit(0 if all(importlib.util.find_spec(m) for m in ('flask','flask_cors','PIL','ultralytics')) else 1)" | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Get-Environment {
    if (($BackendProfile -split ',') -contains 'local' -and
        -not (Test-Path -LiteralPath (Join-Path $AppRoot 'src/main/resources/application-local.properties'))) {
        throw 'The local profile requires src/main/resources/application-local.properties.'
    }
    foreach ($file in @('mvnw.cmd', '.mvn/wrapper/maven-wrapper.properties', 'python_service/app.py',
        'python_service/best.pt', 'docs/detection_target_metadata.json',
        'user-frontend/package.json', 'admin-frontend/package.json')) {
        if (-not (Test-Path -LiteralPath (Join-Path $AppRoot $file) -PathType Leaf)) {
            throw "Required file is missing: $file"
        }
    }
    # Maven uses JAVA_HOME when present; validate that same JDK.
    if ($env:JAVA_HOME) {
        $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
        $javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
        if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $javac)) {
            throw 'JAVA_HOME must point to a JDK with java.exe and javac.exe.'
        }
    } else {
        $java = Find-Tool 'java.exe'
        $javac = Find-Tool 'javac.exe'
    }
    $jdkOutput = (& $javac -version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $jdkOutput -notmatch 'javac (\d+)' -or [int]$Matches[1] -lt 17) {
        throw 'JDK 17 or newer is required.'
    }
    $node = Find-Tool 'node.exe'
    $npm = Find-Tool 'npm.cmd'
    $nodeVersion = (& $node --version).Trim().TrimStart('v')
    $version = [version]$nodeVersion
    if (-not (($version.Major -eq 20 -and $version -ge [version]'20.19.0') -or
        $version -ge [version]'22.12.0')) {
        throw 'Vite 7 requires Node 20.19+ or 22.12+.'
    }
    $venvPython = Join-Path $AppRoot 'python_service/.venv/Scripts/python.exe'
    if ($env:LEAFQUERY_PYTHON) {
        $python = $env:LEAFQUERY_PYTHON
        if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
            throw 'LEAFQUERY_PYTHON must be the full path to python.exe.'
        }
    } elseif (Test-Path -LiteralPath $venvPython) {
        $python = $venvPython
    } else {
        $python = Find-Tool 'python.exe'
    }
    & $python -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.10 or newer is required (3.10 recommended).' }

    # Do not print the JDBC URL or credentials. This checks TCP reachability only.
    $dbHost = 'localhost'
    $dbPort = 3306
    $dbUrl = $env:SPRING_DATASOURCE_URL
    if (-not $dbUrl) { $dbUrl = $env:DB_URL }
    if ($dbUrl) {
        if ($dbUrl -notmatch '^jdbc:mysql://(?<host>\[[^\]]+\]|[^:/?]+)(:(?<port>\d+))?/') {
            throw 'DB_URL/SPRING_DATASOURCE_URL must be a jdbc:mysql://host:port/database URL.'
        }
        $dbHost = $Matches['host'].Trim('[', ']')
        if ($Matches['port']) { $dbPort = [int]$Matches['port'] }
    }
    $dbUsername = $env:SPRING_DATASOURCE_USERNAME
    if (-not $dbUsername) { $dbUsername = $env:DB_USERNAME }
    $dbPassword = $env:SPRING_DATASOURCE_PASSWORD
    if (-not $dbPassword) { $dbPassword = $env:DB_PASSWORD }
    if (-not $dbUsername -or -not $dbPassword) {
        throw 'Configure DB_USERNAME and DB_PASSWORD in leafquery/.env or in the process environment before starting.'
    }
    if (-not (Test-Tcp $dbHost $dbPort)) {
        throw "MySQL is unreachable at ${dbHost}:$dbPort. Start MySQL or configure DB_URL. No tables were changed."
    }
    Write-Host "[OK] $jdkOutput; Node $nodeVersion; Python $python; MySQL TCP reachable."
    return @{ Python = $python; Npm = $npm; VenvPython = $venvPython }
}

function Prepare-Dependencies($Environment) {
    foreach ($frontend in @('user-frontend', 'admin-frontend')) {
        $directory = Join-Path $AppRoot $frontend
        if (-not (Test-Path -LiteralPath (Join-Path $directory 'node_modules/vite/bin/vite.js'))) {
            if ($NoInstall) { throw "$frontend dependencies are missing. Run without -NoInstall to install." }
            Write-Host "[INSTALL] $frontend dependencies..."
            Push-Location $directory
            try {
                if (Test-Path -LiteralPath 'package-lock.json') { & $Environment.Npm ci }
                else { & $Environment.Npm install }
                if ($LASTEXITCODE -ne 0) { throw "$frontend npm install failed." }
            } finally { Pop-Location }
        }
    }
    if (-not (Test-PythonModules $Environment.Python)) {
        if ($NoInstall) { throw 'Python dependencies are missing. Run without -NoInstall to install.' }
        # Never install packages into a global/custom Python environment.
        if (-not (Test-Path -LiteralPath $Environment.VenvPython)) {
            & $Environment.Python -m venv (Join-Path $AppRoot 'python_service/.venv')
            if ($LASTEXITCODE -ne 0) { throw 'Failed to create Python virtual environment.' }
        }
        $Environment.Python = $Environment.VenvPython
        & $Environment.Python -m pip install -r (Join-Path $AppRoot 'python_service/requirements.txt')
        if ($LASTEXITCODE -ne 0 -or -not (Test-PythonModules $Environment.Python)) {
            throw 'Python dependency installation failed. See the pip output above.'
        }
    }
}

function Start-ServiceProcess($Service, [string]$Directory, [string]$Command, [hashtable]$Variables) {
    $code = '$ErrorActionPreference = ''Stop''; $ProgressPreference = ''SilentlyContinue''; '
    foreach ($key in $Variables.Keys) {
        $code += '[Environment]::SetEnvironmentVariable(' + (Quote-Ps $key) + ', ' +
            (Quote-Ps $Variables[$key]) + ', ''Process''); '
    }
    $code += 'Set-Location -LiteralPath ' + (Quote-Ps $Directory) + '; ' + $Command +
        '; exit $LASTEXITCODE'
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($code))
    $outLog = Join-Path $script:LogDirectory "$($Service.Name).out.log"
    $errLog = Join-Path $script:LogDirectory "$($Service.Name).err.log"
    $process = Start-Process -FilePath $ShellPath -ArgumentList @('-NoLogo', '-NoProfile',
        '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encoded) -WorkingDirectory $Directory `
        -WindowStyle Hidden -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
    return [pscustomobject]@{
        Name = $Service.Name; ProcessId = $process.Id
        StartTicks = $process.StartTime.ToUniversalTime().Ticks.ToString()
        OutLog = $outLog; ErrLog = $errLog
    }
}

$lockStream = $null
$started = @()
$keptEntries = @()
try {
    # Stop and status remain available even when local configuration is invalid.
    if ($Action -in @('start', 'check', 'restart-backend')) {
        $originalKeys = @{}
        foreach ($key in [Environment]::GetEnvironmentVariables('Process').Keys) { $originalKeys[$key] = $true }
        $configurationPath = Join-Path $AppRoot '.env'
        if ($EnvFile) {
            $configurationPath = $EnvFile
            if (-not [IO.Path]::IsPathRooted($configurationPath)) {
                $configurationPath = Join-Path $PSScriptRoot $configurationPath
            }
        }
        Import-DotEnv $configurationPath $originalKeys
        $BackendProfile = $env:SPRING_PROFILES_ACTIVE
        if (-not $BackendProfile) { $BackendProfile = 'dev' }
        if (@($BackendProfile -split ',' | ForEach-Object { $_.Trim() }) -contains 'prod') {
            throw 'start-all is a local development launcher. Use dev here; inject the production environment on the server.'
        }
    }
    if ($Action -eq 'status') {
        $entries = @(Read-State)
        foreach ($service in $Services) {
            $entry = $entries | Where-Object Name -eq $service.Name | Select-Object -First 1
            $state = 'not managed'
            if ($entry) {
                $state = 'stopped'
                if (Get-OwnedProcess $entry) {
                    $state = 'running (not ready)'
                    if (Test-Service $service) { $state = 'ready' }
                }
            }
            Write-Host ("{0,-10} {1,-22} {2}" -f $service.Name, $state, $service.Url)
        }
        Write-Host "Logs: $RuntimeRoot\logs"
        exit 0
    }
    if ($Action -eq 'check') {
        $environment = Get-Environment
        $missing = @()
        foreach ($frontend in @('user-frontend', 'admin-frontend')) {
            if (-not (Test-Path -LiteralPath (Join-Path $AppRoot "$frontend/node_modules/vite/bin/vite.js"))) {
                $missing += $frontend
            }
        }
        if (-not (Test-PythonModules $environment.Python)) { $missing += 'Python modules' }
        if ($missing.Count) { throw ('Dependencies missing: ' + ($missing -join ', ') + '. Run start to install.') }
        foreach ($service in $Services) {
            if ((Test-Tcp 'localhost' $service.Port) -or (Test-Tcp '127.0.0.1' $service.Port)) {
                Write-Host "[INFO] Port $($service.Port) is occupied; start will refuse unrelated processes."
            }
        }
        Write-Host '[OK] Preflight passed. Database credentials/tables and external API keys have not been validated.'
        exit 0
    }
    New-Item -ItemType Directory -Path $RuntimeRoot -Force | Out-Null
    try {
        $lockStream = [IO.File]::Open((Join-Path $RuntimeRoot 'launcher.lock'),
            [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch { throw 'Another launcher is starting or stopping services. Try again after it finishes.' }
    $entries = @(Read-State)
    if ($Action -eq 'stop') {
        foreach ($entry in $entries) {
            Stop-OwnedProcess $entry
            Write-Host "[STOP] $($entry.Name)"
        }
        if (Test-Path -LiteralPath $StatePath) { Remove-Item -LiteralPath $StatePath }
        Write-Host '[OK] Managed services stopped. MySQL was left running.'
        exit 0
    }
    if ($Action -eq 'restart-backend') {
        foreach ($entry in @($entries | Where-Object Name -eq 'backend')) {
            Stop-OwnedProcess $entry
            Write-Host '[STOP] backend'
        }
        $keptEntries = @($entries | Where-Object Name -ne 'backend')
        Save-State $keptEntries
        $entries = @()
        $Services = @($Services | Where-Object Name -eq 'backend')
        $NoOpen = $true
    }
    $live = @($entries | Where-Object { Get-OwnedProcess $_ })
    if ($live.Count) {
        if ($live.Count -eq $Services.Count -and @($Services | Where-Object { -not (Test-Service $_) }).Count -eq 0) {
            Write-Host '[OK] All four services are already running. Use status or stop.'
            exit 0
        }
        throw 'A previous launch is still running or incomplete. Run start-all.cmd stop, then start again.'
    }
    $environment = Get-Environment
    foreach ($service in $Services) {
        if ((Test-Tcp 'localhost' $service.Port) -or (Test-Tcp '127.0.0.1' $service.Port)) {
            throw "Port $($service.Port) is already occupied. Stop that application or free the port first."
        }
    }
    Prepare-Dependencies $environment
    Write-Host "[INFO] Backend profile: $BackendProfile"
    $script:LogDirectory = Join-Path $RuntimeRoot ('logs/' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $script:LogDirectory -Force | Out-Null
    # This JDK uses TEMP for Unix-domain loopback sockets. Keep backend temporary
    # files in the workspace to avoid failures with this machine's Unicode TEMP path.
    $javaTemp = Join-Path $RuntimeRoot 'java-tmp'
    New-Item -ItemType Directory -Path $javaTemp -Force | Out-Null
    $metadataPath = Join-Path $AppRoot 'docs/detection_target_metadata.json'
    if ($env:DETECTION_METADATA_PATH) {
        $metadataPath = $env:DETECTION_METADATA_PATH
        if (-not [IO.Path]::IsPathRooted($metadataPath)) { $metadataPath = Join-Path $AppRoot $metadataPath }
    }
    $uploadPath = $env:UPLOAD_DIR
    if (-not $uploadPath) { $uploadPath = Join-Path $AppRoot 'user-frontend/public/images/uploads' }
    elseif (-not [IO.Path]::IsPathRooted($uploadPath)) { $uploadPath = Join-Path $AppRoot $uploadPath }
    $pythonUrl = $env:PYTHON_AI_URL
    if (-not $pythonUrl) { $pythonUrl = 'http://localhost:5000/predict' }
    $backendVariables = @{ SERVER_PORT = '8080'; SERVER_ADDRESS = '127.0.0.1'; SPRING_SQL_INIT_MODE = 'never';
        PYTHON_AI_URL = $pythonUrl; DETECTION_METADATA_PATH = $metadataPath;
        UPLOAD_DIR = $uploadPath; TEMP = $javaTemp; TMP = $javaTemp; SPRING_PROFILES_ACTIVE = $BackendProfile }
    if (-not $env:DB_URL -and -not $env:SPRING_DATASOURCE_URL) {
        # Only the default loopback development connection allows authentication
        # key retrieval. Custom/remote database URLs are never rewritten.
        $backendVariables.DB_URL = 'jdbc:mysql://127.0.0.1:3306/leafquery?useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8&useSSL=false&allowPublicKeyRetrieval=true'
    }
    $commands = @{
        python = @{ Directory = (Join-Path $AppRoot 'python_service'); Command = ('& ' + (Quote-Ps $environment.Python) + ' -u app.py');
            Variables = @{ PORT = '5000'; PYTHONUNBUFFERED = '1'; DETECTION_METADATA_PATH = $metadataPath } }
        backend = @{ Directory = $AppRoot; Command = ('& ' + (Quote-Ps (Join-Path $AppRoot 'mvnw.cmd')) + ' spring-boot:run');
            Variables = $backendVariables }
        frontend = @{ Directory = (Join-Path $AppRoot 'user-frontend');
            Command = ('& ' + (Quote-Ps $environment.Npm) + ' run dev -- --host localhost --port 5173 --strictPort');
            Variables = @{ VITE_API_BASE_URL = '' } }
        admin = @{ Directory = (Join-Path $AppRoot 'admin-frontend');
            Command = ('& ' + (Quote-Ps $environment.Npm) + ' run dev -- --host localhost --port 5174 --strictPort');
            Variables = @{ VITE_API_BASE_URL = 'http://localhost:8080' } }
    }
    foreach ($service in $Services) {
        $spec = $commands[$service.Name]
        $started += Start-ServiceProcess $service $spec.Directory $spec.Command $spec.Variables
        Save-State (@($keptEntries) + @($started))
        Write-Host "[START] $($service.Name) (port $($service.Port))"
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $pending = @($Services)
    while ($pending.Count) {
        foreach ($entry in $started) {
            if (-not (Get-OwnedProcess $entry)) {
                throw "$($entry.Name) exited early. Read $($entry.ErrLog) and $($entry.OutLog)."
            }
        }
        $pending = @($pending | Where-Object { -not (Test-Service $_) })
        if (-not $pending.Count) { break }
        if ((Get-Date) -gt $deadline) {
            throw ('Startup timed out for ' + (($pending | ForEach-Object Name) -join ', ') +
                '. First Maven downloads may need -TimeoutSeconds 600. Logs: ' + $script:LogDirectory)
        }
        Write-Host ('[WAIT] ' + (($pending | ForEach-Object Name) -join ', '))
        Start-Sleep -Seconds 3
    }
    Write-Host "[OK] $($Services.Count) application service(s) are ready." -ForegroundColor Green
    Write-Host 'User frontend:  http://localhost:5173'
    Write-Host 'Admin frontend: http://localhost:5174'
    Write-Host "Logs: $script:LogDirectory"
    Write-Host 'Stop: start-all.cmd stop'
    try {
        Invoke-WebRequest -Uri 'http://localhost:8080/api/discovery/news' -UseBasicParsing -TimeoutSec 10 | Out-Null
        Write-Host '[OK] Database-backed news endpoint responded successfully.'
    } catch {
        Write-Host '[WARN] Database-backed news endpoint failed. Check DB_USERNAME/DB_PASSWORD and existing tables.' -ForegroundColor Yellow
        Write-Host '[INFO] Applications remain running. No database initialization SQL was imported.'
    }
    if (-not $NoOpen) {
        Start-Process 'http://localhost:5173'
        Start-Process 'http://localhost:5174'
    }
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    if ($started.Count) {
        Write-Host '[ROLLBACK] Stopping services created by this attempt...'
        foreach ($entry in $started) {
            try { Stop-OwnedProcess $entry } catch { Write-Host "[ERROR] $($_.Exception.Message)" }
        }
        if (@($started | Where-Object { Get-OwnedProcess $_ }).Count -eq 0 -and
            (Test-Path -LiteralPath $StatePath)) { Save-State $keptEntries }
    }
    exit 1
} finally {
    if ($lockStream) { $lockStream.Dispose() }
}
