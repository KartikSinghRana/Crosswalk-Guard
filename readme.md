# 🚶 CrosswalkGuard

An Android safety app that detects when you're approaching a pedestrian crosswalk
while distracted on your phone — and escalates alerts before you step into traffic.

---

## 📱 Screenshots

| Map View | Level 2 Alert | Level 3 — STOP |
|----------|--------------|-----------------|
| ![Map](screenshots/map_view.png) | ![Alert](screenshots/alert_level2.png) | ![Stop](screenshots/alert_level3.png) |

---

## ✨ Features

- 📍 **Real-time GPS tracking** with 1-second location updates
- 🗺️ **Crosswalk detection** via OpenStreetMap Overpass API (3-mirror parallel racing for speed)
- 📵 **Phone distraction detection** via accelerometer/gravity sensor tilt analysis
- 🔵 **Custom zebra-crossing map markers** (blue = OSM verified, amber = estimated)
- ⭕ **Adjustable search radius** — 100m up to 5km with live map circle overlay
- 🔄 **Manual refresh** button to force re-fetch crosswalk data
- 📊 **Persistent analytics** — distractions prevented & crosswalks approached
- 🚨 **Three-level escalating alerts:**
  - Level 1 (30m): Toast + double vibration
  - Level 2 (12m): Modal popup + vibration pattern + warning tone
  - Level 3 (7m): Full-screen red takeover + emergency tone (non-dismissible until acknowledged)

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 (Android 15) |
| Maps | Google Maps SDK for Android |
| Location | Google Play Services — FusedLocationProvider |
| Crosswalk Data | OpenStreetMap Overpass API |
| Networking | OkHttp 4.12 |
| Concurrency | Kotlin Coroutines |
| Sensor | Android Gravity / Accelerometer |
| Storage | SharedPreferences |
| UI | View Binding, Material3, ConstraintLayout |

---

## ⚙️ Installation

### Prerequisites
- Android Studio Narwhal (2025.1.x) or later
- Android device or emulator running API 26+
- Google Maps API key with **Maps SDK for Android** enabled

### Steps

1. **Clone the repository**
```bash
   git clone https://github.com/yourusername/CrosswalkGuard.git
   cd CrosswalkGuard
```

2. **Add your Google Maps API key**

   Copy the example config file:
```bash
   cp google_maps_api.xml.example app/src/main/res/values/google_maps_api.xml
```
   Then open `google_maps_api.xml` and replace `YOUR_API_KEY_HERE` with your key.

3. **Open in Android Studio**
   - File → Open → select the `CrosswalkGuard` folder
   - Wait for Gradle sync to complete

4. **Run**
   - Connect a physical device (recommended — emulators lack real GPS)
   - Press `Shift+F10` or click ▶ Run

---

## 🚀 Usage

1. Launch the app and grant **Location** permission
2. The map loads centered on your current position
3. Blue markers = verified OSM crosswalks · Amber markers = estimated intersections
4. Blue circle = your current search radius (adjustable with − / + buttons)
5. Keep the app open while walking — it monitors your phone tilt continuously
6. When you hold your phone up (reading posture) near a crosswalk, alerts fire automatically
7. Tap 🔄 to force a fresh crosswalk fetch at any time

---

## 🔑 API Key Setup

Go to [Google Cloud Console](https://console.cloud.google.com/) and enable:
- Maps SDK for Android

Restrict the key to your app's package name `com.crosswalkguard` and SHA-1
fingerprint for security before publishing.

---

## 📂 Key Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | App entry point, map, location loop, UI |
| `CrosswalkRepository.kt` | OSM Overpass fetch with parallel mirrors + cache |
| `DistractionDetector.kt` | Gravity sensor tilt detection |
| `AlertManager.kt` | Three-level alert escalation logic |
| `AnalyticsManager.kt` | SharedPreferences-backed session stats |
| `AlertLevel.kt` | Enum: NONE / APPROACHING / CLOSE / ENTERING |

---

## 🔮 Roadmap

- [ ] Background service mode (alerts even when screen is off)
- [ ] Notification channel for Level 1 alerts
- [ ] Speed-based trigger (only alert when walking, not in a vehicle)
- [ ] Historical session log screen
- [ ] Offline crosswalk tile cache
- [ ] Wear OS companion app

---

## 👥 Contributing

Pull requests are welcome. For major changes please open an issue first to
discuss what you would like to change.

1. Fork the repo
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgements

- [OpenStreetMap](https://www.openstreetmap.org/) & [Overpass API](https://overpass-api.de/) for crosswalk data
- [Google Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk)
- Built with ❤️ at Amity University — IBD Semester Project
