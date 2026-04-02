# 📱 Coin Tracker — Android

Native Android app built with **Kotlin** and **Jetpack Compose**. Talks directly to Firestore using the same data model as the Flask web backend, and authenticates via Firebase custom tokens issued by the Flask API.

---

## Project Layout

```
android/
├── app/
│   ├── google-services.json         ← not in git; add yours manually
│   └── src/main/java/com/cointracker/mobile/
│       ├── CoinTrackerApplication.kt    # @HiltAndroidApp entry point
│       ├── MainActivity.kt              # Edge-to-edge, sets CoinTrackerApp()
│       ├── data/
│       │   ├── Models.kt                # Transaction, Settings, QuickAction,
│       │   │                            #   ProfileEnvelope, UserSession, etc.
│       │   ├── FirestoreRepository.kt   # All Firestore CRUD, auth, admin ops
│       │   └── WerkzeugPasswordHasher.kt
│       ├── domain/
│       │   └── AchievementCalculator.kt
│       └── ui/
│           ├── CoinTrackerApp.kt        # Root composable, nav host, top/bottom bars
│           ├── CoinTrackerViewModel.kt  # @HiltViewModel, AppUiState
│           ├── StateFlows.kt
│           ├── components/
│           │   └── GlassCard.kt         # Glassmorphism card component
│           ├── navigation/
│           │   └── NavGraph.kt          # Sealed Destinations
│           ├── screens/
│           │   ├── LoginScreen.kt
│           │   ├── DashboardScreen.kt
│           │   ├── AnalyticsScreen.kt
│           │   ├── HistoryScreen.kt
│           │   ├── SettingsScreen.kt
│           │   └── AdminScreen.kt
│           └── theme/
│               ├── Color.kt             # Gradient + brand colour palette
│               ├── Theme.kt             # CoinTrackerTheme (light / dark)
│               └── Type.kt             # AppTypography
├── build.gradle                         # App-level (AGP 8.10.1, Kotlin 2.1.21)
├── build.gradle (root)                  # Project-level plugin declarations
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.11.1
└── settings.gradle
```

---

## Build Configuration

| Component | Version |
|---|---|
| AGP (Android Gradle Plugin) | 8.10.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.21 |
| Compose Compiler Plugin | 2.1.21 (matches Kotlin) |
| Compose BOM | 2025.05.01 |
| Firebase BOM | 33.14.0 |
| Hilt | 2.55 |
| KSP | 2.1.21-2.0.1 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| JVM Target | 17 |

> **Note on KSP:** This project uses KSP instead of `kapt`. KSP is faster and is the recommended annotation processor for Kotlin 2.x. There is no `kotlin-kapt` plugin and no `composeOptions` block — both are obsolete with Kotlin 2.0+.

---

## Features

- **Login / Register** via Flask backend (`/api/mobile-login`, `/api/mobile-register`), then Firebase custom token auth
- **Dashboard** — balance, goal progress bar, estimated days to goal, today/week/month stats, quick action buttons with snackbar feedback
- **Analytics** — balance timeline line chart (Canvas), earnings and spending pie charts with legend
- **History** — paginated transaction list, search, source filter, UTC-correct date range picker, edit and delete with undo snackbar; page position survives navigation (`rememberSaveable`)
- **Settings** — goal management, custom quick actions (add/edit/delete), custom income and expense category lists (with hardcoded fallbacks), profile management, JSON backup export
- **Profiles** — multiple independent profiles per account; active profile shown with blue highlight + ✓ in the dropdown
- **Achievements** — milestone badges (balance goals, login streak, no-spend streak)
- **Admin Panel** — admin-only; total users/coins/transactions, 7-day new user bar chart, per-user stats and delete with confirmation; self-delete protected
- **Glassmorphism UI** — animated gradient background, translucent glass cards, light/dark theme persisted across app restarts via `SharedPreferences`
- **Global loading overlay** — semi-transparent spinner overlay during all async operations
- **Error snackbar** — all ViewModel errors surface automatically via a `LaunchedEffect` watcher
- **Offline support** — Firestore offline persistence enabled; data is readable and writes are queued without internet
- **Session expiry detection** — Firebase token validity checked on resume; expired sessions auto-logout with a message
- **Input validation** — username (min 3 chars, no spaces), password (min 4 chars), transaction amounts (no zero, max 999 999), profile name (no Firestore-illegal characters)

---

## Firebase Setup

1. Open [console.firebase.google.com](https://console.firebase.google.com).
2. Add an **Android app** with package name `com.cointracker.mobile`.
3. Download `google-services.json` and place it at `android/app/google-services.json`.
4. Enable **Firestore** in Native mode and **Authentication** (no sign-in providers needed).
5. Apply the Firestore security rules from the [root README](../README.md).

> `google-services.json` is in `.gitignore` — never commit it.

---

## Running

### Prerequisites
- **Android Studio Narwhal** (2025.1.1) or **Meerkat Feature Drop** (2024.3.2+)
- **JDK 17** — use the **bundled JDK** inside Android Studio  
  (`File → Settings → Build → Gradle → Gradle JDK → jbr-17 bundled`)
- **Android SDK** — API 26 (min) and API 35 (target) installed via SDK Manager
- The **Flask backend** running and reachable at the IP in `FirestoreRepository.BASE_URL`

### Steps

```bash
# 1. Open android/ in Android Studio
# 2. Place google-services.json at android/app/google-services.json
# 3. Let Gradle sync (downloads Gradle 8.11.1 + all deps on first run)
# 4. File → Invalidate Caches → Invalidate and Restart (clears stale kapt cache)
# 5. Run the 'app' configuration on a device or emulator
```

### Updating the backend URL

If your Flask server IP changes, update `BASE_URL` in `FirestoreRepository.kt`:

```kotlin
private val BASE_URL = "http://YOUR_SERVER_IP"
```

---

## Architecture

```
UI Layer        CoinTrackerApp (Compose nav host)
                └── Screens (LoginScreen, DashboardScreen, ...)
                        ↕ collectAsState()
ViewModel       CoinTrackerViewModel (@HiltViewModel)
                        ↕ suspend functions / Result<T>
Data Layer      FirestoreRepository (@Singleton, Hilt-injected)
                        ↕ Firestore SDK + OkHttp (login/register)
Remote          Firebase Firestore + Flask REST API
```

State is held in a single `AppUiState` data class inside `MutableStateFlow`. All Firestore operations return `Result<T>` and errors surface via `uiState.error`, which the app-level `LaunchedEffect` picks up and displays as a snackbar.

---

## Next Steps

- Add a charting library (e.g. [Vico](https://github.com/patrykandpatrick/vico)) for richer analytics graphs
- Wire push notifications to mirror `/api/broadcast` from the web backend
- Import from JSON backup (export already works)
- Home screen widget for balance display
- Biometric login
