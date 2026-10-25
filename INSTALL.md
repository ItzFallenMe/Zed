# Installing Zed

Zed is a sideloaded open-source app. Google Play Protect treats that combination — an APK it has
never seen, asking for accessibility and notification access — as suspicious by default, and may
show **"Unsafe app blocked"** or **"App scan recommended"**. Nothing is wrong with the APK; the
warning is about the permission profile every remote-control assistant needs.

Pick one of these, best first.

## 1. Build it yourself (no warning)

The most reliable way past the scanner is to be the one who built the app.

```bash
git clone https://github.com/ItzFallenMe/Zed
cd Zed
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run. Your own build is still scanned, but it is
rarely blocked because the signing history is local.

## 2. Install the release APK with "Install anyway"

1. Download the APK from the Releases page.
2. Tap it. When Play Protect says *Unsafe app blocked*, choose **More details → Install anyway**.
   On some versions the button is **Install without scanning**.
3. If the dialog offers only **OK**, use option 3.

## 3. Temporarily turn off Play Protect's scanner

This is the documented way to install any app the scanner refuses:

1. Open **Play Store → profile picture → Play Protect → Settings** (gear icon).
2. Turn off **Scan apps with Play Protect**.
3. Install the APK.
4. Turn scanning back on immediately afterwards.

Zed keeps working after scanning is re-enabled. Play Protect may offer to "remove harmful app"
later; ignore it, or add Zed to the allowed list if your ROM has one (MIUI, HyperOS, Realme and
ColorOS all have a similar toggle).

## OEM extras

Some Android skins add their own blockers on top of Play Protect:

| Phone | Extra step |
|---|---|
| Xiaomi / Redmi / POCO (MIUI, HyperOS) | Settings → Privacy → **Install unknown apps** for your file manager, and turn off **MIUI optimisation** if the installer loops |
| Realme / OPPO / OnePlus (ColorOS) | Settings → Security → **Install unknown apps**, then allow the file manager |
| Samsung (One UI) | Settings → Security → **Install unknown apps**; the "Auto Blocker" toggle must be off |
| Vivo / iQOO (Funtouch) | Settings → Security → **Install unknown apps**, plus iManager's app-scan prompt |

## Why Zed asks for what it asks for

| Permission | Why | Optional |
|---|---|---|
| Microphone | wake word and voice commands | no |
| Display over other apps | the floating card that unfolds on "zed" | yes |
| Accessibility | taps, swipes, typing, reading the screen | yes (no phone control without it) |
| Notification access | reading unread messages aloud | yes |
| Modify system settings | brightness control | yes |
| Camera | intruder snapshot in the guard | yes |
| Contacts / Phone | "call mom" | yes |
| Screen capture | seeing the screen (asks once per session) | yes |

Zed deliberately does **not** request device admin, SMS, or install-packages permissions, which is
why the guard locks the screen through accessibility and why it cannot send SMS on your behalf.

## Verify your download

Every release lists the APK's SHA-256. On a computer:

```bash
sha256sum Zed-<version>.apk
```

Compare it with the value in the release notes before installing.

## Uninstall

Uninstalling removes the app, its memories, voiceprints and screenshots. If you want to keep them
for a reinstall and you have a rooted device or `adb backup` enabled, back up first — the app
stores nothing in the cloud by design.
