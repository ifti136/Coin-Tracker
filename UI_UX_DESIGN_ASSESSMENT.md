# Coin Tracker — Cross-Platform UI/UX Audit Report

**Scope:** Android (Jetpack Compose, `android/app/src/main/java/com/cointracker/mobile/ui/`) + Web (vanilla JS/CSS, `web/public/`)
**Audit date:** 2026-09-10 · **Mode:** audit · **Baseline:** ui-ux-pro-max skill (a11y/touch/contrast floor) + block list

---

## Executive Summary

Coin Tracker is a feature-complete, genuinely useful app (swipe-to-delete with UNDO, date-range pickers, offline sync with conflict resolution, achievements, admin panel, widget). The functional UX is ahead of the visual/accessibility layer. But both platforms share the same **core design debt**: a purple/pink/blue animated gradient aesthetic, unpaired default typography, emoji-as-icons, and a token system that is duplicated three times across the two codebases instead of defined once.

The Android app is not a Material 3 app — it is a **web design ported into Compose** (the theme file literally imports tokens named `WebPrimary`, `WebSuccess`, `WebDanger`, and sets `background = Color.Transparent` to show a web-style gradient). The web app is not a PWA — no manifest, no service worker, no offline story, despite the Android app having a full offline cache. Platform conventions are inverted: the native app should be the most "native" and it is the least.

**Overall score: 4.3 / 10.** Strong functional UX, weak design system, failing accessibility floor.

---

## 1. Visual Design System Consistency — **3/10**

### What's consistent (the one strength)
- Glassmorphism language is coherent across both platforms: translucent surfaces, `backdrop-filter: blur()` on web (`style.css:66-68, 91-92`), layered blur+alpha surfaces in `GlassCard.kt:37-63`. Cards, top bar, bottom nav, and modals all speak the same glass dialect.
- Semantic color roles (primary/success/danger) exist in both: `Color.kt:18-25` and `style.css:9-11`.

### What's broken
| Finding | Location | Severity |
|---|---|---|
| **Animated purple→pink→blue gradient background** on both platforms. Light: `#c3aed6 → #f0abfc → #a1c4fd → #fdf8c8`; dark: `#1e1b3a → #4a0f4b → #0b3a5d → #4e4a2e`. Animated 10–15s infinite on both. | `style.css:20,36,44-56`; `login.css:10,22,31-43`; `Color.kt:6-15`; `CoinTrackerApp.kt:42-49` | **Block-list fail** |
| **Unpaired typography.** Web: Inter for everything (`style.css:43,596`; `login.css:28`). Android: `FontFamily.SansSerif` for everything (`Type.kt:11,17`). No display face, no pairing, no personality. | both | **Block-list fail** |
| **No type scale.** Android defines only 2 of ~15 M3 type roles (`Type.kt:9-22`); the rest fall back to defaults. Web has no type tokens at all — sizes are scattered literals (10, 11, 12, 13, 14, 16, 18, 20, 24, 26, 32px). | both | High |
| **No spacing/radius/shadow scale.** Android: hardcoded `dp` everywhere (4, 6, 8, 12, 16, 18, 24, 80); `GlassCard` hardcodes `RoundedCornerShape(20.dp)` + `blur(10.dp)`. Web: radii 6/8/10/12/16/20px, shadows `0 4px 6px`, `0 8px 32px`, `0 10px 20px` — no elevation scale. | both | High |
| **Loud gradient surface.** Web balance card is a blue→green gradient with white text (`style.css:239-244`) — a "loud gradient surface" variant of the block-list gradient tell. | `style.css:241` | High |
| **Accent not reserved.** `#3b82f6` is used for active nav, chips, buttons, links, chart lines, stat values, section titles, and the balance gradient simultaneously. Success `#10b981` colors every positive number. Nothing is "reserved". | both | Medium |
| **Emoji as structural icons** (nav, badges, card titles, alerts, buttons). | both | **Block-list fail** (see §5) |
| **Hardcoded hex bypassing tokens in Android screens** — 55 `Color(0xFF…)` literals in screens/components, e.g. `AnalyticsScreen.kt:136-138,254-255`, `SettingsScreen.kt:181,196,247,286,332`, `NotificationsScreen.kt:47-62`. | Android | High |

---

## 2. Screen-by-Screen UX Analysis

### Dashboard
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Good, with flaws** | Balance card hierarchy is solid (label → 32sp bold number → progress → goal/ETA). Stats row (Today/Week/Month) is the "3 identical cards" pattern — acceptable for data, but visually flat. **Flaw:** the Source/Category pickers are *disabled* `OutlinedTextField`s with a `clickable` modifier (`DashboardScreen.kt:263-275, 310-322`) — TalkBack announces them as disabled while they're interactive; `onValueChange = {}` is a dead lambda. **Flaw:** validation errors are transient snackbars ("Enter a valid positive amount"), not field-level. **Flaw:** every add/spend triggers a **full-screen blocking `LoadingOverlay`** (`CoinTrackerApp.kt:276`) — the UI freezes on every quick-action tap. |
| Web | **Good, with flaws** | Labeled inputs (better than Android's placeholder-only "Amt"). Quick-action buttons show `is-processing` opacity instead of blocking (`app.js:455-458`) — better pattern. **Flaw:** balance card white-on-gradient contrast (§5). **Flaw:** no loading skeleton on first load — blank page until Firestore resolves. |

### Analytics
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Good structure, inaccessible charts** | Period chips + custom range + clear button is a strong pattern. 7-day rate + best-week cards are well-designed. **Flaw:** all three charts are `Canvas` draws (`AnalyticsScreen.kt:147-162, 257-263`) — **invisible to TalkBack**, no axis labels, no gridlines, no tooltips, no touch interaction, no data labels on the line chart. **Flaw:** `StatBox` is fixed `width(100.dp)` ×3 in a `SpaceBetween` row — 316dp+ of fixed width, tight on 320dp screens. |
| Web | **Good, similar gaps** | Chart.js line + doughnut with legends. **Flaw:** no empty-data state for charts (empty canvas renders blank). **Flaw:** tab headers lack `role="tab"`/`aria-selected`/`aria-controls` and arrow-key nav (`index.html:217-219`). **Flaw:** doughnut relies on color + legend; no data table alternative. |

### History
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Best screen in the app** | Swipe-to-delete with red reveal + **UNDO snackbar** (`CoinTrackerApp.kt:249-252`) is exemplary. Search + source filter + date range + windowed pill pagination is feature-complete. **Flaw:** edit icon button is 36dp (`HistoryScreen.kt:292-299`), pagination pills/arrows are 36dp (`HistoryScreen.kt:346-352, 434-441`) — below the 48dp floor. **Flaw:** edit dialog's date field is free-text "YYYY-MM-DD" with silent no-op on invalid input. |
| Web | **Functional, weaker patterns** | Table with filters + pagination. **Flaw:** delete uses native `confirm()` (`app.js:809`) and has **no undo** — Android has UNDO, web doesn't (parity gap). **Flaw:** delete button is an emoji `🗑` with no label/aria. **Flaw:** 6-column table scrolls horizontally on mobile (`style.css:513`) — Android's card list is the better mobile pattern. |

### Settings
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Dense but complete** | Long scroll of GlassCards; destructive actions have proper confirmation dialogs; delete-account requires password re-entry (good). **Flaw:** category delete IconButtons are 32dp (`SettingsScreen.kt:300,346`). **Flaw:** quick-action delete has no confirmation. **Flaw:** no visual section hierarchy — 7 equal glass cards, hard to scan. |
| Web | **Complete** | Same feature set. **Flaw:** emoji card titles (🎯⚡📂🛒💾⚠️🗑). **Flaw:** destructive actions mix `confirm()` and a modal inconsistently. |

### Auth (Login/Register)
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Adequate** | Centered glass card on animated gradient — the "full-viewport centered hero" block-list pattern. **Flaw:** no password visibility toggle (M3 convention). **Flaw:** no confirm-password on register — **web has one, Android doesn't** (parity gap). **Flaw:** no autofill hints. |
| Web | **Adequate** | Same centered-card pattern (`login.css:36`). Good: real `<label for>` pairs, `autocomplete` attributes, inline validation hints, spinner-in-button. **Flaw:** `outline: none` on inputs (`login.css:112`) with only a border-color focus change — weak focus indicator. |

### Notifications
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Good** | Alerts + achievements, auto-mark-read on open, badge with 9+ cap. **Flaw:** empty state is a 48sp 🔔 emoji (`NotificationsScreen.kt:139`); achievement icons are emoji from data. |
| Web | **Good** | Same content. **Flaw:** emoji icons; badge counts *all* achievements, not unread (Android computes unread via `seen_achievements` prefs — web badge shows total count, `app.js:698-704` — **logic parity gap**). |

### Admin
| Platform | Verdict | Notes |
|---|---|---|
| Android | **Good** | Stat cards, 7-day bar chart, delete-user with confirmation, "You" badge. **Flaw:** no search/filter — **web has search, Android doesn't** (parity gap). |
| Web | **Good** | Sortable table (but no `aria-sort`), search, broadcast. **Flaw:** delete uses `confirm()`; sort indicators are CSS triangles + text glyphs. |

---

## 3. Interaction Patterns — **5/10**

| Pattern | Android | Web | Verdict |
|---|---|---|---|
| Loading feedback | **Full-screen blocking overlay** (`CoinTrackerApp.kt:64-71, 276`) on *every* save — including quick-action taps. Pulse-dots animation. | `is-processing` opacity on the tapped button only (`app.js:455-458`) | Android fails; web is right. Android should use per-button spinners / optimistic updates. |
| Delete | Swipe + UNDO snackbar | `confirm()` + no undo | Android exemplary; web must match |
| Destructive confirm | Proper `AlertDialog`s with danger-colored confirm | Native `confirm()` (browser chrome, inconsistent with glass aesthetic) | Web should use styled modal |
| Touch targets | 32–36dp violations: edit icon (`HistoryScreen.kt:299`), pagination pills/arrows (`HistoryScreen.kt:346,434`), category delete (`SettingsScreen.kt:300,346`), analytics clear (`AnalyticsScreen.kt:125`) | Mobile nav ~48px OK; `.delete-btn` ~28px tall (`style.css:494-499`); `.chip` ~32px tall (`style.css:394-403`) | Both below floor in places |
| Haptics | None (`LocalHapticFeedback` unused) | N/A | Android gap |
| Press feedback | M3 ripple on buttons; GlassCard clickable has no ripple (plain `clickable`, `GlassCard.kt:35`) | `:active` scale on quick-btn/donation-card | Partial |
| Motion | Infinite animated gradient (10–12s) + pulse dots — decorative, no meaning, **no reduced-motion** | Animated gradient (15s) + hover lift/glow on quick-btn/donation-card/btn-text — **no reduced-motion** | **Block-list fail** (motion with no meaning) |
| Escape routes | `ConflictDialog` has `onDismissRequest = {}` (`CoinTrackerApp.kt:97`) — **non-dismissable, no back/escape** | Modals: no Escape key, no click-outside, no focus trap | Both fail |
| Feedback timing | Snackbars/toasts 3s auto-dismiss | Same | OK |
| Permission request | `POST_NOTIFICATIONS` requested at app launch with no context (`CoinTrackerApp.kt:161`) | N/A | Anti-pattern — should be contextual |

---

## 4. Information Architecture & Content Prioritization — **6/10**

**Good:**
- 4 top-level destinations on Android bottom nav (Dashboard/Analytics/History/Settings) — within the ≤5 limit, labeled with icons.
- Dashboard prioritizes correctly: balance → stats → actions → forms.
- History's filter card + pagination is a coherent sub-IA.

**Gaps:**
- **Notifications placement differs across platforms**: Android = top-bar bell icon (not in bottom nav); Web = 5th bottom-nav item + sidebar item. Users get different mental models per platform.
- **Profile switching differs**: Android = avatar dropdown menu; Web = sidebar `<select>`. Two different patterns for the same action.
- **Admin reachability differs**: Android = profile menu item; Web = sidebar link + separate page.
- Web mobile bottom nav has 5 items — at the Material limit; adding anything forces a redesign.
- Settings is a single long scroll of 7 equal-weight cards on both platforms — no grouping hierarchy (Data vs. Personalization vs. Danger).
- Web heading hierarchy skips h2 entirely (h1 page titles → h3 card titles, `index.html:90,127,135`).

---

## 5. Accessibility — **2/10 (FAIL)**

### Block-list / skill-floor failures
| Finding | Location | Severity |
|---|---|---|
| **Emoji as structural icons** — nav (📊📈📋🔔⚙️👑📤), theme (🌙☀️), achievements (💰📈🏦👑🛡🔥), alerts (🎉🔥⚡💪⏰🏆), buttons (🗑⬇⬆), card titles (🎯⚡📂🛒💾⚠️🗑), empty states (🔔🏆), sync banners (📴🛡⬆), conflict dialog (⚠️📱☁️). Screen readers announce emoji names inconsistently and noisily. | `index.html:49-53,74,78,137,173,304,329,340,366,379,392,401,410`; `app.js:52-58,268-281`; `login.js:71-76`; `admin.js:92-100`; `DashboardScreen.kt:363,388`; `NotificationsScreen.kt:139,225`; `CoinTrackerApp.kt:76-78,99,107,115,191`; `LoginScreen.kt:48` | **Block-list fail** |
| **No focus-visible styles on web**; `outline: none` on login inputs (`login.css:112`) with only a border-color change | web | Critical |
| **Modals are not dialogs**: no `role="dialog"`, no `aria-modal`, no focus trap, no Escape, no focus return, close is a `<span>` (`index.html:454,493,521,539`; `app.js:1105-1106`) | web | Critical |
| **Canvas charts invisible to screen readers** — no semantics, no text alternative, no data table | `AnalyticsScreen.kt:147-162,257-263`; `index.html:225-238` | Critical |
| **Toasts have no `aria-live`** — success/error feedback is silent to SR users | `app.js:1108-1114`; `login.js:211-217`; `admin.js:408-413` | High |
| **Disabled-but-clickable pickers** on Android — TalkBack announces "disabled" for interactive fields | `DashboardScreen.kt:263-275,310-322` | High |
| **Contrast failure — web balance card**: white text on emerald `#10b981` = **2.53:1** (needs 4.5:1); `goal-estimate-text` 13px white@0.9 on green ≈ 2.3:1. The blue end `#3b82f6` = 3.69:1 (fails normal text, passes large only). | `style.css:239-265` | Critical |
| **Small text on web**: 10px badge, 10px mobile-nav-label, 11px table headers, 12px stat-labels — below readable floor | `style.css:145,662,335,232` | High |
| **No skip link, no `<main>` landmark** on web | `index.html` | High |
| **No `aria-label` on icon-only buttons** (theme toggle, delete 🗑, modal close) | web | High |
| **No `aria-sort`** on sortable admin headers | `admin.html:87-91` | Medium |
| **No reduced-motion support** (`prefers-reduced-motion` absent) while both platforms run infinite background animations | both | **Block-list fail** |
| **No `aria-live` on sync banners / no `accessibilityLiveRegion`** | both | Medium |
| Android: several icons with `contentDescription = null` (ArrowDropDown, DateRange, Close, Refresh, Check, alert icons, sync icons) | `DashboardScreen.kt:269,316`; `AnalyticsScreen.kt:120,126`; `HistoryScreen.kt:192,196`; `AdminScreen.kt:42`; `NotificationsScreen.kt:169`; `CoinTrackerApp.kt:84,204` | Medium |

**Passing:** Android uses `sp` units (scales with font size); web has real `<label for>` pairs; login has `autocomplete` attributes; Android destructive dialogs are proper `AlertDialog`s.

---

## 6. Responsive / Adaptive Behavior — **5/10**

| Area | Web | Android |
|---|---|---|
| Breakpoints | 992 / 768 / 480 (`style.css:673,680,762`) — systematic | N/A (single phone layout) |
| Mobile nav | Sidebar→top bar + hamburger + 5-item bottom nav at ≤768 | Fixed 4-item `NavigationBar` |
| Viewport units | `min-height: 100vh` (`style.css:685`; `login.css:36`) — should be `100dvh` (URL-bar overlap on mobile) | N/A |
| Tables on mobile | History table scrolls horizontally (`style.css:513`) — Android uses cards (better) | Card list — good |
| Landscape | No landscape-specific handling on either | No landscape handling |
| Tablet / large screens | Desktop layout is fine (sidebar + content) | **No adaptive layouts** — no window-size classes; content stretches full-width on tablets |
| Safe areas | No `env(safe-area-inset-*)` padding for the fixed bottom nav on notched phones | `statusBarsPadding()` on top bar; bottom nav has no `navigationBarsPadding()` — content inset handled by Scaffold `contentWindowInsets = WindowInsets(0,0,0,0)` then `padding(ip)` — actually the Scaffold padding covers it. OK. |
| `touch-action: manipulation` | Absent (minor; modern viewports don't have 300ms delay) | N/A |

---

## 7. Platform Conventions — **4/10**

### Android (Material 3)
- **Uses M3 components correctly**: `Scaffold`, `NavigationBar`, `FilterChip`, `ModalBottomSheet`, `DateRangePicker`, `SwipeToDismissBox`, `AlertDialog` — the component vocabulary is right.
- **But the theme is a web port**: `background = Color.Transparent` to show a web gradient (`Theme.kt:14,25`), glass surfaces with alpha instead of M3 tonal surfaces, web-named color tokens, 2-style typography. No dynamic color (Material You). No `shapes` customization (defaults everywhere — the "untouched framework defaults" tell). The result is "web design rendered in Compose", which fights M3's elevation/state-layer system.
- **No adaptive/tablet support** (see §6).
- **No haptics**, no password toggle, permission requested without context.

### Web (PWA / progressive enhancement)
- **Not a PWA**: no `manifest.json`, no service worker, no offline support, no `theme-color` meta, no apple-touch-icon, no installability — while the Android app has a full offline cache + sync. The web app is a thin client that blanks out offline.
- **No `<noscript>` fallback**, no meta description.
- **Progressive enhancement is inverted**: the app is entirely JS-rendered (Firebase modules in `<head>` render-blocking, `index.html:24`; Chart.js CDN render-blocking, `index.html:24`).
- **Good:** theme-flash prevention inline script on all 3 pages; `display=swap` on Google Fonts.

---

## 8. Design Token Drift Risk — **2/10**

The token system is the single biggest structural risk in this codebase:

| Drift vector | Evidence |
|---|---|
| **Gradient duplicated 3×** | `Color.kt:6-15` (Android) + `style.css:20,36` + `login.css:10,22` — three independent copies of the same 8 hex values. Change one, the others drift silently. |
| **Android imports web tokens** | `WebPrimary`, `WebSuccess`, `WebDanger`, `WebPrimaryDark`… (`Color.kt:18-25`) — the native app's identity is literally "the web's colors". |
| **Hardcoded hex in Android screens** | 55 literals bypassing the theme: `AnalyticsScreen.kt:136-138,254-255`, `SettingsScreen.kt:181,196,247,286,332`, `NotificationsScreen.kt:47-62`, `DashboardScreen.kt:69-71,153`, `CoinTrackerApp.kt:76-78,105-123,186,224`. |
| **Chart palette duplicated** | Android `AnalyticsScreen.kt:254-255` and web `app.js:1054` — same 5-color array, two copies. |
| **Amber `#f59e0b` scattered** | Android (badge, ETA, best-week, achievements) + web CSS (coin-icon, badge, best-week) — ~10 occurrences, no token. |
| **No spacing/radius/type tokens** on either platform | All literals. |
| **Web CSS vars are color-only** | `:root` defines colors but no spacing/radius/shadow/type tokens (`style.css:8-22`). |
| **Dark-mode pairs duplicated** | `Theme.kt:10-19` + `style.css:24-37` + `login.css:13-23`. |

**Recommended fix:** a single `design-tokens.json` (or shared `tokens.md`) as source of truth; Android consumes via Compose `Color`/`Dp`/`TextStyle` objects generated from it; web consumes via CSS custom properties generated from it. CI check that no raw hex appears in `ui/screens/` or `web/public/css/`.

---

## 9. Component Inventory & Parity — **6/10**

| Component | Android | Web | Parity |
|---|---|---|---|
| Balance card | GlassCard | Gradient card | ✗ visual mismatch |
| Quick actions | 2-col buttons | Grid of lift-on-hover cards | ✗ |
| Add/Spend forms | GlassCard + disabled-picker hack | Card + real `<select>` | ✗ (web better) |
| Stats row | 3 GlassCards | 3 stat-cards | ✓ |
| Period chips | M3 FilterChip | `.chip` | ✓ |
| Charts | Custom Canvas | Chart.js | ~ (both inaccessible) |
| History list | Swipe cards | Table | ✗ (Android better on mobile) |
| Pagination | Windowed pills | Prev/Next + numbers | ✓ |
| Edit transaction | AlertDialog | Modal | ✓ |
| Delete transaction | Swipe + **UNDO** | `confirm()` + **no undo** | ✗ **gap** |
| Notifications | Screen (bell in top bar) | Page (nav item) | ✗ IA differs |
| Settings | GlassCards | Cards | ✓ |
| Admin | Screen (no search) | Page (search + sort) | ✗ **gap** |
| Login | GlassCard (no confirm-pw) | auth-card (confirm-pw) | ✗ **gap** |
| Theme toggle | Emoji icon | Emoji button | ✓ (both wrong) |
| Profile switch | Avatar dropdown | Sidebar select | ✗ |
| Support/donation | AlertDialog | Modal | ✓ |
| Unread badge | Unread-aware (prefs) | Counts all achievements | ✗ **logic gap** |

**Missing on both:** empty-state actions (History's "No transactions found" has no CTA), loading skeletons, password visibility toggle, undo for quick-action/category deletes, confirmation for quick-action delete.

---

## Block-List Compliance

| Tell | Android | Web | Status |
|---|---|---|---|
| Purple-to-blue/pink gradient hero/background | ✅ present (`CoinTrackerApp.kt:42-49`) | ✅ present (`style.css:20,36`) | **FAIL** |
| Same font for heading/body (Inter/SansSerif unpaired) | ✅ present (`Type.kt:11,17`) | ✅ present (`style.css:43`) | **FAIL** |
| 3 identical icon-in-circle cards | ⚠️ 3 identical stat cards (data, not icon) | ⚠️ same | Borderline |
| Gradient text | — | — | Pass |
| Full-viewport centered hero | ✅ login (`LoginScreen.kt:54`) | ✅ login (`login.css:36`) | **FAIL** |
| Fixed IA hero→features→… | N/A (app) | N/A | Pass |
| Emoji as structural icon/badge | ✅ present | ✅ present | **FAIL** |
| Gradient pill buttons | — | — | Pass (chips are solid) |
| Untouched framework shadow/radius defaults | ✅ M3 shapes untouched; GlassCard hardcodes 20dp | ✅ mixed radii, no scale | **FAIL** |
| Generic AI copy / fabricated stats | — | — | Pass |
| Bounce/scale on every hover | ⚠️ infinite gradient + pulse | ✅ hover lift/glow everywhere | **FAIL** (web) / partial (Android) |
| Loud saturated accent everywhere | ✅ | ✅ | **FAIL** |

**6 of 12 block-list tells present on at least one platform.**

---

## Scorecard

| Category | Score | Rationale |
|---|---|---|
| 1. Visual design system consistency | **3/10** | Glass language consistent, but gradient aesthetic, unpaired type, no scales, token bypass |
| 2. Screen-by-screen UX | **6/10** | Feature-complete, strong History/undo, but blocking loading, snackbar-only errors, inaccessible charts |
| 3. Interaction patterns | **5/10** | Undo + native pickers good; blocking overlay, confirm(), small targets, no reduced-motion bad |
| 4. IA & content prioritization | **6/10** | Clean 4–5 destinations; cross-platform IA drift, flat Settings |
| 5. Accessibility | **2/10** | Emoji icons, no focus styles, non-dialog modals, invisible charts, contrast fail — **floor FAIL** |
| 6. Responsive/adaptive | **5/10** | Web breakpoints OK; no dvh, no landscape, no tablet layouts on Android |
| 7. Platform conventions | **4/10** | M3 components but web-port theme; web not a PWA |
| 8. Token drift risk | **2/10** | 3× gradient copies, web-named tokens in Android, 55 hardcoded hex |
| 9. Component parity | **6/10** | Strong feature parity; undo, confirm-pw, admin search, unread badge differ |
| **Overall** | **4.3/10** | |

---

## Prioritized Action Items

### P0 — Accessibility floor (do first, blocks everything)
1. **Replace emoji-as-icons with a real icon set on both platforms.** Web: inline SVG (Lucide/Heroicons) for nav, theme, delete, card titles, alerts, achievements. Android: Material Icons for all — including achievement icons (map emoji strings in data to `ImageVector`).
2. **Web modals → proper dialogs**: `role="dialog"`, `aria-modal="true"`, focus trap, Escape-to-close, focus return to trigger, real `<button>` close. (`index.html:450-554`, `app.js:1105-1106`)
3. **Add `prefers-reduced-motion` support** (web: kill gradient/hover animations; Android: gate `AnimatedGradientBackground`/`PulseDots` behind `LocalReduceMotion`).
4. **Fix web balance-card contrast**: solid surface + dark text, or keep gradient but use `#1e293b` text and ≥4.5:1 pairs. (`style.css:239-265`)
5. **Add `aria-live="polite"` to toasts** on all 3 web pages; `accessibilityLiveRegion` on Android snackbar/sync banner.
6. **Charts**: add text summaries (web: visually-hidden table or `aria-label` on canvas; Android: `semantics` with contentDescription summarizing key figures).
7. **Web focus management**: `:focus-visible` styles globally; remove `outline: none` from login inputs; add skip link + `<main>` landmark.
8. **Android pickers**: replace disabled-TextField hack with a real read-only field or `ExposedDropdownMenuBox` (`DashboardScreen.kt:263-275,310-322`).

### P1 — Interaction & platform conventions
9. **Kill the full-screen `LoadingOverlay` on every save** (Android). Use per-button spinners / optimistic updates; keep the overlay only for initial load. (`CoinTrackerApp.kt:276`)
10. **Web delete → styled modal + UNDO snackbar** to match Android. (`app.js:808-816`)
11. **Replace web `confirm()` calls** (delete profile, delete all, delete user) with the existing modal system.
12. **Touch targets ≥48dp**: Android edit icon (36→48), pagination pills/arrows (36→48), category delete (32→48), analytics clear (32→48); web `.delete-btn` (~28→44+), `.chip` (~32→44+).
13. **Android: contextual notification permission** — request after first achievement, not at launch. (`CoinTrackerApp.kt:161`)
14. **Android: password visibility toggle** on login + settings delete-account; add confirm-password to Android register (match web).
15. **Make `ConflictDialog` dismissable** with a neutral "decide later" path. (`CoinTrackerApp.kt:97`)

### P2 — Design system & tokens
16. **Create a single token source of truth** (`design-tokens.json`): colors (light+dark), spacing (4/8dp scale), radii, shadows, type scale. Generate Compose objects + CSS vars from it. Enforce no raw hex in `ui/screens/` / `web/public/css/`.
17. **Replace the animated gradient with a restrained surface system** on both platforms (solid desaturated background + one accent). This is the flagship visual change.
18. **Type pairing**: pick a display face + body face (e.g., Sora/Space Grotesk + Inter, or a serif display for the "coin ledger" feel); define full M3 type roles on Android; type tokens on web.
19. **Reserve the accent**: `#3b82f6` only for primary actions/active states; success/danger only for semantic values; desaturate 10–20%.
20. **Android: adopt real M3 theming** — opaque tonal surfaces, dynamic color, custom shapes scale; stop importing `Web*` tokens into the native theme.

### P3 — Parity & IA
21. **Unify Notifications placement** (both platforms: same nav position) and **profile switching** (same control pattern).
22. **Web: add admin search parity to Android admin**; **Android: add unread-aware badge logic parity to web** (web currently counts all achievements).
23. **Web: card-list history on mobile** instead of horizontal-scroll table.
24. **Web: PWA baseline** — manifest, service worker, offline fallback message, `theme-color`, `100dvh`.
25. **Android: adaptive layouts** — window-size classes, max content width on tablets.
26. **Empty states with actions** (History: "Add your first transaction" CTA; Analytics charts: "No data for this period" message on web).

---

*Audit performed against `ui-ux-pro-max` skill floor (contrast 4.5:1, touch ≥44/48, reduced-motion, focus states, SR semantics) and the block list. Findings recorded in `WORKFLOW_STATE.md`; Next Agent: **implementor** with the P0–P3 punch list above.*