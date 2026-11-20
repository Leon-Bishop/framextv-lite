# FrameXTV Lite — Android TV

A **text-only, remote-first** Android TV app intended for older, low-memory TVs.

> **Credit to the FrameXTV creators:** The FrameXTV creators deserve all credit for the FrameXTV service, catalogue, publicly documented embeds, playback engine, and upstream infrastructure. This is an **unofficial, independent lightweight Android TV client**; it is not the official FrameXTV app or endorsed by the creators.
It uses Android's native list controls to browse and search, creates **one WebView only during playback**, then destroys that WebView when you return to the catalogue.

## Minimum requirements

- Android **5.0 / API 21** or later (Android TV / Google TV / TV boxes)
- Functional / reasonably current **Android System WebView** that supports the chosen video stream and codec
- Internet connection and access to FrameXTV services
- TV remote with D-pad and OK; Menu and media keys are optional

No third-party app dependencies, no image downloading, no background playback or polling, no accounts, no analytics, no autoplay preview, no bundled streams.

## How to build an APK

### Android Studio

1. Open this folder as a Gradle project in Android Studio.
2. Use JDK 17 and allow Gradle/Android SDK dependencies to download.
3. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Install the generated `app/build/outputs/apk/debug/app-debug.apk` on your TV.

### GitHub Actions (no Android Studio on your computer)

1. Create a new GitHub repository, upload/commit the contents of this project **including `.github/workflows/build-apk.yml`**, and push to `main`.
2. Open **Actions > Build Android TV APK > Run workflow** (or let the push trigger it).
3. Open the finished workflow run, download the **FrameXTV-Lite-APK** artifact, unzip it, and get `app-debug.apk`.
4. Sideload via a file manager on the TV or `adb install -r app-debug.apk`.

The debug APK is signed by the Android build system and can be installed for personal testing. For release distribution, use your own signing key and follow Android's signing guidance.

### Terminal with SDK + Gradle

`gradle :app:assembleDebug`

## How to use

- **Home / Movies / TV** switch catalogue categories; the native search box searches by title.
- Enter a **numeric TMDB ID** in search to show direct movie/TV choices if catalogue API search is not working.
- Press **OK** on a movie to open the official FrameXTV embed player.
- Press **OK** on a TV show, choose season, then choose episode.
- During playback: **OK / Play-Pause** toggles play; **Left/Right** seeks ±10 seconds; **Menu** cycles Auto/Barbarian/Archer/Goblin; **Back** returns to native browsing.
- Use the site's own player controls for subtitles and any settings not exposed through its public postMessage interface.

## Integration design, reliability, and limitations

1. The **published** documentation at `https://framextv.cc/api` describes playback URLs like `https://framextv.xyz/embed/{tmdbId}` and `https://framextv.xyz/embed/{tmdbId}/{season}/{episode}`, along with the `postMessage` control interface. Playback loads the documented `.xyz` host, with a one-time `.cc` fallback on top-level connection failure.
2. The **catalogue** uses the community-observed, **undocumented** `https://api.framextv.tech/api/home`, `/api/search`, `/api/details`, and `/api/season`. These endpoints could change or require additional headers at any time. The app does not use undocumented stream extraction endpoints. If catalogue requests fail, the direct TMDB-ID entry remains available.
3. There is **no guaranteed control over stream resolution/bitrate** via the public embed API; switching servers may help on older hardware, but cannot guarantee smooth playback. The Android System WebView and device hardware decoder determine codec compatibility.
4. Android System WebView can be too old on some TVs, and its video engine is not as efficient as a fully native Media3 player. A native HLS player would require a supported direct-stream API and additional dependencies, neither of which is part of FrameXTV's published embed API.
5. Site availability, playback authorization, ads, media rights, and content availability are managed by FrameXTV, not this app. Use only streams you are authorized to view. This app does not defeat DRM, extract streams, or bypass advertisements.
6. This source project is **not a tested APK**. It needs compiling with an Android SDK and testing on the actual TV. The source can be compiled using the provided GitHub Actions workflow.

## Small implementation notes

- UI is a recycled native `ListView`, with a maximum of 80 catalogue rows and no poster images.
- All catalogue networking runs off the UI thread with connection timeouts and 2 MB maximum response size.
- Only basic Android framework classes are used. Java 8 language level; `minSdk=21`, `targetSdk=28`.
- The player blocks unsolicited top-level navigation away from official embed hosts and prevents popup windows.
- No analytics, accounts, advertising SDKs, private keys, file permissions, or arbitrary downloads.

## Credit and attribution

**All credit for FrameXTV itself goes to the original FrameXTV creators and contributors.** They created and maintain the upstream streaming/discovery platform, catalogue, API, embed player, provider integration, and FrameXTV branding. Thank you to the creators for making the publicly documented integration possible.

Official FrameXTV website: https://framextv.cc/  
Official integration documentation: https://framextv.cc/api

This small Java Android TV client is an unofficial third-party frontend built to reduce the device-side overhead of browsing on older TVs. It is not affiliated with, sponsored by, or endorsed by FrameXTV. It does not bundle FrameXTV code, videos, or stream links, and does not imply any grant of trademark or distribution rights. Media rights remain with their respective owners.

The app displays this credit in its main screen footer; the same wording appears in `CREDITS.md`.
