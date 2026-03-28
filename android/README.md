# 🪙 Coin Tracker

A full-stack coin tracking application for tracking in-game currency — earnings, spending, goals, and analytics — with a **Flask web backend** and a native **Android app** (Kotlin + Jetpack Compose).

---

## 📁 Repository Structure

```
coin-tracker/
├── web/                        # Python/Flask backend
│   ├── app.py                  # Main Flask app & API routes
│   ├── requirements.txt        # Python dependencies
│   └── templates/              # Jinja2 HTML templates
│
└── android/                    # Kotlin/Jetpack Compose Android app
    ├── app/
    │   └── src/main/java/com/cointracker/mobile/
    │       ├── MainActivity.kt
    │       ├── CoinTrackerApplication.kt
    │       ├── data/
    │       │   ├── Models.kt               # Data classes
    │       │   ├── FirestoreRepository.kt  # All Firestore CRUD + auth
    │       │   └── WerkzeugPasswordHasher.kt
    │       ├── domain/
    │       │   └── AchievementCalculator.kt
    │       └── ui/
    │           ├── CoinTrackerApp.kt       # Root composable + nav
    │           ├── CoinTrackerViewModel.kt
    │           └── screens/
    │               ├── LoginScreen.kt
    │               ├── DashboardScreen.kt
    │               ├── AnalyticsScreen.kt
    │               ├── HistoryScreen.kt
    │               ├── SettingsScreen.kt
    │               └── AdminScreen.kt
    ├── build.gradle             # App-level build config
    └── build.gradle (root)      # Project-level build config
```

---

## ✨ Features

### Android App
- **Dashboard** — current balance, progress to goal, estimated days remaining, today/week/month earnings breakdown, quick action buttons
- **Analytics** — balance timeline chart, earnings and spending pie charts broken down by source/category
- **History** — paginated transaction list with search, source filter, date range picker, edit and delete with undo
- **Settings** — goal management, custom quick actions, custom income/expense categories (with hardcoded fallbacks), profile management, JSON backup export
- **Profiles** — multiple independent coin tracking profiles per account, switchable from the top bar
- **Achievements** — milestone badges (balance goals, login streaks, spending discipline)
- **Admin Panel** — visible to admin accounts only; shows total users, total coins, total transactions, 7-day new user chart, per-user stats and delete
- **Glassmorphism UI** — animated gradient background, translucent glass cards, light/dark theme with persistence
- **Offline support** — Firestore offline persistence enabled; data is viewable and writes are queued without internet

### Backend (Flask + Firestore)
- User registration and login with **Werkzeug PBKDF2 password hashing**
- Issues **Firebase custom tokens** for mobile auth (`/api/mobile-login`, `/api/mobile-register`)
- Shared Firestore data model — web and mobile read/write the same `users` and `user_data` collections

---

## 🗄️ Firestore Data Model

```
users/{userId}
  username        : string
  username_lower  : string
  password_hash   : string   (Werkzeug pbkdf2:sha256 format)
  created_at      : string   (ISO 8601 UTC)
  role            : string   ("user" | "admin")

user_data/{userId}
  last_active_profile : string
  profiles/{profileName}
    transactions  : array
      id              : string
      date            : string   (ISO 8601 UTC, e.g. "2025-03-15T10:30:00Z")
      amount          : number   (positive = income, negative = expense)
      source          : string
      previous_balance: number
    settings
      goal              : number
      dark_mode         : boolean
      quick_actions     : array [{text, value, is_positive}]
      income_categories : array  (empty = use app defaults)
      expense_categories: array  (empty = use app defaults)
    last_updated  : string
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Narwhal 2025.1.1+ | Or Meerkat Feature Drop 2024.3.2+ |
| JDK | 17 | Use the **bundled JDK** inside Android Studio |
| Android SDK | API 26–35 | Install via SDK Manager |
| Python | 3.10+ | For the Flask backend |
| Firebase project | — | Firestore in Native mode |
| Google Cloud VM | — | For hosting the Flask backend |

---

### 1. Firebase Setup

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a project (or use an existing one).
2. Enable **Firestore** in **Native mode**.
3. Enable **Firebase Authentication** (custom token provider — no sign-in methods needed in the console).
4. Go to **Project Settings → Your Apps → Android** and download `google-services.json`.
5. Place it at `android/app/google-services.json`.

> ⚠️ `google-services.json` is listed in `.gitignore` — never commit it.

**Firestore Security Rules** (recommended minimum):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    match /user_data/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    // Admins can read all
    match /users/{userId} {
      allow read: if request.auth != null && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == "admin";
    }
    match /user_data/{userId} {
      allow read: if request.auth != null && get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == "admin";
    }
  }
}
```

---

### 2. Flask Backend Setup

```bash
cd web

# Create and activate virtual environment
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt
```

Create a `.env` file in `web/` (never commit this):

```env
FLASK_SECRET_KEY=your-random-secret-key
FIREBASE_PROJECT_ID=your-project-id
GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/service-account.json
```

Run locally:

```bash
flask run
```

Or deploy to your Google Cloud VM:

```bash
# On the VM
gunicorn -w 4 -b 0.0.0.0:80 app:app
```

> The Android app points to `http://34.19.86.210` by default. Update `BASE_URL` in `FirestoreRepository.kt` if your VM IP changes.

---

### 3. Android App Setup

1. Open the `android/` folder in **Android Studio**.
2. Place your `google-services.json` at `android/app/google-services.json`.
3. Let Gradle sync — it will download all dependencies automatically (first sync takes a few minutes).
4. If prompted about AGP version, click **"Don't remind me again"** — the build files are already up to date.
5. Run **File → Invalidate Caches → Invalidate and Restart** once to clear any stale caches.
6. Connect a device or start an emulator and run the `app` configuration.

---

## 🔧 Build Configuration

| Component | Version |
|---|---|
| AGP (Android Gradle Plugin) | 8.10.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.21 |
| Compose BOM | 2025.05.01 |
| Firebase BOM | 33.14.0 |
| Hilt | 2.55 |
| KSP | 2.1.21-2.0.1 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| JVM Target | 17 |

> **Note on KSP:** This project uses KSP instead of `kapt` for annotation processing (Hilt). KSP is significantly faster and is the recommended approach for Kotlin 2.x projects.

---

## 📱 Screenshots

> _Add screenshots here after first build_

---

## 🛣️ Roadmap

- [ ] Charts library integration for Analytics (e.g. Vico or MPAndroidChart)
- [ ] Push notifications mirroring `/api/broadcast`
- [ ] Import from JSON backup (export already works)
- [ ] Biometric login option
- [ ] Widget for home screen balance display

---

## 🔐 Security Notes

- Passwords are hashed using **Werkzeug's PBKDF2-SHA256** (260,000 iterations) on the backend — the Android app never handles raw password hashing for authentication
- Firebase custom tokens expire after **1 hour**; the app detects expiry on resume and prompts re-login
- `google-services.json` and all `.env` files are excluded from git via `.gitignore`
- Admin routes are protected by Firestore security rules and role checks both client-side and server-side

---

## 📄 License

MIT — feel free to use and modify for personal or educational projects.