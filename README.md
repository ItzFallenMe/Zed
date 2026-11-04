# Zed — voice-first Android assistant

Native Android scaffold for a Gemini Live voice assistant.

## Baseline
- Android Kotlin + Jetpack Compose.
- Gemini Live audio-to-audio over a persistent WebSocket.
- 16 kHz mono PCM16 little-endian microphone input.
- 24 kHz PCM16 speaker output with sequential chunk playback.
- Interruption handling and an internal 440 Hz speaker diagnostic.
- Allowlisted app launch, validated HTTP(S) URL opening, and dialer handoff.

## Model and protocol
The default model should be `gemini-3.8-live`. Google currently recommends it for most low-latency voice-agent experiences. `gemini-3.1-flash-live-preview` remains available but is now marked legacy preview. Live API audio is raw PCM16 little-endian: 16 kHz input and 24 kHz output. Verify current model access and API project billing before running.

## Security and platform limits
Enter the Gemini API key for each session; this app does not save it. Do not distribute an APK that contains a long-lived API key: mobile client secrets can be extracted. Use a backend to mint short-lived tokens for production. Device actions are allowlisted. Phone numbers open the dialer only; no calls are placed. Contact lookup, background listening, arbitrary intents, shell commands, and arbitrary JavaScript are not implemented.

## Build
`gradle :app:assembleDebug`

The scaffold is not yet production-ready. It still needs a build run, Android-device audio verification, Live tool-call protocol testing, token provisioning, and broader error/reconnect tests.