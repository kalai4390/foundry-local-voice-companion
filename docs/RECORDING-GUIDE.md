# Record an Ami demo

Start with one unlocked phone, Ami foreground and the sample-data badge visible. Use your local build report:

```powershell
.\scripts\capture-demo.ps1 `
  -ValidationPath .\runtime\build-validation.json `
  -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' `
  -PreventAutoLock
```

The capture profile fits the tested 1080 × 2400 portrait display, cropping 108 pixels from the top and 102 from the bottom. Measure the insets before using it on another phone. The script checks the installed APK and foreground app; it never unlocks the device.

`-PreventAutoLock` temporarily raises a shorter idle timeout to three minutes and verifies restoration in `finally`. If USB disconnects before restoration, use the original value retained in the capture manifest to restore it after reconnecting.

The script saves screenshots and two **silent** test recordings under ignored `runtime/publication-…/`. [prepare-demo-video.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/prepare-demo-video.ps1) crops the system bars, resamples to 30 fps and holds the final frame to preserve source duration. It does not speed up inference or add answers. Host and ADB waits are still present, so these recordings are not latency benchmarks.

The voice suite produces separate WAV previews. The [demo guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md) lists the recordings already selected for the article.

### Capture the physical-microphone demonstration

This is a separate recording from the scripted tests. Use sample mode, cached models and no active call. First complete a turn without mirroring, including Android's microphone-permission dialog.

1. Run `run-device.ps1 -Mode welcome` to open an empty sample conversation. Check the badge and microphone-off status.
2. Start a bounded scrcpy recording of device-output audio. This example keeps the helper window hidden and does not wake the phone. Its crop is for the tested display.

```powershell
New-Item -ItemType Directory -Path .\runtime -Force | Out-Null
$recording = Join-Path (Resolve-Path .\runtime).Path ('microphone-' + (Get-Date -Format yyyyMMdd-HHmmss) + '.mp4')
$capture = Start-Process -FilePath 'C:\tools\scrcpy\scrcpy.exe' -WindowStyle Hidden -PassThru -ArgumentList "--select-usb --no-power-on --no-window --no-control --no-audio-playback --audio-source=output --audio-codec=aac --time-limit=70 --max-size=1600 --crop=1080:2190:0:108 --record=`"$recording`""
```

3. Tap **Talk to Ami**. To reproduce the controlled question, play the fixture through the laptop speaker near the phone:

```powershell
Add-Type -AssemblyName System.Windows.Extensions
$player = [System.Media.SoundPlayer]::new((Resolve-Path .\app\src\main\assets\heartbeat-question.wav).Path)
try { $player.PlaySync() } finally { $player.Dispose() }
```

4. Check the new question bubble, editable transcript, calculated answer, speaking status and return to microphone-off. This records sound through the physical microphone; it is not a direct-file Whisper test.
5. Let the capture finish before opening another app. Reject a recording that contains a personal notification, lock screen or unrelated data. Review the raw file before editing.
6. Without mirroring, try **Listen again**, **Interrupt & talk**, a different question and **Stop voice**. Keep a short result note rather than a private raw recording.

`--audio-source=output` redirects the phone's output during capture. The video hears the reply, not the laptop's input question, and does not measure the handset speaker's loudness. The selected 46-second clip was resampled to 20 fps, trimmed after the turn and amplified by 24 dB because its output track was quiet. Do not apply that gain blindly to a new recording. The [capture notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone-provenance.json) retain the settings, hashes and review results.

### Preview audio compatibility

The article links to the [GitHub video player](https://github.com/kalai4390/foundry-local-voice-companion/issues/1), which uses the original H.264/AAC recording. The separate [editor-preview MP4](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone-preview.mp4) keeps the same video and converts AAC audio to MP3 at 192 kb/s for editors whose preview cannot decode AAC. See the [media-format guidance](https://code.visualstudio.com/api/extension-guides/webview#supported-media-formats).

Sound begins around **0:11**, when Android TTS starts. The conversion adds no voiceover, gain, cuts or speed changes; the original capture's disclosed gain still applies. To reproduce it, use a new output path (`-n` prevents overwriting):

```powershell
& 'C:\tools\ffmpeg\bin\ffmpeg.exe' -n -hide_banner -nostats `
  -i .\docs\demo\ami-microphone.mp4 `
  -map 0:v:0 -map 0:a:0 -map_metadata -1 `
  -c:v copy -c:a libmp3lame -b:a 192k -movflags +faststart `
  .\runtime\ami-microphone-preview.mp4
```

Decode the files end to end and compare the extracted H.264 hashes. Then watch and listen on the intended publishing platform. A successful encode or audio decode is not a listening-quality review.

## Prepare the article and source package

The article is [Building an On-Device Voice Companion with Foundry Local](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BLOG.md). Its examples follow the sample-data voice turn, then the optional chat recap. Keep the fictional-data label beside the recordings and give each component its correct name: Foundry Local runs Whisper and Qwen; Kotlin calculates the normal answers; Android TTS speaks them.

Before selecting media, inspect every image and the full recordings for personal notifications, account screens, consent dialogs and private health values. Preserve the original capture privately, document any crop or audio adjustment, and retain the APK version and checksums with the published files. Use actual results, including a labelled fallback when that is what ran.

### Prepare the diagrams

Use these as full-width landscape figures, not as portrait screenshots:

| Figure | Editable source | Upload-ready PNG |
| --- | --- | --- |
| Voice flow | [1440 × 560 SVG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/voice-flow.svg) | [2880 × 1120 PNG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/voice-flow.png) |
| Optional recap | [1440 × 500 SVG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/recap-flow.svg) | [2880 × 1000 PNG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/recap-flow.png) |

Put the voice diagram beside the Android introduction and the recap beside the chat API example. Keep the aspect ratio, numbered captions and alt text. The preview uses the same landscape image at every width; on a small screen, open the full-size link and zoom to read the labels.

Use the PNG if the publishing editor does not support SVG. Choose the full article-column width and keep a link to the full-resolution figure. Regenerate the PNG whenever its SVG changes. The broader [reference architecture](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/architecture.svg) includes the optional watch integration.

### Build the preview and package

```powershell
.\scripts\build-blog-preview.ps1 -InlineMedia
```

Open the generated `docs/index.html` to preview the article. Its links point to GitHub, not your computer. `-InlineMedia` embeds images and supported media from the checkout so you can see them while the repository is private. The main voice recording opens in GitHub's video player.

For the blog editor, use `docs/BLOG-PUBLISH.md` or `docs/BLOG.html`. Their image URLs require a public repository. Link **Watch Ami answer** to the GitHub video player as an ordinary hyperlink; the blog editor may not accept it as an embedded video.

Run `./scripts/check-doc-links.ps1` to check file targets and headings. Add `-CheckPublicAccess` after the repository is public to test the URLs without signing in.

To create a source folder and ZIP:

```powershell
.\scripts\package-repository.ps1 -ApprovedRepositoryUrl 'https://github.com/kalai4390/foundry-local-voice-companion'
```

The package includes source, docs and selected media, with a checksum manifest. It excludes SDKs, models, APKs, tools, caches and private runtime logs. Packaging checks the links, media hashes and recorded build inputs; it does not upload anything.

## Before sharing

Use fictional records for public recordings. Watch the full clip for notifications or personal information, check its sound, and document any crop or audio adjustment. Source code, SDKs and models may have different license terms.
