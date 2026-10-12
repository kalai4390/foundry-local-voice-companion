# An On-Device Voice Companion with Foundry Local

“When was my heartbeat high this week?”

Meet **Ami**, an Android app you can talk to about your wearable records. Foundry Local transcribes your question on the phone, Kotlin looks up the dates and numbers, and an installed offline Android voice reads the answer back. The conversation does not go to a cloud model.

The included records are fictional, so you can build and try it without a watch or a health account. Ami supports a small set of questions about heart rate, sleep, walking and activity; it is not an open-ended health chatbot.

[Read the walkthrough](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BLOG.md) · [Watch the voice demo](https://github.com/kalai4390/foundry-local-voice-companion/issues/1) · [Build guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BUILD-GUIDE.md)

<img src="https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-answer.png" width="320" alt="Ami answering a heart-rate question from fictional records on an Android phone">

*The [46-second demo](https://github.com/kalai4390/foundry-local-voice-companion/issues/1) runs on a Motorola g35. Sound starts at 0:11. For a repeatable recording, the question was played from a laptop speaker into the phone microphone; the reply you hear is the phone's Android TTS output. More recordings and screenshots are in the [demo guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md).*

## Try a conversation

Once the app and models are ready, tap **Talk to Ami** and ask:

- “When was my heartbeat high this week?”
- “How was my sleep?”
- “How much walking did I record?”

You can inspect **Calculation & coverage**, correct a transcript with **Edit heard question**, or use **Listen again** to repeat the answer. **Interrupt & talk** starts another turn. Typed questions stay quiet.

There is also a **Recap my week** action. After confirmation, a small Qwen model running through Foundry Local chooses two of the app's calculated fact cards. Ami checks the returned IDs and displays the original card text. It does not ask the model to invent measurements or give medical advice.

## How it works

![Ami's on-device voice flow: microphone, Foundry Local Whisper, Kotlin calculations and offline Android speech](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/voice-flow.svg)

The main voice path uses **Whisper Tiny through the Foundry Local Kotlin SDK**. This project uses IPC mode: the SDK connects to the separately installed Foundry Local service on the same phone. The integration handles model discovery, downloads, loading, transcription and the optional chat call.

Whisper turns speech into text. **Android text-to-speech (TTS)** does the reverse: it reads the answer aloud using an installed offline voice. Kotlin handles dates, calculations and normal answer wording in between. The [walkthrough](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BLOG.md) follows those calls in code.

## Build it

The setup below uses Windows and PowerShell. The app's minimum Android version is 13; the recorded tests used a Motorola g35 on Android 16. A compatible Foundry Local service and models are also required.

| Tool | Version used here |
| --- | --- |
| JDK | 17 |
| Gradle | 8.13 |
| Android SDK | Platform 36, build tools and platform-tools |
| PowerShell | 7.4 or newer |
| Foundry Local Android SDK | 0.1.6, IPC AAR |

Download the **IPC AAR** from the [Foundry Local Android 0.1.6 release](https://github.com/microsoft/FoundryLocalAndroid/releases/tag/v0.1.6), and follow the [companion-app setup](https://github.com/microsoft/FoundryLocalAndroid/blob/v0.1.6/docs/INTEGRATION_GUIDE.md#ipc-setup). Use the IPC package rather than the embedded package; the build checks its SHA-256. The SDK, runtime and model weights are not included in this repository.

From the repository root, set the tool paths to match your installation:

```powershell
$env:JAVA_HOME = 'C:\tools\jdk-17'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android'
.\scripts\build.ps1 `
  -GradlePath 'C:\tools\gradle-8.13\bin\gradle.bat' `
  -FoundryAar 'C:\tools\foundry-local-ipc-sdk-0.1.6.aar'
```

If your Android SDK is under `Android\Sdk` or another directory, use that location instead. Install platform 36 and accept the Android SDK licenses before building. The script runs the core tests, builds the debug APK and runs Android lint.

To build both variants and write a local test report, run:

```powershell
.\scripts\validate-build.ps1 `
  -GradlePath 'C:\tools\gradle-8.13\bin\gradle.bat' `
  -FoundryAar 'C:\tools\foundry-local-ipc-sdk-0.1.6.aar'
```

The [validator](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/validate-build.ps1) writes `runtime/build-validation.json` for your APKs. Use that report with the installation and capture scripts in the [build guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BUILD-GUIDE.md). The release APK is unsigned. This project uses Kotlin and Gradle; no npm packages are needed.

## Prepare the phone

With USB debugging authorized on your phone, install the app:

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

1. Open Ami and keep **DEMO / SYNTHETIC** mode selected.
2. In **Privacy**, use the model-preparation controls while the app is foreground on unmetered Wi-Fi. This is a separate step from asking a question.
3. In **Voice & pace**, choose an installed offline English voice and preview it. If none is available, install a voice through Android's speech settings first.
4. Tap **Talk to Ami** and approve microphone access when Android asks. You can also start with a typed question.

The demo uses these catalog packages:

| Purpose | Catalog identifier | Advertised size in the recorded test |
| --- | --- | --- |
| Speech transcription | `openai-whisper-tiny-generic-cpu:4` | 131 MB |
| Optional recap selection | `qwen2.5-0.5b-instruct-generic-cpu:4` | 822 MB |

Both ran on the CPU. The `:4` suffix is part of the catalog identifier, not a four-bit quantization label. Check the cache from inside Ami even if you have already downloaded a model in another app; IPC caches are per client. Ami caps the advertised combined download at 1,000 MB.

Model downloads and voice installation need connectivity. Once prepared, Ami makes its speech and chat calls on the phone and has no cloud inference fallback. This describes Ami's processing, not the network activity of other apps. To try the included recording instead of using your microphone, tap **Play a sample conversation**; this plays audio aloud.

## Run the checks

With one authorized phone connected, Ami unlocked and the models prepared:

```powershell
.\scripts\run-device.ps1 -Mode smoke
.\scripts\run-device.ps1 -Mode audio
.\scripts\run-device.ps1 -Mode debrief
.\scripts\run-device.ps1 -Mode voice
```

These debug suites use fictional records. The audio suite sends five prerecorded WAVs through Whisper; it does not open the microphone. The voice suite writes Android TTS samples without playing them aloud. A completed suite reports `healthReadAttempts: 0`.

The recorded `0.3.4-preview` build passed **171 JVM tests**, debug/release builds and lint with **0 errors and 33 warnings per variant**. Device and media results are tied to their individual app versions in the [test notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/VALIDATION.md). They are recorded results, not a promise that every Android phone will behave the same way.

## Where the code lives

| Component | Source |
| --- | --- |
| SDK connection, catalog, cache, load, speech/chat calls and cleanup | [FoundryRuntime](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/inference/FoundryRuntime.kt) |
| Voice input and output | [ShortRecorder](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/inference/ShortRecorder.kt), [LocalSpeaker](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/inference/LocalSpeaker.kt), [AudioReadiness](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/inference/AudioReadiness.kt) |
| Read-only Health Connect integration | [HealthReader](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/data/HealthReader.kt) |
| Pure Kotlin facts, dates, routing and strict model-output validation | [Core](https://github.com/kalai4390/foundry-local-voice-companion/tree/main/core/src/main/kotlin/dev/daylight/core/) |
| Native UI and lifecycle | [MainActivity](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/MainActivity.kt), [UI](https://github.com/kalai4390/foundry-local-voice-companion/tree/main/app/src/main/java/dev/daylight/local/ui/) |
| Reproducible device evaluation | [DebugRunner](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/DebugRunner.kt), [scripts](https://github.com/kalai4390/foundry-local-voice-companion/tree/main/scripts/) |
| Synthetic regression tests | [Core tests](https://github.com/kalai4390/foundry-local-voice-companion/tree/main/core/src/test/kotlin/dev/daylight/core/) |

The Android package remains `dev.daylight.local`, the project's earlier name. Keeping it avoids changing the installed app's identity.

## About watch data and privacy

The optional Health Connect reader requests Samsung-origin records with explicit, read-only permission. It is separate from the sample-data experience. In the recorded device investigation, Samsung Health had not exposed usable records through Health Connect, so the demos do not claim a live watch feed. The [device notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEVICE-NOTES.md) explain what was checked.

Ami records only after you tap to speak. It does not listen in the background, write health records or train a model on your data. Check-ins are saved only when requested, in encrypted local storage. Missing records produce a missing-data answer rather than a guessed number.

This is an experimental app, not a clinical tool. Speech in varied conditions, battery use and radio-off cold starts still need testing. There is no source license in the repository yet; the Microsoft SDK and model packages have separate terms. See the [release checklist](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/RELEASE-CHECKLIST.md) before sharing a build or recording.
