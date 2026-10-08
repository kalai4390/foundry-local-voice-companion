# Publication review

Reviewed **October 8, 2026** for [Building an On-Device Voice Companion with Foundry Local](./BLOG.md).

The article, preview and supporting documentation now use the approved title. **Ami** remains the application name. The [GitHub repository](https://github.com/kalai4390/ami-foundry-local) remains **private**, with no license added. This review does not authorize a public release.

## Source and history

- [Gitleaks 8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1), downloaded from its official release and checksum-verified, found **zero secrets** in the source export and existing Git history. The initial history review covered both commits through `eda7ad1`; the source and history scans were repeated for the subsequent publication updates. Default rules were used, with inline allow-comments disabled and findings redacted.
- A separate identifying-data check covered the export's text and all **92 distinct text blobs** in the existing history. It checked the local owner name, computer name, profile paths, email addresses, private-network IPv4 addresses, MAC addresses and hard-coded device-identifier fields. **No matches were found.** This supplements the package script's configured-owner scan; it is not a guarantee that pattern matching detects every kind of personal information.
- Git author and committer addresses use GitHub's noreply domain. The approved GitHub owner/repository link and commit attribution remain intentionally visible; the repository is not anonymous.
- The export allowlist excludes private runtime diagnostics, local tools, build caches, SDK AARs, model weights, APKs and signing keys. No corresponding forbidden paths were found in the existing history. The generated `PACKAGE-MANIFEST.json` lists the selected files and their SHA-256 hashes.

The review covers this source export and its repository history, not unrelated files on the laptop, phone, other repositories or account settings. Scanner reports and contact sheets stay in ignored local runtime storage, outside the publication package.

## Screenshots and recordings

The recordings continue to use **fictional records**, not the author's Samsung Health data. Original media hashes and capture-version labels are unchanged. The microphone clip is `0.3.4-preview`; the earlier Ami screenshots and recap remain `0.3.3-preview`, and the Daylight assets remain historical.

All five original video files decoded end to end. Nine contact sheets sampling those videos at two frames per second, plus the full-size microphone poster, were inspected. No personal notifications, foreign application screens or identifying information were seen in those sampled frames. The unchanged screenshots retain their earlier review and checksum provenance. Sampling cannot rule out every short-lived frame; a final human watch-through is still required before public publication.

The new [preview-compatible microphone video](./demo/ami-microphone-preview.mp4) also decoded end to end. Its extracted H.264 stream has the same SHA-256 as the original. Only the existing captured AAC audio was converted to MP3; no voiceover, further gain, trimming or speed change was added. The [provenance](./demo/ami-microphone-provenance.json) records both files and the checks.

The integrated browser selected the compatibility file and successfully decoded its stereo audio. Sound begins around **0:11**. Playback has no autoplay, and the preview's images loaded successfully. Audio decoding is verified; subjective voice quality and physical-speaker audibility are not inferred from that check. The [author runbook](./AUTHOR-RUNBOOK.md#preview-audio-compatibility) includes the reproduction steps.

## Documentation and application checks

- The publication PNGs, including both landscape diagram exports, were checked for embedded text and EXIF chunks; none were present.
- The preview's browser title and visible heading agree with the Markdown title. The builder derives its title from the article rather than keeping a second hard-coded copy.
- All **nine PowerShell scripts** and **19 fenced PowerShell examples** in the supporting documents and repository README parsed without errors. The package's relative-link and media-checksum checks passed.
- All **59 recorded Android/core build inputs** still match the [clean-source reproduction](./evidence/reproducibility.json). Application code and versions did not change in this publication update. The recorded 171-test/build/lint results are preserved, not presented as a new device run.
- The article keeps the demonstrated roles clear: Foundry Local transcribes speech and supports the separate recap; application code calculates normal answers; offline Android TTS speaks. It does not claim that the real Samsung feed, fine-tuning or disconnected cold-start behavior was demonstrated.

## Publication diagrams

The [voice flow](./voice-flow.svg) and [optional recap](./recap-flow.svg) are now landscape publication figures, not stacked phone-style layouts. Both use a white canvas, left-to-right connectors, a dashed on-device boundary and blue emphasis on the Foundry Local component. The recap remains beside the chat API example. Fictional records are labelled, and neither figure implies a working Samsung Health feed.

- Both SVGs and their raster exports were rendered and visually inspected. Text-bound checks covered **32 voice labels and 25 recap labels**, with no overlapping labels or text outside its component or canvas. Each connector has its own arrowhead. Each SVG has a title and description; the article adds numbered captions and descriptive alt text.
- The preview was checked at **320, 390, 768, 945, 960, 961, 1024, 1280 and 1600 CSS pixels**. Both diagrams retained their landscape source and filled the article column at every width, with no page-level horizontal overflow. The 960-pixel portrait switch and 440-pixel cap are removed, along with the unused portrait assets.
- At a **945-pixel viewport**, the voice figure renders at approximately **842 × 327 pixels**, rather than a tall portrait. The recap renders at approximately **842 × 292 pixels**. On a small phone screen, labels necessarily shrink; the linked SVG opens separately for full-size viewing and zoom. No minimum mobile text-size claim is made.
- The **2880 × 1120 voice PNG** and **2880 × 1000 recap PNG** are 2× exports of the editable SVGs. They contain no embedded text or EXIF metadata. The [author runbook](./AUTHOR-RUNBOOK.md#prepare-the-diagrams) explains full-column placement and full-resolution links for publishing editors. These are authored diagrams, not app screenshots.
- The main voice video's audio decoded successfully after the layout changes: two channels, approximately 46.03 seconds and non-silent samples. Autoplay and muting remained off. This is a playback-compatibility check, not a new microphone recording or a subjective voice-quality assessment.

## Before public release

1. The author chooses and approves a source license, including any required organizational review. SDK and model terms remain separate; their binaries are not included.
2. The author explicitly approves changing repository visibility. A private repository URL is not a usable source link for public readers.
3. Watch and listen to the selected recordings in the intended publishing platform, check its captions and media controls, and keep the fictional-data disclosure with the demo.
4. Re-run the source/history checks if files, media or commits change. Upload only the reviewed assets; do not publish the development workspace wholesale.

No public visibility change, license grant or blog-platform publication was performed as part of this update.
