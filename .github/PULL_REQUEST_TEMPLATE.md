## Summary

<!-- What changed and why? Link the issue if one exists. -->

## Verification

- [ ] `./gradlew :app:compileDebugKotlin`
- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `./gradlew :app:assembleDebug :app:lintDebug`
- [ ] Physical-device testing completed for audio, Live, accessibility, overlay,
      creator, or device-action changes

## Safety and privacy

- [ ] No API keys, keystores, creator codes, personal data, or device logs are
      included
- [ ] Creator code, speech transcript, wake audio, and speaker vectors still
      stay on-device
- [ ] Permission and device-action changes are documented
- [ ] New tools validate arguments and return a structured, honest result

## UI and docs

- [ ] Material 3 remains the default; liquid glass remains opt-in
- [ ] Screens use theme tokens rather than hard-coded colours
- [ ] `CHANGELOG.md` and relevant docs are updated for user-visible changes
