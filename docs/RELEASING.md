# Releasing Zed

Release builds are minified with R8 and shrink resources. They are only signed
when the maintainer supplies a keystore; no signing material is committed.

## 1. Bump the version

Update `versionCode` and `versionName` in `app/build.gradle.kts`, then add a
section to `CHANGELOG.md`.

## 2. Verify locally

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

The unsigned release APK is written to
`app/build/outputs/apk/release/app-release-unsigned.apk` unless a signing
configuration is present.

## 3. Provide signing values

Use shell environment variables or Gradle user properties. Do not put these in
the repository.

```bash
export ZED_RELEASE_STORE_FILE="/secure/zed-release.jks"
export ZED_RELEASE_STORE_PASSWORD="…"
export ZED_RELEASE_KEY_ALIAS="zed-release"
export ZED_RELEASE_KEY_PASSWORD="…"
./gradlew :app:assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/`.

## 4. Tag and publish

```bash
git tag -s v1.1.0 -m "Zed 1.1.0"
git push origin v1.1.0
```

Attach the signed APK and its SHA-256 checksum to the GitHub release. Include
the changelog section, the minimum Android version (8.0 / API 26), and the
required permissions.

## Release checklist

- [ ] Unit tests, assemble, and lint pass.
- [ ] `versionCode` is greater than the previous release.
- [ ] Changelog section is complete.
- [ ] No API key, keystore, local property, or device log is staged.
- [ ] APK is signed with the expected certificate.
- [ ] SHA-256 checksum is published with the APK.
- [ ] Privacy and permission changes are documented.
- [ ] Physical-device smoke test covers audio, interruption, typed turns, and
      one device action.
