# Ami validation ledger

This is a prototype validation record, not a claim of medical effectiveness, general language quality or whole-device network isolation. The current source is **Ami 0.3.4-preview**, Android package `dev.daylight.local`. The complete SDK/media baseline remains **0.3.3-preview**. The earlier Daylight name remains only in explicitly labelled historical media and internal identifiers.

## Evidence map

- [Published build manifest](./validation-results.json): exact APK hashes, host tests, lint and release-manifest checks.
- [New device follow-up](./evidence/ami-device-followup.json): current installation/smoke checks, audio guard and limits; [findings](./DEVICE-FINDINGS.md) describe the real-data investigation.
- [Recorded 0.3.3-preview phone results](./evidence/ami-phone-validation.json): matching-baseline-APK synthetic cases, transcripts, calculated answers, model-cache states and voice previews.
- [Showcase status](./SHOWCASE-VALIDATION.md): what is demonstrated versus still open.
- [Current Ami media provenance](./demo/ami-provenance.json): individual asset methods, exact build/source hashes and the interrupted-session boundary; [historical provenance](./demo/provenance.json) is unchanged.
- [Author runbook](./AUTHOR-RUNBOOK.md): reproduce the checks, capture safely and prepare a repository.

The recorded SDK baseline replay was performed on **September 29, 2026 UTC / September 30, 2026 Asia/Kolkata**. The new build follow-up is September 30 UTC. Host filenames and phone event timestamps use different clocks; do not derive latency by subtracting them. Per-operation durations are narrow fixture observations, not a device benchmark.

## Host checks

| Check | Recorded result |
| --- | --- |
| Core JVM tests | 171 tests, 13 suites, zero failures/errors/skips |
| Debug / unsigned release | Both assemble successfully |
| Lint | 0 errors, 33 warnings per variant |
| Release manifest | Ami label; stable application ID; no debug receiver; backup/cleartext disabled; no health writes or background health permission |

The author APK is identified by its hash, not by the display name alone. A clean build can have a different hash or signing key. [validate-build.ps1](../scripts/validate-build.ps1) regenerates a legitimate local validation report from tests, both builds, lint, release metadata and APK hashes. It writes `runtime/build-validation.json`, leaving this publication's historical evidence unchanged.

**Clean-source reproduction passed:** a newly extracted source ZIP with no project caches, local properties or SDK binaries executed all 104 Gradle tasks and passed the same 171 tests and both builds/lint variants. Task-output caching was disabled. Both APK hashes match the current artifacts. This used the same host toolchain, dependency downloads and debug signing environment; it is not a fresh-workstation or universal bit-for-bit claim. The [reproduction report](./evidence/reproducibility.json) pins all 59 Android/core build inputs, and packaging verifies that they remain unchanged.

## Current build follow-up

The installed `0.3.4-preview` hash matches the current build manifest. Smoke passes 8/8 and monthly conversation 3/3, with zero HealthReader calls in the completed synthetic runs. The reserved-communication microphone preflight and a typed greeting were observed successfully; no temporary microphone file remained. Speaker completion, a complete live turn and radio isolation are not verified. The [follow-up evidence](./evidence/ami-device-followup.json) also separates a real external-screen launch regression from a mocked startup-timeout check.

## Recorded 0.3.3-preview SDK replay

For this baseline, Ami was upgraded in place without uninstalling or clearing storage. The installed APK SHA-256 matches the [baseline phone report](./evidence/ami-phone-validation.json), not the later build manifest. Both exact model handles were cached; no model download was requested. The runtime reported `CPUExecutionProvider` for Whisper Tiny and Qwen2.5 0.5B.

| Suite | Result | Meaning |
| --- | --- | --- |
| Smoke | 8/8 | Synthetic application contracts |
| Monthly conversation | 3/3 | Known date-window and follow-up fixtures |
| Foundry Whisper | 5/5 normalized transcript matches | Distinct desktop-synthesized English WAVs, not live speech |
| Calculated answers | 5/5 | Application-authored answers match the selected topic and contract |
| Optional Foundry Qwen recap | 4/4 model contracts accepted | Known two-card output grammar; not ranking usefulness |
| Recap empty/one-category cases | Computed/no-model behavior observed | No unnecessary model call or invented records |
| Offline Android TTS | 3 styles, cancellation and recovery pass | Silent synthesis to WAV; not a listening-quality assessment |
| HealthReader attempts | 0 in every completed suite | Synthetic runs did not query personal records |

Normal answers do not use the earlier failed model classifier. The current optional recap sends calculated fact-card options to a separate chat call and validates the returned IDs. Deterministic fallbacks remain visible; they are not counted as successful model output.

## What the regressions cover

The [core tests](../core/src/test/kotlin/dev/daylight/core/) include:

- Correctly scoped supported questions and rejection of appended/unsupported commands.
- Calendar date windows, time zones, incomplete today and retained monthly follow-up context.
- Highest **recorded** heart rate with a timestamp, sample coverage, duplicates, gaps and read failures.
- Missing versus zero values, interval overlap and sleep-session versus time-asleep wording.
- Strict recap response validation and unavailable-model/computed fallback behavior.
- Spoken replies, cancellation state, stale voice work and deliberate speech controls.

They do not prove physiological accuracy of wearable sensors, clinical interpretation, useful personal-data availability or arbitrary LLM grounding.

## Capture status

The current article uses a **complete September 30, 2026 Ami 0.3.3-preview capture**: a 32.47-second silent speech clip, a 22.10-second silent recap clip, five compositor screenshots and a welcome video frame. A separate Android-synthesized Ami introduction remains from the earlier matching-build voice run. The new runs pass 5/5 transcriptions, 5/5 answer contracts and 4/4 recap contracts, with zero HealthReader calls; see [capture evidence](./evidence/ami-capture-validation.json). Every selected image was viewed; both clips decode end to end, with 12 four-fps contact sheets and both final full-size frames inspected.

The earlier compositor attempt stopped on Android auto-lock. It is retained as **failed**, with its source manifest unchanged; the new session supplies all current images and clips. Historical Daylight media remains separately labelled and unchanged. Human AV/listening review and an owner-supervised live demo remain open.

The capture script offers a reversible three-minute idle-timeout extension and still refuses a locked/unfocused app. The new physical capture found an existing ten-minute timeout and preserved it, verified after completion. Two local mock failure checks cover restoration of a shorter timeout and preservation of a longer one; the shorter-timeout restoration branch has not been physically exercised. No wake or unlock command was issued.

## Remaining acceptance gates

- **Personal Samsung heart-rate data:** reconnecting the watch and inspecting Samsung's basic history did not establish usable Samsung-origin samples in Ami's inspected requests. The upstream sharing cause remains open; see [device findings](./DEVICE-FINDINGS.md).
- **Live voice:** Android reserved audio for calls/communication during the follow-up. The new microphone guard is verified, not a complete microphone → transcript → answer → audible playback pass. Noise, accent, interruption and listening quality remain open.
- **Disconnected operation:** a separately consented radio-off cold start after provisioning; no such measurement is claimed here.
- **Performance:** controlled cold/warm latency, thermal behavior, RAM and battery evaluation. Download sizes are not RAM measurements.
- **Broader AI:** freer grounded conversation, streaming dialogue, embedded deployment, other models and fine-tuning remain experiments, not implemented guarantees.
- **Publication:** the [GitHub repository](https://github.com/kalai4390/ami-foundry-local) is approved for private source review, without a license. Human AV/editorial review, public-reader access and a licensing choice remain open. The full synthetic multi-screen capture is complete.

Raw development logs and private diagnostics are not part of the clean repository package. The public phone report is an allowlisted derivative with original source hashes; it intentionally excludes permission inventory, device identifiers, owner paths and personal records.
