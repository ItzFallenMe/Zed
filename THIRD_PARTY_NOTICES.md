# Third-party notices

Z.E.D. is MIT licensed. The following components are included in source or
binary form, or are contacted at runtime. The notices below are provided to
satisfy their license terms; they do not change the MIT license of the rest of
the project.

## Robot-head avatar

- **Files:** `app/src/main/assets/robot-head/avatar.html`,
  `app/src/main/java/com/zed/assistant/ui/ZedRobotAvatar.kt`
- **Component:** robot-heads v0.3.0, bundled locally and rendered offline
- **License:** MIT
- **Copyright:** Fayaz Ahmed
- **Source:** https://github.com/fayazara/robot-heads
- **Use:** the state-driven robot-head canvas used on Home and in the overlay.

The bundled page also contains a production build of React 19, which is
distributed under the MIT license:

- **Copyright:** Meta Platforms, Inc. and affiliates.
- **Source:** https://github.com/facebook/react

### MIT license text

```text
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Vosk speech recognition

- **Files:** `app/src/main/assets/model-en-us/`
- **Component:** Vosk US English mobile model
- **License:** Apache License 2.0
- **Copyright:** 2020 Alpha Cephei Inc.
- **Source:** https://alphacephei.com/vosk/ and https://github.com/alphacep/vosk-api
- **Use:** fully offline wake-word detection. Audio is not uploaded.

## Speaker-vector model

- **Files:** `app/src/main/assets/model-spk/`
- **Component:** Kaldi x-vector speaker-embedding model and configuration
- **License:** Apache License 2.0
- **Source:** Kaldi `egs/callhome_diarization/v2` recipe and the x-vector
  recipe published by David Snyder.
- **Use:** on-device owner-voice embeddings. Audio and vectors stay local.

If you use this architecture in a publication, the upstream model asks that you
cite:

> D. Snyder, D. Garcia-Romero, G. Sell, D. Povey, and S. Khudanpur,
> "X-vectors: Robust DNN Embeddings for Speaker Recognition," ICASSP 2018.

## AndroidX, Jetpack Compose, Kotlin, OkHttp, and coroutines

These dependencies are resolved from Google Maven / Maven Central at build time
and are not vendored in this repository. They are used under the Apache License
2.0, MIT, or the license declared by each artifact. License texts and notices
are available in the resolved artifacts and in the dependency metadata produced
by Gradle.

## Gemini Live

Z.E.D. communicates with Google's Gemini Live API at runtime using a
user-supplied API key. Gemini, Google AI Studio, and related marks are property
of Google. No Google SDK, model weights, or API key are redistributed with this
project.

## Overlay art and project assets

The overlay toy pages and the Z.E.D. robot-head logo are project assets
distributed under the same MIT license as the application. They are not copied
from an unrelated proprietary product.

## Runtime services

- **Open-Meteo** (https://open-meteo.com/) provides weather data when the user
  asks for weather. No API key is bundled.
- **YouTube** and **WhatsApp** are separate applications. Z.E.D. hands off to
  them through Android intents and does not redistribute their code or assets.
