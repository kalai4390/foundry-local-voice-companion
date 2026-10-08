param(
    [string]$RepositoryUrl = 'https://github.com/kalai4390/ami-foundry-local',
    [ValidateSet('private', 'public')][string]$RepositoryVisibility = 'private',
    [switch]$InlineMedia
)

$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = Join-Path $root 'docs\BLOG.md'
$destination = Join-Path $root 'docs\index.html'
$heading = Get-Content -LiteralPath $source -TotalCount 1
if ($heading -notmatch '^# (.+)$') { throw 'Start the article with a level-one title.' }
$articleTitle = [Net.WebUtility]::HtmlEncode($Matches[1].Trim())
$content = (ConvertFrom-Markdown -LiteralPath $source).Html
if ($InlineMedia) {
    $content = [regex]::Replace($content, 'src="(\./demo/[A-Za-z0-9-]+\.(mp4|wav))"', {
        param($match)
        $mediaPath = Join-Path (Split-Path $source -Parent) $match.Groups[1].Value
        $mediaFile = Get-Item -LiteralPath $mediaPath
        if ($mediaFile.Length -gt 10MB) { throw 'Inline preview media must be at most 10 MB per file.' }
        $mimeType = if ($match.Groups[2].Value -eq 'mp4') { 'video/mp4' } else { 'audio/wav' }
        $encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes($mediaFile.FullName))
        'src="data:' + $mimeType + ';base64,' + $encoded + '"'
    })
}
$repository = if ($RepositoryUrl) {
    $uri = [Uri]$RepositoryUrl
    if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'github.com') { throw 'Use the actual HTTPS GitHub repository URL.' }
    $label = if ($RepositoryVisibility -eq 'private') { 'Private GitHub repository' } else { 'GitHub repository' }
    '<a href="' + [Net.WebUtility]::HtmlEncode($RepositoryUrl) + '">' + $label + '</a>'
} else { '<span>Repository publication pending</span>' }
$html = @"
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>$articleTitle</title>
<style>
:root{color-scheme:light;--ink:#182235;--muted:#516077;--accent:#5545ad}
*{box-sizing:border-box}body{margin:0;background:#f4f5fa;color:var(--ink);font:18px/1.75 system-ui,-apple-system,Segoe UI,sans-serif}
header,main,footer{max-width:1020px;margin:auto;padding:28px 44px}header{display:flex;justify-content:space-between;gap:20px;font-size:15px;color:var(--muted)}
main{background:#fff;border-radius:24px;box-shadow:0 12px 60px #2635540a}h1{font-size:clamp(32px,5vw,52px);line-height:1.15;letter-spacing:-.035em}h2{line-height:1.3;margin-top:2em}h3{line-height:1.4}
a{color:var(--accent)}blockquote{border-left:4px solid #b0a0ed;background:#f6f3ff;margin:22px 0;padding:12px 24px;font-size:21px}
table{display:block;overflow:auto;border-collapse:collapse;width:100%;font-size:15px;line-height:1.6}th,td{border:1px solid #dfe3ec;padding:12px 14px;min-width:140px;text-align:left;vertical-align:top}th{background:#f3f4fa}
pre{padding:20px;background:#11182b;color:#e7e6f6;border-radius:14px;overflow:auto;font-size:14px;line-height:1.65}code{font-family:Consolas,monospace}p code,li code{background:#f0eef8;padding:2px 5px;border-radius:4px;font-size:.88em}
img{max-width:100%;height:auto;border-radius:16px}video{display:block;max-width:100%;max-height:740px;margin:24px auto;background:#0c1223;border-radius:18px}audio{display:block;width:min(100%,650px);margin:18px 0}
footer{font-size:14px;color:var(--muted)}@media(max-width:700px){header,main,footer{padding:22px}body{font-size:16px}main{border-radius:0}}
</style>
</head>
<body>
<header><span>FOUNDRY LOCAL FOR ANDROID · AMI</span>$repository</header>
<main>$content</main>
<footer>Local article preview · Fictional demo records · Media plays only when you choose.</footer>
</body>
</html>
"@
[IO.File]::WriteAllText($destination, $html, [Text.UTF8Encoding]::new($false))
Write-Output "Blog preview: $destination"
