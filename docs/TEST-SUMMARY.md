# Test results at a glance

Ami's sample-data voice turn works on the tested Motorola g35: record a question, transcribe it through Foundry Local, calculate an answer and complete Android speech playback. The [voice demo](https://github.com/kalai4390/foundry-local-voice-companion/issues/1) shows that path. This page is a short guide to the recorded checks; [test details](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/VALIDATION.md) and [device notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEVICE-NOTES.md) explain the individual runs.

## Results by app version

| Check | Version | Recorded result |
| --- | --- | --- |
| Core tests | 0.3.4-preview | 171 tests in 13 suites; no failures, errors or skips |
| Debug and release builds | 0.3.4-preview | Both assembled; release APK unsigned |
| Lint | 0.3.4-preview | 0 errors, 33 warnings per variant |
| Phone smoke and monthly conversation | 0.3.4-preview | 8/8 smoke and 3/3 conversation cases |
| Physical microphone, reply and playback controls | 0.3.4-preview | Controlled acoustic questions completed; replay, interruption and Stop passed |
| Whisper transcription and calculated answers | 0.3.3-preview | 5/5 prerecorded English questions and 5/5 answer cases |
| Qwen recap | 0.3.3-preview | 4/4 known two-card selection cases |
| Android TTS styles | 0.3.3-preview | Three WAV styles generated; cancellation and recovery passed |

The acoustic questions were played from a laptop speaker into the phone's microphone. The prerecorded suite sent files directly to Whisper. Neither is a broad test of human accents or room noise. The recap result checks the expected card-ID format and selections for known examples, not whether people find the ranking useful.

The full 0.3.3-preview SDK run and the later 0.3.4-preview follow-up have different APK hashes. Both are preserved rather than presenting every result as a rerun of the newer build. Completed sample-data suites recorded zero HealthReader calls.

## Recordings

The 46-second microphone video is from **0.3.4-preview** and includes captured Android TTS output. The earlier 32.47-second speech video and 22.10-second recap video are **0.3.3-preview** and silent. Five screenshots, a welcome video frame and a separate Warm voice sample belong to that earlier version. The [demo guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md#recording-details) links each set to its capture notes.

Selected images, sampled contact sheets and final frames were inspected, and the videos decoded end to end. Human watch-through and listening review on the publishing platform are still needed. The capture interrupted by screen lock remains failed and supplies none of the selected media.

## Not yet tested successfully

- **Live Samsung Health data:** the native Health Connect Samsung-source view showed no data despite the required permissions. The demo uses fictional records.
- **Human voice quality:** quiet speech, accents, noise and subjective listening have not been evaluated. The speaker warning during reserved audio also lacks a completed physical test.
- **Offline cold starts and battery use:** no radio-off cold-start, controlled memory, thermal or battery study was performed.
- **Broader conversations:** free-form grounded dialogue, streaming speech, embedded deployment, other models and fine-tuning are not features demonstrated by this build.

## Repeat the checks

Supply the tools and IPC AAR using the [build guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BUILD-GUIDE.md), then run:

```powershell
.\scripts\validate-build.ps1 `
  -GradlePath 'C:\tools\gradle-8.13\bin\gradle.bat' `
  -FoundryAar 'C:\tools\foundry-local-ipc-sdk-0.1.6.aar'
.\scripts\resume-device.ps1 -ValidationPath .\runtime\build-validation.json -RunAudio -RunDebrief -RunVoice
.\scripts\capture-demo.ps1 -ValidationPath .\runtime\build-validation.json -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' -PreventAutoLock
```

Use your own newly generated build report. These commands preserve the installation's data and permissions; they do not unlock the phone or prepare models automatically. Raw runtime results stay local until reviewed for sharing.
