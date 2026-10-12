# Testing voice and Samsung Health on the phone

These notes describe the September 30, 2026 tests on a Motorola g35 running Ami 0.3.4-preview. They explain two issues encountered while building the demo: audio being reserved by another session, and watch records not reaching Health Connect. Personal readings, account details and screenshots are not included.

## The microphone conversation

With Android audio in `MODE_NORMAL`, a laptop speaker played a known heartbeat question into the phone's physical microphone. Ami displayed a new question bubble with **Edit heard question**, calculated the answer and completed Android TTS playback. That first turn ran without screen or audio mirroring, and no temporary microphone file remained afterward.

A second turn produced the [46-second video](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-microphone.mp4) and [poster](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone.png). The soundtrack is captured Android TTS output, not a voiceover. The capture tool redirected phone audio, so the recording does not tell us how loud the handset speaker was. The external question is not part of that output track.

The [recording notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone-provenance.json) document cropping, resampling to 20 fps, trimming the idle tail and amplifying the quiet track by 24 dB. The final clip decoded without errors; its sampled frames and full-size poster were inspected.

After stopping capture, **Listen again → Interrupt & talk** accepted a different sleep question, and **Stop voice** returned the app to microphone-off. Temporary recordings were again absent. An earlier attempt ended at the permission prompt before the intended sound played; that attempt is not counted as a successful turn.

These are controlled English questions played through a speaker. They establish the microphone-to-reply path, but not recognition across accents, background noise or quiet speech. Subjective listening quality still needs a human check.

## Why an earlier voice attempt failed

During the initial investigation, Android reported call and voice-communication audio modes. A prerecorded file could still reach Whisper, but speaker playback was refused audio focus. That pointed to audio availability, not proof that speech recognition or the installed voice was broken.

Ami 0.3.4-preview adds an audio check in [AudioReadiness.kt](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/inference/AudioReadiness.kt) before microphone or speaker work. [ShortRecorder.kt](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/app/src/main/java/dev/daylight/local/inference/ShortRecorder.kt) also checks during capture and removes a failed or cancelled recording. The app explains that audio is in use rather than ending the other session or switching to cloud speech.

On the updated app, tapping Talk during reserved communication audio showed the microphone warning without opening a permission dialog or leaving a recording. A typed greeting still worked. The corresponding speaker guard was not fully verified in that occupied-audio state. The successful normal-audio turns were a later, separate test.

The guard reads Android's audio mode; it does not read call logs or conversation content. No phone-state or call-log permission was added. Calls should be allowed to finish normally before testing, rather than forcing audio focus or disabling radios.

## Why watch readings were missing

The existing Galaxy Watch4 Classic pairing reconnected after Bluetooth was enabled. Galaxy Wearable and Samsung Health both showed the watch as connected. Samsung Health's basic **Heart rate** history had entries under its selected **Today** view. Adding that tile to the home screen only made existing history easier to find; it did not create a measurement. The separate **Heart health** score was not used to assess basic heart-rate support.

Ami still found no usable Samsung-origin records after an explicit refresh and weekly heart-rate query. The Health Connect step entries inspected at that point came from the phone, not Samsung Health.

A later native check was more conclusive: **Samsung Health settings → Health Connect → Samsung Health → See app data** showed **No data**, even though Samsung's write permissions and Ami's heart-rate read permission were granted.

That explains why granting permission did not give Ami an answer: the requested source had no shared records in the inspected view. It does not establish why Samsung had not shared them. No new pairing, account sync, measurement or source-filter change was made to manufacture a successful result. No personal health value or screenshot was exported.

The sample therefore keeps the missing-data reply for personal mode and uses explicitly fictional records for the published demo. Successful live-watch integration remains unverified.

## Build and test changes

Besides the audio check, [run-device.ps1](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/scripts/run-device.ps1) now launches Ami with `--activity-clear-top`. Previously, returning from a Health Connect screen could leave that screen on top and prevent a debug suite from starting. A return-to-Ami test passed after the change. Host startup results are recorded separately from the app's JSONL output, including when no app result file was created.

The recorded 0.3.4-preview build passed 171 JVM tests in 13 suites, debug/release builds and lint with 0 errors and 33 warnings per variant. Phone smoke tests passed 8/8 and monthly-conversation cases 3/3. The [build report](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/validation-results.json) and [clean-source reproduction](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/reproducibility.json) identify the APKs and inputs.

## Test records

| Record | App version | Contents |
| --- | --- | --- |
| [Initial device follow-up](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-device-followup.json) | 0.3.4-preview | Installation, smoke tests, audio warning and return-from-Health-Connect check |
| [Reconnection tests](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-reconnect-validation.json) | 0.3.4-preview | Acoustic input, TTS completion, replay, interruption, Stop and empty Samsung source |
| [Earlier SDK tests](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-phone-validation.json) | 0.3.3-preview | Five transcription/answer cases, recap contracts and voice styles |
| [Earlier media capture](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-capture-validation.json) | 0.3.3-preview | Silent videos and screenshots, with [media notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-provenance.json) |

The earlier screen-lock capture remains a failed attempt; it does not supply the selected media. A suggestion chip such as **Hello Ami** is not a recognized question—recognition requires a new user bubble and editable transcript.

After the final follow-up, the phone was left on Ami's sample welcome screen with the microphone off and capture stopped. That follow-up did not change the APK, radios or screen timeout. No radio-off cold-start test was performed. The [test summary](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/TEST-SUMMARY.md) lists the work still to evaluate.
