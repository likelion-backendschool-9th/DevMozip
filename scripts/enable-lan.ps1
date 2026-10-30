# Run in Windows PowerShell as Administrator. Exposes only Vite's TCP port to the local subnet.
$ErrorActionPreference = 'Stop'
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Run this script as Administrator.' }
$logPath = Join-Path $PSScriptRoot 'lan-setup.log'
try {
    $network = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address -and $_.InterfaceAlias -notlike '*vEthernet*' } | Select-Object -First 1
    if (-not $network) { throw 'No active LAN or Wi-Fi interface found.' }
    $lanAddress = ($network.IPv4Address | Select-Object -First 1).IPAddress
    $wslOutput = & wsl.exe -d Ubuntu-26.04 hostname -I
    if ($LASTEXITCODE -ne 0) { throw 'Unable to resolve WSL address.' }
    $wslAddress = ($wslOutput.Trim() -split '\s+')[0]
    $parsed = $null
    if (-not [System.Net.IPAddress]::TryParse($wslAddress, [ref]$parsed) -or $parsed.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { throw 'Invalid WSL IPv4 address.' }
    # Refresh only this project's exact listener. Other forwarded ports are preserved.
    & netsh interface portproxy delete v4tov4 listenaddress=$lanAddress listenport=5173 | Out-Null
    & netsh interface portproxy add v4tov4 listenaddress=$lanAddress listenport=5173 connectaddress=$wslAddress connectport=5173
    if ($LASTEXITCODE -ne 0) { throw 'Port forwarding setup failed.' }
    $ruleName = 'DevMozip-Frontend-LAN-5173'
    $rule = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
    if ($rule) {
        $rule | Set-NetFirewallRule -Enabled True -Direction Inbound -Action Allow -Profile Any -RemoteAddress LocalSubnet
        $rule | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort 5173
    } else {
        New-NetFirewallRule -Name $ruleName -DisplayName 'DevMozip frontend LAN (TCP 5173)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5173 -RemoteAddress LocalSubnet -Profile Any | Out-Null
    }
    Start-Sleep -Seconds 2
    $response = Invoke-WebRequest -Uri "http://${lanAddress}:5173" -UseBasicParsing -TimeoutSec 10
    "SUCCESS URL=http://${lanAddress}:5173 HTTP=$($response.StatusCode) WSL=$wslAddress" | Set-Content -Path $logPath -Encoding UTF8
} catch {
    "FAILED $($_.Exception.Message)" | Set-Content -Path $logPath -Encoding UTF8
    throw
}
