param(
    [string]$Destination,
    [string[]]$PrivateDeviceIdentifiers = @(),
    [string]$ApprovedRepositoryUrl
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$approvedRepositoryName = $null
if ($ApprovedRepositoryUrl) {
    if ($ApprovedRepositoryUrl -cnotmatch '^https://github\.com/([A-Za-z0-9][A-Za-z0-9-]*)/([A-Za-z0-9][A-Za-z0-9._-]*)$') {
        throw 'Approve one exact HTTPS GitHub owner/repository URL, without credentials, query or fragment.'
    }
    $approvedRepositoryName = "$($Matches[1])/$($Matches[2])"
}
if (-not $Destination) {
    $Destination = Join-Path $root ('../publication/ami-foundry-local-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
}
$destinationPath = [IO.Path]::GetFullPath($Destination)
$zipPath = "$destinationPath.zip"
if (Test-Path -LiteralPath $destinationPath) { throw 'Choose a new destination; existing work is never replaced.' }
if (Test-Path -LiteralPath $zipPath) { throw 'The archive already exists; choose a new destination.' }
$files = [ordered]@{
    '.gitignore' = '.gitignore'
    '.gitattributes' = '.gitattributes'
    'build.gradle.kts' = 'build.gradle.kts'
    'settings.gradle.kts' = 'settings.gradle.kts'
    'gradle.properties' = 'gradle.properties'
    'app/build.gradle.kts' = 'app/build.gradle.kts'
    'core/build.gradle.kts' = 'core/build.gradle.kts'
}
$readmeSource = if (Test-Path -LiteralPath (Join-Path $root 'REPOSITORY-README.md')) { 'REPOSITORY-README.md' } else { 'README.md' }
$files[$readmeSource] = 'README.md'
foreach ($directory in @('app/src', 'core/src', 'scripts')) {
    foreach ($item in (Get-ChildItem -LiteralPath (Join-Path $root $directory) -Recurse -File)) {
        $relative = [IO.Path]::GetRelativePath($root, $item.FullName).Replace('\', '/')
        if ($relative -match '(^|/)\.core-validation-[^/]+/') { continue }
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse-point files are not allowed in the package.' }
        if ($item.Extension -notin @('.kt', '.java', '.xml', '.json', '.wav', '.png', '.webp', '.ps1')) {
            throw "Review this unexpected source-file type before packaging: $($item.Extension)"
        }
        $files[$relative] = $relative
    }
}
foreach ($name in @('BLOG.md', 'AUTHOR-RUNBOOK.md', 'DEMO-GUIDE.md', 'DEVICE-FINDINGS.md', 'VALIDATION.md', 'SHOWCASE-VALIDATION.md', 'PUBLICATION-REVIEW.md', 'validation-results.json', 'architecture.svg', 'architecture.png', 'voice-flow.svg', 'index.html')) {
    $files["docs/$name"] = "docs/$name"
}
foreach ($name in @('audio.png', 'audio.mp4', 'debrief.png', 'debrief.mp4', 'today.png', 'evidence.png', 'privacy.png', 'warm.wav', 'provenance.json', 'ami-answer.png', 'ami-welcome.png', 'ami-recap.png', 'ami-today.png', 'ami-evidence.png', 'ami-privacy.png', 'ami-audio.mp4', 'ami-recap.mp4', 'ami-warm.wav', 'ami-provenance.json')) {
    $files["docs/demo/$name"] = "docs/demo/$name"
}
$files['docs/evidence/ami-phone-validation.json'] = 'docs/evidence/ami-phone-validation.json'
$files['docs/evidence/ami-capture-validation.json'] = 'docs/evidence/ami-capture-validation.json'
$files['docs/evidence/ami-device-followup.json'] = 'docs/evidence/ami-device-followup.json'
$files['docs/evidence/ami-reconnect-validation.json'] = 'docs/evidence/ami-reconnect-validation.json'
foreach ($name in @('ami-microphone.mp4', 'ami-microphone-preview.mp4', 'ami-microphone.png', 'ami-microphone-provenance.json')) {
    $files["docs/demo/$name"] = "docs/demo/$name"
}
if (Test-Path -LiteralPath (Join-Path $root 'docs/evidence/reproducibility.json')) {
    $files['docs/evidence/reproducibility.json'] = 'docs/evidence/reproducibility.json'
}
New-Item -ItemType Directory -Path $destinationPath | Out-Null
foreach ($entry in $files.GetEnumerator()) {
    $source = Join-Path $root $entry.Key
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Required publication file is missing: $($entry.Key)" }
    $target = Join-Path $destinationPath $entry.Value
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}
$privateValues = @($env:USERNAME, $env:COMPUTERNAME, $env:USERPROFILE) + $PrivateDeviceIdentifiers
$privatePatterns = @($privateValues | Where-Object { $_ -and $_.Length -ge 4 } | ForEach-Object { [regex]::Escape($_) })
$textExtensions = @('.md', '.kt', '.java', '.kts', '.ps1', '.xml', '.json', '.html', '.svg', '.properties')
$localLinksChecked = 0
foreach ($file in (Get-ChildItem -LiteralPath $destinationPath -File -Recurse)) {
    if ($file.Extension -notin $textExtensions -and $file.Name -notin @('.gitignore', '.gitattributes')) { continue }
    $content = [IO.File]::ReadAllText($file.FullName)
    $ownerScanContent = if ($ApprovedRepositoryUrl) {
        $content.Replace($ApprovedRepositoryUrl, '[approved-repository-url]').Replace($approvedRepositoryName, '[approved-repository-name]')
    } else { $content }
    foreach ($pattern in $privatePatterns) {
        if ($ownerScanContent -match $pattern) { throw "Owner/device metadata found in $([IO.Path]::GetRelativePath($destinationPath, $file.FullName)). No archive was created." }
    }
    if ($file.Extension -notin @('.md', '.html')) { continue }
    $targets = @([regex]::Matches($content, '\]\(([^\s)]+)\)') | ForEach-Object { $_.Groups[1].Value })
    $targets += @([regex]::Matches($content, '(?:href|src|poster)\s*=\s*"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
    foreach ($target in $targets) {
        if ($target -match '^(https?://|mailto:|data:|#)') { continue }
        if ($target -match '^(file:|[A-Za-z]:|/|\\)') { throw 'Absolute local links are not portable publication links.' }
        $relative = [Uri]::UnescapeDataString(($target -split '[#?]', 2)[0])
        $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName $relative))
        if (-not $resolved.StartsWith($destinationPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $resolved)) {
            throw "Broken or outside-package link in $([IO.Path]::GetRelativePath($destinationPath, $file.FullName)): $target"
        }
        $localLinksChecked++
    }
}
foreach ($provenanceFile in @('provenance.json', 'ami-provenance.json', 'ami-microphone-provenance.json')) {
    $provenance = Get-Content -LiteralPath (Join-Path $destinationPath "docs/demo/$provenanceFile") -Raw | ConvertFrom-Json
    foreach ($asset in $provenance.assets) {
        if ($asset.file -notmatch '^[a-z0-9-]+\.(png|mp4|wav)$') { throw 'Unexpected media manifest path.' }
        $path = Join-Path $destinationPath "docs/demo/$($asset.file)"
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $asset.sha256 -or
            (Get-Item -LiteralPath $path).Length -ne $asset.bytes) { throw "Media checksum mismatch: $($asset.file)" }
    }
}
$reproducedInputsMatch = $null
$reproductionPath = Join-Path $destinationPath 'docs/evidence/reproducibility.json'
if (Test-Path -LiteralPath $reproductionPath) {
    $reproduction = Get-Content -LiteralPath $reproductionPath -Raw | ConvertFrom-Json
    if ($reproduction.status -ne 'passed' -or @($reproduction.buildInputs).Count -eq 0) { throw 'Invalid source-reproduction evidence.' }
    foreach ($inputFile in $reproduction.buildInputs) {
        $path = [IO.Path]::GetFullPath((Join-Path $destinationPath $inputFile.path))
        if (-not $path.StartsWith($destinationPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $inputFile.sha256) {
            throw 'Build inputs changed after clean-source validation; regenerate the evidence before packaging.'
        }
    }
    $digestLines = ($reproduction.buildInputs | Sort-Object path | ForEach-Object { "$($_.sha256)  $($_.path)" }) -join "`n"
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($digestLines))).ToLowerInvariant()
    if ($digest -cne $reproduction.buildInputsSha256) { throw 'Build-input digest mismatch.' }
    $reproducedInputsMatch = $true
}
$records = @(Get-ChildItem -LiteralPath $destinationPath -File -Recurse | Sort-Object FullName | ForEach-Object {
    [ordered]@{ path = [IO.Path]::GetRelativePath($destinationPath, $_.FullName).Replace('\', '/'); bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
})
$manifest = [ordered]@{
    product = 'Ami'
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    policy = 'Explicit source/document/media allowlist; no Git history, runtime logs, SDK/model binaries, APKs, tools, caches or signing keys'
    validation = [ordered]@{ localLinksChecked = $localLinksChecked; mediaChecksumsMatch = $true; reproducedBuildInputsMatch = $reproducedInputsMatch; configuredOwnerIdentifierScanPassed = $true; approvedRepositoryUrl = $ApprovedRepositoryUrl; ownerScanExemption = 'Only the exact explicitly approved repository URL and owner/repository label; unrelated owner/device metadata remains blocked'; scanIsNotAComprehensiveSecretAudit = $true }
    publication = 'This packaging step writes a local archive only. Remote publication requires a separate authorized operation; no license grant is implied.'
    fileCountExcludingThisManifest = $records.Count
    files = $records
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $destinationPath 'PACKAGE-MANIFEST.json') -Encoding utf8
[IO.Compression.ZipFile]::CreateFromDirectory($destinationPath, $zipPath, [IO.Compression.CompressionLevel]::Optimal, $true)
[ordered]@{ directory = $destinationPath; archive = $zipPath; files = $records.Count + 1; localLinksChecked = $localLinksChecked; archiveSha256 = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant() } | ConvertTo-Json
