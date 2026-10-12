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

Raw results go to ignored `runtime/results/` and may contain device or permission details. Keep that directory out of public uploads. The [test results](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/VALIDATION.md) summarize the recorded runs.

## 5. Have a conversation

1. On **Today**, check the sample-data badge and open the evidence behind a chart. Gaps mean missing data, not zero.
2. On **Talk**, type “When was my heartbeat high this week?” Open **Calculation & coverage** to see the recorded peak, timestamp and coverage. Here, “high” means the highest recorded value, not a medical assessment.
3. Ask “When was my heart rate highest last month?” and then “Why?” The follow-up should retain the date window without inventing a cause for the peak.
4. Tap **Talk to Ami**, allow microphone access and ask about sleep. Look for a new question bubble and **Edit heard question**. Correct the transcript there if necessary.
5. Try **Done**, **Cancel turn**, **Listen again**, **Stop voice** and **Interrupt & talk**. A spoken question can trigger a spoken reply; a typed one stays quiet.
6. Choose **Recap my week · experimental** and read the confirmation. The result should show either valid Qwen-selected cards or the labelled calculated fallback.

The microphone stops after at most 20 seconds; trailing quiet can end the turn sooner. This is tap-to-talk, not an always-listening or wake-word feature. Test pauses, quiet speech and room noise separately from the prerecorded fixtures, and note whether the voice is comfortable to listen to.

If a call or voice session is using the phone's audio, wait until it ends before starting a conversation with Ami.

**Play a sample conversation** is another way to try the app without recording your voice. It deliberately plays a prerecorded question and a reply. Keep it labelled as a sample when demonstrating it.

## 6. Connect your watch

The optional data path is Galaxy Watch4 → Samsung Health → Health Connect → Ami. Ami requests read-only access to Samsung-origin records, with heart rate and older history handled separately. Start with the [Samsung Health Connect FAQ](https://developer.samsung.com/health/health-connect-faq.html) and the app's permission controls.

Live Samsung Health integration has not been validated in this sample. Check that Samsung Health shares the requested record types with Health Connect before granting Ami read access. The published demo uses fictional records.

Use real records only with the phone owner's consent. Ami's saved-memory controls do not delete the original Health Connect records.

## Troubleshooting

- **No models available:** check that the Foundry Local service is installed, then prepare the models from inside Ami. IPC model caches are per client app.
- **No spoken reply:** choose an installed offline voice under **Voice & pace**. A typed question stays quiet; use **Listen again** to hear it. If another call or voice session owns the phone's audio, wait for it to finish.
- **Transcript is wrong:** use **Edit heard question** and try again in a quiet room. The fixture tests cover known English questions, not every speaker or environment.
- **No watch records:** check that Samsung Health has shared the requested data with Health Connect and that Ami has read permission. Permission alone does not create records. Use sample mode to try the app independently of watch sync.

For screenshots, voiced recordings and blog exports, follow the [recording guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/RECORDING-GUIDE.md).
