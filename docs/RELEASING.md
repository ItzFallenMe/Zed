# Releasing Z.E.D.

Release builds are minified with R8 and shrink resources. They are signed only
when the maintainer supplies a keystore; no signing material is committed.

## 1. Bump the version

Update `versionCode` and `versionName` in `app/build.gradle.kts`, then add a
section to `CHANGELOG.md`.

For 2.0.0 the values are:

```kotlin
versionCode = 20
versionName = "2.0.0"
```

`versionCode` must always increase, even for a documentation-only rebuild.

## 2. Verify locally

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

The unsigned release APK is written to
`app/build/outputs/apk/release/app-release-unsigned.apk` unless a signing
configuration is present.

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

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
git tag -s v2.0.0 -m "Z.E.D. 2.0.0"
git push origin v2.0.0
```

Attach the signed APK and its SHA-256 checksum to the GitHub release. Include
the changelog section, the minimum Android version (8.0 / API 26), and the
required permissions.

## Release checklist

- [ ] Unit tests, assemble, and lint pass.
- [ ] `versionCode` is greater than the previous release.
- [ ] Changelog section is complete and includes the 2.0.0 creator protocol,
      voice presets, Material 3 default, and robot-head avatar.
- [ ] No API key, keystore, local property, or device log is staged.
- [ ] APK is signed with the expected certificate.
- [ ] SHA-256 checksum is published with the APK.
- [ ] Privacy and permission changes are documented (`QUERY_ALL_PACKAGES`,
      `SYSTEM_ALERT_WINDOW`, accessibility, location, contacts, phone).
- [ ] Default creator code `160123` is documented, and release notes tell users
      to change it after first unlock.
- [ ] Physical-device smoke test covers audio, interruption, typed turns, one
      device action, creator unlock, and lockout.
- [ ] Release APK is tested with liquid glass off (default) and on.

## Versioning policy

Z.E.D. uses semantic versioning:

- **MAJOR** — creator protocol, permission model, or incompatible storage
  changes.
- **MINOR** — new user-facing capabilities, voice presets, or presence features.
- **PATCH** — fixes, copy, performance, and documentation that do not change
  the contract.

The 1.x milestone entries in `CHANGELOG.md` are a consolidated development
history; they are not separate Git tags.
