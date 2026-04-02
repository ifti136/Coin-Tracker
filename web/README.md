# 🌐 Coin Tracker — Web

Flask web application with Jinja2 templates, Firestore persistence, and a responsive glassmorphism UI. Also serves as the **authentication backend** for the Android app — issuing Firebase custom tokens via `/api/mobile-login` and `/api/mobile-register`.

---

## Project Layout

```
web/
├── app.py                  # Flask app, all routes, Firebase init, WebCoinTracker
├── requirements.txt        # Python dependencies
├── render.yaml             # Render.com deployment config (gunicorn)
├── static/
│   ├── css/
│   │   ├── style.css       # Main glassmorphism theme (light + dark via CSS vars)
│   │   ├── login.css       # Login page styles
│   │   └── admin.css       # Admin panel overrides
│   ├── js/
│   │   ├── app.js          # CoinTrackerApp class — all dashboard/history/analytics logic
│   │   ├── admin.js        # Admin panel — pagination, sorting, chart
│   │   └── login.js        # Auth form + theme toggle on login page
│   └── images/
│       ├── coin.ico        # App favicon
│       ├── bkash.png       # Donation card logos
│       ├── nagad.png
│       └── rocket.png
└── templates/
    ├── index.html          # Main app (dashboard, analytics, history, settings)
    ├── login.html          # Login / register form
    └── admin.html          # Admin panel (stats, broadcast, user table)
```

---

## Features

- **Auth** — username/password registration and login; Werkzeug PBKDF2-SHA256 hashing; session-based auth with 7-day persistence
- **Mobile auth bridge** — `/api/mobile-login` and `/api/mobile-register` issue Firebase custom tokens so the Android app can authenticate with the same credentials
- **Dashboard** — balance card with gradient, goal progress, estimated days, today/week/month stats, quick action buttons, achievements grid
- **Analytics** — Chart.js line (timeline), doughnut (earnings breakdown), and bar (spending breakdown) charts; total earnings/spending/net stats
- **History** — server-side paginated transaction list (20/page), date range, source, and search filters; edit and delete with inline buttons; sortable columns
- **Settings** — goal management, quick action add/delete, JSON export and import, Firebase connection status
- **Profiles** — multiple profiles per user; last active profile persisted in Firestore
- **Admin panel** — total users/coins/transactions, 30-day new-user Chart.js line chart, searchable and sortable user table with client-side pagination, broadcast message system
- **Glassmorphism UI** — animated gradient background, backdrop-blur glass cards, CSS variable-based dark/light theme; theme persisted in `localStorage` so it survives page reloads
- **Broadcast** — admins can set a message that appears as a toast for all users on next load

---

## API Routes

### Auth
| Method | Route | Description |
|---|---|---|
| `POST` | `/api/register` | Create account (web) |
| `POST` | `/api/login` | Login (web), returns redirect URL |
| `POST` | `/api/logout` | Clear session |
| `GET` | `/api/user` | Current user info |
| `POST` | `/api/mobile-login` | Login + return Firebase custom token (Android) |
| `POST` | `/api/mobile-register` | Register + return Firebase custom token (Android) |

### Data
| Method | Route | Description |
|---|---|---|
| `GET` | `/api/data` | Full dashboard payload (balance, stats, analytics, achievements) |
| `GET` | `/api/history` | Paginated, filtered transaction list |
| `POST` | `/api/add-transaction` | Add a transaction |
| `POST` | `/api/update-transaction/<id>` | Edit a transaction |
| `POST` | `/api/delete-transaction/<id>` | Delete a transaction |
| `POST` | `/api/update-settings` | Save goal, dark_mode, quick_actions |
| `POST` | `/api/import-data` | Overwrite current profile with imported JSON |
| `POST` | `/api/add-quick-action` | Append a quick action |
| `POST` | `/api/delete-quick-action` | Remove quick action by index |

### Profiles
| Method | Route | Description |
|---|---|---|
| `GET` | `/api/profiles` | List profiles + current profile |
| `POST` | `/api/switch-profile` | Switch active profile |
| `POST` | `/api/create-profile` | Create a new profile |

### Admin
| Method | Route | Description |
|---|---|---|
| `GET` | `/api/admin/stats` | Aggregate stats + 30-day signup chart |
| `GET` | `/api/admin/users` | All users with balance and txn count |
| `POST` | `/api/admin/delete-user` | Delete user + their data |
| `GET` | `/api/broadcast` | Get current broadcast message |
| `POST` | `/api/admin/broadcast` | Set broadcast message |

---

## Local Development Setup

### 1. Install dependencies

```bash
cd web
python -m venv venv
source venv/bin/activate          # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Configure Firebase

**Option A — environment variables** (recommended for production):

Create `web/.env`:

```env
SECRET_KEY=change-me-to-a-random-string
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_PRIVATE_KEY_ID=your-private-key-id
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
FIREBASE_CLIENT_EMAIL=service-account@your-project.iam.gserviceaccount.com
FIREBASE_CLIENT_ID=your-client-id
```

**Option B — service account file** (local dev only):

Place `firebase-key.json` in `web/`. The app picks it up automatically if env vars are absent.

> Neither `.env` nor `firebase-key.json` should ever be committed. Both are in `.gitignore`.

### 3. Run

```bash
python app.py
# Development server: http://127.0.0.1:5001
```

---

## Production Deployment (Render)

The repo includes `render.yaml` which defines:

```yaml
startCommand: "gunicorn app:app"
workingDirectory: web
```

**Steps:**

1. Push to GitHub.
2. Connect your repo in the [Render dashboard](https://render.com).
3. Render auto-detects `render.yaml` and creates the service.
4. Add your environment variables in **Render → Environment**:
   - `SECRET_KEY`
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_PRIVATE_KEY` (paste with literal `\n` newlines)
   - `FIREBASE_PRIVATE_KEY_ID`
   - `FIREBASE_CLIENT_EMAIL`

> The free Render plan spins down after inactivity. The first request after sleep is slow (~30 s). Upgrade to a paid plan for always-on.

---

## Running the Mobile Auth Bridge

The Android app posts credentials to `/api/mobile-login`. The Flask server:

1. Verifies the password against Firestore's `password_hash` (Werkzeug PBKDF2).
2. Issues a Firebase custom token via the Admin SDK.
3. Returns `{ success: true, token: "..." }`.

The Android app then calls `auth.signInWithCustomToken(token)` and talks to Firestore directly from that point.

**The Flask server must be reachable** from the Android device. Update `BASE_URL` in `android/.../data/FirestoreRepository.kt` to your server's public IP or domain.

---

## Making a User an Admin

In the Firestore console:

1. Open `users/{userId}`.
2. Set the `role` field to `"admin"`.

That user will then see the Admin Panel link in the sidebar and have access to all `/api/admin/*` routes.
