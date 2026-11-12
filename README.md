<p align="center">
  <img src="docs/arrtv/banner.png" alt="ArrTV" width="480" />
</p>

# ArrTV

ArrTV is an IPTV app for **Google TV / Android TV**, phones and tablets. It plays live TV with a
full program guide, movies, series and recordings from a **Dispatcharr** server, an **Xtream
Codes** provider or an **M3U** playlist.

> [!TIP]
> **ArrTV is built to be used with [Dispatch More](https://github.com/ckegels/dispatch-more)**, a
> modified Dispatcharr server. ArrTV works with any Dispatcharr, but many of its best features
> need Dispatch More on the server: see [Works best with Dispatch More](#works-best-with-dispatch-more).

ArrTV is a modified version of [AerioTV for Android](https://github.com/jonzey231/AerioTV-Android)
by Logan Jones. It keeps everything AerioTV does and adds a TV player overlay in the style of
classic IPTV set-top apps, live updates from Dispatcharr, a compact TV layout, simpler
recording, speed fixes for low-end TV boxes, and its own update channel. ArrTV is not affiliated
with AerioTV or with the Dispatcharr project.

**Contents:** [Works best with Dispatch More](#works-best-with-dispatch-more) · [Install](#install) ·
[First setup](#first-setup) · [Updates](#updates) · [What ArrTV adds](#what-arrtv-adds) ·
[Remote control](#remote-control) · [Features](#features) · [Supported servers](#supported-servers) ·
[Tips for low-end TV boxes](#tips-for-low-end-tv-boxes) · [Building from source](#building-from-source) ·
[License](#license)

## Works best with Dispatch More

[Dispatch More](https://github.com/ckegels/dispatch-more) installs over an existing Dispatcharr
(Linux, LXC or Docker) with one command, and comes off again with one button. Together with
ArrTV it adds:

| Feature | What it does |
|---|---|
| **Each TV is its own device** | Switching channels on one TV never cuts off another TV on the same provider login, and each TV gets streams it can play. |
| **Faster failover** | A stream that does not start is replaced by the next one sooner; a stuttering stream is reported so the server can switch. |
| **Report a problem** | One press sends what went wrong with a channel to the server. |
| **Wrong guide? Choose another** | Pick the right guide for a channel from the player, by comparing what is on each with the picture. It changes for everyone. |
| **Instant updates** | New channels, Show Groups (channels grouped by what is on, like Cooking or Movies) and guide changes appear within seconds. |
| **Ad breaks skipped in recordings** | Breaks the server marks are skipped while you watch; the recording is never cut. |
| **Recordings that start** | On a Linux Dispatcharr install, stock Dispatcharr never starts a recording; Dispatch More fixes that. |

Each of these has a switch in ArrTV (Settings › General › arrTV optimizations) and on the server.
Without Dispatch More, ArrTV works like any Dispatcharr app and simply leaves these out.

## Install

ArrTV is not in the Play Store. It installs from this repository's releases.

**Direct download (always the latest version):**
`https://github.com/ckegels/AerioTV-Android/releases/latest/download/ArrTV.apk`

**On Google TV / Android TV**

1. Install the **Downloader** app (by AFTVnews) from the Play Store.
2. In Downloader, enter the direct download link above.
3. When asked, allow Downloader to install unknown apps, then install ArrTV.

Other ways: send `ArrTV.apk` to the TV with an app like *Send Files to TV* and open it with a
file manager, or install it from a computer with `adb install ArrTV.apk`.

**On a phone or tablet:** open the direct download link, open the downloaded file, and allow
your browser to install unknown apps.

ArrTV has its own app ID (`com.ckegels.arrtv`), so it installs **next to** AerioTV instead of
replacing it. Android 8.0 or newer is required.

**The server:** to get everything ArrTV can do, install [Dispatch More](https://github.com/ckegels/dispatch-more#install)
on your Dispatcharr (one command) and switch on what you want under Settings → Streaming → arrTV.

## First setup

1. Open ArrTV. On the welcome screen it looks for **Dispatcharr servers on your network** for a
   few seconds. Pick the one it finds and enter your username and password.
2. Otherwise choose **Connect a Server** and pick Dispatcharr, Xtream Codes or M3U.
3. Optional: **Sync via Google Account** carries playlists, favorites, reminders, watch progress
   and settings between your devices.

Network discovery only searches the TV's own network on Dispatcharr's standard port 9191. A
server in another network, behind a VPN or on another port is added with **Connect a Server**.

## Updates

ArrTV updates itself from this repository's releases.

- **Update** at the bottom of the side panel (compact layout), or **Settings › App Updates ›
  Check for updates**: checks now and, when there is a new version, downloads and installs it.
- **Check for updates automatically** (on by default) looks for a new version each time ArrTV
  opens and offers it once. Nothing is installed without somebody pressing Update.
- Android asks once to allow ArrTV to install apps. Android 12 and newer then update without
  asking again; Android 11 and older show a confirmation for each update. Your channels,
  settings and recordings are kept.
- Each release includes a compile profile, so the app is fast right away after an update.

Updates are refused while a local recording is running, so a recording is never cut off.

## What ArrTV adds

Compared with AerioTV:

**Player (TV)**
- **Info bar overlay** (Settings › Player › Overlay Style › Info bar, off by default). Changing
  channel shows a bar along the bottom with the channel logo, the programme with season and
  episode, time, progress, minutes left, channel number and name, picture format, description
  and what's next.
- **OK** shows the same bar with a timeline, **start, back, play / pause, forward and live**
  buttons, and a row of cards: **TV guide**, **History** and the next channels in the guide.
- **Hold OK**, or **Down** from the cards, slides up the options: report a problem, wrong guide
  (Dispatch More), subtitles, audio track, speed, picture scale, record, sleep timer, stream
  info, switch stream, Multiview and audio only.
- Channel up / down wraps around from the last channel to the first and back.

**Recording**
- **Start 5 minutes early and end 5 minutes late** by default (Settings › DVR).
- **The record sheet asks one thing:** this episode, all episodes or new episodes only. One line
  says what will happen; **Adjust** changes the times, the destination or ad breaks for that
  one recording.
- **Ad breaks skipped** in server recordings (with Dispatch More and Comskip in Mark mode):
  skipped automatically with a few seconds to press Left and go back, or with a **Skip ad**
  button (Settings › DVR › Commercial Breaks).

**Dispatcharr**
- **Live updates** (Settings › General › Live updates, on by default, needs a username and
  password login): new, removed or changed channels and guide updates appear while the app is
  open, without a restart.
- **With [Dispatch More](https://github.com/ckegels/dispatch-more)**: see
  [Works best with Dispatch More](#works-best-with-dispatch-more). Each part has its own switch
  in Settings › General › arrTV optimizations.
- Background data (the scheduled refresh and the guide sweep) shows up in the open app, and the
  scheduled refresh also runs while one channel is playing.

**TV layout**
- **Compact modern layout** (Settings › Appearance › Layout, TV only, off by default): a
  navigation rail on the left instead of the top tab bar, a guide with a group sidebar and a
  programme preview, and compact guide rows. Turning it off restores your previous settings.
- Monochrome theme by default, and the ArrTV icon and TV banner.

**Language**
- **English and French.** ArrTV follows the device: French when the TV or phone is set to any
  French, English otherwise. Settings › Appearance › Language picks one for ArrTV only (on
  Android 13 and newer this is also the system's App languages page).

**Speed and reliability**
- The guide loads several times faster on low-end boxes (22 s → 6 s on a Chromecast with Google
  TV HD), paints a small time window first, and stays on screen when memory runs low.
- Delete Playlist is instant; the playlist's cached data is removed in the background.

## Remote control

Standard buttons in the player (all of them can be changed in Settings › Remote Control):

| Button | Short press | Hold |
|---|---|---|
| Up / Down | Next / previous channel | Recently watched / Search |
| OK | Show the info bar and cards (or the player controls) | Options (info bar style) |
| Left | The guide (or a channel list from the side, if switched on in Settings › Player) | Back to the guide, playback continues in the corner |
| Right | Previous channel | Program info |
| Back | Back to the guide, playback continues in the corner | Stop playback |

In the guide, OK plays the selected channel and holding OK opens the programme menu (reminder, recording, catch-up).

**Remote tour.** The first time a TV has the guide loaded, ArrTV offers a short tour: the app
moves through the guide and the player by itself while a remote at the side of the screen lights
up each button and says what it does (in English or French). Back stops it. Start it again any
time from Settings › Remote Control › Show the remote tutorial.

## Features

Everything from AerioTV, including:

- **Live TV and guide**: hardware-accelerated playback (Media3 / ExoPlayer, HLS, DASH, MPEG-TS, HEVC and HDR where the device supports it), a full EPG grid, favorites, hidden groups, reminders, catch-up on channels that support it, and jump to any cached day.
- **Live Rewind**: pause and rewind live TV from a buffer on the device (Settings › Player).
- **Multiview**: up to 9 channels at once, with audio on the selected tile.
- **Recording**: server-side on Dispatcharr (keeps running when the app is closed, with ad breaks marked by Comskip) and local recording on the device for every server type, with start-early and end-late buffers.
- **Movies and series** from Dispatcharr (with TMDB details) and Xtream Codes, with Continue Watching.
- **Casting** from a phone to Google Cast devices.
- **Picture-in-picture**, sleep timer, audio-only mode, subtitle and audio track selection (DVB, closed captions, and teletext subtitle pages such as 888, which most European channels use), stream info, and refresh-rate matching.
- **LAN / WAN switching**: a separate home address for your server that is used automatically on your home network.
- **Google Drive sync** (optional).

## Supported servers

- **Dispatcharr** (recommended), with username and password or an API key. Server-side recording and Comskip, stream switching, TMDB-rich movie and series details, fast bulk guide loading, network discovery and live updates.
- **Xtream Codes**, with the URL, username and password from your provider. Live TV, movies and series, local recording.
- **M3U**, with a playlist URL and an optional XMLTV guide URL. Live TV and local recording.

Over-the-air MPEG-2 channels (for example from an HDHomeRun) play on most TV boxes, but many phones and tablets cannot decode MPEG-2. With Dispatcharr, assign those channels an ffmpeg stream profile that converts them to H.264; see the [AerioTV README](https://github.com/jonzey231/AerioTV-Android#ota--hdhomerun-channels-mpeg-2) for the exact parameters.

## Tips for low-end TV boxes

On boxes with little memory and a slow processor (for example the Chromecast with Google TV HD):

- Update ArrTV through **App Updates**, so it is prepared for your device during the install.
- Close apps you are not using. Android keeps them in memory and the whole device slows down when memory runs out.
- **Live Rewind** writes the channel you watch to storage continuously. Turn it off (Settings › Player) if you do not use pause and rewind.
- A smaller **Guide Days** setting on the playlist keeps less guide data to load at startup.

## Building from source

Requirements: Android Studio (or JDK 21 from JetBrains, as `gradle/gradle-daemon-jvm.properties` asks) and the Android SDK 36.

```
git clone https://github.com/ckegels/AerioTV-Android.git
cd AerioTV-Android
./gradlew :app:assembleGithubDebug
```

- The `github` flavor contains the in-app updater; the `play` flavor does not.
- The updater's release repository, its trusted signing certificate and the default theme are Gradle properties, so a build can point them at its own releases:
  - `aerio.updateRepo=owner/repo` (default: the official AerioTV releases)
  - `aerio.updateKeySha256=<SHA-256 of the release signing certificate>`
  - `aerio.defaultTheme=Monochrome` (any theme name; default `Aerio`)
- The updater only runs in builds signed with the certificate it trusts; debug builds hide it.
- Google Drive sync needs your own Google Cloud OAuth web client ID in `local.properties` as `GOOGLE_DRIVE_WEB_CLIENT_ID`; without it only sync is disabled.

The ArrTV source of every release is on its tag (`v<version>`), and the `personal` branch holds the current combined source.

## License

ArrTV is a modified version of AerioTV for Android.

Copyright (C) 2026 Logan Jones (AerioTV)
Modifications copyright (C) 2026 ckegels (ArrTV)

ArrTV is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

An additional permission under GPL section 7 covers linking with the proprietary Google Play services client libraries. See [LICENSE-EXCEPTIONS.md](LICENSE-EXCEPTIONS.md).

AerioTV releases up to and including v0.4.20 were published under the MIT License. That grant is not revoked: any copy obtained under MIT keeps its MIT rights to that snapshot in perpetuity. Everything from the relicensing commit forward is GPL-3.0-or-later.

### Third-party components

ArrTV bundles an FFmpeg build (LGPL-2.1-or-later) and depends on a number of Apache-2.0 libraries. The FFmpeg build is configured without `--enable-gpl` and enables exactly ten audio decoders: `aac`, `ac3`, `eac3`, `dca`, `truehd`, `mlp`, `mp2`, `mp3`, `flac` and `alac`. Anything outside that set relies on the device's own hardware decoder. See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for the full list, the exact FFmpeg build configuration, and where to obtain the corresponding source. The same information is in the app under Settings › About › Open Source Licenses.

The Apache-2.0 components include AndroidX and Jetpack Compose, Media3 / ExoPlayer, Kotlin with kotlinx.coroutines and kotlinx.serialization, Ktor, Dagger and Hilt, OkHttp, Okio (Square), Coil, ZXing Core and Reorderable. They are used under the Apache License, Version 2.0, whose full text is at https://www.apache.org/licenses/LICENSE-2.0 and is also bundled in the app.

FFmpeg is loaded as a dynamically linked JNI shared object (`libffmpegJNI.so`) inside `app/libs/media3-decoder-ffmpeg.aar`, and can be replaced with a modified build, as section 6 of the LGPL requires. Build steps are in [app/libs/README.md](app/libs/README.md).

The Google Play services client libraries (Cast sender, Cast Connect receiver, Play services Auth, Google Identity) are proprietary and are used under the Android Software Development Kit License Agreement and the Google APIs Terms of Service. Linking them with this GPL-licensed program is permitted by an additional permission under GPL section 7; see [LICENSE-EXCEPTIONS.md](LICENSE-EXCEPTIONS.md).

The four-color Google "G" on the Sign in with Google button (`res/drawable/ic_google_g.xml`) is a trademark of Google LLC, used per the Google Sign-In branding guidelines.

### Patents

ArrTV includes independent implementations of audio and video codecs, supplied by FFmpeg, that may be covered by patents in some countries. ArrTV is free software under GPL-3.0-or-later and grants no patent license of any kind; users and redistributors are responsible for compliance with the patent law that applies where they live.

Dolby, DTS and other names that appear in this project are trademarks of their respective owners, used only to identify the audio formats concerned. ArrTV is not affiliated with, endorsed by, or certified by any of them.

### TMDB attribution

This product uses the TMDB API but is not endorsed or certified by TMDB. TMDB data and images are used only after a TMDB API key is configured in the app's settings.

## Support

For problems with ArrTV, open an issue at [github.com/ckegels/AerioTV-Android/issues](https://github.com/ckegels/AerioTV-Android/issues). Please do not report ArrTV problems to the AerioTV project.
