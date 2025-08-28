# CoinTracker — eFootball 26 Coin Tracker

A simple, sleek desktop app to track the coins you **earn** and **spend** in eFootball 26 — including when and how you got them, plus spending via **Box Draws**. Built with PyQt5 and ships with a one-click PyInstaller build script.&#x20;

---

## 1) Features

1. **Add earnings & spending (Box Draws)**

   * Track positive coin gains by source (Event Reward, Login, Daily Games, Achievements, Ads, Others). For **Box Draws**, amounts are recorded as spending (negative) automatically and the main button switches to “Use Coins.”&#x20;
2. **Live balance + goal tracking**

   * Always-visible balance card and a goal progress bar you can set/update anytime.&#x20;
3. **Dark/Light theme toggle (persisted)**

   * One click dark mode that’s saved per profile.&#x20;
4. **Transaction history with smart filters**

   * Search by source, filter by source/type (Earnings/Spending), and date range (defaults to the last month), with sortable columns.&#x20;
5. **Breakdown charts (Earnings & Spending)**

   * Beautiful pie charts (Qt Charts) showing totals by source, with separate tabs for **Earnings** and **Spending**.&#x20;
6. **Import/Export JSON**

   * Backup or reuse your data as JSON.&#x20;
7. **Multiple profiles**

   * Keep separate datasets (e.g., alt accounts) and switch instantly. Data is stored per profile.&#x20;
8. **Custom app icon generator**

   * Optional script to create a coin-themed app icon (`coin.ico`).&#x20;
9. **One-file desktop build**

   * PyInstaller script to create a single EXE (`--onefile`, `--windowed`) with your icon. Output goes to `dist/`.&#x20;

---

## 2) Required Python Libraries (to download)

Install these from PyPI:

* `PyQt5` — UI toolkit
* `PyQtChart` — Qt Charts add-on used for the pie charts (imported as `from PyQt5.QtChart import ...`)
* `pyinstaller` — **only** if you plan to build a desktop executable

> The app code imports `PyQt5` UI modules and `PyQt5.QtChart` for charts; the build script imports `PyInstaller`. &#x20;

**Quick install:**

```bash
pip install PyQt5 PyQtChart pyinstaller
```

Optionally, add a `requirements.txt`:

```txt
PyQt5
PyQtChart
pyinstaller
```

---

## 3) Project Structure

```
/your-repo
├─ coin_tracker.py     # Main PyQt5 app (GUI, storage, charts, filters, profiles)
├─ coin_icon.py        # Optional: generate coin.ico programmatically
├─ build.py            # PyInstaller build script (onefile, windowed, icon)
└─ README.md
```

* App logic/UI live in `coin_tracker.py`.&#x20;
* Icon generation is in `coin_icon.py`.&#x20;
* Packaging is handled by `build.py`.&#x20;

---

## 4) Step-by-Step: Getting Started

1. **Clone & enter the repo**

   ```bash
   git clone https://github.com/<you>/<repo>.git
   cd <repo>
   ```
2. **Create & activate a virtual environment (recommended)**

   ```bash
   python -m venv .venv
   # Windows
   .venv\Scripts\activate
   # macOS/Linux
   source .venv/bin/activate
   ```
3. **Install dependencies**

   ```bash
   pip install PyQt5 PyQtChart
   ```
4. **(Optional) Create the app icon**

   ```bash
   python coin_icon.py
   ```

   This generates `coin.ico` in the repo root.&#x20;
5. **Run the app**

   ```bash
   python coin_tracker.py
   ```

   * A `Documents/CoinTracker/<Profile>.json` file will be created to store your data. Default profile is **“Default”**.&#x20;

---

## 5) Step-by-Step: Using the App

1. **Set a goal**

   * Enter a number and click **Set Goal** to see a progress bar update as your balance grows.&#x20;
2. **Record earnings**

   * Type an **Amount** (or use quick buttons like **+10 / +20 / +50**) and choose a **Source** (Event Reward, Login, etc.), then click **Add Transaction**.&#x20;
3. **Record spending (Box Draws)**

   * Select **Box Draws**, enter the coins you used, and click **Use Coins**. The app saves a **negative** amount for spending and labels it accordingly.&#x20;
4. **Search, filter & sort history**

   * Use **Search**, **Source**, **Type** (Earnings/Spending), and **From/To** date pickers (defaults to one month back) to narrow results; click column headers to sort.&#x20;
5. **See breakdown charts**

   * Click **View Coin Breakdown** to open tabs for **Earnings** and **Spending**, each with totals by source and a pie chart.&#x20;
6. **Import/Export JSON**

   * Use **Import JSON** to add transactions from a file or **Export JSON** to back up/share your data.&#x20;
7. **Manage profiles**

   * Switch profiles from the top bar or click **+ New Profile**. Each profile stores its own JSON file.&#x20;
8. **Toggle Dark Mode**

   * Use the **Dark Mode** checkbox. The preference is saved per profile.&#x20;

---

## 6) Step-by-Step: Build a Single-File App (Windows)

1. **Ensure you have the icon (optional but recommended)**

   ```bash
   python coin_icon.py
   ```

   Saves `coin.ico` for branding.&#x20;
2. **Install PyInstaller**

   ```bash
   pip install pyinstaller
   ```
3. **Run the build script**

   ```bash
   python build.py
   ```

   * Produces `dist/CoinTracker.exe` using `--onefile --windowed --icon=coin.ico`.
   * Build intermediates go to `build/`.&#x20;

> Advanced: You can also call PyInstaller directly against `coin_tracker.py` with similar flags if you prefer. The provided script already sets sensible defaults.&#x20;

---

## 7) Data Storage & Format

* **Location:** `~/Documents/CoinTracker/<Profile>.json`
* **What’s stored:**

  * `transactions`: list of entries `{ date (ISO), amount, source, previous_balance }`
  * `settings`: `{ goal, dark_mode }`
  * `profile_name`, `last_updated`
    All of this is handled automatically by the app.&#x20;

---

## 8) Troubleshooting

1. **`ModuleNotFoundError: No module named 'PyQt5.QtChart'`**
   Install the charts add-on:

   ```bash
   pip install PyQtChart
   ```

   The code imports `PyQt5.QtChart` for pie charts.&#x20;
2. **No icon in the EXE**
   Make sure `coin.ico` exists before running `build.py`.&#x20;
3. **Can’t find or save data**
   Verify your OS user has write access to `Documents/CoinTracker/`. The app writes JSON per profile there.&#x20;
4. **Dark mode state not persisting**
   Settings (including dark mode and goal) are saved per profile in the same JSON.&#x20;

---

## 9) Contributing

* Open issues/PRs for feature requests or fixes.
* Ideas: per-source targets, CSV export, multi-currency support.

---

### Run commands (copy-paste)

```bash
# 1) Setup
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS/Linux
source .venv/bin/activate

pip install PyQt5 PyQtChart

# 2) Run
python coin_tracker.py

# 3) (Optional) Build icon + EXE
python coin_icon.py
pip install pyinstaller
python build.py
```

Happy tracking & good luck with those Box Draws! 🎯
