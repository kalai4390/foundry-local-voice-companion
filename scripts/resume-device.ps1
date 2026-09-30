param(
    [switch]$RunAudio,
    [switch]$RunDebrief,
    [switch]$RunVoice,
    [string]$ValidationPath,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe"
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion -lt [version]'7.4') { throw 'Use PowerShell 7.4 or newer.' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$manifestPath = if ($ValidationPath) { [IO.Path]::GetFullPath($ValidationPath) } else { Join-Path $root 'docs\validation-results.json' }
foreach ($required in @($AdbPath, $apk, $manifestPath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required local file is missing: $required" }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$localHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($localHash -ne $manifest.apk.sha256) {
    throw 'The APK no longer matches the retained validation manifest. Build and validate this revision before installing it.'
}
$deviceLines = @(& $AdbPath devices)
if ($LASTEXITCODE -ne 0) { throw 'ADB could not enumerate devices.' }
$devices = @($deviceLines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -ne 1) { throw "Reconnect exactly one authorized, unlocked Android phone. Found $($devices.Count) authorized devices." }
$device = $devices[0]
$package = 'dev.daylight.local'
$directory = Join-Path $root 'runtime\results'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$runId = 'reconnect-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$reportPath = Join-Path $directory "$runId.json"
$report = [ordered]@{
    atUtc = [DateTime]::UtcNow.ToString('o')
    package = $package
    version = $manifest.version
    localApkSha256 = $localHash
    installedApkSha256 = $null
    installedHashMatches = $false
    status = 'started'
    requestedTests = @('smoke'; if ($RunAudio) { 'audio' }; if ($RunDebrief) { 'debrief' }; if ($RunVoice) { 'voice' })
    modelDownloads = 'None requested; cached packages only'
    privacy = 'No health reads, grants, microphone use, playback, account sync, radio changes or unlocking requested by this script'
    quality = 'Runtime completion is separate from per-test accuracy and output-contract results'
}
try {
    Write-Output "Installing validated Ami $($manifest.version) without uninstalling or granting permissions..."
    $install = (& $AdbPath -s $device install -r $apk 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $install -match 'Failure|INSTALL_FAILED') {
        throw 'Install failed. Existing app data was not cleared; do not uninstall automatically to work around signing or version errors.'
    }
    $packagePaths = @(& $AdbPath -s $device shell pm path $package)
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the installed APK.' }
    $baseApk = @($packagePaths | Where-Object { $_ -match '^package:(/data/app/[A-Za-z0-9_./~+=-]+/base\.apk)$' })
    if ($baseApk.Count -ne 1) { throw 'Could not safely identify exactly one installed base APK.' }
    $remotePath = $baseApk[0].Substring('package:'.Length)
    $hashOutput = (& $AdbPath -s $device shell sha256sum $remotePath) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $hashOutput -notmatch '^([0-9a-fA-F]{64})\s') { throw 'Installed APK hashing failed.' }
    $report.installedApkSha256 = $Matches[1].ToLowerInvariant()
    $report.installedHashMatches = $report.installedApkSha256 -eq $localHash
    if (-not $report.installedHashMatches) { throw 'Installed APK does not match the locally validated artifact.' }
    foreach ($mode in $report.requestedTests) {
        & (Join-Path $PSScriptRoot 'run-device.ps1') -Mode $mode -AdbPath $AdbPath -ExpectedSerial $device -TimeoutSeconds 900
    }
    $report.status = 'runtime_checks_completed'
    Write-Output 'Synthetic runtime checks finished. Inspect ASR and recap quality fields separately; personal records and disconnected operation remain unverified.'
} catch {
    $report.status = 'failed'
    throw
} finally {
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8
    Write-Output "Reconnection record: $reportPath"
}
