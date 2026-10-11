# =====================================================================================
# upload.ps1 – puts the Nexus online server on your Oracle machine (first time AND every update).
#
# First time (from the project folder, in PowerShell):
#   .\deploy\oracle\upload.ps1 -Ip 123.45.67.89 -Domain nexusclient.duckdns.org -Key C:\Users\Jay\.ssh\oracle-nexus.key
#
# After that it remembers them (in deploy\oracle\server.local.json – never put on GitHub), so updating
# the server after a change is just:
#   .\deploy\oracle\upload.ps1
#
# What it does:
#   1. builds the server program (gradlew :backend:bootJar)
#   2. copies it + the files in this folder to ~/nexus on the Oracle machine (scp)
#   3. runs setup.sh there (ssh), which starts or restarts everything
# Windows 10/11 already include ssh and scp, nothing to install.
# =====================================================================================
param(
    [string]$Ip,
    [string]$Domain,
    [string]$Key,
    [string]$User = "ubuntu"
)
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$project = Resolve-Path (Join-Path $here "..\..")
$memory = Join-Path $here "server.local.json"

# Use what was given, or what was remembered last time.
if (Test-Path $memory) {
    $saved = Get-Content $memory -Raw | ConvertFrom-Json
    if (-not $Ip) { $Ip = $saved.Ip }
    if (-not $Domain) { $Domain = $saved.Domain }
    if (-not $Key) { $Key = $saved.Key }
    if ($saved.User -and -not $PSBoundParameters.ContainsKey("User")) { $User = $saved.User }
}
if (-not $Ip -or -not $Domain -or -not $Key) {
    Write-Host "First time? Give all three: -Ip <public IP> -Domain <yourname.duckdns.org> -Key <path to the .key file>" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path $Key)) {
    Write-Host "Can't find the key file: $Key" -ForegroundColor Red
    exit 1
}
@{ Ip = $Ip; Domain = $Domain; Key = $Key; User = $User } | ConvertTo-Json | Out-File $memory -Encoding utf8

# ssh refuses keys that other Windows users could read – make the key readable only by you.
icacls $Key /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null

$target = "$User@$Ip"
$sshOptions = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new")

Write-Host "== 1/3 Building the Nexus server" -ForegroundColor Cyan
Push-Location $project
& .\gradlew.bat :backend:bootJar -q
if ($LASTEXITCODE -ne 0) { Pop-Location; Write-Host "Build failed." -ForegroundColor Red; exit 1 }
Pop-Location
$jar = Get-ChildItem (Join-Path $project "backend\build\libs") -Filter "backend-*.jar" |
        Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1

Write-Host "== 2/3 Copying to $target" -ForegroundColor Cyan
& ssh @sshOptions $target "mkdir -p ~/nexus"
if ($LASTEXITCODE -ne 0) { Write-Host "Can't connect with ssh. Is the IP right, and is the VM running?" -ForegroundColor Red; exit 1 }
$files = @("docker-compose.yml", "Caddyfile", "setup.sh", "backup.sh") | ForEach-Object { Join-Path $here $_ }
& scp @sshOptions @files "${target}:~/nexus/"
& scp @sshOptions $jar.FullName "${target}:~/nexus/nexus-server.jar"
if ($LASTEXITCODE -ne 0) { Write-Host "Copying failed." -ForegroundColor Red; exit 1 }

Write-Host "== 3/3 Starting it on the server" -ForegroundColor Cyan
# (sed removes Windows line endings, just in case, so bash can read the scripts.)
& ssh @sshOptions $target "sed -i 's/\r$//' ~/nexus/*.sh ~/nexus/Caddyfile ~/nexus/docker-compose.yml && bash ~/nexus/setup.sh $Domain"
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Done! The Nexus server is at https://$Domain" -ForegroundColor Green
}
