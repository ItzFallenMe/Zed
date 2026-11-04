# Releasing Z.E.D.

Release builds are minified with R8 and shrink resources. They are signed only
when the maintainer supplies a keystore; no signing material is committed.

## 1. Bump the version

Update `versionCode` and `versionName` in `app/build.gradle.kts`, then add a
section to `CHANGELOG.md`.

For 2.1.3 the values are:

```kotlin
versionCode = 24
versionName = "2.1.3"
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
# Optional: pin the release certificate so creator master control is blocked
# when the installed APK was signed by a different key.
export ZED_SIGNING_CERT_SHA256="<colon-free SHA-256 of the release certificate>"
./gradlew :app:assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/`.

## 4. Tag and publish

```bash
git tag -s v2.1.3 -m "Z.E.D. 2.1.3"
git push origin v2.1.0
```

The `Release Zed APK` workflow runs on the tag, builds a signed release APK,
attaches it to a GitHub release, and publishes a `.sha256` checksum. Include the
changelog section, the minimum Android version (8.0 / API 26), and the required
permissions in the release notes.

### Release workflow secrets

Add these repository secrets before tagging. The workflow fails before building
if any of the first four are missing; it never publishes an unsigned release.

| Secret | Purpose |
| --- | --- |
| `ZED_RELEASE_KEYSTORE_BASE64` | Base64 of the release `.jks` keystore |
| `ZED_RELEASE_STORE_PASSWORD` | Keystore password |
| `ZED_RELEASE_KEY_ALIAS` | Release key alias |
| `ZED_RELEASE_KEY_PASSWORD` | Key password |
| `ZED_SIGNING_CERT_SHA256` | Optional colon-free certificate pin |

Generate the keystore value locally with:

```bash
base64 -w 0 /secure/zed-release.jks
```

For a manual dry run, open **Actions → Release Zed APK → Run workflow**. A
manual run uploads the signed APK and checksum as workflow artifacts but does
not create a GitHub release unless the ref is a `v*` tag.

## Release checklist

- [ ] Unit tests, assemble, and lint pass.
- [ ] `versionCode` is greater than the previous release.
- [ ] Changelog section is complete and includes the 2.1.0 task runner,
      WhatsApp/YouTube automation, owner-voice gating, headset routing, live
      screen context, and history browser.
- [ ] No API key, keystore, local property, or device log is staged.
- [ ] APK is signed with the expected certificate.
- [ ] SHA-256 checksum is published with the APK.
- [ ] Privacy and permission changes are documented (`QUERY_ALL_PACKAGES`,
      `SYSTEM_ALERT_WINDOW`, accessibility, location, contacts, phone).
- [ ] Default creator code `160123` is documented, and release notes tell users
      to change it after first unlock.
- [ ] Physical-device smoke test covers audio, headset routing, interruption,
      typed turns, one device action, creator unlock, and lockout.
- [ ] Screen sharing is tested with consent, the visible stop action, and
      session teardown; WhatsApp/YouTube taps are treated as best-effort.
- [ ] If `ZED_SIGNING_CERT_SHA256` is set, a mismatched test APK is rejected by
      `creatorControl`.
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
