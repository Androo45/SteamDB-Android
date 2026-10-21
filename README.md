# 🚂 SteamDB Android Client

<p align="center">
  <b>An unofficial, open-source, modern native Android client for SteamDB.</b><br>
  Track 41 regional Steam currencies, monitor live player charts, watch price drops, calculate TF2 key prices, and consult the sacred Gaben Oracle!
</p>

<p align="center">
  <a href="https://github.com/Androo45/SteamDB-Android/releases/latest"><img src="https://img.shields.io/github/v/release/Androo45/SteamDB-Android?style=flat-square&color=blue" alt="Latest Release"></a>
  <a href="https://github.com/Androo45/SteamDB-Android/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Androo45/SteamDB-Android?style=flat-square&color=green" alt="License"></a>
  <a href="https://android-arsenal.com/api?level=26"><img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat-square" alt="API"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple?style=flat-square" alt="Kotlin"></a>
</p>

---

## 🌟 What's New in v1.1.2

- 🎨 **Compact Drawer UI & Light/Dark Theme:** Redesigned navigation drawer header featuring an animated **pill-style Language Switcher (`ES` / `EN`)** and a **360° animated Theme Toggle button** (Dark / Light mode) with dynamic ink contrast (`SdbInk`).
- 🌐 **Full Bilingual Localization (`ES` / `EN`):** Complete built-in translation engine covering 100% of the app: Navigation Drawer, Home Portal, Top 100 Charts, Sales Explorer, Release Calendar dates, all 39 Steam Market currencies in Key Commander, Gaben's Oracle verdicts, and Favorites Watchlist.
- 📈 **Local Price History Tracker:** Automatically records and plots game price evolution in Spain with a custom Compose Canvas line chart, highlighting the **Recorded Low**, **Recorded High**, and optimal buy alerts.
- 📊 **Monthly Player Trends & Community Insights:**
  - **12-Month Player History:** Bar charts powered by *SteamCharts* showing monthly average and peak player counts with trend percentages.
  - **Community Estimates:** Owner ranges, 2-week active players, and average/median playtime via *SteamSpy*.
  - **Rarest Achievements:** Global unlock percentages from the official Steamworks API sorted from rarest to most common.
- ⭐ **Favorites & Price Drop Watchlist:** Star any game to monitor price changes. Set a custom target price to get instant **«▼ PRICE DROP!»** and **«🎯 TARGET REACHED!»** badges.
- 📅 **Swipeable Release Calendar:** Steam-style day strip with release counts, today's highlight, and pinned sticky headers as you scroll through upcoming titles.
- 🔑 **Key Commander (TF2 Keys Calculator):** Calculate how many *Mann Co. Supply Crate Keys* you need to buy any game using live Steam Community Market prices across **39 official currencies**, including the 15% market fee breakdown (5% Steam + 10% TF2).
- 🔮 **Gaben's Oracle:** Flip the sacred 50/50 RNG coin to decide whether to buy a game or save your wallet, complete with a cheat-proof history log.
- 🔔 **In-App Update Checker & Unified APK Signing:** Automatic notification when a new GitHub Release drops, plus consistent signing support for seamless updates without uninstalling.

---

## 📱 Screenshots

*(Add your app screenshots here in a clean grid or row to showcase the UI)*
<!-- 
<p align="center">
  <img src="screenshots/screenshot1.png" width="28%"/>
  <img src="screenshots/screenshot2.png" width="28%"/>
  <img src="screenshots/screenshot3.png" width="28%"/>
</p>
-->

---

## 🛠️ Tech Stack & Architecture

- **Language:** [Kotlin](https://kotlinlang.org/) (JVM Target 17)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3, Custom Canvas Charts, Animated Transitions)
- **Architecture:** MVVM (`SdbMainViewModel`) + Persistent Local Stores (`SharedPreferences` JSON serialization)
- **Network / Data:** Valve Storefront API, Steamworks Web API, Steam Community Market API, SteamCharts & SteamSpy integrations
- **Image Loading:** [Coil Compose](https://coil-kt.github.io/coil/) with multi-CDN fallback (`SteamSmartThumb`)
- **Build System:** Gradle (Kotlin DSL) & GitHub Actions CI/CD

---

## 🚀 Getting Started & Installation

1. Head over to the [Releases Page](https://github.com/Androo45/SteamDB-Android/releases/latest).
2. Download the latest `.apk` file (`v1.1.2`).
3. Install it on your Android device (Android 8.0 / API 26 or higher).

### Building from Source

If you want to compile it locally or contribute:

```bash
git clone [https://github.com/Androo45/SteamDB-Android.git](https://github.com/Androo45/SteamDB-Android.git)
cd SteamDB-Android
./gradlew assembleDebug
