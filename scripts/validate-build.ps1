param(
    [string]$GradlePath,
    [string]$FoundryAar
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tasks = @(':core:test', ':app:assembleDebug', ':app:lintDebug', ':app:assembleRelease', ':app:lintRelease')
& (Join-Path $PSScriptRoot 'build.ps1') -Tasks $tasks -GradlePath $GradlePath -FoundryAar $FoundryAar

$testFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'core/build/test-results/test') -Filter 'TEST-*.xml' | Sort-Object Name)
if ($testFiles.Count -eq 0) { throw 'No JVM test reports were produced.' }
$suites = @($testFiles | ForEach-Object {
    [xml]$document = Get-Content -LiteralPath $_.FullName -Raw
    $suite = $document.testsuite
    if (-not $suite) { throw 'Invalid JVM test report.' }
    [ordered]@{ name = [string]$suite.name; tests = [int]$suite.tests; failures = [int]$suite.failures; errors = [int]$suite.errors; skipped = [int]$suite.skipped }
})
if (@($suites | Where-Object { $_.failures -gt 0 -or $_.errors -gt 0 -or $_.skipped -gt 0 }).Count -gt 0) {
    throw 'Failed, errored or skipped tests prevent a validated capture build.'
}
$testCount = [int](($suites | ForEach-Object { [int]$_.tests } | Measure-Object -Sum).Sum)
if ($testCount -lt 1) { throw 'The validation report must contain executed JVM tests.' }
$lint = @{}
foreach ($variant in @('debug', 'release')) {
    [xml]$report = Get-Content -LiteralPath (Join-Path $root "app/build/reports/lint-results-$variant.xml") -Raw
    $issues = @($report.issues.issue)
    $errors = @($issues | Where-Object { $_.severity -in @('Error', 'Fatal') }).Count
    $warnings = @($issues | Where-Object severity -eq 'Warning').Count
    if ($errors -gt 0) { throw "Lint errors prevent validation of $variant." }
    $lint[$variant] = "$errors errors, $warnings warnings"
}
$debugMetadata = Get-Content -LiteralPath (Join-Path $root 'app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
$releaseMetadata = Get-Content -LiteralPath (Join-Path $root 'app/build/outputs/apk/release/output-metadata.json') -Raw | ConvertFrom-Json
if (@($debugMetadata.elements).Count -ne 1 -or @($releaseMetadata.elements).Count -ne 1 -or
    $debugMetadata.elements[0].outputFile -cne 'app-debug.apk' -or
    $releaseMetadata.elements[0].outputFile -cne 'app-release-unsigned.apk' -or
    $debugMetadata.applicationId -cne 'dev.daylight.local' -or
    $releaseMetadata.applicationId -cne $debugMetadata.applicationId -or
    $debugMetadata.elements[0].versionName -cne $releaseMetadata.elements[0].versionName -or
    $debugMetadata.elements[0].versionCode -ne $releaseMetadata.elements[0].versionCode) {
    throw 'Unexpected or inconsistent APK metadata.'
}
[xml]$androidManifest = Get-Content -LiteralPath (Join-Path $root 'app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml') -Raw
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$application = $androidManifest.manifest.application
$permissions = @($androidManifest.SelectNodes('/manifest/uses-permission') | ForEach-Object { $_.GetAttribute('name', $androidNamespace) })
$receiverNames = @($androidManifest.SelectNodes('/manifest/application/receiver') | ForEach-Object { $_.GetAttribute('name', $androidNamespace) })
$checks = [ordered]@{
    brandIsAmi = $application.GetAttribute('label', $androidNamespace) -ceq 'Ami'
    applicationIdPreserved = $androidManifest.manifest.GetAttribute('package') -ceq 'dev.daylight.local'
    debugReceiverAbsent = @($receiverNames | Where-Object { $_ -match 'DebugReceiver$' }).Count -eq 0
    backupDisabled = $application.GetAttribute('allowBackup', $androidNamespace) -ceq 'false'
    cleartextDisabled = $application.GetAttribute('usesCleartextTraffic', $androidNamespace) -ceq 'false'
    healthWritesAbsent = @($permissions | Where-Object { $_ -like 'android.permission.health.WRITE_*' }).Count -eq 0
    backgroundHealthPermissionAbsent = 'android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND' -notin $permissions
    heartRateReadDeclared = 'android.permission.health.READ_HEART_RATE' -in $permissions
    historyReadDeclared = 'android.permission.health.READ_HEALTH_DATA_HISTORY' -in $permissions
}
if (@($checks.Values | Where-Object { $_ -ne $true }).Count -gt 0) { throw 'Release-manifest safety checks failed.' }
function Get-ApkRecord([string]$RelativePath) {
    $path = Join-Path $root $RelativePath
    return [ordered]@{ relativePath = $RelativePath; sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant(); bytes = (Get-Item -LiteralPath $path).Length }
}
$unsignedRelease = Get-ApkRecord 'app/build/outputs/apk/release/app-release-unsigned.apk'
$unsignedRelease.distributionSigned = $false
$validation = [ordered]@{
    product = 'Ami'
    version = $debugMetadata.elements[0].versionName
    versionCode = $debugMetadata.elements[0].versionCode
    atUtc = [DateTime]::UtcNow.ToString('o')
    scope = 'Full host validation of this local build; no device results implied'
    tasks = $tasks
    hostStatus = 'passed'
    coreSuites = $suites
    coreTestCount = $testCount
    lintSummary = $lint.debug
    releaseLintSummary = $lint.release
    releaseManifestChecks = $checks
    apk = Get-ApkRecord 'app/build/outputs/apk/debug/app-debug.apk'
    unsignedReleaseApk = $unsignedRelease
    phone = [ordered]@{ status = 'not_replayed_for_this_local_build'; personalHeartRateVerified = $false; liveMicrophoneOrAudibleQualityVerified = $false; radioIsolationEstablished = $false }
    privacy = 'No owner identifiers, raw build logs, credentials or personal records are included.'
}
$directory = Join-Path $root 'runtime'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$output = Join-Path $directory 'build-validation.json'
$validation | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $output -Encoding utf8
Write-Output "Validated $($validation.coreTestCount) JVM tests, both builds and lint. Local capture manifest: $output"
Write-Output 'The published docs/validation-results.json is unchanged. Pass -ValidationPath runtime/build-validation.json when replaying or capturing this local APK.'
