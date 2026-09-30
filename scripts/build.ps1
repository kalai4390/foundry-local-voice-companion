param(
    [string[]]$Tasks = @(':core:test', ':app:assembleDebug', ':app:lintDebug'),
    [string]$GradlePath,
    [string]$FoundryAar
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem -LiteralPath "$env:ProgramFiles\Microsoft" -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-17*' | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to JDK 17 before building.'
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android' }
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    throw 'Install Android SDK platform 36 and accept its SDK licenses before building.'
}
if (-not $GradlePath) {
    $candidates = @(
        (Join-Path $root '.tools\gradle-8.13\bin\gradle.bat'),
        (Join-Path $root '..\feasibility\runtime\android-build\gradle-8.13\bin\gradle.bat')
    )
    $GradlePath = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $GradlePath) { throw 'Install Gradle 8.13 in .tools, or pass -GradlePath. See README.' }
if (-not $FoundryAar) {
    $candidates = @(
        (Join-Path $root '.tools\foundry-local-ipc-sdk-0.1.6.aar'),
        (Join-Path $root '..\feasibility\runtime\android-artifacts\foundry-local-ipc-sdk-0.1.6.aar')
    )
    $FoundryAar = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $FoundryAar) { throw 'Acquire the official Foundry Local IPC 0.1.6 AAR first; see README.' }
$FoundryAar = (Resolve-Path -LiteralPath $FoundryAar).Path
Push-Location -LiteralPath $root
try {
    & $GradlePath --no-daemon --console=plain "-PfoundryAar=$FoundryAar" @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
