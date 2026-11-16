# 📱 Coin Tracker Android — Changelog

All notable changes to the Android app will be documented in this file.

---

## [v4.4.0] — 2026

### Added
- **Accessibility & Touch Target Optimization**:
  - Full TalkBack and screen-reader audit with explicit content descriptions and live region semantics.
  - Interactive touch targets padded to adhere to Android accessibility guidelines (≥ 48dp).
- **Responsive Layout Polish**:
  - Refined layout constraints and adaptive maximum content widths across phone and tablet form factors.
  - Smooth adaptive navigation and status bar handling.
- **Production Build & Packaging**:
  - Zero deprecation warnings across all Kotlin and Compose UI modules.
  - Final verified release-ready debug APK build artifact.

---

## [v4.3.0] — 2026

### Added
- **Notifications & Milestone Center (`NotificationsScreen.kt`)**:
  - Activity alerts for goal reached, 50%/75% milestones, strong earning days, and remaining-goal proximity.
  - Achievement cards styled with gold & graphite container tokens, achievement badge icons, and green completion checkmarks.
- **Admin Panel Polish (`AdminScreen.kt`)**:
  - System overview stat cards (Users, Coins Tracked, Transactions) with brand gold typography.
  - 7-day user registration bar chart with tokenized progress indicators.
  - User list with semantic balance coloring, join dates, and delete confirmation dialogs.
- **Glance App Widget (`CoinTrackerWidget.kt`)**:
  - Complete overhaul to gold & graphite design tokens matching the app theme.
  - Responsive layout displaying profile name, live balance, progress bar, and 7-day average rate.
- **Modernized Material 3 Components**:
  - Upgraded to `HorizontalDivider` and AutoMirrored icons (`ArrowBack`, `TrendingUp`, `OpenInNew`).

---

## [v4.2.0] — 2026

### Added
- **Analytics Timeline Canvas Chart**:
  - Custom Compose `Canvas` line chart with continuous gold curve, data points, and vertical gradient fill.
  - Formatted horizontal date axis and adaptive vertical grid lines.
- **Doughnut Breakdown Chart**:
  - Multi-category doughnut chart using five-token palette from `design-tokens.json`.
  - Detailed legend breakdown showing percentage shares and formatted coin amounts.
- **Analytics Stat Cards**:
  - Highlights for Best Week, 7-Day Daily Earning Rate, and Net Coins with semantic container tinting.
- **Transaction History Overhaul**:
  - Refined summary cards for Total Income, Total Spent, and Net Change.
  - Enhanced filter panel with search bar, source selector dropdown, and date-range picker.
  - Improved swipe-to-delete interaction with smooth danger background and delete action label.
  - Transaction rows featuring semantic badge pills (`+` income in green, `−` expense in red) and edit modal.
  - Modern bottom pill pagination with gold active indicator and smooth page windowing.

---

## [v4.1.0] — 2026

### Added
- **Dashboard Focal Balance Panel**:
  - Prominent balance display with bold display typography and coin metadata.
  - Interactive goal progress bar with **25%, 50%, and 75% milestone markers**.
  - Dynamic remaining-goal pill (`⚡ X to goal` / `🎉 Goal Achieved!`).
  - Integrated "Share progress card" action embedded directly in the focal panel.
- **Branded Progress Card Generator (`ProgressCardGenerator.kt`)**:
  - Complete overhaul to 900×500 gold/graphite graphics matching Web v4.0.4.
  - Features dark canvas background, gold accent strip, dot grid, balance, progress bar with milestone ticks, and 6 stat boxes (Today, Week, Month, 7d Rate, Best Week, Estimated Days).
- **Refined Quick Actions & Entry Forms**:
  - Clean `+ New Action` tile navigating directly to settings.
  - Income and Expense entry forms with container tinting, semantic badges (`Income +` / `Expense −`), and proper error handling.

---

## [v4.0.0] — 2026

### Changed
- **Gold & Graphite Design Overhaul**: Full migration from the legacy blue palette to the unified gold & graphite design system (`design-tokens.json`).
  - Dark Theme: Deep graphite canvas (`#11151B`), surface raised (`#222B35`), brand gold (`#E8B84A`), income emerald (`#47D7A0`), expense coral (`#F28B82`).
  - Light Theme: Crisp light canvas (`#F5F6F8`), brand gold (`#9A6A08`), income emerald (`#06734F`), expense coral (`#B93832`).
- **Material 3 Palette & Semantic Extensions**: Integrated token color extensions (`income`, `expense`, `brand`, `surfaceSubtle`, etc.) for clean, idiomatic Compose usage.
- **App Shell & Top Bar**: Refined top bar card, brand gold navigation indicators, and compact sync status pill.
- **Login Experience**: Redesigned login and registration cards with brand logo mark, consistent padding, and gold primary button.

### Fixed
- **Profile Switching Freeze**: Resolved permanent loading freeze when changing profiles by awaiting profile data in `refreshDataInternal` and safely releasing action state in a guaranteed `finally` block.

---

## [v3.3.0] — 2026

### Web & Mobile Feature Parity
- **Achievements Expanded**: Added 6 new achievements mirroring the web app: *Master Hoarder* (25,000 coins), *First Payday* (1st earning), *Active Tracker* (25 txns), *Century Club* (100 txns), *High Roller* (1,000+ single earning), and multi-tier *3-Day*, *7-Day*, and *14-Day* consecutive login streaks.
- **Notifications & Icons**: Added Material 3 vector icons (`AttachMoney`, `TrackChanges`, `Bolt`, `EmojiEvents`) and descriptive accessibility labels for all achievement types.
- **History Badging**: Transaction amounts now rendered with Income (`↑` in green) and Expense (`↓` in red) pill badges.
- **Context-Aware History Empty States**: Clean visual separation between empty history vs no filter matches, with one-tap "Clear Filters" action.
- **Single History Pagination**: Removed redundant top pagination to keep single, uncluttered bottom pagination.
- **Dashboard Goal Indicators**: Real-time remaining pill (`⚡ X to goal` / `🎉 Goal Achieved!`) in balance header, milestone ticks on progress bar at 25%, 50%, and 75%.
- **Quick Action Navigation**: Added `+ New Action` shortcut tile directly in the Quick Actions grid navigating to settings.
- **Live DB Sync Status**: Added compact sync status indicator pill (`Synced` / `Syncing...` / `Offline`) to the top bar.
- **Database Loading State**: Full-screen database retrieval state (`Retrieving data from database...` / `Switching profile...`) during initial data load and profile switching.
- **Danger Zone Isolation**: Separated destructive actions (`Delete All Data` and `Delete My Account`) into an isolated, red-accented Danger Zone block.
- **GlassCard Stroke**: Enhanced `GlassCard` composable with optional border stroke support.

---

## [v3.2.0] — 2026

### Safety Cache System
- Local safety cache introduced — every profile snapshot is written to `filesDir/safety_cache/{userId}/{profile}.json` after every successful data load or save.
- Cache stores full transaction list, settings, balance, transaction count, and a `savedAt` timestamp.
- On every app launch, both the cache and Firestore are loaded; `SyncManager` compares them and decides what to do automatically:
  - **Synced** — data matches, normal operation
  - **DB wiped/empty** — cache has significant data but DB returned 0 transactions; cache is auto-pushed to Firestore in the background, no user action needed
  - **Offline / DB failed** — Firestore unreachable; app loads from cache and shows an offline banner; full read/write still works locally
  - **Offline edits** — cache has more transactions than DB (user edited offline); cache pushed to DB silently on reconnect
  - **Conflict** — both sources have significant but disagreeing data (balance diff ≥ 500 coins AND txn count diff ≥ 5); dialog shown with a side-by-side comparison of balance and transaction count for each source; user chooses which to keep
- `SyncBanner` composable shown below top bar for offline, restored, and sync-pushed states; dismissible.
- `ConflictDialog` forces a choice — cannot be dismissed without selecting a version.
- No new gradle dependencies — cache uses `org.json` (built into Android) and `filesDir` (no permissions needed).
- Cache deleted automatically when profile is deleted or all data is wiped.
- `LocalCacheRepository` and `SyncManager` added under `data/` package.

---

## [v3.1.2] — 2026

### Notification Badge Fix
- Bell icon badge now clears correctly after viewing the notifications screen — badge count is computed against a persisted set of seen achievement names (`seen_achievements_{profile}` in SharedPreferences).
- "Read All" button added to Notifications screen top bar — marks all current achievements as seen and clears the badge immediately.
- Badge also clears automatically on screen entry via `LaunchedEffect`.
- Back arrow added to Notifications screen top bar to navigate back to the previous screen.
- Bottom navigation tab buttons now work correctly when tapped from the Notifications screen — `popUpTo` with `inclusive = false` ensures the notifications route is cleared from the back stack.

### Pills Pagination
- Transaction history pagination replaced with pill-style page buttons.
- `LazyRow` of rounded pill buttons centred below and above the list.
- Shows a sliding window of 5 pages around the current page with `…` ellipsis for large page counts.
- Previous and next arrow buttons on either end, dimmed when at the boundary.

---

## [v3.1.1] — 2026

### Notifications Screen
- Dedicated notifications screen (`NotificationsScreen.kt`) replacing the old achievement list.
- Alerts section: shows proactive alerts based on transaction history (streak milestones, goal progress, big earnings).
- Achievements section: shows unlocked and locked achievement cards with progress indicators.

---

## [v3.1.0] — 2026

### Bug Fixes & Refinements
- Fixed transaction balance discrepancy when editing transactions in non-default profiles.
- Optimized Firestore batch updates.
- Improved error handling on network disconnect.

---

## [v3.0.0] — 2026

### Analytics Intelligence
- Added 7-day rolling rate and projected days-to-goal calculator.
- Added Best Earning Week tracker card to Analytics.

### Widget & System Integration
- Glance home-screen widget with real-time balance and goal progress.
- WorkManager background daily reminder at 8pm local time.
- Shareable progress card image generator.

---

## [v2.1.0] — 2026

### Firebase Direct Auth
- Removed Flask backend entirely; authenticates directly with Firebase Auth SDK.
- Synthetic email format (`username@cointracker.app`).
- Silent migration for legacy accounts.

---

## [v2.0.0] — 2026

### UI Modernization
- Glassmorphism design system (`GlassCard`) with background blur.
- Dark and light theme toggle.
- Bottom navigation bar.

---

## [v1.0.0] — 2025

### Initial Release
- Native Kotlin + Jetpack Compose Android app.
- Direct Firestore sync, transactions CRUD, quick actions, and goal progress.

