param(
    [string]$Root = (Join-Path $PSScriptRoot '..'),
    [string]$RepositoryUrl = 'https://github.com/kalai4390/foundry-local-voice-companion',
    [switch]$CheckPublicAccess
)

$ErrorActionPreference = 'Stop'
$rootPath = [IO.Path]::GetFullPath($Root)
$repositoryUri = [Uri]$RepositoryUrl
if ($repositoryUri.Scheme -ne 'https' -or $repositoryUri.Host -ne 'github.com' -or
    $repositoryUri.AbsolutePath -notmatch '^/[^/]+/[^/]+$') { throw 'Use an HTTPS GitHub repository URL.' }
$repositoryName = $repositoryUri.AbsolutePath.Trim('/')
$files = @(Get-ChildItem -LiteralPath $rootPath -Filter '*.md' -File) +
    @(Get-ChildItem -LiteralPath (Join-Path $rootPath 'docs') -File -Recurse |
        Where-Object { $_.Extension -in @('.md', '.html', '.svg') })
$failures = [Collections.Generic.List[string]]::new()
$checked = 0
$external = [Collections.Generic.HashSet[string]]::new()
$publicUrls = [Collections.Generic.HashSet[string]]::new()
foreach ($file in $files) {
    $content = if ($file.Extension -eq '.md') {
        (ConvertFrom-Markdown -LiteralPath $file.FullName).Html
    } else { [IO.File]::ReadAllText($file.FullName) }
    $references = @([regex]::Matches($content, '(?:href|src|poster)="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value })
    foreach ($reference in $references) {
        $target = [Net.WebUtility]::HtmlDecode($reference)
        if ($target -match '^(data:|mailto:|#)') { continue }
        $label = [IO.Path]::GetRelativePath($rootPath, $file.FullName)
        if ($target -match '^(file:|vscode:|[A-Za-z]:|/|\\)' -or
            $target -match '^https?://(localhost|127\.0\.0\.1)([:/]|$)') {
            $failures.Add("${label}: computer-local link $target")
            continue
        }
        $relative = $null
        $fragment = ''
        if ($target -match '^https?://') {
            [void]$publicUrls.Add($target)
            $uri = [Uri]$target
            $fragment = $uri.Fragment
            $prefix = '/' + $repositoryName + '/'
            if ($uri.Host -eq 'github.com' -and $uri.AbsolutePath.StartsWith($prefix)) {
                $tail = $uri.AbsolutePath.Substring($prefix.Length)
                if ($tail -match '^(blob|tree)/main/(.+)$') { $relative = $Matches[2] }
                elseif ($tail -match '^(issues|pull|releases|actions)(/|$)') { [void]$external.Add($target); continue }
                else { $failures.Add("${label}: unexpected repository path $target"); continue }
            } elseif ($uri.Host -eq 'raw.githubusercontent.com' -and $uri.AbsolutePath.StartsWith($prefix + 'main/')) {
                $relative = $uri.AbsolutePath.Substring(($prefix + 'main/').Length)
            } else { [void]$external.Add($target); continue }
            $resolved = [IO.Path]::GetFullPath((Join-Path $rootPath ([Uri]::UnescapeDataString($relative))))
        } else {
            $parts = $target -split '#', 2
            if ($parts.Count -gt 1) { $fragment = '#' + $parts[1] }
            $resolved = [IO.Path]::GetFullPath((Join-Path $file.DirectoryName ([Uri]::UnescapeDataString($parts[0]))))
            if ($file.Name -in @('BLOG-PUBLISH.md', 'BLOG.html', 'index.html')) {
                $failures.Add("${label}: article link must be a full GitHub URL: $target")
            }
        }
        if (-not $resolved.StartsWith($rootPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $resolved)) {
            $failures.Add("${label}: missing repository target $target")
            continue
        }
        if ($fragment -and [IO.Path]::GetExtension($resolved) -eq '.md') {
            $headings = @(Select-String -LiteralPath $resolved -Pattern '^#{1,6}\s+(.+)$' | ForEach-Object {
                $heading = $_.Matches[0].Groups[1].Value.ToLowerInvariant().Trim()
                ($heading -replace '[^\p{L}\p{N}\s_-]', '') -replace '\s', '-'
            })
            if ([Uri]::UnescapeDataString($fragment.TrimStart('#')) -notin $headings) {
                $failures.Add("${label}: missing heading $target")
            }
        }
        $checked++
    }
}
if ($failures.Count) { throw ($failures -join "`n") }
$publicChecked = 0
if ($CheckPublicAccess) {
    $response = Invoke-WebRequest -Uri $RepositoryUrl -SkipHttpErrorCheck -TimeoutSec 20
    if ($response.StatusCode -ne 200) {
        throw "Public access check failed: $RepositoryUrl returned HTTP $($response.StatusCode) without authentication. File-target checks do not establish reader access. Check repository visibility before publishing."
    }
    foreach ($url in ($publicUrls | Sort-Object)) {
        $response = Invoke-WebRequest -Uri $url -SkipHttpErrorCheck -TimeoutSec 20
        if ($response.StatusCode -ne 200) { throw "Public access check failed: $url returned HTTP $($response.StatusCode)." }
        $publicChecked++
    }
}
[pscustomobject]@{ Documents = $files.Count; RepositoryLinksChecked = $checked; PublicAccessChecked = [bool]$CheckPublicAccess; PublicUrlsChecked = $publicChecked; ExternalLinks = @($external | Sort-Object) }
