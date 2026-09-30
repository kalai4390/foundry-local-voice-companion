# Real-device follow-up: data sharing and voice availability

Checked September 30, 2026. This is a sanitized engineering account, not a health assessment or an export of personal records. The [initial follow-up evidence](./evidence/ami-device-followup.json) remains unchanged; the later [reconnection evidence](./evidence/ami-reconnect-validation.json) records the completed acoustic voice checks and direct Samsung-source inspection.

## Reconnected phone: what is now established

The same `0.3.4-preview` app was awake, unlocked and in `MODE_NORMAL`. A known synthetic heartbeat question played from the laptop loudspeaker into the **physical phone microphone**. A new, non-clickable question bubble with **Edit heard question** appeared; Android TTS entered speaking and completed. This first turn used no screen/audio mirroring. No temporary microphone file remained. It is an objective completion check, not a person's assessment of the speaker or voice quality.

A second acoustic turn produced the new [46-second video](./demo/ami-microphone.mp4) and [poster](./demo/ami-microphone.png). Its audio is actual Android TTS output, not a voiceover. The capture tool redirected device output during this recording, so it is not a physical-speaker audibility test. The external laptop question is not on that output track. Cropping, constant-frame-rate resampling, trailing-idle trimming and a 24 dB gain are disclosed in the [provenance](./demo/ami-microphone-provenance.json). The final file decodes without errors; a two-fps contact sheet and the full-size poster were inspected. Human listening review remains open.

With capture stopped, **Listen again → Interrupt & talk** accepted a different spoken sleep question, and **Stop voice** returned to microphone-off. Temporary microphone files were again absent. An earlier permission-prompt attempt ended without enough sound before the intended stimulus played; it is not counted as a successful turn. These controlled English fixtures do not establish accent, noise or unrestricted conversational quality.

A fresh personal Samsung-origin weekly question still returned the explicit missing-readings answer, with heart-rate permission enabled. More decisively, **Samsung Health settings → Health Connect → Samsung Health → See app data** displayed **No data**. Samsung's required write permissions and Ami's heart-rate read permission were granted. This establishes that the inspected native Samsung source was empty; it does not explain or repair Samsung's sharing behavior. No account/cloud-sync setting, health measurement or source filter was changed. No personal screenshot or numerical health value was exported.

The phone was returned to Ami's synthetic welcome screen with the microphone off and the capture helper stopped. No application source, APK, radio setting or screen timeout changed in this follow-up.

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

On the updated APK, a Talk tap during reserved communication audio showed the new guard without opening a microphone permission dialog or leaving a temporary recording. A typed greeting still worked. The speaker-guard assertion and complete microphone-to-audible-reply path were not established in that initial reserved-audio attempt. The later normal-audio tests above are separate evidence, not a relabelling of the earlier failure. The reserved-audio speaker guard still lacks a completed physical assertion.

The guard reads Android's audio mode, not call logs or conversation content. No `READ_PHONE_STATE` or call-log permission was added. Existing Android audio-focus handling remains in force. The engineering check does not establish subjective voice quality.

## Keep the evidence versions separate

- Current code/build: **0.3.4-preview**, [build manifest](./validation-results.json), [follow-up](./evidence/ami-device-followup.json).
- Additional current-build microphone/output video and interruption checks: [reconnection evidence](./evidence/ami-reconnect-validation.json), [media provenance](./demo/ami-microphone-provenance.json).
- Full synthetic SDK/media baseline: **0.3.3-preview**, [phone results](./evidence/ami-phone-validation.json), [capture results](./evidence/ami-capture-validation.json), [media provenance](./demo/ami-provenance.json).
- The earlier failed auto-lock capture remains failed. A suggestion chip saying **Hello Ami** is not a newly recognized transcript; live recognition requires a new user bubble with **Edit heard question**.

The controlled microphone, transcript, calculated answer, Android TTS completion and interruption checks are now established. Human speech, room noise and subjective listening remain separate gates. Do not terminate a call, record its content, force audio focus, or disable radios during a call to finish a test. Radio-off cold starts remain a separate, reversible test; none was performed here.

The two earlier `0.3.3-preview` recordings are silent and the offline voice introduction remains a separate WAV. The new `0.3.4-preview` video has its own captured device-output audio. Do not dub the unrelated introduction over an answer and label it a live conversation. Public GitHub access and licensing remain separate decisions.
