# 📍 Insta Mapper

An Android app that turns an Instagram travel reel into a place you can map, plan and visit.

Paste a reel link (or share a reel from Instagram straight to **Insta Mapper**) and you get the
reel's details – creator, caption, views/likes, tagged location and audio – followed by a menu of
things to do with it:

| Action | What it does |
|---|---|
| 📍 **Add spot to map** | Detects the place (tagged location → AI → caption clues like 📍 lines and hashtags), lets you correct it, then saves it to **My Spots** (in-app OpenStreetMap) or opens it in Google Maps. My Spots can be exported as **KML** to import into Google My Maps / Google Earth. |
| 🌤️ **Best time to visit** | Pulls 5 years of daily weather history for the spot from Open-Meteo, scores every month (comfortable 20–28 °C highs, few rainy days), shows a month chart, months to avoid, and today's golden hour. With an AI key it adds festivals/crowd tips. |
| ⬇️ **Download video** | Saves the reel MP4 to `Downloads/InstaMapper` using Android's download manager. |
| 🎵 **Find original audio** | Shows the song/artist or "original audio" creator, opens the Instagram audio page (the oldest reel there is the original), searches Spotify / YouTube Music / YouTube, or launches Shazam. |
| 🧭 Directions · 🏨 Stays & food nearby · 📤 Share spot · 🔤 Translate caption · 📋 Copy caption · ▶️ Open in Instagram | Quick hand-offs to the right app. |

## Install / share the APK

Every push to `main` is built by GitHub Actions into an optimised release APK.

* **Download link (always the newest build):**
  https://github.com/nishanthks373-git/Insta_mapper/releases/latest
* Send that link (or the `InstaMapper.apk` file itself, e.g. over WhatsApp) to anyone with an
  Android 8.0+ phone. They open it and tap Install (allowing "Install unknown apps" if asked).
* Inside the app, **Settings → Share Insta Mapper** sends the link, and the home screen shows a
  banner when a newer version is available.
* Nothing in the app is tied to one person: every user signs in with their own Instagram account
  (optional), keeps their own spots on their phone, and can add their own free Gemini key.
  Temperatures switch to °F and translations go to the phone's language automatically.

### Signing key

Builds are signed with `app/instamapper.keystore` (committed) so new versions install as updates.
Because the repo is public, anyone could sign an APK with that key. Before sharing widely, switch to a
private key stored in GitHub Secrets – the workflow uses it automatically when these secrets exist:
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
(Note: people who installed a build signed with the old key must uninstall once when you switch.)

## Getting the best results

* **Sign in to Instagram** (Settings → Sign in). Logged-out, Instagram usually only exposes the caption
  and thumbnail. Signed in, the app also gets the tagged location (often with exact coordinates), the
  audio track and the video file. Sign-in happens on Instagram's own page in a WebView; only the
  session cookie is kept on the phone.
* **AI key (optional)** – Settings → AI assistant. Choose Google Gemini (free tier, key from
  [aistudio.google.com](https://aistudio.google.com/apikey)) or Anthropic Claude. Used for better place
  detection from captions and for local tips. You can override the model name if the default changes.

## How it works

```
app/src/main/java/com/nishanth/instamapper/
├── MainActivity.kt          share-intent entry point
├── MainViewModel.kt         app state + actions
├── data/
│   ├── InstagramFetcher.kt  web API (signed in) → reel page JSON/OpenGraph → embed page; results merged
│   ├── PlaceResolver.kt     tagged location / AI / caption heuristics → Android Geocoder or OSM Nominatim
│   ├── ClimateService.kt    Open-Meteo archive → monthly scores; sunrise/sunset → golden hour
│   ├── AiClient.kt          Gemini + Claude REST calls
│   ├── Storage.kt           settings, saved spots (JSON), KML export
│   └── Actions.kt           maps/share/translate intents, DownloadManager
└── ui/                      Jetpack Compose screens (Home, Reel + action sheets, My Spots, Settings, Login)
```

Free services used, no keys required: Open-Meteo (weather), OpenStreetMap tiles via osmdroid,
Nominatim (fallback geocoding).

## Build locally

Open the folder in Android Studio (Ladybug or newer) and run, or:

```bash
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+. Min Android version: 8.0 (API 26).

## Notes

Instagram has no official API for reading arbitrary reels, so the fetcher relies on the same endpoints
the website uses. If Instagram changes them, some fields may go missing until the parser is updated –
the app degrades gracefully (you can always search the place manually). Respect creators: credit them
when sharing and don't re-upload downloaded videos.
