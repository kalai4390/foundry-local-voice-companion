# Watch Ami

These recordings show Ami running on a Motorola g35 with fictional wearable records. They are recordings of the Android app, not interface mockups. You can follow the same steps using the [build guide](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BUILD-GUIDE.md).

## Ask a question and hear the answer

[Watch the voice demo — 46 seconds, with sound](https://github.com/kalai4390/foundry-local-voice-companion/issues/1).

<a href="https://github.com/kalai4390/foundry-local-voice-companion/issues/1"><img src="https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-microphone.png" width="320" alt="Ami's recorded microphone demonstration, using fictional records"></a>

The question is “When was my heartbeat high this week?” Ami records it, transcribes it with Foundry Local Whisper, calculates an answer and speaks it with Android TTS. **Sound starts at 0:11**, when the reply begins.

For a repeatable test, a laptop speaker played the question into the phone's microphone. The laptop did not run the model or calculate the answer. The soundtrack contains the phone's speech output, not the input question or an added voiceover.

The GitHub player uses the [original MP4](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-microphone.mp4), with H.264 video and AAC audio. A second copy with MP3 audio is included for editors whose preview cannot decode AAC. Both copies are listed in the [recording notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone-provenance.json).

## Try the weekly recap

[Watch the recap demo — 22 seconds, silent](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-recap.mp4).

<a href="https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-recap.mp4"><img src="https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-recap.png" width="320" alt="Ami displaying calculated fact cards selected by a local Qwen model"></a>

This is a separate action from the voice conversation. After confirmation, Qwen 2.5 0.5B runs through Foundry Local and selects two calculated fact cards. Ami validates the IDs and displays the original card text. The model is selecting facts, not writing new advice.

The [earlier speech test](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-audio.mp4) is also available. That 32-second silent recording sends five prerecorded questions directly through Whisper rather than recording a microphone turn.

To hear the installed offline Android voice on its own, [play the Warm preview](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/ami-warm.wav). It is a separate introduction, not the soundtrack of either silent video. Do not place it over an answer and present the result as a live conversation.

## Screenshots and diagrams

| Image | Use it to show |
| --- | --- |
| [Welcome](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-welcome.png) | Where a voice turn starts; a frame from the speech recording |
| [Answer](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-answer.png) | A dated heart-rate answer with its source and coverage |
| [Today](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-today.png) | Activity, sleep and coverage at a glance |
| [Evidence](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-evidence.png) | The records and calculations behind a reply |
| [Privacy](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-privacy.png) | Model preparation and data controls |
| [Recap](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-recap.png) | Two selected fact cards |

The screenshots come from Android's screen capture; system bars were cropped. The welcome image is a decoded video frame. All use fictional records.

For an article or presentation, use the landscape [voice diagram](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/voice-flow.png) and [recap diagram](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/recap-flow.png) at the full column width. Editable [voice SVG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/voice-flow.svg) and [recap SVG](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/recap-flow.svg) files are included. Keep the diagrams wide rather than formatting them like phone screenshots.

## Recording details

| Recording set | App version | Details |
| --- | --- | --- |
| Microphone video and poster | Ami 0.3.4-preview | [Capture and editing notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-microphone-provenance.json) |
| Earlier Ami videos, screenshots and voice preview | Ami 0.3.3-preview | [Media notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/ami-provenance.json) and [device capture results](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/evidence/ami-capture-validation.json) |
| Earlier Daylight recordings | Daylight 0.3.2-preview | [Original media notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/demo/provenance.json) |

The microphone recording was cropped, resampled to 20 fps and trimmed after the turn. Its quiet output track was amplified by 24 dB. Inference was not sped up, and no answer was inserted. The capture tool redirected phone audio, so the video is not a test of the handset speaker's loudness. The [device notes](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/DEVICE-NOTES.md) cover the separate unmirrored turn, replay, interruption and Stop checks.

The earlier silent recordings were resampled to 30 fps while preserving duration. Their full-size images, final frames and sampled contact sheets were reviewed; both videos decoded end to end. The microphone video received its own decode and sampled-frame review. A human watch-through and listening check are still needed on the intended publishing platform.

Historical Daylight [speech](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/audio.mp4), [recap](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/debrief.mp4) and [voice](https://raw.githubusercontent.com/kalai4390/foundry-local-voice-companion/main/docs/demo/warm.wav) files remain for comparison. They are not the clips used in the article. An earlier capture interrupted by screen lock remains a failed attempt in the test records.

## Record your own demo

First build and validate your copy, prepare the models and leave Ami unlocked in sample mode. Use your own build report when capturing:

```powershell
.\scripts\capture-demo.ps1 `
  -ValidationPath .\runtime\build-validation.json `
  -FfmpegPath 'C:\tools\ffmpeg\bin\ffmpeg.exe' `
  -PreventAutoLock
```

This script captures screenshots and silent test videos; it does not record the microphone or play speech aloud. Its crop profile is for the tested 1080 × 2400 display. Other phones need measured insets. The optional timeout change is temporary and must be restored; the script never unlocks the phone.

For a microphone recording with device-output sound, follow the [separate voice-capture steps](https://github.com/kalai4390/foundry-local-voice-companion/blob/main/docs/BUILD-GUIDE.md#capture-the-physical-microphone-demonstration). Keep raw captures in ignored local storage until reviewed. Check every selected image and the full video for personal notifications, account screens and health records before sharing, and retain the capture settings and checksums with the chosen files.
