\# 🚶 CrosswalkGuard



An Android safety app that detects when you're approaching a pedestrian crosswalk while distracted on your phone — and escalates alerts before you step into traffic.



---



\## 📱 Screenshots



| Map View | Level 2 Alert | Level 3 — STOP |

|----------|--------------|-----------------|

| !\[Map](screenshots/map\_view.png) | !\[Alert](screenshots/alert\_level2.png) | !\[Stop](screenshots/alert\_level3.png) |



---



\## ✨ Features



\- 📍 \*\*Real-time GPS tracking\*\* with 1-second location updates

\- 🗺️ \*\*Crosswalk detection\*\* via OpenStreetMap Overpass API (3-mirror parallel racing for speed)

\- 📵 \*\*Phone distraction detection\*\* via accelerometer/gravity sensor tilt analysis

\- 🔵 \*\*Custom zebra-crossing map markers\*\* (blue = OSM verified, amber = estimated)

\- ⭕ \*\*Adjustable search radius\*\* — 100m up to 5km with live map circle overlay

\- 🔄 \*\*Manual refresh\*\* button to force re-fetch crosswalk data

\- 📊 \*\*Persistent analytics\*\* — distractions prevented \& crosswalks approached

\- 🚨 \*\*Three-level escalating alerts:\*\*

&nbsp; - Level 1 (30m): Toast + double vibration

&nbsp; - Level 2 (12m): Modal popup + vibration pattern + warning tone

&nbsp; - Level 3 (7m): Full-screen red takeover + emergency tone (non-dismissible until acknowledged)



---



\## 🛠️ Tech Stack



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



\## ⚙️ Installation



\### Prerequisites

\- Android Studio Narwhal (2025.1.x) or later

\- Android device or emulator running API 26+

\- Google Maps API key with \*\*Maps SDK for Android\*\* enabled



\### Steps

1\. \*\*Clone the repository\*\*

bash ```git clone https://github.com/yourusername/CrosswalkGuard.git 

db CrosswalkGuard``` 

to clone the project.

2\. \*\*Add your Google Maps API key\*\*

to the config file:

bash ```cp google\_maps\_api.xml.example app/src/main/res/values/google\_maps\_api.xml``` 

and replace `YOUR\_API\_KEY\_HERE` with your actual key.

3\. \*\*Open in Android Studio\*\*

after cloning:

follows by opening the folder and syncing Gradle.

4\. \*\*Run the app\*\* by connecting a device and clicking run.

---

\## 🚀 Usage

1\. Launch the app and grant Location permission.

2\. The map loads centered on your current position.

3\. Blue markers = verified OSM crosswalks; Amber markers = estimated intersections.

4\. Blue circle indicates your current search radius, adjustable with − / + buttons.

5\. Keep the app open while walking; it monitors phone tilt continuously.

6\. When near a crosswalk, alerts fire automatically.

e7. Tap 🔄 to fetch fresh data anytime.

---

\## 🔑 API Key Setup

Visit \[Google Cloud Console](https://console.cloud.google.com/) and enable:

such as Maps SDK for Android.

to restrict access, set package name `com.crosswalkguard` and SHA-1 fingerprint for security before publishing.

'these steps ensure proper setup of your API key.'

defaults to secure usage of Google services in your app.

'thanks for using CrosswalkGuard!'

details about licensing, acknowledgements, and contribution guidelines follow below.

