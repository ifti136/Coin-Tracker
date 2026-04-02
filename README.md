# 🪙 Coin Tracker

A cross-platform in-game coin tracker for eFootball (or any game with a coin economy) — track earnings, spending, goals, and analytics across **Android**, **Desktop** (Windows/macOS/Linux), and **Web**.

All three platforms share the same Firebase/Firestore backend and data model, so data syncs automatically across devices.

---

## 📁 Repository Structure

```
coin-tracker/
├── android/                    # Kotlin + Jetpack Compose Android app
│   ├── app/
│   │   └── src/main/java/com/cointracker/mobile/
│   │       ├── data/           # Models, FirestoreRepository, WerkzeugPasswordHasher
│   │       ├── domain/         # AchievementCalculator
│   │       └── ui/             # Screens, ViewModel, theme, components
│   ├── build.gradle            # App-level build (AGP 8.10.1, Kotlin 2.1.21, KSP)
│   └── build.gradle (root)     # Project-level build
│
├── desktop/                    # PyQt5 desktop app
│   ├── coin_tracker.py         # Full PyQt5 application
│   ├── build.py                # PyInstaller build script
│   └── coin_icon.py            # Icon generator
│
├── web/                        # Flask web app
│   ├── app.py                  # Flask routes, Firebase auth, Firestore CRUD
│   ├── requirements.txt        # Python dependencies
│   ├── render.yaml             # Render.com deployment config
│   ├── static/                 # CSS, JS (app.js, admin.js, login.js)
│   └── templates/              # Jinja2 HTML (index.html, login.html, admin.html)
│
├── .gitignore
├── LICENSE                     # MIT — Copyright (c) 2025 Ifti
└── README.md
```

---

## ✨ Features (all platforms)

| Feature | Android | Desktop | Web |
|---|:---:|:---:|:---:|
| Login / Register | ✅ | ✅ | ✅ |
| Dashboard (balance, progress, stats) | ✅ | ✅ | ✅ |
| Quick action buttons | ✅ | ✅ | ✅ |
| Add / spend coins | ✅ | ✅ | ✅ |
| Transaction history + filters | ✅ | ✅ | ✅ |
| Analytics (charts, breakdowns) | ✅ | ✅ | ✅ |
| Goal tracking + estimated days | ✅ | ✅ | ✅ |
| Achievements | ✅ | — | ✅ |
| Multiple profiles | ✅ | ✅ | ✅ |
| JSON backup export | ✅ | ✅ | ✅ |
| JSON import | — | ✅ | ✅ |
| Admin panel | ✅ | — | ✅ |
| Dark / light theme (persisted) | ✅ | ✅ | ✅ |
| Offline support | ✅ | ✅ (local) | — |
| Firebase / Firestore sync | ✅ | ✅ (optional) | ✅ |

---

## 🗄️ Firestore Data Model

All platforms read and write the same collections:

```
users/{userId}
  username         : string
  username_lower   : string
  password_hash    : string   (Werkzeug pbkdf2:sha256, 260 000 iterations)
  created_at       : string   (ISO 8601 UTC, e.g. "2025-03-15T10:30:00Z")
  role             : string   ("user" | "admin")

user_data/{userId}
  last_active_profile : string
  profiles/
    {profileName}/
      transactions : array
        id               : string  (UUID v4)
        date             : string  (ISO 8601 UTC)
        amount           : number  (positive = income, negative = expense)
        source           : string
        previous_balance : number
      settings
        goal               : number
        dark_mode          : boolean
        quick_actions      : [{text, value, is_positive}]
        income_categories  : string[]  (empty → use app defaults)
        expense_categories : string[]  (empty → use app defaults)
      last_updated : string

app_config/broadcast
  message  : string
  set_by   : string
  set_at   : string
```

---

## 🔥 Firebase Setup (shared across all platforms)

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a project.
2. Enable **Firestore Database** in **Native mode**.
3. Enable **Firebase Authentication** — no sign-in providers needed in the console (the Flask backend issues custom tokens).

### Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      allow read: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
    match /user_data/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      allow read: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
    match /app_config/{doc} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
  }
}
```

### Promoting a user to admin

In the Firestore console, find the user's document under `users/{userId}` and set `role` → `"admin"`.

---

## 🚀 Quick Start

Each platform has its own detailed README:

- **[Android →](android/README.md)**
- **[Web →](web/README.md)**
- **[Desktop →](desktop/README.md)**

---

## 🔐 Security Notes

- Passwords are hashed with **Werkzeug PBKDF2-SHA256** (260 000 iterations) server-side. Plaintext passwords are never stored.
- The Flask backend issues Firebase **custom tokens** for mobile auth. These expire after ~1 hour; the Android app detects expiry on resume and prompts re-login.
- `google-services.json`, `firebase-key.json`, and `.env` files are all excluded from git via `.gitignore`. Never commit them.
- Admin routes are protected by both Firestore security rules and server-side role checks.

---

## 📄 License

MIT — Copyright (c) 2025 Ifti. See [LICENSE](LICENSE) for details.
