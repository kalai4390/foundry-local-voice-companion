# Tests and recorded results

The source is Ami **0.3.4-preview**, with Android package `dev.daylight.local`. This document keeps the build, phone and media results tied to the version that produced them. For a shorter overview, see the [test summary](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/TEST-SUMMARY.md).

## Build results

| Check | Recorded result for 0.3.4-preview |
| --- | --- |
| Core JVM tests | 171 tests in 13 suites; no failures, errors or skips |
| Debug and unsigned release | Both assembled successfully |
| Lint | 0 errors and 33 warnings per variant |
| Release manifest | Ami label and stable application ID; no debug receiver; backup and cleartext disabled; no health writes or background health permission |

The [build report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/validation-results.json) contains the exact APK hashes. A build on another machine may have a different signing key or hash. [validate-build.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/validate-build.ps1) writes a new report to `runtime/build-validation.json` without changing the archived results.

A [clean-source reproduction](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/reproducibility.json) extracted the source ZIP without project caches, local properties or bundled SDK binaries. All 104 Gradle tasks executed with task-output caching disabled. The same tests, builds and lint checks passed, and both APK hashes matched. The run reused the original machine's toolchain, dependency downloads and debug signing environment; it was not a fresh-workstation test. The report lists 59 Android/core build inputs, which the packaging script verifies.

## Phone tests on 0.3.4-preview

The installed APK matched the recorded hash. Smoke tests passed 8/8 and monthly-conversation cases 3/3, with no HealthReader calls in the completed sample-data runs.

During a reserved communication-audio session, Ami showed its microphone warning without leaving a recording; a typed greeting still worked. The [initial device report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-device-followup.json) also records the return-from-Health-Connect regression and a mocked startup-timeout check. The speaker guard in the occupied-audio state was not fully verified.

The later [reconnection tests](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-reconnect-validation.json) used known questions played from a laptop speaker into the phone microphone. Ami produced the expected editable transcripts, calculated replies and Android TTS completion. Replay, interruption and Stop also passed. The [device notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEVICE-NOTES.md) explain the separate attempts and the empty Samsung Health source.

## Earlier SDK tests on 0.3.3-preview

This run checked the rename from Daylight to Ami without changing `dev.daylight.local`, clearing storage or adding permissions. Both exact Whisper and Qwen caches were available after the SDK client name changed. No model download was needed.

| Check | Result |
| --- | --- |
| Smoke | 8/8 supported or explicitly unavailable question cases |
| Monthly conversation | 3/3 clarification, date-window and follow-up cases |
| Whisper | 5/5 prerecorded English transcriptions |
| Calculated answers | 5/5 answer checks |
| Qwen recap | 4/4 known two-card selection cases; empty and one-category cases use computed fallbacks |
| Android TTS | Three styles generated; cancellation and recovery passed |
| HealthReader | Zero calls in every completed suite |

The [phone report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-phone-validation.json) retains that APK's hash, transcripts, cache states and outputs. The run took place on September 29, 2026 UTC, September 30 in India. Host filenames and phone events use different clocks; subtracting them would not produce a reliable latency measurement.

Normal replies do not use the earlier unsuccessful model-based question classifier. That experiment remains available for debug evaluation. The optional recap instead asks the model to choose calculated fact cards, then validates the returned IDs. A computed fallback is shown as such and is not counted as a successful model selection.

## What the core tests cover

The [Kotlin tests](https://github.com/kalai4390/foundry-local-voice-companion/tree/main/core/src/test/kotlin/dev/daylight/core/) check:

- Supported questions and rejection of appended or unsupported commands.
- Calendar windows, time zones, incomplete days and retained follow-up dates.
- The highest recorded heart rate, its timestamp, duplicates, coverage gaps and read failures.
- Missing versus zero values, interval overlap and sleep-session wording.
- Recap ID validation, unavailable models and computed fallbacks.
- Spoken-answer text, cancellation state and stale voice work.

These are software checks. They do not measure wearable-sensor accuracy, clinical interpretation or open-ended language-model reliability.

## Capture results

The completed September 30, 2026 UTC capture of **0.3.3-preview** produced a 32.47-second silent speech video, a 22.10-second silent recap video, five Android screenshots and a welcome frame. Its [capture report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-capture-validation.json) repeats the 5/5 transcription, 5/5 answer and 4/4 recap checks with zero HealthReader calls. The separate Warm introduction came from the matching-version voice run.

Every selected image, both final video frames and 12 four-fps contact sheets were inspected; the videos decoded end to end. An earlier attempt ended at screen lock and remains failed. Its manifest was not changed, and it supplies none of the selected media. Older Daylight assets retain their [original capture notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/provenance.json).

The script can temporarily extend a short idle timeout and restore it afterward. The recorded phone already had a ten-minute timeout, which was preserved. Local mock checks cover restoring a shorter timeout and retaining a longer one; the shorter-timeout restoration branch has not been exercised on the physical phone. Capture never woke or unlocked the device.

The **0.3.4-preview** [microphone video](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone.mp4) is a separate 46.02-second capture with Android TTS output. It was cropped, resampled to 20 fps, trimmed after the turn and amplified by 24 dB. No answer or voiceover was inserted. It decoded end to end, and a two-fps contact sheet and full-size poster were inspected. The [microphone notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone-provenance.json) also identify the preview-compatible copy. The [demo guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEMO-GUIDE.md) explains which clips have sound.

## Work still to evaluate

Successful live Samsung data sharing, speech from people in varied acoustic conditions, listening quality, radio-off cold starts and controlled performance/battery measurements remain unverified. Embedded deployment, streaming dialogue, other models and fine-tuning were not demonstrated. The [test summary](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/TEST-SUMMARY.md#not-yet-tested-successfully) keeps that list separate from the completed sample-data checks.

Public test reports omit personal readings, permission inventories, device identifiers and local paths. Raw logs remain outside the repository. Before publishing, check the [publication notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/RELEASE-CHECKLIST.md) for source access, licensing and the final media review.
