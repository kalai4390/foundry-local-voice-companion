# Real-device follow-up: data sharing and voice availability

Checked September 30, 2026. This is a sanitized engineering account, not a health assessment or an export of personal records. See the [machine-readable evidence](./evidence/ami-device-followup.json).

## What changed

**Ami 0.3.4-preview** is installed in place with a verified APK hash. It adds two focused fixes:

- [AudioReadiness](../app/src/main/java/dev/daylight/local/inference/AudioReadiness.kt) checks Android audio mode before microphone or speaker work. [ShortRecorder](../app/src/main/java/dev/daylight/local/inference/ShortRecorder.kt) checks during capture as well and deletes a failed/cancelled temporary file. Calls and reserved voice sessions produce a specific message; the app does not end them or silently switch to cloud speech.
- [run-device.ps1](../scripts/run-device.ps1) launches with `--activity-clear-top`. Previously, returning the task from an external Health Connect screen could leave that screen on top and never start the debug suite. A genuine return-to-Ami regression run passed. The runner now reports the host record separately and never advertises a nonexistent application JSONL file.

There are 171 passing JVM tests in 13 suites, both APK variants assemble, and both lint variants have zero errors / 33 warnings. The [clean-source reproduction](./evidence/reproducibility.json) separately pins the inputs and both APK hashes. Phone smoke checks pass 8/8, with 3/3 monthly-conversation cases.

## Samsung data: connected does not mean shared

Bluetooth was off at the beginning of the investigation. Enabling it restored the existing Galaxy Watch4 Classic connection, which Galaxy Wearable and Samsung Health both reported as connected. No new pairing or account/cloud-sync setting was configured.

Samsung Health's basic **Heart rate** screen showed entries under its selected **Today** view. A Heart rate home tile was added to make that existing history accessible; this did not create or edit a measurement. The newer **Heart health** score is a different feature and was not used as a test of basic Watch4 heart-rate support.

Ami's explicit Samsung-origin refresh and weekly heart-rate question still returned no usable shared data after the watch connection was restored. The inspected Health Connect step entries were attributed to the phone rather than Samsung Health. Existing Ami read and Samsung write permissions were present, but those grants did not establish that the requested records were available to Ami.

This narrows the problem to the sharing/availability path without establishing its precise cause. Do not declare the real-data integration complete, infer that missing heart rate is normal, substitute fictional readings, widen the data-origin filter silently, or enable account sync just to produce a positive screenshot. No numerical health records, personal screenshots or account identifiers from this investigation belong in the publication package.

## Voice: distinguish an occupied audio session from ASR failure

The original microphone attempts did not establish a successful live turn. Android subsequently reported call and voice-communication audio modes. A labelled synthetic-file question was transcribed, but speaker playback was refused audio focus. This is not evidence that the user spoke an unrecognizable question, nor proof that Whisper or an offline voice is broken.

On the updated APK, a Talk tap during reserved communication audio showed the new guard without opening a microphone permission dialog or leaving a temporary recording. A typed greeting still worked. The speaker-guard assertion and complete microphone-to-audible-reply path were not observed successfully in this follow-up; they remain unverified, not silently counted as passes.

The guard reads Android's audio mode, not call logs or conversation content. No `READ_PHONE_STATE` or call-log permission was added. Existing Android audio-focus handling remains in force. The engineering check does not establish subjective voice quality.

## Keep the evidence versions separate

- Current code/build: **0.3.4-preview**, [build manifest](./validation-results.json), [follow-up](./evidence/ami-device-followup.json).
- Full synthetic SDK/media baseline: **0.3.3-preview**, [phone results](./evidence/ami-phone-validation.json), [capture results](./evidence/ami-capture-validation.json), [media provenance](./demo/ami-provenance.json).
- The earlier failed auto-lock capture remains failed. A suggestion chip saying **Hello Ami** is not a newly recognized transcript; live recognition requires a new user bubble with **Edit heard question**.

When the handset audio is free, the next physical gate is a deliberate short microphone turn, visible transcript, grounded answer and actual speaker completion, followed by interruption and listening review. Do not terminate a call, record its content, force audio focus, or disable radios during a call to finish the test. Radio-off cold starts remain a separate, reversible test; none was performed here.

The existing article's two screen recordings are silent and its offline voice introduction is a separate WAV. Do not dub that introduction over an answer and label it a live conversation. GitHub publication and licensing are also separate from generating a local source archive.
