param([string]$VoiceName = 'Microsoft Zira Desktop')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$assets = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\app\src\main\assets'))
New-Item -ItemType Directory -Path $assets -Force | Out-Null
$synthesizer = [System.Speech.Synthesis.SpeechSynthesizer]::new()
$format = [System.Speech.AudioFormat.SpeechAudioFormatInfo]::new(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$fixtures = @(
    @{ file = 'weekly-question.wav'; text = 'How did my sleep change this week?' },
    @{ file = 'walking-question.wav'; text = 'How much walking did I record?' },
    @{ file = 'overview-question.wav'; text = 'Give me an overview of my week.' },
    @{ file = 'heartbeat-question.wav'; text = 'When was my heartbeat high this week?' },
    @{ file = 'monthly-heartbeat-question.wav'; text = 'When was my heart rate highest last month?' }
)
try {
    $synthesizer.SelectVoice($VoiceName)
    foreach ($fixture in $fixtures) {
        $path = Join-Path $assets $fixture.file
        $synthesizer.SetOutputToWaveFile($path, $format)
        $synthesizer.Speak($fixture.text)
        $synthesizer.SetOutputToNull()
        $fixture.voice = $synthesizer.Voice.Name
        $fixture.sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        $fixture.bytes = (Get-Item -LiteralPath $path).Length
    }
    [ordered]@{
        synthetic = $true
        source = 'Offline Windows System.Speech synthesis; no microphone or playback'
        sampleRate = 16000
        channels = 1
        bitsPerSample = 16
        fixtures = $fixtures
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $assets 'fixtures.json') -Encoding UTF8
    Write-Output "Generated $($fixtures.Count) labelled synthetic WAV fixtures in $assets"
} finally { $synthesizer.Dispose() }
