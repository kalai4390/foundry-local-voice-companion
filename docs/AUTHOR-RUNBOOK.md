# Ami author runbook: build, demonstrate and publish

## The story

**Ami — Your day. Your pace. Your space.**

Lead with a person asking about their day, not a test-count slide. The reusable idea is to bring a model to private context instead of uploading that context to obtain a conversational interface. The [blog](./BLOG.md) introduces Foundry Local, explains the SDK lifecycle and follows the real implementation.

Keep three boundaries visible: **Foundry Whisper transcribes; application code computes normal answers; offline Android TTS speaks.** The separately confirmed Foundry Qwen experiment selects calculated fact-card IDs. Do not describe this build as unrestricted LLM conversation, Foundry-generated speech, a fine-tuned health model or a medical product.

## Current handoff

Ami **0.3.4-preview** is installed in place on the Motorola g35 with a hash matching the [published build manifest](./validation-results.json). It passes 171 host tests, both builds/lint, 8/8 phone smoke cases and 3/3 monthly-conversation checks. The [new device follow-up](./evidence/ami-device-followup.json) records the call-aware microphone check and its limits. The full 5/5 ASR, 5/5 answer, four recap and three voice-style results remain the separately identified **0.3.3-preview** [baseline replay](./evidence/ami-phone-validation.json), not a claim of replay on the new APK.

The article now uses a **complete September 30, 2026 capture**: a 32.47-second speech clip, a 22.10-second recap clip, five actual compositor screenshots and a welcome video frame. The separate Ami Warm preview remains from the earlier matching-build voice run. [Media provenance](./demo/ami-provenance.json) and [sanitized capture evidence](./evidence/ami-capture-validation.json) bind those assets to the installed APK and passing checks. All images, 12 four-fps contact sheets and both final video frames were inspected; both clips decode end to end. The phone's existing ten-minute screen timeout remained unchanged. The earlier auto-lock attempt remains failed; historical Daylight media retains its original label in the [demo guide](./DEMO-GUIDE.md). Personal Samsung heart-rate availability, live microphone/listening quality and disconnected operation remain separate gates.

## 1. Windows setup and build

Use the repository root as your working directory. No cloud deployment, Docker container or npm install is required for this Android project.

| Tool | Project pin |
| --- | --- |
| PowerShell | 7.4 or newer, for binary-safe ADB redirection |
| JDK | 17 |
| Gradle | 8.13 |
| Android Gradle Plugin / Kotlin | 8.13.0 / 2.1.20 |
| Android SDK | compile/target 36; minimum API 33 |
| Health Connect client | 1.1.0 |
| Foundry Local Android | IPC SDK 0.1.6 |

Install the Android platform, build tools and platform-tools; review their licenses. Acquire the **IPC** AAR from the [official release](https://github.com/microsoft/FoundryLocalAndroid/releases/tag/v0.1.6), not the embedded AAR, and follow the [matching companion setup](https://github.com/microsoft/FoundryLocalAndroid/blob/v0.1.6/docs/INTEGRATION_GUIDE.md#ipc-setup). The app build verifies this AAR SHA-256:

```text
7dc74260d2dbd16c2c3c02bcd9de1d852bb89366f71a63ea9813fe764c2665c1
```

Replace the example tool locations with your installation paths:

```powershell
$env:JAVA_HOME = 'C:\tools\jdk-17'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android'
.\scripts\validate-build.ps1 `
  -GradlePath 'C:\tools\gradle-8.13\bin\gradle.bat' `
  -FoundryAar 'C:\tools\foundry-local-ipc-sdk-0.1.6.aar'
```

[validate-build.ps1](../scripts/validate-build.ps1) runs all core tests, debug/release builds and both lint variants. It rejects failed or skipped tests, checks the release manifest and writes `runtime/build-validation.json` for **your** APK. It does not overwrite the author's published evidence. The release APK is unsigned, not a distributable release. [build.ps1](../scripts/build.ps1) remains available for a shorter debug-only iteration.

## 2. Install without destroying an existing app

Connect exactly one authorized Android phone and manually open/unlock it. For your newly validated APK:

```powershell
.\scripts\resume-device.ps1 -ValidationPath .\runtime\build-validation.json
```

This verifies the local APK, installs with `-r`, hashes the installed binary and runs synthetic smoke checks. It never uninstalls, clears storage or adds grants. A signing mismatch must be resolved deliberately; do not use an uninstall as an automatic workaround. The application ID remains `dev.daylight.local` so an upgrade from the earlier Daylight build retains its identity.

The author can omit `-ValidationPath` only when using the exact retained APK matching `docs/validation-results.json`. Never edit a checksum merely to bypass a mismatch.

## 3. Prepare models separately from asking a question

Start in **DEMO / SYNTHETIC** mode. In Privacy, use **Prepare models → Check cache / prepare** while Ami is unlocked, foreground and on validated, unmetered Wi-Fi. Initial acquisition needs connectivity; ordinary questions must not trigger an unexpected download.

| Role | Exact tested catalog identifier | Advertised size |
| --- | --- | --- |
| Speech recognition | `openai-whisper-tiny-generic-cpu:4` | 131 MB |
| Optional recap | `qwen2.5-0.5b-instruct-generic-cpu:4` | 822 MB |

The current Ami replay found both cached and used `CPUExecutionProvider`; it did not request downloads. Recheck exact handles on a different installation. IPC caches are isolated per client app; a model downloaded for another client is not proof that Ami can use it. Download size is not RAM use, and the identifier suffix is not a quantization claim.

## 4. Reproduce the silent synthetic checks

Keep Ami foreground and unlocked:

```powershell
.\scripts\run-device.ps1 -Mode smoke
.\scripts\run-device.ps1 -Mode audio
.\scripts\run-device.ps1 -Mode debrief
.\scripts\run-device.ps1 -Mode voice
```

The scripts use the debug-only receiver. They do not use the microphone, play audio, read personal records, grant health access or change radios. Read the quality summaries, not just process exit status. Every successful suite must end with `healthReadAttempts: 0`.

| Suite | Check |
| --- | --- |
| Smoke | Supported questions, unavailable scope, monthly clarification/follow-up behavior |
| Audio | Five distinct synthetic WAVs → actual Whisper → transcript → calculated answer; silent answer synthesis |
| Debrief | Four known two-card selection contracts, plus empty/one-category computed cases |
| Voice | Three actual Android-synthesized preview files; cancellation and recovery |

The [fixture manifest](../app/src/main/assets/fixtures.json) contains the expected text and hashes. The fixtures ask about sleep, walking, an overview, weekly heartbeat and last month's heartbeat. They are English desktop-synthesized speech, not microphone recordings or an accent study. [make-fixtures.ps1](../scripts/make-fixtures.ps1) is optional; regeneration creates a different baseline.

Raw results land under ignored `runtime/results/`. They can contain device/permission metadata: **do not bulk-copy that directory into a repository or blog.** Use a reviewed field allowlist for publishable evidence, as in the current [sanitized report](./evidence/ami-phone-validation.json).

## 5. Walk the experience yourself

1. **Today:** show the Ami name, synthetic badge, coverage and independent chart scales. Missing is not zero. Open the evidence.
2. **Talk:** type “When was my heartbeat high this week?” Show the dated highest recorded sample and coverage. “High” here means highest observed, not medically too high.
3. Ask “When was my heart rate highest last month?” Follow with “Why?” The time window must persist; the app must not invent a cause from the peak alone.
4. Use **Calculation & coverage** to inspect the full answer. Use **Edit heard question** when speech recognition needs correction; do not conceal a correction in the demo.
5. Select **Recap my week · experimental** and read the confirmation. Show whether Qwen selected valid cards or the app used its labelled calculated fallback.
6. **Privacy:** explain model preparation, separate permissions and deliberate local check-ins. Do not open real notes or personal data during publication capture.

### Owner-supervised voice test — still required

In synthetic mode, open **Voice & pace**, preview a voice and choose it yourself. These previews are deliberate audible actions. Tap **Talk to Ami**, say “Hello Ami”, then ask about sleep. The visible transcript should match your words and the reply should be understandable. Test **Done**, **Cancel turn**, **Stop voice**, **Listen again** and **Interrupt & talk**. Typed questions must remain quiet.

The microphone has a 20-second cap; a trailing-quiet heuristic can finish a turn. It is not a wake word, always-on recorder or validated speech detector. Try quiet speech, pauses and room noise before calling the interaction natural. Record voice ID/style and listening observations separately from measured load/inference time.

First check that no call or voice/video session is using the phone. Ami `0.3.4-preview` checks Android audio mode and refuses to start its microphone or speaker while audio is reserved. It does not end another session. In the real follow-up, the microphone guard and typed greeting worked, but an audible reply and a complete live turn were not verified. See [device findings](./DEVICE-FINDINGS.md). When checking recognition, require a newly added user bubble and **Edit heard question**; the always-present **Hello Ami** suggestion chip is not evidence of a recognized greeting.

For a deliberate no-microphone demo, tap **Play a sample conversation**. Label it as prerecorded synthetic speech processed by the real SDK—not live microphone recognition. Automated tests do not perform this audible action.

## 6. Keep personal-watch integration separate

The intended path is Galaxy Watch4 → Samsung Health → Health Connect → Ami's explicit, read-only Samsung-origin queries. The [Samsung FAQ](https://developer.samsung.com/health/health-connect-faq.html) documents the upstream sharing boundaries. A paired watch and granted permission do not establish that a requested record exists.

The September 30 follow-up reconnected the existing watch pairing after Bluetooth was enabled. Samsung's basic heart-rate history showed entries, while Ami's refreshed Samsung-origin snapshot and weekly query still lacked usable shared data. The inspected Health Connect steps were phone-origin. Existing read/write grants alone did not resolve this; the upstream sharing cause remains open. Keep the honest missing-data response. Do not substitute synthetic values, broaden to every provider silently, enable account sync automatically or use fine-tuning to hide missing data. Samsung's newer **Heart health** score is not the same feature as its basic **Heart rate** history.

Only test real records with owner consent. Historical reads are separately gated. Keep personal screens out of screenshots and public logs; publication mode must remain synthetic. Health Connect originals are not deleted by Ami's app-memory controls.

## 7. Capture real screenshots and video

Use [capture-demo.ps1](../scripts/capture-demo.ps1) with the validated local build:

```powershell
.\scripts\capture-demo.ps1 `
  -ValidationPath .\runtime\build-validation.json `
  -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' `
  -PreventAutoLock
```

The profile is for the Motorola's unmodified 1080×2400 portrait display. Other displays need reviewed insets. The script checks the installed APK, focused/awake app and unique synthetic badge before capture. `-PreventAutoLock` temporarily raises a shorter idle timeout to three minutes and verifies restoration in `finally`. It does **not** wake or unlock the phone. If USB disappears during restoration, the retained capture manifest records the original value and the attempt fails; restore that value on reconnection.

The script produces actual compositor screenshots and two bounded, **silent** Android screen recordings under ignored `runtime/publication-…/`. [prepare-demo-video.ps1](../scripts/prepare-demo-video.ps1) crops system bars, resamples the original timeline to 30 fps and holds the final still to source duration. It does not speed up inference or insert answers. Host/ADB setup remains visible; these clips are not latency benchmarks.

The voice suite separately writes Warm, Clear and Unhurried WAV previews. Select the matching run's actual Ami introduction, not a renamed Daylight file. A preview is neither microphone input nor the soundtrack to a different answer. If an end-to-end audible demonstration is needed, make a separately labelled, owner-supervised recording.

### Media review gate

- View every full-size screenshot and the entire recordings; decode all clips end to end.
- Confirm synthetic badges, the unaltered answer and provenance; reject notifications, lock screens, private notes or personal health values.
- Preserve APK, source-event and media hashes, crop parameters, timing transformations and failed attempts.
- Distinguish app-view renders from actual Android compositor screenshots.
- Do not equate silent file synthesis with a listening-quality pass. Human AV/editorial review remains explicit.

## 8. Assemble the article and source package

The [blog](./BLOG.md), [architecture](./architecture.svg), [demo guide](./DEMO-GUIDE.md) and [showcase ledger](./SHOWCASE-VALIDATION.md) must agree on the recorded build and remaining limits. Update media provenance only after successful capture and review. Keep older results labelled historical.

```powershell
.\scripts\build-blog-preview.ps1 -InlineMedia
```

This creates the local `docs/index.html` preview. Inline media avoids local-file video restrictions without a CDN, server upload or autoplay. The Markdown retains ordinary relative media links for a repository. GitHub Markdown does not guarantee inline video playback; keep explicit download/view links. Upload reviewed assets through the blog platform's editor rather than inventing hosting URLs.

Build a local source archive with [package-repository.ps1](../scripts/package-repository.ps1):

```powershell
.\scripts\package-repository.ps1 -ApprovedRepositoryUrl 'https://github.com/kalai4390/ami-foundry-local'
```

It uses an explicit source/document/media allowlist, checks portable links and media hashes, scans for configured owner identifiers and writes a file-hash manifest plus ZIP in a new directory. It never initializes Git, commits or uploads. This pattern scan is not a comprehensive secret audit; review the result yourself. Tools, model weights, SDK AARs, APKs, private diagnostics, build caches and signing keys are excluded. Validate a fresh extracted copy with independently supplied SDK/tool paths before announcing reproducibility.

The current [clean-source reproduction](./evidence/reproducibility.json) passes all 171 tests, both builds and lint, with both APK hashes matching the recorded artifacts. It reused the same host tools, dependency downloads and debug signing environment—not a fresh workstation. Gradle task-output caching was disabled and all 104 tasks executed. The package script verifies that all 59 recorded Android/core build inputs still match that reproduction.

## 9. Publication decision

The author has approved a **private** source review repository at [kalai4390/ami-foundry-local](https://github.com/kalai4390/ami-foundry-local), with `main` as the initial branch and no license added. This is a restricted-access review link, not an externally reproducible public blog link. Public visibility and licensing still require a separate decision. Do not change visibility or grant a license automatically.

The clean export includes `.gitattributes` with text normalization disabled so Git preserves the exact bytes recorded in the source/evidence manifests. Build scripts still generate fresh local validation reports; do not edit a recorded hash merely to make a check pass.

`-ApprovedRepositoryUrl` permits only that exact approved GitHub URL and its owner/repository link label in the owner-identifier scan. It does not exempt other usernames, machine paths, device identifiers or diagnostic logs. The original file content is still used for link and checksum validation.

Suggested editorial sequence: question and answer → actual demo → local architecture → SDK setup/lifecycle → facts and voice boundaries → reproduction link → measured limitations → grounded-conversation/embedded-runtime experiments. Keep claims about future work out of the demonstrated-feature list.
