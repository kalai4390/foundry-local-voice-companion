param(
    [Parameter(Mandatory)][string]$InputPath,
    [Parameter(Mandatory)][string]$OutputPath,
    [Parameter(Mandatory)][string]$FfmpegPath,
    [ValidateRange(0, 400)][int]$CropTop = 72,
    [ValidateRange(0, 400)][int]$CropBottom = 68,
    [ValidateRange(24, 60)][int]$FrameRate = 30
)

$ErrorActionPreference = 'Stop'
foreach ($required in @($InputPath, $FfmpegPath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing input: $required" }
}
if (Test-Path -LiteralPath $OutputPath) { throw 'Output already exists; keep prior captures and choose a new path.' }
$inputFile = (Resolve-Path -LiteralPath $InputPath).Path
$encoder = (Resolve-Path -LiteralPath $FfmpegPath).Path
$outputFile = [IO.Path]::GetFullPath($OutputPath)
$culture = [Globalization.CultureInfo]::InvariantCulture

function Read-VideoMetadata([string]$Path) {
    $probe = (& $encoder -hide_banner -nostats -i $Path -an -f null NUL 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Video decoding failed; do not publish this clip.' }
    $duration = [regex]::Match($probe, 'Duration: (\d{2,}):(\d{2}):(\d{2}\.\d+), start: (-?[\d.]+)')
    $dimensions = [regex]::Match($probe, 'Video: [^\r\n]*?\b(\d{3,})x(\d{3,})\b')
    if (-not $duration.Success -or -not $dimensions.Success) { throw 'Cannot verify video timing and dimensions.' }
    $seconds = [int]$duration.Groups[1].Value * 3600 + [int]$duration.Groups[2].Value * 60 +
        [double]::Parse($duration.Groups[3].Value, $culture)
    [pscustomobject]@{
        seconds = $seconds
        startSeconds = [double]::Parse($duration.Groups[4].Value, $culture)
        width = [int]$dimensions.Groups[1].Value
        height = [int]$dimensions.Groups[2].Value
    }
}

$source = Read-VideoMetadata $inputFile
if ($source.width -ne 720 -or $source.height -ne 1600 -or [Math]::Abs($source.startSeconds) -gt 0.001) {
    throw 'This profile requires a zero-start 720x1600 portrait screenrecord.'
}
$height = $source.height - $CropTop - $CropBottom
if ($height -le 0 -or $height % 2 -ne 0 -or $CropTop % 2 -ne 0) { throw 'H.264 crop coordinates must be positive and even.' }
$durationText = $source.seconds.ToString('0.00', $culture)
$filter = "crop=720:${height}:0:${CropTop},setpts=PTS-STARTPTS,fps=${FrameRate},tpad=stop_mode=clone:stop_duration=$durationText"
& $encoder -hide_banner -loglevel error -n -i $inputFile -vf $filter -an -t $durationText -vsync 0 `
    -c:v libx264 -preset fast -crf 21 -pix_fmt yuv420p -movflags +faststart $outputFile
if ($LASTEXITCODE -ne 0) { throw 'Video crop/encoding failed.' }
$encoded = Read-VideoMetadata $outputFile
$delta = [Math]::Abs($encoded.seconds - $source.seconds)
if ($encoded.width -ne 720 -or $encoded.height -ne $height -or $delta -gt (1.0 / $FrameRate + 0.02)) {
    throw 'Encoded dimensions or duration differ from the expected crop/timeline; do not publish.'
}
[pscustomobject]@{
    file = Split-Path -Leaf $outputFile
    sourceSha256 = (Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash.ToLowerInvariant()
    sha256 = (Get-FileHash -LiteralPath $outputFile -Algorithm SHA256).Hash.ToLowerInvariant()
    sourceDurationSeconds = $source.seconds
    durationSeconds = $encoded.seconds
    durationDeltaMs = [Math]::Round($delta * 1000, 3)
    frameRate = $FrameRate
    width = $encoded.width
    height = $encoded.height
    transform = 'System bars cropped; timestamps resampled to constant frame rate without speed adjustment; final still held to source container duration; no soundtrack or inserted answers'
}
