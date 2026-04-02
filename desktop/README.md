# 🖥️ Coin Tracker — Desktop

Cross-platform desktop app built with **PyQt5**. Works fully offline with local JSON storage, and optionally syncs to the same Firestore database as the web and Android apps when a `firebase-key.json` service account key is present.

---

## Project Layout

```
desktop/
├── coin_tracker.py     # Full PyQt5 application — all UI, logic, and data handling
├── build.py            # PyInstaller build script (produces CoinTracker.exe / .app)
├── coin_icon.py        # Generates coin.ico from scratch using QPainter
├── firebase-key.json   # Service account key — not in git; add yours manually
└── coin.ico            # Generated icon — not in git; run coin_icon.py to create
```

---

## Features

- **Login** — connects to Firestore if `firebase-key.json` is present; falls back to local JSON automatically if Firebase is unavailable
- **Dashboard** — balance display, goal progress bar, quick action buttons (customisable), add/spend coin forms with source/category dropdowns, recent transactions card, today/week/month stats
- **Analytics** — earnings doughnut chart, spending bar chart, balance timeline line chart (all via PyQtChart); graceful fallback message if `PyQtChart` is not installed
- **History** — filterable and searchable table; date range pickers; right-click context menu for edit and delete; period earnings summary
- **Settings** — goal input, quick action manager (add/delete via dialog), data export/import (JSON), Firebase sync status, backup to `~/Documents/CoinTracker/Backups/`
- **Multiple profiles** — dropdown in the sidebar; last active profile is persisted in Firestore (or locally); new profile creation via dialog
- **Light / dark theme** — toggle button in sidebar; preference saved per profile
- **Toast notifications** — animated fade-in/out toast overlay for all actions
- **Standalone executable** — `build.py` uses PyInstaller to produce a single-file `CoinTracker.exe` (or equivalent on macOS/Linux); Firebase key is bundled if present

---

## Requirements

| Package | Purpose |
|---|---|
| `PyQt5` | GUI framework |
| `PyQtChart` | Analytics charts (optional — charts disabled if missing) |
| `firebase-admin` | Firestore sync (optional — falls back to local if missing) |
| `requests` | HTTP (used by firebase-admin internally) |
| `google-auth` | Firebase auth credentials |
| `google-cloud-core` | Firebase Cloud dependency |
| `google-auth-oauthlib` | OAuth support for Firebase |
| `pyinstaller` | Building the standalone executable |

Python **3.10+** required (tested on Windows; macOS and Linux also work).

---

## Setup

```bash
cd desktop

# Create and activate a virtual environment
python -m venv venv
venv\Scripts\activate          # Windows
# source venv/bin/activate     # macOS / Linux

# Install all dependencies (including optional ones)
pip install PyQt5 PyQtChart firebase-admin requests \
            google-auth google-cloud-core google-auth-oauthlib pyinstaller
```

---

## Running from Source

```bash
venv\Scripts\activate
python coin_tracker.py
```

The app starts and loads whichever profile was last active (from Firestore if connected, otherwise `Default`).

---

## Firebase Setup (optional)

Without Firebase the app works entirely offline — data is saved to `~/Documents/CoinTracker/<profileName>.json`.

To enable Firestore sync:

1. Go to [console.firebase.google.com](https://console.firebase.google.com) → **Project Settings → Service Accounts**.
2. Click **Generate New Private Key** and save the JSON file.
3. Rename it `firebase-key.json` and place it next to `coin_tracker.py` in `desktop/`.

> `firebase-key.json` is in `.gitignore`. Never commit it.

When the key is present the app reads and writes the same `users` and `user_data` Firestore collections as the web and Android apps, so all data syncs automatically.

---

## Local Data Storage

When offline (or Firebase is unavailable), profiles are saved at:

```
~/Documents/CoinTracker/<profileName>.json
```

Each file contains:

```json
{
  "profile_name": "Default",
  "last_updated": "2025-03-15T10:30:00",
  "transactions": [...],
  "settings": { "goal": 13500, "dark_mode": false, "quick_actions": [...] }
}
```

Backups are written to `~/Documents/CoinTracker/Backups/`.

---

## Building a Standalone Executable

### 1. Generate the app icon (optional)

```bash
python coin_icon.py
# Creates coin.ico in desktop/
```

### 2. Run the build script

```bash
python build.py
```

`build.py` calls PyInstaller with the following key options:

- `--onefile` — single executable
- `--windowed` — no console window on launch
- `--add-data=firebase-key.json;.` — bundles the key if it exists
- Hidden imports for PyQt5, firebase_admin, google.auth, and requests

Output: `desktop/dist/CoinTracker.exe` (Windows) or `desktop/dist/CoinTracker` (macOS/Linux).

> If `coin.ico` exists in `desktop/` it is used as the executable icon automatically.

### 3. Distribute

Copy the entire `dist/` folder. If `firebase-key.json` was bundled, Firestore sync works out of the box on any machine — no Python installation needed.

---

## Customising Quick Actions

Quick actions are the one-click buttons on the dashboard (e.g. "Login +50", "Box Draw (10) −900"). You can manage them in two places:

- **Dashboard** — click the **✎ Customize** button next to the Quick Actions header.
- **Settings page** — click **Manage Quick Actions**.

Both open a dialog where you can add new actions (text, amount, income/expense) and delete existing ones. Changes are saved immediately to Firestore or locally.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `PyQt5` not found | `pip install PyQt5` |
| Charts not showing | `pip install PyQtChart` |
| Firebase import error | `pip install firebase-admin` |
| App crashes on startup | Check Python version (3.10+ required); run from terminal to see traceback |
| `firebase-key.json` not found | Place it next to `coin_tracker.py`; the app falls back to offline mode silently |
| Executable doesn't start | Run from terminal: `dist/CoinTracker.exe`; check for missing DLLs or antivirus blocking |
