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

### v3.0.0 — 2025
**Analytics & Intelligence**
- Estimated days to goal now uses 7-day rolling average instead of all-time lifetime average — far more accurate for active players
- Best earning week card added to Analytics screen showing highest single-week income and date range
- 7-day earning rate card added to Analytics screen showing coins/day average and 7-day total

**Home Screen Widget**
- Glance-based home screen widget showing current balance, goal, progress bar percentage, and 7-day earning rate
- Widget updates automatically after every transaction save
- Supports light and dark system theme

**Notifications**
- Achievement milestone notifications — fires immediately when a new achievement is unlocked (e.g. "Serious Saver", "🔥 5-Day Streak")
- Daily reminder notification at 8pm BDT (14:00 UTC) via WorkManager if no transaction has been logged that day
- Notification permission requested on first login (Android 13+)
- Notifications delivered directly to phone — no server required

**History**
- Swipe left to delete transactions — red delete background with icon revealed on swipe
- Delete icon button removed from cards to reduce clutter (swipe replaces it)
- Edit button retained on card

**Progress Card Sharing**
- Share Progress Card button added to Dashboard
- Generates a 900×500 PNG card with animated gradient background, balance, goal progress bar, today/week/month stats, 7-day rate, best week, and estimated days
- Shares via Android system share sheet — works with WhatsApp, Telegram, Instagram, etc.

**Infrastructure**
- Added Glance (`1.1.1`) and WorkManager (`2.10.1`) dependencies
- Hilt WorkManager integration via `HiltWorkerFactory`
- `CoinTrackerApplication` now implements `Configuration.Provider` for custom WorkManager init
- `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` permissions added to manifest
- FileProvider configured for sharing generated images
- `DailyReminderWorker` and `NotificationHelper` added under `notifications/` package
- `CoinTrackerWidget` and `CoinTrackerWidgetReceiver` added under `widget/` package
- `ProgressCardGenerator` added under `ui/screens/`
- WorkManager job scheduled on login, cancelled on logout and account deletion

---

### v2.1.0 — 2025
**Firebase Direct Auth (Flask Removed)**
- Removed Flask/OkHttp backend dependency entirely — app authenticates directly with Firebase Auth
- Synthetic email format (`username@cointracker.app`) preserves username/password UX without exposing email
- Silent first-login migration for legacy accounts — verifies Werkzeug PBKDF2 hash, creates Firebase Auth account, copies Firestore data to new UID, cleans up old documents
- `WerkzeugPasswordHasher.kt` retained for migration path only
- `FirestoreRepository.kt` rewritten to use Firebase Auth SDK directly

**Data Management**
- JSON backup import via Android system file picker — restores full transaction history to current profile
- Export backup as JSON array compatible with web and desktop versions

**Build Modernisation**
- Migrated from `kapt` to `KSP` for all annotation processing (Hilt)
- AGP 8.10.1, Gradle 8.11.1, Kotlin 2.1.21, Compose BOM 2025.05.01, Firebase BOM 33.14.0
- JVM target raised to 17
- `targetSdk` raised to 35

**Settings**
- Account deletion with password re-authentication confirmation dialog
- Custom income and expense category lists — override defaults per profile
- Profile deletion added

**Minor**
- Snackbar undo on transaction delete in History screen
- Analytics period filter (Lifetime / Monthly / Weekly / Custom Range)
- Date range picker in History screen

---

### v2.0.0 — 2025
**UI Overhaul**
- New app icon — coin design in mipmap folders
- Animated gradient background (light and dark variants matching web palette)
- Glassmorphism card component (`GlassCard`) with blur and semi-transparent layering
- Dark / light theme toggle persisted across sessions
- Bottom navigation bar replacing sidebar navigation pattern

**Analytics**
- Analytics screen added — balance timeline, earnings breakdown pie chart, spending breakdown pie chart
- Daily and weekly earnings stats shown on Dashboard (Today / This Week / This Month)
- Estimated days to goal calculation based on average daily earnings

**Admin**
- Admin panel screen added for users with `role: admin`
- User list with balance and transaction count
- Delete user action with confirmation dialog
- 7-day new user signup bar chart

**Profiles**
- Multiple profile support — create, switch, and delete profiles
- Last active profile persisted in Firestore

**Minor**
- Edit transaction dialog in History
- Source/category filter in History
- Achievement cards on Dashboard (milestone, streak, no-spend)
- Support / donation dialog (bKash, Nagad, Rocket)

---

### v1.0.0 — 2025
**Initial Release**
- Firebase Firestore integration for cloud sync
- Login and registration screens with username/password
- Dashboard with balance display and goal progress bar
- Quick action buttons for common eFootball coin sources (Login, Ads, Daily Games, Event Reward, Box Draw)
- Transaction history list with pagination
- Add and spend coin forms with source/category dropdowns
- Settings screen — goal input, quick action management, data export
- Multiple profile support (basic)
- Light and dark theme
- Glassmorphism UI foundation
