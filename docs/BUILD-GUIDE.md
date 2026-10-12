# Build Ami and record a demo

This guide takes you from a clean checkout to a conversation on your phone, then through the tests and recording steps used for the article. The commands run from the repository root on Windows. If you only want to see the result, start with the [demos](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md) or the [walkthrough](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BLOG.md).

Use the included fictional records while learning and recording. A watch and Health Connect permissions are not needed for that path.

## 1. Set up the build

| Dependency | Version used here |
| --- | --- |
| PowerShell | 7.4 or newer |
| JDK | 17 |
| Gradle | 8.13 |
| Android Gradle Plugin / Kotlin | 8.13.0 / 2.1.20 |
| Android SDK | Platform 36, build tools and platform-tools; minimum app API 33 |
| Health Connect client | 1.1.0 |
| Foundry Local Android | IPC SDK 0.1.6 |

Install the Android tools and accept their licenses. Download the **IPC AAR** from the [Foundry Local Android release](https://github.com/microsoft/FoundryLocalAndroid/releases/tag/v0.1.6), and follow the [matching companion-app setup](https://github.com/microsoft/FoundryLocalAndroid/blob/v0.1.6/docs/INTEGRATION_GUIDE.md#ipc-setup). This project does not use the embedded AAR. The build checks the IPC archive against this SHA-256:

```text
7dc74260d2dbd16c2c3c02bcd9de1d852bb89366f71a63ea9813fe764c2665c1
```

Replace these example locations with your installed paths, including `ANDROID_HOME` if your SDK is under `Android\Sdk`:

```powershell
$env:JAVA_HOME = 'C:\tools\jdk-17'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android'
.\scripts\validate-build.ps1 `
  -GradlePath 'C:\tools\gradle-8.13\bin\gradle.bat' `
  -FoundryAar 'C:\tools\foundry-local-ipc-sdk-0.1.6.aar'
```

[validate-build.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/validate-build.ps1) runs the core tests, debug/release builds and both lint variants. It writes `runtime/build-validation.json` for the APKs you just built. The release APK is unsigned. For a shorter debug-only build, use [build.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/build.ps1) with the same path arguments.

The archived [build results](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/validation-results.json) describe the original tested APKs. Keep your new results separate rather than replacing those recorded hashes.

## 2. Install on your phone

Connect one authorized Android phone, unlock it and run:

```powershell
.\scripts\resume-device.ps1 -ValidationPath .\runtime\build-validation.json
```

The script verifies the local APK, installs it with `-r`, checks the installed hash and runs sample-data smoke tests. It does not uninstall the app, clear storage or grant permissions. If the installed app uses a different signing key, resolve that before continuing; uninstalling would remove its local data.

The package name is still `dev.daylight.local`, from the project's earlier name. That lets an existing installation keep its identity when upgraded to Ami. Omitting `-ValidationPath` is only appropriate for the original retained APK that matches the archived build results.

## 3. Prepare the models and voice

Open Ami in **DEMO / SYNTHETIC** mode. Under **Privacy**, use the model-preparation controls while the app is foreground on unmetered Wi-Fi. Downloads happen here, not unexpectedly during a question.

| Model | Catalog identifier | Advertised download in the recorded test |
| --- | --- | --- |
| Whisper Tiny | `openai-whisper-tiny-generic-cpu:4` | 131 MB |
| Qwen 2.5 0.5B | `qwen2.5-0.5b-instruct-generic-cpu:4` | 822 MB |

The recorded phone run used `CPUExecutionProvider`. Check the exact model cache from inside Ami; a download made by another IPC client may not be available to it. Download size is not runtime memory use, and `:4` is a catalog suffix rather than a quantization setting.

Open **Voice & pace** and preview an installed offline English voice. Install an offline voice through Android's speech settings if the list is empty. Warm, Clear and Unhurried change playback style; they are not Foundry models. Previewing a voice plays it aloud.

## 4. Run the sample-data tests

Keep Ami unlocked and foreground:

```powershell
.\scripts\run-device.ps1 -Mode smoke
.\scripts\run-device.ps1 -Mode audio
.\scripts\run-device.ps1 -Mode debrief
.\scripts\run-device.ps1 -Mode voice
```

| Suite | What runs |
| --- | --- |
| Smoke | Supported questions, date windows, clarification and follow-ups |
| Audio | Five prerecorded WAVs through Whisper, followed by calculated answers |
| Debrief | Four two-card selection cases, plus empty/one-category fallbacks |
| Voice | Three Android TTS samples, cancellation and recovery |

These debug suites neither open the microphone nor play sound. They do not grant health access, change radios or query personal records. Each completed suite should report `healthReadAttempts: 0`; also read its quality results, since a completed model call can still return the wrong output.

The [fixture manifest](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/assets/fixtures.json) holds expected text and hashes. The five English fixtures cover sleep, walking, an overview, this week's heartbeat and last month's heartbeat. They were synthesized on a desktop; they are not recordings of different speakers. Running [make-fixtures.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/make-fixtures.ps1) creates new assets and a different test baseline.

Raw results go to ignored `runtime/results/`. They can include device and permission details. For sharing, keep a small reviewed report such as the existing [phone test results](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-phone-validation.json), not the whole runtime directory.

## 5. Have a conversation

1. On **Today**, check the sample-data badge and open the evidence behind a chart. Gaps mean missing data, not zero.
2. On **Talk**, type “When was my heartbeat high this week?” Open **Calculation & coverage** to see the recorded peak, timestamp and coverage. Here, “high” means the highest recorded value, not a medical assessment.
3. Ask “When was my heart rate highest last month?” and then “Why?” The follow-up should retain the date window without inventing a cause for the peak.
4. Tap **Talk to Ami**, allow microphone access and ask about sleep. Look for a new question bubble and **Edit heard question**. Correct the transcript there if necessary.
5. Try **Done**, **Cancel turn**, **Listen again**, **Stop voice** and **Interrupt & talk**. A spoken question can trigger a spoken reply; a typed one stays quiet.
6. Choose **Recap my week · experimental** and read the confirmation. The result should show either valid Qwen-selected cards or the labelled calculated fallback.

The microphone stops after at most 20 seconds; trailing quiet can end the turn sooner. This is tap-to-talk, not an always-listening or wake-word feature. Test pauses, quiet speech and room noise separately from the prerecorded fixtures, and note whether the voice is comfortable to listen to.

If a call or voice session is using the phone's audio, Ami refuses to open its microphone or speaker. Wait until that session has ended. The [device notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEVICE-NOTES.md) explain the audio checks and why a reserved session should not be mistaken for a Whisper failure.

**Play a sample conversation** is another way to try the app without recording your voice. It deliberately plays a prerecorded question and a reply. Keep it labelled as a sample when demonstrating it.

## 6. Connect your watch

The optional data path is Galaxy Watch4 → Samsung Health → Health Connect → Ami. Ami requests read-only access to Samsung-origin records, with heart rate and older history handled separately. Start with the [Samsung Health Connect FAQ](https://developer.samsung.com/health/health-connect-faq.html) and the app's permission controls.

A connected watch does not guarantee that Health Connect has received its records. In the September 30 investigation, the watch was connected and Samsung Health showed heart-rate entries, but its Health Connect source displayed **No data**. Ami therefore kept its missing-readings response. That is why the published demos use fictional records. The [device notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEVICE-NOTES.md) preserve the investigation without exposing personal readings.

Use real records only with the phone owner's consent. Keep that work separate from recording a public demo; do not broaden the source filter or substitute sample values to make a missing-data screen look successful. Ami's saved-memory controls do not delete the original Health Connect records.

## 7. Record screenshots and video

Start with one unlocked phone, Ami foreground and the sample-data badge visible. Use your local build report:

```powershell
.\scripts\capture-demo.ps1 `
  -ValidationPath .\runtime\build-validation.json `
  -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' `
  -PreventAutoLock
```

The capture profile fits the tested 1080 × 2400 portrait display, cropping 108 pixels from the top and 102 from the bottom. Measure the insets before using it on another phone. The script checks the installed APK and foreground app; it never unlocks the device.

`-PreventAutoLock` temporarily raises a shorter idle timeout to three minutes and verifies restoration in `finally`. The recorded phone already had a ten-minute timeout, so it was left unchanged. If USB disconnects before restoration, use the original value retained in the capture manifest to restore it after reconnecting.

The script saves screenshots and two **silent** test recordings under ignored `runtime/publication-…/`. [prepare-demo-video.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/prepare-demo-video.ps1) crops the system bars, resamples to 30 fps and holds the final frame to preserve source duration. It does not speed up inference or add answers. Host and ADB waits are still present, so these recordings are not latency benchmarks.

The voice suite produces separate WAV previews. Keep the preview from the matching build rather than dubbing an unrelated introduction over an answer. The [demo guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md) lists the recordings already selected for the article.

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

The article embeds [ami-microphone-preview.mp4](https://github.com/kalai4390/foundry-local-voice-companion/issues/1). It has the same video as the [original](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-microphone.mp4), with the existing AAC audio converted to MP3 at 192 kb/s. This avoids silent playback in the VS Code preview; see its [media-format guidance](https://code.visualstudio.com/api/extension-guides/webview#supported-media-formats).

Sound begins around **0:11**, when Android TTS starts. The conversion adds no voiceover, gain, cuts or speed changes; the original capture's disclosed gain still applies. To reproduce it, use a new output path (`-n` prevents overwriting):

```powershell
& 'C:\tools\ffmpeg\bin\ffmpeg.exe' -n -hide_banner -nostats `
  -i .\docs\demo\ami-microphone.mp4 `
  -map 0:v:0 -map 0:a:0 -map_metadata -1 `
  -c:v copy -c:a libmp3lame -b:a 192k -movflags +faststart `
  .\runtime\ami-microphone-preview.mp4
```

Decode the files end to end and compare the extracted H.264 hashes. Then watch and listen on the intended publishing platform. A successful encode or audio decode is not a listening-quality review.

## 8. Prepare the article and source package

The article is [Building an On-Device Voice Companion with Foundry Local](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BLOG.md). Its examples follow the sample-data voice turn, then the optional chat recap. Keep the fictional-data label beside the recordings and give each component its correct name: Foundry Local runs Whisper and Qwen; Kotlin calculates the normal answers; Android TTS speaks them.

Before selecting media, inspect every image and the full recordings for personal notifications, account screens, consent dialogs and private health values. Preserve the original capture privately, document any crop or audio adjustment, and retain the APK version and checksums with the published files. Use actual results, including a labelled fallback when that is what ran.

### Prepare the diagrams

Use these as full-width landscape figures, not as portrait screenshots:

| Figure | Editable source | Upload-ready PNG |
| --- | --- | --- |
| Voice flow | [1440 × 560 SVG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/voice-flow.svg) | [2880 × 1120 PNG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/voice-flow.png) |
| Optional recap | [1440 × 500 SVG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/recap-flow.svg) | [2880 × 1000 PNG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/recap-flow.png) |

Put the voice diagram beside the Android introduction and the recap beside the chat API example. Keep the aspect ratio, numbered captions and alt text. The preview uses the same landscape image at every width; on a small screen, open the full-size link and zoom to read the labels.

Use the PNG if the publishing editor does not support SVG. Choose the full article-column width and keep a link to the full-resolution figure. Regenerate the PNG whenever its SVG changes. The broader [reference architecture](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/architecture.svg) includes the optional watch integration; it is not evidence of a live data feed.

### Build the preview and package

```powershell
.\scripts\build-blog-preview.ps1 -InlineMedia
```

Open the generated `docs/index.html` to preview the article. Its links point to GitHub, not your computer. `-InlineMedia` embeds images and supported media from the checkout so you can see them while the repository is private. The main voice recording opens in GitHub's video player.

For the blog editor, use `docs/BLOG.md` or the generated `docs/BLOG.html` article body. Both use complete GitHub URLs for source links and image files. Use **Watch Ami answer** as an ordinary hyperlink, not in the editor's video-URL field. The repository must be public for readers to open these links and images. A separate GitHub Pages website is not required.

Create a fresh source archive:

```powershell
.\scripts\package-repository.ps1 -ApprovedRepositoryUrl 'https://github.com/kalai4390/foundry-local-voice-companion'
```

[package-repository.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/package-repository.ps1) copies an explicit list of source, docs and media into a new folder and ZIP. It checks local links, original media hashes and the recorded build inputs, then writes `PACKAGE-MANIFEST.json`. It also checks configured owner identifiers. That pattern check complements, rather than replaces, a secret scan and content review.

The package excludes tools, SDK AARs, models, APKs, build caches, runtime diagnostics and signing keys. It does not initialize Git, commit or upload anything. `-ApprovedRepositoryUrl` exempts only the exact repository URL and owner/repository label from the owner-name check, not arbitrary personal information.

The recorded [clean-source build](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/reproducibility.json) ran all 104 Gradle tasks with task-output caching disabled and reproduced both APK hashes. It used the same machine, downloaded dependencies and debug signing environment. It is a source-reproduction check, not a fresh-workstation test. Packaging checks the same 59 Android/core build inputs.

## 9. Before you share

Check the [release checklist](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/RELEASE-CHECKLIST.md) for repository access, licensing, privacy and media checks. The code is still in a private repository with no source license. A public article needs a source link its readers can open and a deliberate licensing decision; this guide does not make either change automatically.

Keep `.gitattributes` unchanged when importing the package: it preserves the exact file bytes used by the recorded checksums. New builds should generate new local reports. The [test notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/VALIDATION.md) and [recording notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md#recording-details) identify which results belong to each app version.
