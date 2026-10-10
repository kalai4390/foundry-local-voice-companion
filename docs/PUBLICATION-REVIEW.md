# Publication review

This is the release checklist for the [article](./BLOG.md), code and demo assets. The reader-facing documentation has been rewritten around building and trying Ami. The application code, recorded test results, diagrams and original media are unchanged by that editorial work.

**Release status:** the GitHub repository remains private, with no source license. Preparing the copy does not publish the repository or grant a license. Both decisions are still needed before an external reader can follow the source link and reuse the project on stated terms.

## Source and identifying information

The publication package contains an explicit list of source files, documents and selected media. It excludes runtime diagnostics, tools, build caches, APKs, SDK AARs, model weights and signing keys. `PACKAGE-MANIFEST.json` records the file list and SHA-256 hashes.

[Gitleaks 8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1) was downloaded from its official release and checksum-verified for the original review. Source-export and full-history scans use its default rules, disable inline allow-comments and redact findings. The recorded scans found no secrets; scans are repeated before each publication update is pushed.

The identifying-data review checks owner and computer names, profile paths, email addresses, private-network addresses, MAC addresses and hard-coded device identifiers. The original history review covered 92 distinct text blobs; later edits require another pass. A pattern scan cannot identify every possible piece of personal information, so it is used alongside media and text review.

The approved GitHub owner/repository link and GitHub-noreply commit attribution are intentionally visible. The project is not anonymous. Scanner reports and private contact sheets stay outside the package.

## Screenshots and recordings

The demos use fictional records, not the phone owner's Samsung Health data. The microphone clip is **0.3.4-preview**; the earlier Ami screenshots, silent recordings and voice introduction are **0.3.3-preview**. Historical Daylight assets keep their original labels. The [demo guide](./DEMO-GUIDE.md#recording-details) links their capture notes.

The five original videos decoded end to end. The review included nine contact sheets sampled at two frames per second and the full-size microphone poster; the earlier two-video capture also had its own four-fps review. No personal notification, unrelated app screen or identifying information was seen in those sampled frames. Sampling cannot exclude every short-lived frame, so a human watch-through is still required before publication. Unchanged images retain their recorded checksums and review history.

The [preview-compatible voice video](./demo/ami-microphone-preview.mp4) keeps the original H.264 stream and converts its captured AAC audio to MP3. That conversion adds no narration, gain, trim or speed change. The original capture's 24 dB amplification remains disclosed in its [notes](./demo/ami-microphone-provenance.json).

The preview browser decoded the stereo audio, with sound beginning around **0:11**. Autoplay and muting were off. That confirms playback compatibility, not the quality of the voice or the handset speaker. The [audio instructions](./AUTHOR-RUNBOOK.md#preview-audio-compatibility) explain how to repeat the check.

## Documentation and figures

The README starts with the conversation and links to the build, demos and walkthrough. Test history stays in the [test notes](./VALIDATION.md), and watch-sharing findings stay in the [device notes](./DEVICE-FINDINGS.md). The article keeps the roles clear: Foundry Local transcribes speech and runs the optional recap model; Kotlin calculates normal answers; Android TTS speaks them.

The [voice](./voice-flow.svg) and [recap](./recap-flow.svg) figures use a landscape layout at every preview width. They include numbered captions, alt text and full-size links. The earlier diagram review checked 32 voice labels and 25 recap labels without overlap or text escaping its component. Both 2× PNG exports were visually inspected and contained no text or EXIF metadata.

That review covered viewport widths from 320 to 1600 CSS pixels, including 945 pixels where the former portrait switch was most noticeable. At 945 pixels the voice figure was about 842 × 327 and the recap 842 × 292. The figures remain unchanged in this editorial update. On narrow screens, the full-size link provides a readable zoomable copy; the inline labels necessarily shrink.

The preview title is derived from the Markdown heading. Packaging checks relative links, original media checksums and all 59 recorded Android/core build inputs. PowerShell scripts and fenced examples are parsed as part of the editorial checks. The documented 171-test/build/lint result remains the recorded application result, not a new phone run.

## Before public release

1. Choose a source license and complete any required organizational review. The SDK and model terms are separate; their binaries are not bundled.
2. Approve public repository access so readers can reach the code linked from the article.
3. Watch and listen to the chosen videos on the publishing platform, including the opening silent seconds, captions and playback controls. Keep the fictional-data disclosure.
4. Repeat the source/history and identifying-data checks after any file, media or commit change. Upload only the reviewed package, not the development workspace.

No repository visibility change, license grant or blog-platform publication is part of this documentation update.
