# Coin Tracker - Remaining Issues Fix Plan

**Generated:** 2026-09-12  
**Source:** Debugger analysis + Reviewer findings  
**Status:** Phase 1 (BLOCKERS) - Ready to implement

---

## Phase 1: BLOCKERS (Immediate - Must Fix Before Release)

### 1. Android Compile: `dp` extension missing in `Type.kt`
**File:** `android/app/src/main/java/com/cointracker/mobile/ui/theme/Type.kt:10`  
**Fix:** Add `import androidx.compose.ui.unit.dp` at top of file  
**Lines:** Add after line 7 (after `sp` import)

### 2. Web CSS Syntax Error: Periods instead of semicolons in `login.css`
**File:** `web/public/css/login.css:48-60`  
**Fix:** Replace all `.` with `;` in dark theme block (13 declarations)  
**Lines:** 48-60 (dark theme block)

### 3. Web Modal Deadlock (app.js): Escape doesn't resolve confirm promise
**File:** `web/public/js/app.js:1345-1356`  
**Fix:** In `_handleEscape`, when `id === "confirmModal"`, call `this._confirmResolver(false)` before `closeModal`  
**Lines:** 1345-1356

### 4. Web Modal X Button Dead (admin.js): No handler for `[data-modal]` close button
**File:** `web/public/js/admin.js` (missing handler) / `admin.html:118`  
**Fix:** Add event listener for `[data-modal]` buttons in admin.js `setupEventListeners` or add inline handler  
**Location:** Add to `setupEventListeners` function

---

## Phase 2: CRITICAL (This Week)

### 5. Design Token Drift: 17 color mismatches
**File:** `android/app/src/main/java/com/cointracker/mobile/ui/theme/Color.kt`  
**Fix:** Align all 34 colors with `design-tokens.json`  
**Key mismatches:**
- Light: `onBackground`/`onSurface` `0xFF1E293B` vs `#0F172A`
- Light: `outline`/`outlineVariant` swapped (`0xFFCBD5E1`/`0xFFE2E8F0` vs `#E2E8F0`/`#CBD5E1`)
- Dark: `onPrimary`/`onSecondary`/`onTertiary`/`onError` all `0xFF1E3A5F`/`0xFF064E3B`/`0xFF78350F`/`0xFF7F1D1D` vs `#0F172A`
- Dark: `background` `0xFF1A1D23` vs `#0F172A`
- Dark: `onBackground`/`onSurface` `0xFFE2E8F0` vs `#F1F5F9`
- Dark: `onSurfaceVariant` `0xFF94A3B8` vs `#CBD5E1`
- Dark: `outline` `0xFF475569` vs `#64748B`
- Dark: `outlineVariant` `0xFF334155` vs `#475569`
- Dark: `inverseSurface` `0xFFF8FAFC` vs `#F1F5F9`
- Dark: `inverseOnSurface` `0xFF1E293B` vs `#0F172A`
- Dark: `inversePrimary` `0xFF60A5FA` vs `#3B82F6`

### 6. Typography Not Token-Driven
**File:** `android/app/src/main/java/com/cointracker/mobile/ui/theme/Type.kt`  
**Fix:** 
- Change `FontFamily.Default` to Sora (display) / Inter (body) per tokens
- Fix font weights: `displayLarge` Normal→Bold, `headlineLarge` Bold→SemiBold, `titleMedium` SemiBold→Medium, `labelLarge` SemiBold→Medium
- Fix `extraLarge` 24dp → 20dp per tokens

### 7. Dead Empty-State CTAs (Settings)
**File:** `android/app/src/main/java/com/cointracker/mobile/ui/screens/SettingsScreen.kt:399-410, 499-510`  
**Fix:** `effectiveIncomeCategories()`/`effectiveExpenseCategories()` always return non-empty defaults → `isEmpty()` never true. Change check to `isCustomIncome`/`isCustomExpense` or remove CTAs.

### 8. No-Op Empty State Buttons (History & Analytics)
**Files:** 
- `HistoryScreen.kt:241-246` - `onClick = { /* TODO */ }`
- `AnalyticsScreen.kt:168-170, 186-188, 203-205` - `onClick = { /* Navigate to dashboard */ }`
**Fix:** Wire real navigation callback or remove buttons.

### 9. Silent Register Validation
**File:** `android/app/src/main/java/com/cointracker/mobile/ui/screens/LoginScreen.kt:140-144`  
**Fix:** Show inline error text when `password != confirmPassword` instead of silent `return@Button`

### 10. PWA Offline Broken
**File:** `web/public/sw.js:31-39`  
**Fix:** 
- Cache cross-origin CDN URLs separately with per-request catch
- `cache.addAll` fails entire install if any asset fails
- Remove unused `CACHE_NAME`

### 11. Dark Mode Splash Flash
**File:** `web/public/manifest.json:7`  
**Fix:** Add dark mode `background_color` or use CSS media query approach

### 13. SW Update Uses Native confirm()
**File:** `web/public/index.html:597`  
**Fix:** Replace native `confirm()` with custom modal system

---

## Phase 3: HIGH PRIORITY (Next Sprint)

### Accessibility
- Android: Add `contentDescription` to icon-only composables (ArrowDropDown, DateRange, Close, Refresh, Check, alert icons, sync icons)
- Android: Fix disabled-but-clickable `OutlinedTextField` pickers on Dashboard → use `ExposedDropdownMenuBox`
- Web: Touch targets ≥44px (`.delete-btn`, `.chip`, `.mobile-nav-label`)
- Web: Charts `aria-live` summaries populated in `createOrUpdateChart` - verify wired
- Cross-platform: Add haptics on Android quick actions

### Parity
- Notifications placement: Android top-bar bell vs Web bottom nav
- Profile switching: Android dropdown vs Web sidebar select

---

## Implementation Order

```
Week 1: Phase 1 (4 BLOCKERS)
Week 2: Phase 2 items 5-9 (Token drift, Typography, Dead CTAs, No-op buttons, Silent register)
Week 3: Phase 2 items 10-13 (PWA, Manifest, SW update)
Week 4: Phase 3 (Accessibility, Parity)
```

---

## Files to Modify Summary

| File | Issues |
|------|--------|
| `android/app/src/main/java/com/cointracker/mobile/ui/theme/Type.kt` | 1, 6 |
| `android/app/src/main/java/com/cointracker/mobile/ui/theme/Color.kt` | 5 |
| `android/app/src/main/java/com/cointracker/mobile/ui/screens/SettingsScreen.kt` | 7 |
| `android/app/src/main/java/com/cointracker/mobile/ui/screens/HistoryScreen.kt` | 8 |
| `android/app/src/main/java/com/cointracker/mobile/ui/screens/AnalyticsScreen.kt` | 8 |
| `android/app/src/main/java/com/cointracker/mobile/ui/screens/LoginScreen.kt` | 9 |
| `web/public/css/login.css` | 2 |
| `web/public/js/app.js` | 3, 13 |
| `web/public/js/admin.js` | 4 |
| `web/public/sw.js` | 10 |
| `web/public/manifest.json` | 11 |
| `web/public/index.html` | 13 |

---

**Next Agent:** implementer (start with Phase 1 BLOCKERS)