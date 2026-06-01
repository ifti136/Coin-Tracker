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
├── web/                        # Firebase Hosting web app (vanilla JS + Firestore SDK)
│   ├── public/
│   │   ├── index.html          # Main app shell
│   │   ├── login.html          # Auth page
│   │   ├── admin.html          # Admin panel
│   │   ├── css/                # style.css, login.css, admin.css
│   │   └── js/                 # app.js, login.js, admin.js
│   ├── firebase.json           # Firebase Hosting + Firestore config
│   ├── firestore.rules         # Security rules
│   └── firestore.indexes.json
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

All platforms read and write the same collections. Profiles are stored as a subcollection under `user_data/{userId}`.

```
users/{userId}
  username         : string
  role             : string   ("user" | "admin")
  created_at       : string   (ISO 8601 UTC, e.g. "2025-03-15T10:30:00Z")

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

> **Note:** Passwords are never stored in Firestore. Authentication is handled entirely by Firebase Auth using a synthetic email format (`username@cointracker.app`) to preserve a username/password UX. Legacy Werkzeug-hashed accounts are migrated silently on first login by the Android app.

---

## 🔥 Firebase Setup (shared across all platforms)

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a project.
2. Enable **Firestore Database** in **Native mode**.
3. Enable **Firebase Authentication → Sign-in methods → Email/Password**.

### Firestore Security Rules

Deploy these rules from `web/firestore.rules` or paste directly in the Firebase console:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() { return request.auth != null; }
    function isOwner(uid) { return isSignedIn() && request.auth.uid == uid; }
    function isAdmin() {
      return isSignedIn() &&
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }

    match /users/{uid} {
      allow read:   if true;
      allow create: if true;
      allow update: if isOwner(uid) || isAdmin();
      allow delete: if isAdmin();
    }

    match /user_data/{uid} {
      allow read:   if isOwner(uid) || isAdmin();
      allow create: if true;
      allow update: if isOwner(uid) || isAdmin();
      allow delete: if isOwner(uid) || isAdmin();

      match /profiles/{profileName} {
        allow read:   if isOwner(uid) || isAdmin();
        allow create: if true;
        allow update: if isOwner(uid);
        allow delete: if isOwner(uid) || isAdmin();
      }
    }

    match /app_config/{docId} {
      allow read:  if isSignedIn();
      allow write: if isAdmin();
    }

    match /{document=**} {
      allow read, write: if false;
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

- Authentication is handled entirely by **Firebase Auth** (Email/Password provider). Passwords are never stored in Firestore.
- A synthetic email format (`username@cointracker.app`) preserves username/password UX without exposing real emails.
- Legacy accounts that used Werkzeug PBKDF2-SHA256 password hashing are migrated silently on first login by the Android app (`WerkzeugPasswordHasher.kt` handles verification only).
- `firebase-key.json` (desktop service account), `google-services.json` (Android), `firebase-config.js` (web), and `.env` files are all excluded from git via `.gitignore`. Never commit them.
- Admin routes are protected by both Firestore security rules and client-side role checks.

---

## 📄 License

MIT — Copyright (c) 2025 Ifti. See [LICENSE](LICENSE) for details.
