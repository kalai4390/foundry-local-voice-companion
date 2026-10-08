# Author guide: build, capture and publish the Foundry Local story

## The deliverable

[Building an On-Device Voice Companion with Foundry Local](./BLOG.md) introduces Foundry Local and the supporting pieces, then builds a concrete example: a private, voice-first companion that answers supported questions about local context. Ami is the application used in the walkthrough. The demo is evidence for that implementation—not a product launch, a clinical claim or a suggestion that synthetic records came from the author's watch.

Use the [repository README](../README.md) for clean-checkout setup. The complete Android source, Kotlin core, synthetic fixtures and test/capture scripts belong in the repository. Download the separately licensed SDK from Microsoft; do not bundle it or model weights.

## Media and its meaning

**The opening microphone demo is Ami 0.3.4-preview; the baseline assets below remain Ami 0.3.3-preview.** A complete, hash-verified physical-phone capture on September 30, 2026 produced the two baseline clips and all five compositor screenshots. A welcome frame was extracted from the speech clip; the separate voice preview comes from an earlier matching-APK run. See [baseline provenance](./demo/ami-provenance.json), [capture evidence](./evidence/ami-capture-validation.json) and the [showcase ledger](./SHOWCASE-VALIDATION.md). The microphone recording has its own provenance in the next section. Human speech and listening-quality review remain separate gates. The earlier interrupted capture is retained as failed, not reclassified.

| Asset | What it shows | What it does not show |
| --- | --- | --- |
| [Ami speech-to-answer video](./demo/ami-audio.mp4) | 32.47 seconds of actual phone UI; five synthetic WAVs through real Foundry Whisper and calculated answers | A live microphone or speaker recording; a latency benchmark |
| [Ami recap video](./demo/ami-recap.mp4) | 22.10 seconds of actual phone UI; the separate Qwen fact-card selection path | Free-form model-authored answers or validated ranking usefulness |
| [Ami answer](./demo/ami-answer.png) | Real Android compositor screenshot, system bars cropped, matching that speech run | A repainted name or personal health record |
| [Ami welcome](./demo/ami-welcome.png) | First decoded frame of the same actual screen recording | A separate screenshot or UI mockup |
| [Ami recap](./demo/ami-recap.png) | Actual compositor screenshot after the real Qwen fact-card run | A native-view export or free-form model-authored advice |
| [Today](./demo/ami-today.png), [Evidence](./demo/ami-evidence.png), [Privacy](./demo/ami-privacy.png) | Actual compositor screenshots from that same synthetic capture session | Personal Samsung records or independently verified network isolation |
| [Ami Warm voice](./demo/ami-warm.wav) | New Ami introduction synthesized by the installed offline Android voice | Foundry TTS, microphone input or the video's soundtrack |

Both screen recordings are silent; this test does not play its synthesized files through the speaker. Keep the separately labelled voice sample alongside them. Never dub the preview over another answer as though it were a live conversation. Both clips decode end to end; seven speech and five recap contact sheets at four frames per second, every full-size image and both final video frames were inspected. No private notifications or lock screens were seen in the sampled frames. This is not a substitute for human AV and listening review.

### Additional current-build microphone and output recording

The [46-second microphone demo](./demo/ami-microphone.mp4) and [poster](./demo/ami-microphone.png) are new captures of **Ami 0.3.4-preview**. A known synthetic heartbeat question played from a laptop loudspeaker into the phone microphone; it was not injected directly into Whisper. The new user bubble and **Edit heard question** appear before the calculated answer.

The soundtrack contains actual Android TTS device output. It does not contain the external laptop stimulus or an added voiceover. scrcpy's output capture redirects handset playback, so this video is not a physical-speaker audibility test. A separate unmirrored turn reached TTS completion; replay, a new microphone turn through **Interrupt & talk**, and **Stop voice** also passed. The [reconnection checks](./evidence/ami-reconnect-validation.json) preserve the boundaries, including the unsuccessful pre-stimulus permission attempt and the still-empty Samsung source.

The [new provenance](./demo/ami-microphone-provenance.json) records the APK, media hashes, crop, 20-fps conversion, idle-tail trim and 24 dB gain. No inference was sped up and no answer was inserted. The final clip decodes without errors; its two-fps contact sheet and full-size poster were inspected. Human speech/noise and listening quality remain open. Reproduce this separate, deliberate capture using the [author runbook](./AUTHOR-RUNBOOK.md#capture-the-physical-microphone-demonstration).

The article plays an [H.264/MP3 compatibility copy](./demo/ami-microphone-preview.mp4) so the original AAC audio does not become silent in the VS Code preview. Its video stream is identical to the original. **Sound starts around 0:11**; the listening and transcription steps have no output audio. Both versions are listed in the provenance. See [preview audio compatibility](./AUTHOR-RUNBOOK.md#preview-audio-compatibility) for the conversion and verification steps.

### Historical recording versions

**Source versus recording:** the source is `0.3.4-preview`, as is the new microphone/output recording. The two earlier Ami recordings remain `0.3.3-preview`; they are not represented as new-build or microphone captures. The [device follow-up](./DEVICE-FINDINGS.md) records the checks separately. Do not change older media hashes or version labels to make them appear current.

The earlier **Daylight 0.3.2-preview** [speech video](./demo/audio.mp4), [recap video](./demo/debrief.mp4), [Warm sample](./demo/warm.wav) and [baseline provenance](./demo/provenance.json) remain unchanged for comparison. The earlier [Today](./demo/today.png), [answer](./demo/audio.png), [recap](./demo/debrief.png), [Evidence](./demo/evidence.png) and [Privacy](./demo/privacy.png) screens are also historical. Do not relabel them as new Ami captures.

## Reproduce a device capture

1. Build the code and prepare the exact models through the app. Keep exactly one authorized phone unlocked with Ami foreground. Never script unlock, native health grants, account sync or network settings.
2. Run smoke/audio/debrief/voice checks and inspect their actual results. Do not claim zero health reads for a failed run that never reached its completion assertion.
3. The [capture script](../scripts/capture-demo.ps1) is profiled for the Motorola's unmodified portrait 1080×2400 display. It crops 108 top and 102 bottom system-bar pixels. Different devices need measured insets and a reviewed profile, not guessed tap coordinates.

The script delegates video conversion to [prepare-demo-video.ps1](../scripts/prepare-demo-video.ps1). It resamples screenrecord timestamps to 30 fps without speed adjustment, holds the final still to the original container duration, and verifies dimensions and duration after decoding. This replaces an earlier conversion that mishandled the variable-frame-rate recording. Preserve the unedited source privately and disclose the transformation in the media manifest.

```powershell
.\scripts\capture-demo.ps1 -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' -PreventAutoLock
```

The script verifies the installed APK against the local validation manifest, resets into synthetic mode through the existing debug harness, confirms the synthetic badge, and captures real screens and two bounded screen recordings. For a new build, use [validate-build.ps1](../scripts/validate-build.ps1) first and pass `-ValidationPath .\runtime\build-validation.json`; do not replace an archived hash to bypass verification. `-PreventAutoLock` temporarily extends a shorter idle timeout to three minutes, then verifies restoration. It never wakes or unlocks the phone. If ADB, focus, restoration or a guard fails, the attempt is not a passed capture. Its output remains under ignored `runtime/` until reviewed. No microphone, audible playback, model download or personal-record read is requested.

The `voice` test separately writes three Android TTS preview WAVs into its evidence directory. Preserve the matching JSONL and hashes when selecting one for the article. Do not use an older build's preview without saying so.

## Review before selecting media

- Verify the permanent synthetic badge and the complete answer/provenance. No personal app, notification, consent screen or health value may appear.
- View every screen and the entire recordings. A successful encoder exit code does not establish acceptable content.
- Keep the actual model result or labelled fallback. Do not insert a replacement answer into an inference recording.
- Preserve source APK hash, source event file, recording/crop method and media checksums in the publication manifest.
- Do not change playback speed to suggest better latency. Initial waits include host/ADB work; they are not an end-to-end benchmark.
- Mark the voice file as a separate synthetic voice preview. Listening quality still needs human judgment.

## Suggested article sequence

1. **The idea:** a personal voice experience without sending local context to a cloud model.
2. **The technology:** Foundry Local runtime/SDK, Whisper, Health Connect and Android TTS—attribute each job correctly.
3. **Show it:** one real answer screenshot, the actual speech pipeline recording and the voice sample.
4. **Build it:** IPC versus embedded, pinned setup, catalog/cache/load, transcription, calculated context, optional chat call, validation and cleanup.
5. **Make it useful:** dates, coverage, clarification, interruption and deliberate memory/actions.
6. **Extend it:** better grounded free-form conversation, embedded packaging and different local models, clearly labelled future work.
7. **Reproduce it:** the GitHub repository, build instructions, synthetic assets and tests.

## Publication controls

Generate a local reading preview with `scripts/build-blog-preview.ps1`. If an integrated browser refuses `file:` video requests, use `scripts/build-blog-preview.ps1 -InlineMedia`; it embeds the small synthetic MP4/WAV assets in the generated HTML without a server, external scripts or uploads. Media remains user-controlled and never autoplays. The Markdown source and its ordinary media links stay unchanged.

The local article preview should play assets without an external script/CDN. GitHub Markdown does not guarantee inline video playback; use explicit MP4/WAV links. For Microsoft Tech Community, upload the reviewed assets through its editor and link the actual repository. Do not invent a video-hosting URL or silently publish to a third-party account.

The repository should initially be private for source and licensing review unless the author explicitly approves a public release. A private URL is a review link, not a publicly reproducible blog link. Publication is ready only when the intended audience can access the repository and media.

Do not make the initial article depend on the owner's Samsung syncing issue. The synthetic SDK walkthrough and the real-watch product gate are separate. Keep the latter honestly open in the [validation summary](./SHOWCASE-VALIDATION.md).
