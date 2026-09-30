param(
    [ValidateSet('smoke', 'catalog', 'provision', 'routing', 'audio', 'debrief', 'voice', 'capture', 'welcome')]
    [string]$Mode = 'smoke',
    [ValidateSet('TODAY', 'TALK', 'EVIDENCE', 'PRIVACY')]
    [string]$Screen = 'TODAY',
    [switch]$AllowDownload,
    [ValidateRange(10, 1800)][int]$TimeoutSeconds = 360,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe",
    [string]$ExpectedSerial
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion -lt [version]'7.4') { throw 'Use PowerShell 7.4 or newer for byte-preserving native capture redirection.' }
if ($Mode -eq 'provision' -and -not $AllowDownload) { throw 'Provisioning requires -AllowDownload and the phone-side foreground/unmetered-Wi-Fi guard.' }
$package = 'dev.daylight.local'
$devices = @(& $AdbPath devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -ne 1) { throw "Expected exactly one authorized device; found $($devices.Count)." }
$device = $devices[0]
if ($ExpectedSerial -and $device -cne $ExpectedSerial) { throw 'The authorized device changed between installation and testing.' }
$runId = $Mode + '-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$directory = Join-Path $root 'runtime\results'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$output = Join-Path $directory "$runId.jsonl"
$arguments = @('-s', $device, 'shell', 'am', 'broadcast', '--include-stopped-packages', '-a', "$package.RUN_SYNTHETIC_TEST", '-n', "$package/.DebugReceiver", '--es', 'command', $Mode,
    '--es', 'run_id', $runId, '--es', 'screen', $Screen, '--ez', 'allow_download', $AllowDownload.IsPresent.ToString().ToLowerInvariant())
$queued = (& $AdbPath @arguments 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or -not $queued.Contains("queued:$runId")) { throw "Synthetic request was not acknowledged: $queued" }
$launched = (& $AdbPath -s $device shell am start -W --activity-clear-top -n "$package/.MainActivity" 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or $launched -match 'Error:|SecurityException') { throw "Debug launch failed: $launched" }
$watch = [Diagnostics.Stopwatch]::StartNew()
$events = @()
$completed = $null
$startupTimedOut = $false
$samples = [Collections.Generic.List[object]]::new()
while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
    $raw = @(& $AdbPath -s $device shell run-as $package cat "files/evidence/$runId.jsonl" 2>$null)
    if ($LASTEXITCODE -eq 0 -and $raw.Count -gt 0) {
        $events = @($raw | Where-Object { $_.StartsWith('{') -and $_.EndsWith('}') } | ForEach-Object { $_ | ConvertFrom-Json })
        $raw | Set-Content -LiteralPath $output -Encoding utf8
        $completed = $events | Where-Object event -eq 'completed' | Select-Object -Last 1
    }
    if ($completed) { break }
    if ($events.Count -eq 0) {
        if ($watch.Elapsed.TotalSeconds -ge [Math]::Min(30, $TimeoutSeconds)) { $startupTimedOut = $true; break }
        Start-Sleep -Seconds 1
        continue
    }
    $sample = [ordered]@{ elapsedMs = $watch.ElapsedMilliseconds }
    foreach ($processName in @($package, 'com.microsoft.foundrylocal.app', 'com.microsoft.foundrylocal.app:foundryservice')) {
        $memory = (& $AdbPath -s $device shell dumpsys meminfo -s $processName 2>&1) -join "`n"
        $totals = [regex]::Matches($memory, 'TOTAL PSS:\s*(\d+)')
        $sample[$processName] = if ($totals.Count -gt 0) { [long](($totals | ForEach-Object { [long]$_.Groups[1].Value } | Measure-Object -Sum).Sum) } else { $null }
    }
    $samples.Add($sample)
    Start-Sleep -Seconds 2
}
$stoppedOnTimeout = $false
if (-not $completed) {
    & $AdbPath -s $device shell am force-stop $package
    $stoppedOnTimeout = $LASTEXITCODE -eq 0
}
$hostOutput = Join-Path $directory "$runId-host.json"
[ordered]@{
    runId = $runId
    command = $Mode
    status = if ($completed) { $completed.status } elseif ($startupTimedOut) { 'app_start_timeout' } else { 'host_timeout' }
    applicationEventsObserved = $events.Count
    applicationEvidenceWritten = Test-Path -LiteralPath $output
    daylightStoppedOnTimeout = $stoppedOnTimeout
    queueOutput = $queued
    launchOutput = $launched
    networkIsolation = 'Not established; phone radios unchanged'
    healthData = 'Synthetic fixture only; no permission grants or real-record requests issued by this script'
    memorySampling = 'Sequential app, companion UI and service PSS in KiB; not a true peak or whole-device measurement'
    samples = $samples.ToArray()
} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $hostOutput -Encoding utf8

if ($Mode -in @('capture', 'audio', 'debrief', 'welcome')) {
    $capture = $events | Where-Object event -eq 'view_capture' | Select-Object -Last 1
    if ($capture -and $capture.file -match ('^' + [regex]::Escape($runId) + '-(today|talk|evidence|privacy)\.png$')) {
        $target = Join-Path $directory $capture.file
        & $AdbPath -s $device exec-out run-as $package cat "files/evidence/$($capture.file)" > $target
        if ($LASTEXITCODE -ne 0) { throw 'App-only capture transfer failed.' }
        $bytes = [IO.File]::ReadAllBytes($target)
        if ($bytes.Length -lt 8 -or [BitConverter]::ToString($bytes[0..7]) -ne '89-50-4E-47-0D-0A-1A-0A') {
            throw 'Transferred bytes are not a PNG. Do not use this capture.'
        }
        Write-Output "Synthetic app-view capture: $target"
    }
}
if (Test-Path -LiteralPath $output) { Write-Output "Evidence: $output" }
else { Write-Warning 'No application evidence file was produced.' }
Write-Output "Host report: $hostOutput"
if ($Mode -eq 'voice') {
    foreach ($preview in @($events | Where-Object event -eq 'voice_preview_file')) {
        if ($preview.file -notmatch ('^' + [regex]::Escape($runId) + '-(warm|clear|unhurried)\.wav$')) { throw 'Unexpected synthetic preview filename.' }
        $target = Join-Path $directory $preview.file
        & $AdbPath -s $device exec-out run-as $package cat "files/evidence/$($preview.file)" > $target
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic voice-preview transfer failed.' }
        $bytes = [IO.File]::ReadAllBytes($target)
        if ($bytes.Length -ne $preview.bytes -or $bytes.Length -lt 44 -or
            [Text.Encoding]::ASCII.GetString($bytes, 0, 4) -ne 'RIFF' -or [Text.Encoding]::ASCII.GetString($bytes, 8, 4) -ne 'WAVE') {
            throw 'Transferred voice preview is not the expected WAV. Do not publish it.'
        }
        Write-Output "Synthetic offline voice preview: $target"
    }
}
$events | Where-Object { $_.event -match 'summary|completed|tts_voice|tts_file' } | ConvertTo-Json -Depth 6
if (-not $completed -or $completed.status -ne 'runtime_pass') { throw 'Device run did not complete successfully; inspect the saved evidence.' }
