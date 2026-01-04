# Coin Tracker Web

A Flask web app that tracks coin earnings/spending with user accounts, per-user profiles, analytics, and an admin dashboard. Data is stored in Firestore when Firebase is configured.

## Features

- Email-free username/password auth with hashed passwords and sessions.
- Per-user profiles containing transactions, quick-action buttons, goal settings, and dark/light theme preference.
- Dashboard stats (today/week/month), goal progress, achievements, charts, filtered history with pagination, and import/export.
- Admin panel for broadcast messages, user metrics, and deleting users.
- Deployable to Render via `render.yaml` (uses Gunicorn).

## Requirements

- Python 3.10+
- Dependencies listed in `requirements.txt`
- A Firebase service account (Firestore) for auth + data persistence.

## Setup (local dev)

```powershell
cd web
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

Create a `.env` (or set environment variables) with your Firebase credentials and app secret:

```env
SECRET_KEY=change-me
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_PRIVATE_KEY_ID=your-private-key-id
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
FIREBASE_CLIENT_EMAIL=service-account@your-project.iam.gserviceaccount.com
FIREBASE_CLIENT_ID=your-client-id
```

- Alternatively, place `firebase-key.json` in `web/` and omit the Firebase env vars.
- `FIREBASE_PRIVATE_KEY` must preserve newlines; the escaped `\n` form above works in `.env`.

## Running

```powershell
.\.venv\Scripts\activate
python app.py
```

- Default dev server: http://127.0.0.1:5001
- Production command (used by Render): `gunicorn app:app`

## Accounts

- Registration/login calls Firestore; ensure Firebase is reachable before testing auth.
- To promote an admin, set the user's `role` field to `admin` in Firestore `users/{userId}`.

## Deployment (Render)

- `render.yaml` defines a Python web service with `pip install -r requirements.txt` and `gunicorn app:app`.
- Add the same env vars in Render dashboard/secrets.

## Frontend

- Templates live in `templates/`; bundled JS/CSS under `static/` powers dashboard, login, and admin views.
