# 🌐 Coin Tracker Web — Changelog

All notable changes to the web client will be documented in this file.

---

## [v4.0.5] — 2026

### Fixed
- **Full-Width Desktop Layout**: Removed artificial `max-width: 900px` and `max-width: 1080px` limits on `.main-content`. The dashboard, analytics, history, and settings now gracefully span the full desktop viewport without an empty gap on the right side.

### Changed
- **Documentation Architecture**: Extracted detailed web version history into standalone `web/CHANGELOG.md` for clean, modular release tracking.

---

## [v4.0.4] — 2026

### Added
- **Web Progress Card Generator**: Offscreen Canvas 2D engine that renders a branded 900×500 PNG graphic mirroring the Android progress card.
  - Displays live balance, goal target, 25%/50%/75% milestone ticks, today/week/month stats, 7-day rate, and best week.
  - Leverages active CSS design tokens (`--surface`, `--brand`, `--income`, `--text-primary`) for high-fidelity rendering.
- **Web Share Flow**:
  - Direct sharing via Web Share API (`navigator.share`) on supported mobile and desktop browsers.
  - Automatic PNG image download fallback on unsupported browsers.
  - Added "Share progress card" trigger button inside the dashboard focal panel.

---

## [v4.0.3] — 2026

### Added
- **Chart-to-History Navigation**:
  - Interactive click handlers on analytics charts (balance timeline, earnings doughnut, spending doughnut).
  - Clicking a timeline point pre-fills the History date range for that day.
  - Clicking a doughnut slice pre-fills the source or category filter in History.
  - Contextual selection bar below charts with a "View matching transactions" action and dismiss button.
  - Full ARIA region announcement for assistive technology.

---

## [v4.0.2] — 2026

### Added
- **Keyboard Shortcuts & Quick Navigation**:
  - Sequential two-key page navigation: `G` then `D` (Dashboard), `A` (Analytics), `H` (History), `N` (Notifications), `S` (Settings).
  - Quick search shortcut: `/` jumps to and focuses the History search bar.
  - Interactive Keyboard Shortcuts dialog accessible via `?`, top bar icon button, or Settings.
  - Input field isolation ensuring typing in forms or text fields is never intercepted.

---

## [v4.0.1] — 2026

### Added
- **Transaction Undo Capability**:
  - Accessible undo toast extended to Add Coins, Spend Coins, and Quick Actions.
  - Reverting a transaction safely removes the record and recalculates running balances in Firestore without race conditions.
  - Accessible undo button with explicit `aria-label` and 8-second action window.

---

## [v4.0.0] — 2026

### Changed
- **Gold & Graphite Design System**:
  - Redesigned visual tokens matching `design-tokens.json` across all web surfaces.
  - Consolidated stylesheets into a single unified `base.css` with dark and light theme palettes.
  - Rebuilt Dashboard with a focal balance and goal panel, remaining coins pill, and 25%/50%/75% milestone markers.
  - Restructured Analytics, History, Notifications, Settings, Login, and Admin screens.
  - Replaced dead CSS files (`style.css`, `login.css`, `admin.css`) with v6 offline service worker caching.

### Security & Safety
- **Write Hardening**:
  - Added write locks and duplicate-submission guards on Add, Spend, Quick Actions, and Edit modals.
  - Truthful pending spinners and disabled button states during network I/O.
  - Replaced native dialog prompts with accessible `.modal.open` dialogs with focus trap and Escape key support.
  - XSS sanitization across all dynamic list renderers.

---

## [v3.3.0] — 2026

### Added
- Six achievements mirroring the mobile app (Master Hoarder, First Payday, streak milestones).
- Income and Expense pill badges in transaction history.
- Context-aware empty states with a "Clear Filters" action.
- Dashboard remaining-goal pill and 25%, 50%, 75% milestone ticks.
- Separated Danger Zone in Settings for high-impact destructive operations.
- Live database sync status indicator and full-screen loading states.
- `+ New Action` tile in the Quick Actions grid.

---

## [v3.2.0] — 2026

### Added
- Local safety snapshots saved after successful database queries.
- Automatic cache restore if the database is empty, plus offline conflict resolution.
- Offline safety banner with cached mode operations.

---

## [v3.1.2] — 2026

### Added
- Persistent unread achievement badge clearing and a "Read All" action.
- Sliding-window pill pagination with rounded buttons and ellipsis.

---

## [v3.1.1] — 2026

### Added
- Dedicated notifications page with activity alerts and achievements.
- Top bar bell badge showing the unread notification counter.
- Separated swipe and edit actions in history.

---

## [v3.1.0] — 2026

### Fixed
- Resolved build and compile issues across web and mobile targets.

---

## [v3.0.0] — 2026

### Added
- 7-day rolling average and estimated days to goal.
- Best Earning Week and 7-day earning rate in Analytics.
- Milestone notifications and progress card sharing generator.

---

## [v2.0.0] — 2026

### Changed
- Complete web layout overhaul with modern card primitives.
- Analytics charts with Chart.js.
- Multi-profile support with Firestore synchronization.

---

## [v1.0.0] — 2025

### Added
- Initial web release on Firebase Hosting.
- Firebase Auth (synthetic email mapping) and Cloud Firestore backend.
- Dashboard, Add/Spend forms, History table, and Settings.

