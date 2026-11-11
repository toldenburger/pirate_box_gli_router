# Box Chat (Android app)

Optional companion app for the owner's phone. When the phone is on the box's Wi-Fi it polls the chat every 5 seconds and shows
a notification for new messages (not for your own nickname, and never for old history). Tapping it opens a small chat screen to
read and reply. Your phone keeps using mobile data for everything else.

Package `box.chat.alerts`, plain Java (no Gradle, no libraries). Box address: `http://192.168.60.1`, fixed in two places that
must agree: `src/box/chat/alerts/Prefs.java` (`BASE`) and `res/xml/network_security_config.xml` (cleartext HTTP is allowed
for that one address only; everything else stays HTTPS-only).

## Build and install

Needs the Android SDK (platform `android-37.0` and build-tools `37.0.0` are what `build.sh` expects; edit the two version
strings at the top if yours differ) and a JDK 17.

```
cd android
./build.sh                     # -> box_chat.apk   (aapt2 + javac + d8 + zipalign + apksigner)
adb install -r box_chat.apk    # USB debugging on; or send the file to the phone any other way
```
`build.sh` creates its own signing key (`debug.jks`, password `android`) on first run. Keep it: updates must be signed with the
same key. The key and the APK are git-ignored and **must not be committed**. Raise `--version-code` in `build.sh` for each new
build, or Android refuses to update the app. No APK is shipped in this repository: build your own.

Parser test (plain Java, no phone): compile `src/box/chat/alerts/ChatParser.java` with `test/ParserTest.java` and run
`box.chat.alerts.ParserTest`.

## Phone setup that matters (found the hard way, Android 17 / GrapheneOS)

1. **If you run an always-on, non-bypassable VPN (Tailscale, for example), exclude Box Chat from it.** While such a VPN is active
   Android refuses ordinary apps the right to bind a connection to a specific network (`EPERM`), which is exactly what the app has
   to do to reach a Wi-Fi that has no internet. Look for "split tunnelling" / "per-app" / "excluded apps" in the VPN app.
   (You can confirm with `adb shell dumpsys connectivity`: a VPN line with `bypassable=false`.)
2. **Do not choose "use as is" / "stay connected"** at the "Sign in to network" prompt of the box Wi-Fi. Doing so made Android treat
   it as a validated network and make it the *default* one, so every other app lost its internet. Ignore the prompt (or forget the
   network and rejoin). Mobile data then stays the default and the box Wi-Fi is a side network.
3. Allow notifications for the app; keep Wi-Fi and "auto-connect" on for the box network.

## What the screen tells you

The **Detail:** line shows what the listener is doing: `Connected. Last check HH:MM:SS` is healthy; otherwise it shows the real
error (for example `Binding socket to network N failed: EPERM`, which is setup point 1). Your nickname (no alerts for your own
messages, and the name used when you reply) is the box at the bottom of the screen; the default is `owner`.

Handy over adb when debugging: `adb shell dumpsys connectivity` (default network, the Wi-Fi's capabilities, the app's network
request), `adb shell uiautomator dump` (read the screen text), `adb shell dumpsys notification --noredact`.

## Notes

- A foreground service of type `specialUse` keeps the listener alive; it shows a silent "Box Chat" notification.
- It restarts after a phone reboot (if alerts are switched on). The switch in the app turns it off.
- Every poll is one line in the box's web log (about 1.7 MB per day while connected); `tools/show_log.py` counts them separately.
