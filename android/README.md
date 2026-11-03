# 📱 Coin Tracker — Android

Native Android app built with **Kotlin + Jetpack Compose**. Tracks in-game eFootball coins across multiple profiles, syncs directly to Firestore via Firebase Auth — no backend server required.

---

## Project Layout

```
android/
├── build.gradle                        # Root build file — plugin versions
├── settings.gradle                     # Module includes
├── gradle.properties                   # JVM args, AndroidX flags
├── gradle/wrapper/
│   └── gradle-wrapper.properties       # Gradle 8.11.1
└── app/
    ├── build.gradle                    # App deps — Compose BOM, Firebase BOM, Hilt, Glance, WorkManager
    ├── google-services.json            # Firebase config — not in git, add manually
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/cointracker/mobile/
        │   ├── MainActivity.kt
        │   ├── CoinTrackerApplication.kt
        │   ├── data/
        │   │   ├── Models.kt               # All data classes + defaults
        │   │   ├── FirestoreRepository.kt  # All Firestore + Firebase Auth operations
        │   │   ├── LocalCacheRepository.kt # Safety cache — read/write/delete JSON snapshots
        │   │   ├── SyncManager.kt          # Cache vs DB comparison logic, typed SyncResult
        │   │   └── WerkzeugPasswordHasher.kt  # Legacy migration helper
        │   ├── domain/
        │   │   └── AchievementCalculator.kt
        │   ├── notifications/
        │   │   ├── NotificationHelper.kt   # Channel setup + notification builders
        │   │   └── DailyReminderWorker.kt  # WorkManager worker — 8pm BDT daily reminder
        │   ├── widget/
        │   │   └── CoinTrackerWidget.kt    # Glance home screen widget + updater
        │   └── ui/
        │       ├── CoinTrackerApp.kt       # Root composable, nav, scaffold
        │       ├── CoinTrackerViewModel.kt # All state, side effects, notifications
        │       ├── StateFlows.kt
        │       ├── components/
        │       │   └── GlassCard.kt
        │       ├── navigation/
        │       │   └── NavGraph.kt
        │       ├── screens/
        │       │   ├── LoginScreen.kt
        │       │   ├── DashboardScreen.kt
        │       │   ├── AnalyticsScreen.kt
        │       │   ├── HistoryScreen.kt
        │       │   ├── SettingsScreen.kt
        │       │   ├── AdminScreen.kt
        │       │   └── ProgressCardGenerator.kt
        │       └── theme/
        │           ├── Color.kt
        │           ├── Theme.kt
        │           └── Type.kt
        └── res/
            ├── drawable/
            │   └── coin.png
            ├── xml/
            │   ├── widget_info.xml         # Glance widget metadata
            │   └── file_paths.xml          # FileProvider paths for share card
            └── values/
                └── strings.xml
```

---

## Build Stack

| Tool | Version |
|---|---|
| AGP | 8.10.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.21 |
| Compose BOM | 2025.05.01 |
| Firebase BOM | 33.14.0 |
| Hilt | 2.55 |
| KSP | 2.1.21-2.0.1 |
| JVM target | 17 |
| Min SDK | 26 (Android 8) |
| Target SDK | 35 (Android 15) |

---

## Features

- **Authentication** — Firebase Email/Password auth using synthetic email format (`username@cointracker.app`) to preserve username/password UX. Legacy Werkzeug-hashed accounts migrated silently on first login
- **Dashboard** — animated gradient background, balance card with goal progress, estimated days to goal (7-day rate), today/week/month stats, quick action buttons, add/spend forms with bottom-sheet category pickers
- **Analytics** — period filter (Lifetime / Monthly / Weekly / Custom Range), balance timeline canvas chart, earnings and spending pie charts, 7-day earning rate card, best earning week card
- **History** — paginated transaction list, search, source filter, date range picker, swipe-to-delete, edit dialog, undo via snackbar
- **Settings** — goal management, quick action add/edit/delete, custom income and expense category lists, JSON backup export and import, profile management, account deletion with re-auth confirmation
- **Multiple profiles** — per-user profiles stored in Firestore; last active profile persisted
- **Admin panel** — total users/coins/transactions, 7-day new-user bar chart, user list with delete
- **Home screen widget** — Glance widget showing balance, goal progress bar, percentage, and 7-day earning rate; updates after every transaction save
- **Notifications** — achievement milestone notifications fire when a new achievement is unlocked; daily reminder notification at 8pm BDT (14:00 UTC) via WorkManager if no transaction logged that day
- **Progress card sharing** — generates a 900×500 branded PNG card with balance, progress bar, stats, best week, and estimated days; shares via system share sheet
- **Glassmorphism UI** — animated gradient background, blurred glass cards, dark/light theme toggle persisted in SharedPreferences

---

## Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Add an Android app with package `com.cointracker.mobile`
3. Download `google-services.json` → place in `android/app/`
4. In Firebase Console:
   - **Authentication → Sign-in methods → Email/Password → Enable**
   - **Project Settings → Your Android app → Add SHA-1 fingerprint** (from `./gradlew signingReport`)
5. Deploy Firestore security rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow list: if request.query.limit <= 1;
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    match /user_data/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

> The `allow list` rule on `users` exists to support legacy Werkzeug account migration. It can be tightened or removed once all legacy users have migrated.

---

## Making a User Admin

In Firestore Console → `users/{userId}` → set `role` field to `"admin"`.

---

## Building & Running

```bash
cd android
./gradlew assembleDebug
# or open in Android Studio and run normally
```

Requires `google-services.json` in `android/app/` before building.

---

## Changelog

For a detailed breakdown of all past releases, see **[CHANGELOG.md](CHANGELOG.md)**.
