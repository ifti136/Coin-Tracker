# Coin Tracker — Codebase Design Assessment Report

**Generated:** 2026-09-10  
**Scope:** Full cross-platform codebase (Android, Web, Desktop)  
**Architecture:** Shared Firebase/Firestore backend, platform-native frontends

---

## 1. Executive Summary

Coin Tracker is a well-structured cross-platform application for tracking in-game coin economies (primarily eFootball). The codebase demonstrates **strong architectural consistency** across three platforms sharing a single Firestore data model, with thoughtful offline-first design on Android and clean separation of concerns.

**Overall Design Quality: 8.5/10** — Solid foundation with clear patterns, minor inconsistencies to address.

---

## 2. Architecture Overview

### 2.1 Platform Structure

| Platform | Language/Framework | State Management | Backend |
|----------|-------------------|------------------|---------|
| **Android** | Kotlin + Jetpack Compose + Hilt DI | StateFlow + ViewModel | Firebase Auth + Firestore (offline persistence) |
| **Web** | Vanilla JS (ES Modules) + Chart.js | Class-based singleton (`CoinTrackerApp`) | Firebase Auth + Firestore SDK |
| **Desktop** | Python + PyQt5 + QtCharts | MVC-like (`MainWindow` + `OnlineCoinTracker`) | Firebase Admin SDK (optional) + local JSON |

### 2.2 Shared Data Model (Firestore)

```
users/{uid}
  username, role, created_at

user_data/{uid}
  last_active_profile
  profiles/{profileName}/
    transactions[] (id, date, amount, source, previous_balance)
    settings (goal, dark_mode, quick_actions[], income_categories[], expense_categories[])
    last_updated

app_config/broadcast
  message, set_by, set_at
```

**Strengths:**
- Single document per user (`user_data/{uid}`) — atomic reads/writes, no subcollection queries
- Snake_case field names consistently used across all platforms
- Synthetic email format (`username@cointracker.app`) preserves username/password UX without real emails
- Legacy Werkzeug password migration handled gracefully (Android only)

---

## 3. Platform-Specific Design Analysis

### 3.1 Android (Kotlin + Compose) — **Strongest Implementation**

#### Architecture Patterns
- **Clean Architecture** layers: `data/` (repository, models), `domain/` (calculators), `ui/` (ViewModel, screens, components)
- **Hilt DI** for dependency injection — `@Singleton` repositories, `@HiltViewModel`
- **Unidirectional data flow**: ViewModel exposes `StateFlow<AppUiState>`, screens observe via `collectAsStateWithLifecycle()`
- **Offline-first sync** via `SyncManager` — sophisticated conflict detection/resolution (7 result types)

#### Key Strengths
| Area | Implementation |
|------|----------------|
| **Sync Logic** | `SyncManager.compare()` handles 7 states: `Synced`, `DbOnly`, `UseCache`, `RestoreFromCache`, `CacheNewer`, `DbNewer`, `ConflictDetected`, `BothEmpty` |
| **Local Cache** | JSON files in `filesDir/safety_cache/{uid}/{profile}.json` with full `ProfileEnvelope` serialization |
| **Achievements** | Domain-calculated (`AchievementCalculator`), notifications on new unlocks |
| **Widget** | Glance-based home screen widget (`CoinTrackerWidget.kt`) |
| **Notifications** | WorkManager daily reminders + milestone notifications |
| **Type Safety** | Full Kotlin data classes, sealed classes for `SyncState`, `Result<T>` for fallible ops |

#### Minor Issues
- `StateFlows.kt` is a 1-line wrapper — could be inlined or removed
- Some screens (e.g., `DashboardScreen.kt` at 435 lines) are large; could extract composables
- `ProgressCardGenerator.kt` referenced but not reviewed — verify it exists

---

### 3.2 Web (Vanilla JS + Chart.js) — **Clean & Consistent**

#### Architecture Patterns
- **Single-class app** (`CoinTrackerApp`) — all state + logic in one ES module
- **Computed getters** for derived state (`balance`, `progress`, `estimatedDays`, `achievements`, etc.)
- **Snake_case field mapping** matches Android exactly (`previous_balance`, `is_positive`, `dark_mode`, `quick_actions`)
- **Event-driven UI** — `updateAllUI()` called after every mutation

#### Key Strengths
| Area | Implementation |
|------|----------------|
| **Data Parity** | Mirrors Android's `FirestoreRepository` logic: `estimatedDays` uses 7-day rate, `bestEarningWeek`, `sevenDayRate` |
| **Achievements** | Identical definitions to Android (`ACHIEVEMENT_DEFS` array with same check functions) |
| **Charts** | Chart.js for timeline (line), earnings/spending (doughnut) |
| **Responsive Design** | Mobile-first CSS with hamburger menu + bottom nav (768px breakpoint) |
| **Theme Persistence** | `localStorage` + `data-theme` attribute, no flash on load |
| **Admin Panel** | Separate `admin.html` + `admin.js` with sortable tables |

#### Minor Issues
- `app.js` is 1000+ lines — could split into modules (auth, analytics, history, settings)
- No TypeScript — runtime type safety gaps (e.g., `transaction.previous_balance` vs `previousBalance`)
- `login.js` duplicates default constants (`DEFAULT_QUICK_ACTIONS`, `defaultSettingsMap`) — should share

---

### 3.3 Desktop (Python + PyQt5) — **Functional but Divergent**

#### Architecture Patterns
- **MVC-like**: `OnlineCoinTracker` (model/data), `MainWindow` (view/controller)
- **Dual storage**: Firebase Admin SDK (optional) + local JSON fallback (`~/Documents/CoinTracker/{profile}.json`)
- **Custom painting** for quick action buttons (`ModernQuickActionButton.paintEvent`)

#### Key Strengths
| Area | Implementation |
|------|----------------|
| **Charts** | QtCharts integration (donut, bar, line) with theme-aware styling |
| **Theme System** | `LIGHT`/`DARK` dicts + `apply_modern_theme()` with comprehensive QSS |
| **Profile Switching** | Recreates `OnlineCoinTracker` instance per profile |
| **Context Menus** | Right-click history table for edit/delete |

#### Significant Divergences from Android/Web
| Aspect | Android/Web | Desktop |
|--------|-------------|---------|
| **Firestore Structure** | `user_data/{uid}/profiles/{name}` | `users/{uid}/profiles` (nested in user doc) |
| **Auth** | Firebase Auth (Email/Password) | **None** — uses hardcoded `user_id="default_user"` |
| **Field Names** | Snake_case (`previous_balance`, `is_positive`) | Mixed (`previous_balance` OK, but `is_positive` vs `isPositive` inconsistent) |
| **Quick Actions** | `is_positive` (snake_case) | `is_positive` (OK) but no icon field |
| **Categories** | `income_categories` / `expense_categories` | **Missing** — hardcoded in dialogs |
| **Achievements** | Full calculator | **Missing** |
| **Admin** | Full admin panel | **Missing** |
| **Offline Sync** | Sophisticated `SyncManager` | Simple fallback to local JSON |

#### Critical Issues
1. **No Authentication** — Desktop app has no login; uses fixed `user_id="default_user"`
2. **Different Firestore Path** — Writes to `users/{uid}/profiles` instead of `user_data/{uid}/profiles` → **data isolation from mobile/web**
3. **Missing Features** — No achievements, no admin, no custom categories, no import/export parity
4. **Firebase Admin SDK** — Requires service account (`firebase-key.json`); not suitable for distributed desktop apps (security risk)

---

## 4. Cross-Platform Consistency Assessment

### 4.1 Data Model Alignment

| Field | Android | Web | Desktop | Status |
|-------|---------|-----|---------|--------|
| `previous_balance` | ✅ | ✅ | ✅ | **Consistent** |
| `is_positive` | ✅ | ✅ | ✅ | **Consistent** |
| `dark_mode` | ✅ | ✅ | ✅ | **Consistent** |
| `quick_actions` | ✅ | ✅ | ✅ | **Consistent** |
| `income_categories` | ✅ | ✅ | ❌ | **Desktop missing** |
| `expense_categories` | ✅ | ✅ | ❌ | **Desktop missing** |
| `goal` | ✅ | ✅ | ✅ | **Consistent** |
| Firestore Path | `user_data/{uid}` | `user_data/{uid}` | `users/{uid}` | **Desktop diverges** |

### 4.2 Feature Parity Matrix

| Feature | Android | Web | Desktop |
|---------|---------|-----|---------|
| Login/Register | ✅ | ✅ | ❌ |
| Dashboard | ✅ | ✅ | ✅ |
| Quick Actions | ✅ | ✅ | ✅ |
| Add/Spend Coins | ✅ | ✅ | ✅ |
| Transaction History | ✅ | ✅ | ✅ |
| Analytics (Charts) | ✅ | ✅ | ✅ (QtCharts) |
| Goal Tracking | ✅ | ✅ | ✅ |
| Achievements | ✅ | ✅ | ❌ |
| Multiple Profiles | ✅ | ✅ | ✅ |
| JSON Export | ✅ | ✅ | ✅ |
| JSON Import | ❌ | ✅ | ✅ |
| Admin Panel | ✅ | ✅ | ❌ |
| Dark/Light Theme | ✅ | ✅ | ✅ |
| Offline Support | ✅ (Firestore + cache) | ❌ | ✅ (local only) |
| Firebase Sync | ✅ | ✅ | ⚠️ (Admin SDK) |
| Home Widget | ✅ | N/A | N/A |
| Notifications | ✅ | ⚠️ (in-app only) | ❌ |

---

## 5. Code Quality & Maintainability

### 5.1 Strengths
- **Consistent naming** across Android/Web (snake_case in Firestore, camelCase in code)
- **Single source of truth** for defaults in Android (`Models.kt`) — Web duplicates but mirrors
- **Comprehensive error handling** — `Result<T>` pattern on Android, try/catch + toasts on Web/Desktop
- **Theme systems** well-implemented on all platforms (CSS variables, Compose `ColorScheme`, PyQt QSS)
- **Responsive UI** on Web (mobile-first), adaptive on Android (Compose), fixed on Desktop

### 5.2 Technical Debt & Risks

| Priority | Issue | Platform | Impact |
|----------|-------|----------|--------|
| **HIGH** | Desktop uses different Firestore path (`users/` vs `user_data/`) | Desktop | Data silo — desktop users can't sync with mobile/web |
| **HIGH** | Desktop has no authentication | Desktop | Security — any user accesses same `default_user` data |
| **HIGH** | Desktop uses Firebase Admin SDK (service account) | Desktop | Security risk — service account in distributed app |
| **MEDIUM** | Web `app.js` monolithic (1000+ lines) | Web | Maintainability — hard to test, debug, onboard |
| **MEDIUM** | Android `DashboardScreen.kt` 435 lines | Android | Maintainability — extract composables |
| **MEDIUM** | Duplicate constants (quick actions, defaults) | Web ↔ Android | Drift risk — single source needed |
| **LOW** | `StateFlows.kt` 1-line wrapper | Android | Noise — inline or remove |
| **LOW** | Desktop missing achievements, categories, admin | Desktop | Feature parity gap |

---

## 6. Security Assessment

### 6.1 Strengths
- **Firebase Auth** handles all authentication — passwords never in Firestore
- **Firestore Rules** properly scoped: owner-only for `user_data`, admin for `app_config`
- **Synthetic emails** prevent email enumeration
- **Re-authentication** required for account deletion (Android + Web)
- **Admin routes** protected by both rules + client-side checks

### 6.2 Concerns
| Issue | Severity | Platform |
|-------|----------|----------|
| Service account (`firebase-key.json`) in desktop app | **Critical** | Desktop |
| No auth on desktop — single shared `default_user` | **Critical** | Desktop |
| `usernames` collection rules allow world-create (needed for registration race) | Low (by design) | All |
| Legacy Werkzeug hashes only verified, never stored | Low (migration path) | Android |

---

## 7. UI/UX Design Consistency

### 7.1 Visual Language
| Element | Android | Web | Desktop |
|---------|---------|-----|---------|
| **Primary Color** | `#3B82F6` | `#3B82F6` | `#3B82F6` / `#0096FF` (dark) |
| **Success** | `#10B981` | `#10B981` | `#10B981` / `#34D399` (dark) |
| **Danger** | `#EF4444` | `#EF4444` | `#EF4444` / `#FF6B6B` (dark) |
| **Background** | Animated gradient | Animated gradient | Solid (`#F8FAFC` / `#1A1D23`) |
| **Cards** | Glassmorphism (`surface` 60% alpha) | Glassmorphism (`backdrop-filter`) | Solid cards with border |
| **Typography** | Material3 / Inter | Inter (Google Fonts) | Segoe UI |

**Verdict:** Strong visual consistency. Desktop uses solid cards vs glassmorphism — acceptable for native feel.

### 7.2 Interaction Patterns
- **Quick Actions**: Grid of buttons (Android/Web), custom painted buttons (Desktop) — consistent UX
- **Add/Spend Forms**: Side-by-side cards (Android/Web), stacked in dialog (Desktop) — minor divergence
- **Navigation**: Bottom bar (Android), sidebar + bottom nav (Web), sidebar (Desktop) — platform-appropriate
- **Modals**: Consistent patterns across all three

---

## 8. Recommendations

### 8.1 Critical (Do First)

1. **Fix Desktop Firestore Path & Auth**
   - Migrate desktop to use `user_data/{uid}` structure
   - Implement Firebase Auth (Email/Password) on desktop — or remove Firebase entirely, make it local-only
   - **Rationale:** Current desktop data is isolated; service account is a security liability

2. **Unify Constants**
   - Create `shared/constants.json` or `shared/defaults.kt` consumed by all platforms
   - Include: `DEFAULT_QUICK_ACTIONS`, `DEFAULT_INCOME_CATEGORIES`, `DEFAULT_EXPENSE_CATEGORIES`, `DEFAULT_GOAL`, `ACHIEVEMENT_DEFS`

### 8.2 High Priority

3. **Modularize Web `app.js`**
   - Split into: `auth.js`, `data.js`, `analytics.js`, `history.js`, `settings.js`, `ui.js`
   - Consider TypeScript for type safety

4. **Extract Android Composables**
   - Break `DashboardScreen.kt` into: `BalanceCard`, `StatsRow`, `QuickActionsGrid`, `TransactionForms`, `ShareCard`, `SupportCard`
   - Same for `SettingsScreen.kt`, `AnalyticsScreen.kt`

5. **Add Desktop Feature Parity**
   - Implement achievements calculator (port from Android)
   - Add custom income/expense categories
   - Add import/export with same JSON format

### 8.3 Medium Priority

6. **Shared Achievement Logic**
   - Extract achievement definitions to JSON; implement calculator in Kotlin/JS/Python
   - Ensures identical unlocks across platforms

7. **Desktop: Replace Admin SDK with Client SDK**
   - Use `firebase_admin` only for server-side; desktop should use client SDK like Android/Web
   - Or go fully local-first with optional sync to a user-provided Firebase project

8. **Automated Cross-Platform Tests**
   - Add integration tests verifying: data written by Android readable by Web/Desktop, sync works, achievements match

### 8.4 Low Priority / Nice-to-Have

9. **Design System Documentation**
   - Document color tokens, spacing, component specs in `design.md`
   - Add Figma/Sketch reference or Storybook for Web

10. **Desktop: Modern Packaging**
    - Consider `flet` (Flutter for Python) or `tauri` (Rust + WebView) for more consistent cross-platform UI
    - Or keep PyQt but add `pyqtgraph` for lighter charts

---

## 9. File Inventory (Key Files)

### Android (`android/app/src/main/java/com/cointracker/mobile/`)
```
data/
  Models.kt              ← Core data classes, defaults
  FirestoreRepository.kt ← All Firestore ops, auth, migration, sync
  LocalCacheRepository.kt← Offline JSON cache
  SyncManager.kt         ← Conflict resolution logic
  WerkzeugPasswordHasher.kt
domain/
  AchievementCalculator.kt
ui/
  CoinTrackerViewModel.kt← Central StateFlow, all user actions
  StateFlows.kt          ← 1-line helper
  navigation/NavGraph.kt
  screens/               ← Dashboard, Analytics, History, Settings, Admin, Login, Notifications
  components/GlassCard.kt
  theme/Color.kt, Theme.kt, Type.kt
widget/CoinTrackerWidget.kt
notification/NotificationHelper.kt, DailyReminderWorker.kt
CoinTrackerApplication.kt (Hilt setup)
```

### Web (`web/public/`)
```
js/
  app.js      ← Main app (1000+ lines)
  login.js    ← Auth page
  admin.js    ← Admin panel
css/style.css ← Full styling (765 lines)
index.html    ← App shell
login.html    ← Auth shell
admin.html    ← Admin shell
firebase.json, firestore.rules, firestore.indexes.json
```

### Desktop (`desktop/`)
```
coin_tracker.py  ← MainWindow + OnlineCoinTracker (2300+ lines)
build.py         ← PyInstaller script
coin_icon.py     ← Icon generator
```

---

## 10. Conclusion

Coin Tracker is a **well-architected multi-platform app** with a clear shared data model and strong implementations on Android and Web. The **Desktop app is the weak link** — it diverges in data structure, lacks authentication, uses an insecure Firebase Admin SDK pattern, and misses key features.

**Recommended Path Forward:**
1. **Immediate:** Fix desktop Firestore path + auth (or pivot to local-only)
2. **Short-term:** Unify constants, modularize web, extract Android composables
3. **Long-term:** Consider rewriting desktop in a framework that shares more logic (Kotlin Multiplatform, Flutter, or Tauri) to achieve true parity

The Android implementation serves as the **reference architecture** — Web mirrors it well; Desktop should align to it.

---

*Report generated by automated codebase analysis. For questions or deeper dives into specific modules, request a focused review.*