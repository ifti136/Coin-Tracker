# 🌐 Coin Tracker — Web

Vanilla JS web app hosted on **Firebase Hosting**. Reads and writes Firestore directly from the browser using the Firebase JS SDK — no backend server required. Shares the same data model as the Android and Desktop apps.

---

## Project Layout

```
web/
├── public/
│   ├── index.html              # Main app shell (dashboard, analytics, history, settings)
│   ├── login.html              # Login / register page
│   ├── admin.html              # Admin panel (role-gated)
│   ├── css/
│   │   ├── style.css           # Main app styles + responsive layout
│   │   ├── login.css           # Auth page styles
│   │   └── admin.css           # Admin panel styles
│   ├── js/
│   │   ├── app.js              # Main app logic (Firestore CRUD, charts, UI)
│   │   ├── login.js            # Auth logic (register, login, theme)
│   │   └── admin.js            # Admin panel logic (stats, user management, broadcast)
│   └── images/
│       ├── coin.ico            # Favicon
│       ├── bkash.png           # Donation card logo
│       ├── nagad.png           # Donation card logo
│       └── rocket.png          # Donation card logo
├── firebase.json               # Hosting config + URL rewrites
├── firebase-config.example.js  # Template — copy to firebase-config.js and fill in
├── firestore.rules             # Firestore security rules
├── firestore.indexes.json      # Firestore index definitions
└── README.md
```

---

## Features

- **Authentication** — Firebase Email/Password auth using synthetic email format (`username@cointracker.app`) to preserve username/password UX. Register and login from `login.html`.
- **Dashboard** — balance card with goal progress bar, estimated days to goal (7-day rolling rate), today/week/month stats, quick action buttons, add/spend coin forms, achievements grid, donation support modal.
- **Analytics** — period filter (Lifetime / Monthly / Weekly / Custom Range), balance timeline line chart, earnings doughnut chart, spending doughnut chart, 7-day earning rate card, best earning week card. Charts via Chart.js.
- **History** — paginated transaction table, date range filter, source/category filter, search, edit dialog, delete with confirmation.
- **Notifications** — activity alerts (goal milestones, strong earning days, best week, proximity warnings) and achievements list with read indicators.
- **Settings** — goal management, quick action add/delete, custom income/expense category lists, JSON backup export/import, profile management, account deletion with password re-auth.
- **Multiple profiles** — stored as subcollection under `user_data/{uid}/profiles/`; last active profile persisted in Firestore.
- **Admin panel** — total users/coins/transactions, 7-day new-user bar chart, user list with sortable columns, search, delete user, broadcast message.
- **Dark / light theme** — toggle persisted in `localStorage` and synced to Firestore `settings.dark_mode`.
- **Responsive** — desktop sidebar nav + mobile bottom nav bar with hamburger menu.
- **Broadcast** — admin can set a message shown as a toast to all users on app load.

---

## Tech Stack

| Tool | Purpose |
|---|---|
| Firebase Hosting | Static hosting + URL rewrites |
| Firebase Auth | Email/Password authentication |
| Firestore | Real-time database |
| Firebase JS SDK v10 | Browser SDK (ESM, no bundler needed) |
| Chart.js | Analytics charts |
| Vanilla JS (ESM) | No framework, no build step |

---

## Firebase Setup

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create (or reuse) a project.
2. Enable **Firestore Database** in **Native mode**.
3. Enable **Authentication → Sign-in methods → Email/Password**.
4. Go to **Project Settings → Your apps → Add app → Web**.
5. Copy the `firebaseConfig` object shown.
6. In `web/public/js/`, create `firebase-config.js` from the example template:

```bash
cp web/public/js/firebase-config.example.js web/public/js/firebase-config.js
# then fill in your project values
```

```js
// firebase-config.js
export const firebaseConfig = {
  apiKey:            "YOUR_API_KEY",
  authDomain:        "YOUR_PROJECT_ID.firebaseapp.com",
  projectId:         "YOUR_PROJECT_ID",
  storageBucket:     "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
  appId:             "YOUR_APP_ID",
};
```

> `firebase-config.js` is in `.gitignore`. Never commit it.

7. Deploy Firestore security rules:

```bash
firebase deploy --only firestore:rules
```

---

## Running Locally

No build step needed. Serve the `public/` folder with any static server, or use the Firebase emulator:

```bash
npm install -g firebase-tools
firebase login
firebase emulators:start --only hosting,firestore,auth
```

Then open [http://localhost:5000](http://localhost:5000).

> When using the emulator, point the SDK to local ports by adding emulator connect calls in `app.js` and `login.js`. For production testing, just open the hosted URL.

---

## Deploying to Firebase Hosting

```bash
firebase login
firebase use --add          # select your project
firebase deploy --only hosting,firestore:rules
```

The `firebase.json` rewrites route `/admin` → `admin.html`, `/login` → `login.html`, and everything else → `index.html`.

Hosting URL: `https://YOUR_PROJECT_ID.web.app`

---

## Firestore Data Model

Data is stored in one document per user with profiles nested as a subcollection — matching the Android and Desktop apps exactly.

```
user_data/{userId}/profiles/{profileName}
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
    income_categories  : string[]
    expense_categories : string[]
  last_updated : string
```

Field names use `snake_case` throughout to match `FirestoreRepository.kt` on Android.

---

## Making a User Admin

In Firestore Console → `users/{userId}` → set `role` field to `"admin"`.

Admin users see an **Admin Panel** link in the sidebar and can access `/admin`.

---

## Customising Quick Actions

Quick actions are the one-click buttons on the dashboard (e.g. "Login +50", "Box Draw (10) −900"). Manage them in **Settings → Manage Quick Actions**. Changes save immediately to Firestore and sync to all platforms.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Blank page / module error | Check `firebase-config.js` exists in `public/js/` and has correct values |
| Login fails with "Username not found" | User doc missing in `users` collection — re-register |
| Charts not rendering | Ensure Chart.js CDN loads; check browser console for errors |
| Firestore permission denied | Re-deploy `firestore.rules`; check user has correct `role` field |
| Admin panel redirects to `/` | User `role` in Firestore is not `"admin"` |
| Emulator not connecting | Add `connectFirestoreEmulator` / `connectAuthEmulator` calls in JS files |
