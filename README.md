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

## Install the APK

Every push to `main` is built by GitHub Actions.

1. Open the repo's **Releases → "Insta Mapper (latest build)"** on your phone.
2. Download `InstaMapper.apk` and open it (allow "Install unknown apps" for your browser if asked).

All builds are signed with the same key (`app/instamapper.keystore`), so new builds install as
updates over the old one.

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

Requires JDK 17+. Min Android version: 10 (API 29).

## Notes

Instagram has no official API for reading arbitrary reels, so the fetcher relies on the same endpoints
the website uses. If Instagram changes them, some fields may go missing until the parser is updated –
the app degrades gracefully (you can always search the place manually). Respect creators: credit them
when sharing and don't re-upload downloaded videos.
