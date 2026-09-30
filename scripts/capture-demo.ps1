param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\platform-tools\adb.exe",
    [Parameter(Mandatory)][string]$FfmpegPath,
    [switch]$PreventAutoLock,
    [string]$ValidationPath,
    [ValidateRange(0, 400)][int]$CropTop = 108,
    [ValidateRange(0, 400)][int]$CropBottom = 102
)

$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSVersion -lt [version]'7.4') { throw 'Use PowerShell 7.4 or newer for binary capture redirection.' }
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$devices = @(& $AdbPath devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -ne 1) { throw 'Exactly one authorized device is required.' }
$device = $devices[0]
$package = 'dev.daylight.local'
$runId = 'publication-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$output = Join-Path $root "runtime\$runId"
New-Item -ItemType Directory -Path $output | Out-Null
$previousScreenTimeout = $null
$screenTimeoutChanged = $false
$manifest = [ordered]@{
    runId = $runId
    recordedAtUtc = [DateTime]::UtcNow.ToString('o')
    syntheticOnly = $true
    microphoneUsed = $false
    audiblePlaybackRequested = $false
    healthPermissionsChanged = $false
    radiosChanged = $false
    screenTimeout = [ordered]@{ adjustmentRequested = $PreventAutoLock.IsPresent; changed = $false; restored = $null }
    recordingMethod = 'Android screenrecord; system bars cropped; 30-fps resampling without speed adjustment; final still held to source duration; no inserted answers'
    screenshotMethod = 'Android screencap, cropped to exclude system bars'
    portraitSource = @{ width = 1080; height = 2400; cropTop = $CropTop; cropBottom = $CropBottom }
    clips = @()
    captures = @()
    status = 'started'
}

function Assert-DaylightForeground {
    $focus = (& $AdbPath -s $device shell dumpsys window | Select-String 'mCurrentFocus=') -join ''
    $power = (& $AdbPath -s $device shell dumpsys power | Select-String 'mWakefulness=') -join ''
    if ($focus -notmatch 'dev\.daylight\.local/dev\.daylight\.local\.MainActivity' -or $power -notmatch 'mWakefulness=Awake') {
        throw 'Ami must remain unlocked and foreground. No wake, unlock or foreground takeover was attempted.'
    }
}

function Assert-SyntheticView {
    Assert-DaylightForeground
    $remoteUi = "/data/local/tmp/$runId-ui.xml"
    try {
        & $AdbPath -s $device shell uiautomator dump $remoteUi | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the current synthetic view.' }
        [xml]$tree = ((& $AdbPath -s $device exec-out cat $remoteUi) -join "`n")
        $badge = @($tree.SelectNodes('//node') | Where-Object { $_.package -eq $package -and $_.text -match '^DEMO\s+SYNTHETIC$' })
        if ($badge.Count -ne 1) { throw 'No unique synthetic-mode badge; capture refused.' }
    } finally {
        & $AdbPath -s $device shell rm -f $remoteUi | Out-Null
    }
}

function Invoke-SyntheticRun([string]$Mode, [string]$Screen = 'TODAY') {
    Assert-DaylightForeground
    $begun = [DateTime]::UtcNow
    & (Join-Path $PSScriptRoot 'run-device.ps1') -Mode $Mode -Screen $Screen -AdbPath $AdbPath -ExpectedSerial $device -TimeoutSeconds 180 | Out-Host
    $log = Get-ChildItem (Join-Path $root 'runtime\results') -Filter "$Mode-*.jsonl" |
        Where-Object { $_.LastWriteTimeUtc -ge $begun.AddSeconds(-1) } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $log) { throw "No new $Mode evidence." }
    $events = @(Get-Content -LiteralPath $log.FullName | ForEach-Object { $_ | ConvertFrom-Json })
    $completed = $events | Where-Object event -eq 'completed' | Select-Object -Last 1
    if (-not $completed -or $completed.status -ne 'runtime_pass' -or $completed.healthReadAttempts -ne 0 -or
        @($events | Where-Object { $_.syntheticOnly -ne $true }).Count -ne 0) { throw 'Synthetic completion/health-read guard failed.' }
    Assert-DaylightForeground
    Copy-Item -LiteralPath $log.FullName -Destination (Join-Path $output $log.Name)
    return $log.FullName
}

function Save-Screenshot([string]$Name) {
    Assert-SyntheticView
    $raw = Join-Path $output "$Name-raw.png"
    & $AdbPath -s $device exec-out screencap -p > $raw
    if ($LASTEXITCODE -ne 0) { throw 'Device screenshot failed.' }
    Assert-DaylightForeground
    $target = Join-Path $output "$Name.png"
    & $FfmpegPath -hide_banner -loglevel error -n -i $raw -vf "crop=1080:$(2400 - $CropTop - $CropBottom):0:$CropTop" -frames:v 1 $target
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot crop failed.' }
    $manifest.captures += @{ file = "$Name.png"; sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() }
}

function Record-SyntheticRun([string]$Mode) {
    Invoke-SyntheticRun 'welcome' | Out-Null
    Assert-SyntheticView
    $remoteVideo = "/data/local/tmp/$runId-$Mode.mp4"
    $raw = Join-Path $output "$Mode-raw.mp4"
    $stderr = Join-Path $output "$Mode-screenrecord.stderr.log"
    $stdout = Join-Path $output "$Mode-screenrecord.stdout.log"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = Start-Process -FilePath $AdbPath -ArgumentList @('-s', $device, 'shell', 'screenrecord', '--time-limit', '28', '--size', '720x1600', '--bit-rate', '2500000', $remoteVideo) -PassThru -WindowStyle Hidden -RedirectStandardError $stderr -RedirectStandardOutput $stdout
    try {
        Start-Sleep -Seconds 2
        if ($process.HasExited) { throw 'Device screenrecord exited before the demonstration.' }
        $inferenceStartedMs = $watch.ElapsedMilliseconds
        $log = Invoke-SyntheticRun $Mode
        $inferenceFinishedMs = $watch.ElapsedMilliseconds
        Save-Screenshot $Mode
        while (-not $process.HasExited) {
            Assert-DaylightForeground
            if ($watch.Elapsed.TotalSeconds -gt 45) { throw 'Screenrecord exceeded its bounded duration.' }
            Start-Sleep -Milliseconds 800
        }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'Device recording failed.' }
        & $AdbPath -s $device pull $remoteVideo $raw | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot transfer the synthetic recording.' }
        $target = Join-Path $output "$Mode.mp4"
        $scaledTop = [int](2 * [Math]::Floor(($CropTop * 2 / 3) / 2))
        $scaledHeight = [int](2 * [Math]::Floor(((2400 - $CropTop - $CropBottom) * 2 / 3) / 2))
        $video = & (Join-Path $PSScriptRoot 'prepare-demo-video.ps1') -InputPath $raw -OutputPath $target -FfmpegPath $FfmpegPath `
            -CropTop $scaledTop -CropBottom (1600 - $scaledTop - $scaledHeight)
        $manifest.clips += @{
            file = "$Mode.mp4"
            evidence = Split-Path -Leaf $log
            inferenceCommandStartedMs = $inferenceStartedMs
            inferenceCommandFinishedMs = $inferenceFinishedMs
            captureStartedAt = 'Before the test command; timings include host/ADB setup, not a latency benchmark'
            soundtrack = 'None; actual phone screen recording'
            sourceDurationSeconds = $video.sourceDurationSeconds
            durationSeconds = $video.durationSeconds
            durationDeltaMs = $video.durationDeltaMs
            frameRate = $video.frameRate
            transform = $video.transform
            sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    } finally {
        if (-not $process.HasExited) { $process.WaitForExit(30000) | Out-Null }
        if ($process.HasExited) { & $AdbPath -s $device shell rm -f $remoteVideo | Out-Null }
    }
}

try {
    Assert-DaylightForeground
    if ($PreventAutoLock) {
        $timeoutOutput = ((& $AdbPath -s $device shell settings get system screen_off_timeout) -join '').Trim()
        if ($LASTEXITCODE -ne 0 -or $timeoutOutput -notmatch '^\d{1,10}$') { throw 'Cannot safely preserve the original screen timeout.' }
        $previousScreenTimeout = [long]$timeoutOutput
        $manifest.screenTimeout.originalMilliseconds = $previousScreenTimeout
        if ($previousScreenTimeout -lt 180000) {
            $screenTimeoutChanged = $true
            $manifest.screenTimeout.changed = $true
            & $AdbPath -s $device shell settings put system screen_off_timeout 180000
            if ($LASTEXITCODE -ne 0) { throw 'Cannot temporarily extend the screen timeout.' }
            $manifest.screenTimeout.temporaryMilliseconds = 180000
        }
    }
    $size = (& $AdbPath -s $device shell wm size) -join ''
    if ($size -notmatch 'Physical size: 1080x2400' -or $size -match 'Override') { throw 'This capture profile requires the unmodified 1080x2400 Motorola portrait display.' }
    $manifestPath = if ($ValidationPath) { [IO.Path]::GetFullPath($ValidationPath) } else { Join-Path $root 'docs\validation-results.json' }
    $validation = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $installedPath = ((& $AdbPath -s $device shell pm path $package) -join '').Trim()
    if ($installedPath -notmatch '^package:(/data/app/[A-Za-z0-9_./~+=-]+/base\.apk)$') { throw 'Cannot identify the installed app.' }
    $installedHash = ((& $AdbPath -s $device shell sha256sum $Matches[1]) -join '').Split(' ')[0]
    if ($installedHash -ne $validation.apk.sha256) { throw 'Installed APK differs from the validation manifest.' }
    $manifest.apkSha256 = $installedHash
    $manifest.version = $validation.version
    Record-SyntheticRun 'audio'
    Record-SyntheticRun 'debrief'
    foreach ($screen in @('TODAY', 'EVIDENCE', 'PRIVACY')) {
        Invoke-SyntheticRun 'capture' $screen | Out-Null
        Save-Screenshot $screen.ToLowerInvariant()
    }
    $manifest.status = 'captured_pending_visual_review'
} catch {
    $manifest.status = 'failed'
    $manifest.failure = $_.Exception.Message
    throw
} finally {
    $restoreFailed = $false
    if ($screenTimeoutChanged) {
        & $AdbPath -s $device shell settings put system screen_off_timeout $previousScreenTimeout
        $restoredTimeout = ((& $AdbPath -s $device shell settings get system screen_off_timeout) -join '').Trim()
        $manifest.screenTimeout.restored = $LASTEXITCODE -eq 0 -and $restoredTimeout -ceq [string]$previousScreenTimeout
        if (-not $manifest.screenTimeout.restored) {
            $restoreFailed = $true
            $manifest.status = 'failed_screen_timeout_restore'
        }
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $output 'capture-manifest.json') -Encoding utf8
    Write-Output "Capture workspace: $output"
    if ($restoreFailed) { throw "Screen-timeout restoration failed. Restore screen_off_timeout to $previousScreenTimeout using the recorded authorized device before another capture." }
}
