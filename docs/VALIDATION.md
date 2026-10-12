# Tests and recorded results

These results describe the recorded builds of Ami, not a new run on your machine. Use the [build guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BUILD-GUIDE.md) to test your own checkout.

## Build results

| Check | Ami 0.3.4-preview |
| --- | --- |
| Kotlin core | 171 tests in 13 suites; no failures, errors or skips |
| Debug and release builds | Both assembled; release APK unsigned |
| Android lint | 0 errors and 33 warnings per variant |
| Release manifest | Debug receiver absent; backup and cleartext disabled; no health-write permissions |

The [build report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/validation-results.json) contains the suite counts and APK hashes. The [source-reproduction report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/reproducibility.json) records a clean rebuild of the 59 Android/core inputs. That rebuild used the same machine and signing environment, with Gradle task-output caching disabled.

## Phone tests

The test phone was a Motorola g35 running Android 16. The SDK was Foundry Local Android 0.1.6 in IPC mode, using CPU inference and fictional records.

| Check | App version | Recorded result |
| --- | --- | --- |
| Supported questions | 0.3.4-preview | 8/8 smoke cases |
| Monthly heart-rate conversation | 0.3.4-preview | 3/3 cases |
| Physical microphone through spoken reply | 0.3.4-preview | Completed controlled acoustic turns |
| Replay, interruption and Stop | 0.3.4-preview | Replay entered speaking; interruption accepted a new question; Stop returned to microphone-off |
| Whisper transcription and calculated answers | 0.3.3-preview | 5/5 prerecorded questions and 5/5 answer cases |
| Qwen recap | 0.3.3-preview | 4/4 known two-card selections |
| Android TTS | 0.3.3-preview | Three voice styles generated; cancellation and recovery completed |

The acoustic test played a question from a laptop speaker into the phone microphone. The five-file transcription suite passed WAVs directly to Whisper. The full SDK suite was not rerun on 0.3.4-preview; the table keeps those results separate. The [device test report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/device-tests.json) records the versions and results.

## Test coverage

Core tests cover date windows and time zones, missing records, heart-rate peaks and follow-ups, sleep and activity calculations, answer formatting, audio availability, and validation of the recap model's card IDs.

The device tests do not establish speech accuracy across accents or noisy rooms, subjective voice quality, battery life, thermal behavior or radio-off cold starts. Live Samsung Health integration has not been validated. The sample does not implement streaming dialogue or free-form medical advice.

## Run the checks

After configuring the tools and preparing the models:

```powershell
.\scripts\validate-build.ps1 `
  -GradlePath 'C:\tools\gradle-8.13\bin\gradle.bat' `
  -FoundryAar 'C:\tools\foundry-local-ipc-sdk-0.1.6.aar'
.\scripts\resume-device.ps1 -ValidationPath .\runtime\build-validation.json -RunAudio -RunDebrief -RunVoice
```

Your reports go under ignored `runtime/`. Use the new build report when testing or recording your APK; the checked-in results identify the original builds.
