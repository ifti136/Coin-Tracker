# Coin Tracker Desktop

A PyQt5 desktop app for tracking coin earnings and spending with optional Firebase sync and a polished UI.

## Features

- Track income/expenses with running balance, goal progress, and quick-action buttons.
- Multiple profiles per user; data stored locally or in Firestore if configured.
- Import/export transactions and settings as JSON.
- Light/dark themes, toast notifications, and (if QtCharts is installed) balance and breakdown charts.
- Buildable into a single-file Windows executable via PyInstaller.

## Requirements

- Python 3.10+ (Windows tested)
- Packages: PyQt5, PyQtChart (for charts), firebase-admin (optional online sync), requests, google-auth, google-cloud-core, google-auth-oauthlib, PyInstaller (for building)

## Setup

```powershell
cd desktop
python -m venv .venv
.\.venv\Scripts\activate
pip install PyQt5 PyQtChart firebase-admin requests google-auth google-cloud-core google-auth-oauthlib pyinstaller
```

## Running from source

```powershell
.\.venv\Scripts\activate
python coin_tracker.py
```

## Building an EXE

```powershell
.\.venv\Scripts\activate
python build.py
```

- Output: `desktop/dist/CoinTracker.exe`
- Optional: place `coin.ico` in `desktop/` to brand the exe; run `coin_icon.py` to generate one.
- Optional: place `firebase-key.json` in `desktop/` to bundle Firestore access into the build; the key is also copied into `dist/`.

## Firebase (optional)

- Drop a service account file named `firebase-key.json` next to `coin_tracker.py`.
- The app falls back to local storage if Firebase is unavailable.

## Data storage

- Local saves live under `%USERPROFILE%\Documents\CoinTracker/<profile>.json`.
- Each profile keeps its own transactions, quick actions, goal, and theme preference.

## Tips

- Charts require the QtCharts module (`PyQtChart`).
- If Firebase is configured, last active profile and data sync across machines; otherwise everything stays local.
