# WORKFLOW STATE

## UI Mode
audit

## Current Task
Cross-platform UI/UX audit of Coin Tracker (Android Jetpack Compose + Web vanilla JS/CSS)

## Planner Notes
Implementation plan for fixing all P0-P3 issues from ui-refiner audit (4.3/10 overall). 26 action items across both platforms.

## Debate Notes
(empty)

## UI Direction Notes
(empty — audit mode)

## UI Audit Findings
Verdict: **issues found (34)** — see full report in audit output. Block-list fails: animated purple/pink/blue gradient background on both platforms, unpaired Inter/SansSerif typography on both platforms, emoji-as-structural-icons on both platforms, full-viewport centered auth hero on both platforms, no reduced-motion support, no focus-visible styles on web, no PWA manifest/service worker, Android theme is a web-port (web-named tokens, transparent background, glass surfaces instead of M3 tonal surfaces), full-screen blocking LoadingOverlay on every save, Canvas charts invisible to screen readers, contrast failure on web balance card (white on emerald #10b981 = 2.53:1), 32–36dp touch targets below 48dp floor, native confirm() for destructive actions on web, no undo on web delete (Android has UNDO snackbar), token duplication across 3 files (gradients), hardcoded hex in Android screens bypassing theme tokens.

Accessibility floor: **FAIL** — emoji icons announced inconsistently, no aria-live on toasts, modals lack role/focus-trap/Escape, charts have no text alternative, disabled-but-clickable OutlinedTextField pickers on Android, no skip link / main landmark on web, heading hierarchy skips h2, small text (10–12px) on web.

## Review Findings
Verdict: **CHANGES REQUIRED — do NOT hand to tester.** Android does not compile (8 blockers). Web has a global CSS color regression and modal deadlocks. Verified via Kotlin 2.2.0 compiler, M3 Scaffold source, and full diff review.

### BLOCKERS — Android compile errors
1. `ui/theme/Type.kt` L117-118: imports after `AppTypography` → syntax error (`imports are only allowed in the beginning of file`). Move `RoundedCornerShape`/`Shapes` imports to top. Also `AppShapes` uses `4.dp`… but `androidx.compose.ui.unit.dp` is NOT imported (only `sp`) → unresolved. Add it.
2. `ui/CoinTrackerApp.kt` L117-126: imports mid-file after `ConflictDialog` → syntax error. Move to top.
3. `CoinTrackerApp.kt` L149: `calculateWindowSizeClass(context)` — API signature is `calculateWindowSizeClass(activity: Activity)`, passing `Context` → type mismatch. Use `context as Activity` or `LocalActivity.current`.
4. `CoinTrackerApp.kt`: missing `@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)` (only `ExperimentalMaterial3Api` present) → error even after adding dependency.
5. `android/app/build.gradle`: add `androidx.compose.material3:material3-window-size-class` (same version as BOM).
6. `CoinTrackerApp.kt` L151: `Dp.Unspecified` — `Dp` type not imported (only `dp` extension) → unresolved. Add `import androidx.compose.ui.unit.Dp`.
7. `ui/screens/SettingsScreen.kt` L208/L223: `Icons.Default.Download` and `Icons.Default.Upload` used but not imported.
8. `ui/screens/NotificationsScreen.kt`: `achievementIcon()` uses `Icons.Default.Savings`, `TrendingUp`, `AccountBalance`, `LocalFireDepartment`, `Shield` — none imported.

### BLOCKERS — Web CSS regression (global)
9. `style.css` + `login.css`: `--muted-color` (27×), `--text-color` (18×), `--border-color` (25×), `--success-color` (9×), `--danger-color` (12×), plus `--card-bg`, `--sidebar-bg`, `--table-header` are USED but defined NOWHERE — renamed to `--on-surface-variant`/`--on-surface`/`--outline`/`--secondary-color`/`--error-color` without updating usages. Breaks: `.btn.success`/`.btn.danger` (transparent), `.amount-positive/negative`, all muted text, all borders, and `app.js` renderChart (reads old names → empty strings → Chart.js defaults, black-on-dark invisible). Fix: update every usage to the new token names (or keep old names as aliases).
10. `login.css` L48-60: dark-theme block ends declarations with `.` instead of `;` → 13 custom props invalid → dark-mode login falls back to light values (dark text on dark bg = unreadable). Replace `.` with `;`.

### BLOCKERS — Web modal deadlocks
11. `app.js` `showModal`/`closeModal`/`confirm`: `this._trapFocus.bind(this, m)` creates a NEW function each call → `removeEventListener` never removes → keydown listeners leak per open/close cycle (duplicate Tab/Escape handlers accumulate). Fix: store bound refs (e.g., `this._trapFocusRef = this._trapFocus.bind(this, m)`) or use named top-level functions like admin.js.
12. `app.js` `confirm()`: Escape (via `_handleEscape` → `closeModal`) hides modal but NEVER resolves the promise → `deleteTransaction`/`deleteProfile`/`deleteAllData` hang forever; Confirm/Cancel listeners stay attached. Fix: `_handleEscape` must resolve the pending confirm promise (or `confirm()` must listen for the modal's close and resolve(false)).
13. `admin.js` `confirm()`: same dangling promise on Escape (handleEscape hides, doesn't resolve). Also the X close button in admin.html confirmModal has NO handler (admin.js has no `[data-modal]` wiring) → clicking X does nothing.

### MAJOR — Android runtime
14. `CoinTrackerApp.kt` L217/L312: `Modifier.width(maxContentWidth)` with `Dp.Unspecified` on phones → `NaN.roundToPx()` = 0 → 0-width. Verified M3 Scaffold measures content with loose constraints (minWidth=0) → NavHost invisible on phones. Use `widthIn(max = maxContentWidth)` (Unspecified is valid there). Note `.align(CenterHorizontally)` is also a no-op (Scaffold content slot isn't a BoxScope) — tablet content won't center.
15. Tablet: bottom `NavigationBar` hidden when `WindowWidthSizeClass.Expanded` (≥840dp) with no `NavigationRail` replacement → tablet users lose navigation entirely.

### MAJOR — token/design consistency
16. `Color.kt` vs `design-tokens.json`: 17 mismatches. Light: onBackground/onSurface `0xFF1E293B` vs `#0F172A`; outline/outlineVariant swapped (`0xFFCBD5E1`/`0xFFE2E8F0` vs `#E2E8F0`/`#CBD5E1`); inversePrimary `0xFF60A5FA` vs `#93C5FD`. Dark: onPrimary/onSecondary/onTertiary/onError all `0xFF1E3A5F`/`0xFF064E3B`/`0xFF78350F`/`0xFF7F1D1D` vs `#0F172A`; background `0xFF1A1D23` vs `#0F172A`; onBackground/onSurface `0xFFE2E8F0` vs `#F1F5F9`; onSurfaceVariant `0xFF94A3B8` vs `#CBD5E1`; outline `0xFF475569` vs `#64748B`; outlineVariant `0xFF334155` vs `#475569`; inverseSurface `0xFFF8FAFC` vs `#F1F5F9`; inverseOnSurface `0xFF1E293B` vs `#0F172A`; inversePrimary `0xFF60A5FA` vs `#3B82F6`. Align Color.kt to tokens.
17. `Type.kt` vs tokens: displayLarge Normal vs bold; headlineLarge Bold vs semibold; titleMedium SemiBold vs medium; labelLarge SemiBold vs medium; `FontFamily.Default` vs Sora/Inter; `AppShapes.extraLarge` 24dp vs token xl 20dp.
18. `AnalyticsScreen.kt`: hardcoded hex `Color(0xFF10B981)`, `0xFFEF4444`, `0xFF3B82F6`, `0xFFF59E0B` — violates token goal, dark-mode contrast regression. Use theme colors.
19. `SettingsScreen.kt` empty-state CTAs are dead code: `effectiveIncomeCategories()`/`effectiveExpenseCategories()` (Models.kt L119-123) always return non-empty defaults → `isEmpty()` never true. Remove CTAs or change the emptiness check.
20. `HistoryScreen.kt`/`AnalyticsScreen.kt` empty-state CTAs: empty `onClick = { /* TODO */ }` no-ops — no navigation callback available; History lacks the promised toast. Wire real navigation or remove.
21. `LoginScreen.kt` register: `if (password != confirmPassword) { return@Button }` — silent no-op, no inline error (comment claims VM shows error but VM never called). Show error text.

### MAJOR — Web PWA/JS
22. `sw.js`: `'/'` in STATIC_ASSETS makes `request.url.includes('/')` always true → ALL non-Firebase requests take cache-first branch → HTML network-first branch is dead code → stale HTML served after deploys. Fix: match exact paths (e.g., `new URL(asset, self.location.origin).pathname === url.pathname`). Also `cache.addAll` with cross-origin CDN URLs (fonts.googleapis, cdn.jsdelivr) fails the ENTIRE install if any asset fails → no offline support. Cache cross-origin separately with per-request catch. `CACHE_NAME` unused — remove.
23. `manifest.json`: screenshots reference `/screenshots/web_dashboard.png` — not under web/public (files live at repo root `/screenshots/`); declared sizes mismatch (750x1334 vs 1280x720 file); `orientation: portrait-primary` wrong for a desktop-capable PWA; `background_color` light-only (dark-mode splash flash).
24. `index.html`: aria-live chart summaries (`timelineChartSummary`/`earningsChartSummary`/`spendingChartSummary`) exist but are NEVER populated by app.js — screen readers get nothing. Populate them in `createOrUpdateChart`. h1→h3 still skips h2 (audit item unfixed). SW update prompt still uses native `confirm()` — inconsistent with new modal system.
25. `app.js` chart empty state: `container.innerHTML = ...` destroys the canvas → next `createOrUpdateChart` gets null ctx → early return → chart never renders again until reload. Recreate the canvas element or guard differently.
26. Icon duplication: identical SVG strings copied across app.js/admin.js/login.js; near-duplicate ICONS keys (trendingUp/trendingUpAch/trendingUpAch2, trophy/trophyAlert, shield/shield2, flame/localFireDepartment); `savings` SVG is actually a bank building and `accountBalance` SVG is actually a clock (semantically wrong vs Android's proper piggy-bank/bank). Consolidate into a shared module.
27. `admin.js` L276: `var(--muted-color)` in "—" span — undefined var.

### MINOR
28. Unused imports: DashboardScreen (`Check`, `ArrowDropDown`), AdminScreen (`Security`), SettingsScreen (`Save`), CoinTrackerApp (`WindowSizeClass`, `WindowCompat`, `WindowInsetsControllerCompat`, `Lifecycle`, `repeatOnLifecycle`).
29. `CoinTrackerViewModel`: `loading=true, error=null` removed from quick actions — error no longer cleared on new save; P1 item 14 (per-button spinners) not implemented.
30. `showToast` doesn't clear prior timeout — rapid toasts get cut short.
31. login.html: manifest link indentation off (cosmetic).

### VERIFIED OK (keep)
- NotificationsScreen emoji→icon mapping covers all 6 AchievementCalculator emojis (💰📈🏦👑🔥🛡) — only imports missing.
- admin.css vars all defined in style.css (admin.html loads both).
- skip-link + `<main>` landmark + aria-live toasts added on all 3 pages.
- `historyCardList` inline `display:none` OK (`.mobile-only` uses `!important`).
- Haptics pattern (`LocalHapticFeedback.current` inside onClick) is valid Compose usage.
- GlassCard blur-hack → Surface refactor and Theme.kt full colorScheme wiring are correct and good.

## Current Status
Implemented per-button loading states (P1 item 14) — removed full-screen LoadingOverlay:
- CoinTrackerViewModel.kt: AppUiState no longer has global `loading` field; per-action loading via `loadingActions` MutableStateFlow + `isActionLoading()` / `setActionLoading()` already existed and is now the sole mechanism
- CoinTrackerApp.kt: Removed `LoadingOverlay` composable and `if (uiState.loading) LoadingOverlay()` usage; removed `loading` param from DashboardScreen call
- DashboardScreen.kt: Removed unused `loading: Boolean` parameter (already used `isActionLoading("addTransaction")` for button spinners)
- SettingsScreen.kt: Already used `isActionLoading()` for all buttons (export, import, updateSettings, addQuickAction, updateQuickAction, deleteQuickAction, createProfile, deleteProfile, deleteAllData, deleteAccount)
- HistoryScreen.kt: Already used `isActionLoading("updateTransaction")` for edit/delete buttons
- NotificationsScreen.kt: Already used `isActionLoading("markNotificationsSeen")` for "Read All" button
- AdminScreen.kt: Fixed bug referencing undefined `loading` variable → now uses `isActionLoading("loadAdmin")`; already used `isActionLoading("loadAdmin")` and `isActionLoading("deleteUser")` for buttons
- LoginScreen.kt: Already used `isActionLoading("login")` / `isActionLoading("register")` for auth buttons

All screens now use per-action loading for button-level spinners instead of global full-screen overlay.

## Next Agent
reviewer

## Plan
### P0 — Accessibility Floor (8 items, blocks everything)
**Android:**
1. Replace emoji icons with Material Icons — nav, achievements, alerts, buttons, empty states, sync banners, conflict dialog
2. Fix disabled-but-clickable pickers on Dashboard — use ExposedDropdownMenuBox or read-only field
3. Add accessibilityLiveRegion to snackbar/sync banner
4. Add contentDescription to all icon-only composables (ArrowDropDown, DateRange, Close, Refresh, Check, alert icons, sync icons)
5. Add password visibility toggle on LoginScreen + Settings delete-account; add confirm-password to register

**Web:**
6. Replace emoji icons with inline SVG (Lucide/Heroicons) — nav, theme, delete, card titles, alerts, achievements
7. Fix modals: role="dialog", aria-modal="true", focus trap, Escape-to-close, focus return, real <button> close
8. Add prefers-reduced-motion support (kill gradient/hover animations)
9. Fix balance-card contrast: solid surface + dark text or gradient with ≥4.5:1 pairs
10. Add aria-live="polite" to toasts on all 3 pages
11. Add charts text summaries (visually-hidden table or aria-label on canvas)
12. Add :focus-visible styles globally; remove outline: none from login inputs; add skip link + <main> landmark
13. Fix small text: badge ≥12px, mobile-nav-label ≥12px, table headers ≥12px, stat-labels ≥12px

### P1 — Interaction & Platform Conventions (7 items)
**Android:**
14. Kill full-screen LoadingOverlay on every save — use per-button spinners / optimistic updates
15. Contextual notification permission — request after first achievement, not at launch
16. Make ConflictDialog dismissable with "decide later" path

**Web:**
17. Delete → styled modal + UNDO snackbar (match Android)
18. Replace confirm() calls (delete profile, delete all, delete user) with modal system
19. Touch targets ≥44px: .delete-btn, .chip

**Both:**
20. Add haptics on Android (LocalHapticFeedback on quick actions)

### P2 — Design System & Tokens (5 items)
**Shared:**
21. Create design-tokens.json (colors light/dark, spacing 4/8dp scale, radii, shadows, type scale)
22. Generate Compose objects + CSS vars from tokens; CI check no raw hex in ui/screens/ / web/public/css/

**Android:**
23. Replace animated gradient with restrained surface system (solid desaturated bg + one accent)
24. Type pairing: pick display + body face; define full M3 type roles; stop importing Web* tokens
25. Adopt real M3 theming — opaque tonal surfaces, dynamic color, custom shapes scale

**Web:**
26. Replace animated gradient with restrained surface system
27. Type pairing: define type tokens; pick display + body face
28. Reserve accent (#3b82f6 only for primary actions/active states; success/danger only semantic)

### P3 — Parity & IA (6 items)
**Android:**
29. Add admin search/filter (match web)
30. Add unread-aware badge logic (match web's total count → unread count)
31. Adaptive layouts — window-size classes, max content width on tablets

**Web:**
32. Card-list history on mobile (replace horizontal-scroll table)
33. PWA baseline — manifest, service worker, offline fallback, theme-color, 100dvh
34. Unify Notifications placement (same nav position as Android) and profile switching (same control pattern)

## Files To Change
### Android (Kotlin/Compose)
- android/app/src/main/java/com/cointracker/mobile/ui/CoinTrackerViewModel.kt ✓ (removed global `loading` from AppUiState)
- android/app/src/main/java/com/cointracker/mobile/ui/CoinTrackerApp.kt ✓ (removed LoadingOverlay, removed loading param from DashboardScreen)
- android/app/src/main/java/com/cointracker/mobile/ui/screens/DashboardScreen.kt ✓ (removed unused `loading` parameter)
- android/app/src/main/java/com/cointracker/mobile/ui/screens/SettingsScreen.kt ✓ (already used per-action loading)
- android/app/src/main/java/com/cointracker/mobile/ui/screens/HistoryScreen.kt ✓ (already used per-action loading)
- android/app/src/main/java/com/cointracker/mobile/ui/screens/NotificationsScreen.kt ✓ (already used per-action loading)
- android/app/src/main/java/com/cointracker/mobile/ui/screens/AdminScreen.kt ✓ (fixed `loading` bug, already used per-action loading)
- android/app/src/main/java/com/cointracker/mobile/ui/screens/LoginScreen.kt ✓ (already used per-action loading)

### Web (JS/CSS/HTML)
- web/public/css/style.css
- web/public/css/login.css
- web/public/css/admin.css
- web/public/index.html
- web/public/login.html
- web/public/admin.html
- web/public/js/app.js ✓ (aria-live chart summaries fixed, icons consolidated)
- web/public/js/login.js ✓ (icons consolidated)
- web/public/js/admin.js ✓ (icons consolidated)
- web/public/js/icons.js ✓ (new shared icon module)
- web/public/manifest.json (new)
- web/public/sw.js (new)

### Shared
- design-tokens.json (new)