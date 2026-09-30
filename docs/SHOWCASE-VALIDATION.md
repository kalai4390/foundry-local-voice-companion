# Ami showcase: working code, evidence and remaining gates

## Current build and phone

**Ami 0.3.4-preview is installed and hash-verified on the Motorola g35.** It passes 171 JVM tests in 13 suites, both Android builds and lint with 0 errors / 33 warnings per variant. The release APK is unsigned. The [current build manifest](./validation-results.json), [device follow-up](./evidence/ami-device-followup.json) and [findings](./DEVICE-FINDINGS.md) identify this update.

The new phone replay passes 8/8 smoke and 3/3 monthly-conversation cases. During reserved communication audio, Talk shows the microphone guard without requesting microphone permission or leaving a recording; a typed greeting still works. The later [reconnection checks](./evidence/ami-reconnect-validation.json) establish controlled acoustic microphone turns, expected editable transcripts, Android TTS completion, replay, interruption and Stop. These are known synthetic questions played through a laptop speaker, not a human accent or voice-quality study. The full SDK baseline below remains explicitly tied to **0.3.3-preview**, not relabelled as a new replay.

The additional [46-second current-build demo](./demo/ami-microphone.mp4) contains actual captured Android TTS output. Device output is redirected during capture; no voiceover or external input soundtrack is added. A separate unmirrored turn completed. The [provenance](./demo/ami-microphone-provenance.json) records transformations, hashes and review limits. A native Health Connect inspection now confirms **Samsung Health → See app data → No data**, despite the required grants. This is an upstream data-availability blocker, not evidence of a successful personal-data integration.

## Recorded SDK baseline: 0.3.3-preview

The recorded rename upgrade preserved `dev.daylight.local`, permissions and app-private storage identity. Both exact Whisper/Qwen model caches were verified after the SDK client name changed to Ami. That baseline needed no uninstall, new grants or model download. Its [sanitized phone results](./evidence/ami-phone-validation.json) retain the original APK hash.

| Physical-phone check | Result | Limit |
| --- | --- | --- |
| Smoke | 8/8 | Synthetic functional contracts |
| Monthly conversation | 3/3 | Known clarification/date fixtures |
| Foundry Whisper transcription | 5/5 | Prerecorded synthetic English, not microphone/accent evaluation |
| Calculated voice answers | 5/5 | Code calculates and authors normal replies; no LLM medical advice |
| Optional Foundry Qwen recap | 4/4 contracts | Known fact-card choices; ranking usefulness not evaluated |
| Android voice | 3 styles; cancellation/recovery pass | Silent file synthesis; audible quality unassessed |
| HealthReader calls | 0 per completed suite | Not proof of whole-device zero egress |

Replay date: **September 29, 2026 UTC / September 30 in India**. Event/source-file timestamps use phone/host clocks respectively. Do not interpret the scripted run as a latency benchmark.

## Media is a separate evidence gate

The current article presents a **complete Ami 0.3.3-preview capture from September 30, 2026 UTC**: a 32.47-second speech screen recording, a 22.10-second recap recording, five compositor screenshots, a welcome video frame and the separately synthesized Ami Warm preview from the earlier voice run. [Current provenance](./demo/ami-provenance.json) identifies each method, matching APK/source hashes and review limits. The [new capture evidence](./evidence/ami-capture-validation.json) repeats 5/5 ASR, 5/5 answer and 4/4 model-output contract checks with zero HealthReader calls.

Both clips and all five screenshots completed in the new session. Full-size images, both final video frames and all 12 four-fps contact sheets were inspected; both clips decode end to end. The earlier auto-lock session **remains failed** and its source manifest is unchanged; it supplies none of the current images or clips. Human AV/listening review remains pending.

The historical Daylight screenshots, videos and voice sample retain their [original provenance](./demo/provenance.json) and are labelled historical in the [demo guide](./DEMO-GUIDE.md). The scripted capture offers a temporary idle-timeout extension with restoration; the physical phone already had a ten-minute timeout, so this run correctly left it unchanged. The shorter-timeout restoration branch remains mock-tested, not physically exercised. Capture requires a manually unlocked, foreground app and never bypasses the lock.

## Reproduce without resetting the phone

For a clean build, use the [author runbook](./AUTHOR-RUNBOOK.md) to supply the independent SDK and tools, then:

```powershell
.\scripts\validate-build.ps1
.\scripts\resume-device.ps1 -ValidationPath .\runtime\build-validation.json -RunAudio -RunDebrief -RunVoice
.\scripts\capture-demo.ps1 -ValidationPath .\runtime\build-validation.json -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' -PreventAutoLock
```

Inspect quality fields as well as runtime completion. Use the author's default manifest only with the exact retained author APK. Never overwrite a hash simply to bypass the check, clear app data to force an upgrade or grant health permissions automatically.

## Still not demonstrated

Useful personal Samsung heart-rate records; human speech/noise and subjective listening quality; disconnected cold starts; battery/thermal behavior; unrestricted grounded LLM dialogue; fine-tuning or an embedded runtime build. The [GitHub repository](https://github.com/kalai4390/ami-foundry-local) is approved for private review only; public release and licensing remain separate decisions. The [validation ledger](./VALIDATION.md) and [demo guide](./DEMO-GUIDE.md) keep these boundaries separate from the working synthetic SDK and controlled acoustic paths.
