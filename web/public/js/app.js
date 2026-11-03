// ─────────────────────────────────────────────────────────────
//  app.js  —  Coin Tracker Web App
//  Reads/writes Firestore in mobile app format:
//    user_data/{uid}  ← single doc, profiles nested inside
//  Field names: snake_case matching FirestoreRepository.kt
// ─────────────────────────────────────────────────────────────

import { initializeApp }  from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signOut,
  EmailAuthProvider, reauthenticateWithCredential, deleteUser,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, doc, getDoc, setDoc, deleteDoc,
  collection, getDocs, updateDoc,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { firebaseConfig } from "/firebase-config.js";
import { getIcon, getAlertIcon } from "/js/icons.js";

const firebaseApp = initializeApp(firebaseConfig);
const auth        = getAuth(firebaseApp);
const db          = getFirestore(firebaseApp);

// ── Defaults (match Models.kt + FirestoreRepository.kt) ──────
const DEFAULT_INCOME_CATEGORIES  = ["Event Reward","Login","Daily Games","Campaign Reward","Ads","Achievements","Other"];
const DEFAULT_EXPENSE_CATEGORIES = ["Box Draw","Manager Purchase","Pack Purchase","Store Purchase","Other"];
const DEFAULT_GOAL               = 13500;
const TRANSACTIONS_PER_PAGE      = 10;

// Escape user-derived strings before inserting them into innerHTML.
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (ch) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[ch]));
}

function hashString(value) {
  const str = String(value ?? "");
  let hash = 0;
  for (let i = 0; i < str.length; i++) hash = (hash * 31 + str.charCodeAt(i)) | 0;
  return Math.abs(hash).toString(36);
}

const DEFAULT_QUICK_ACTIONS = [
  { text: "Event Reward",      value: 50,  is_positive: true  },
  { text: "Ads",               value: 10,  is_positive: true  },
  { text: "Daily Games",       value: 100, is_positive: true  },
  { text: "Login",             value: 50,  is_positive: true  },
  { text: "Campaign Reward",   value: 50,  is_positive: true  },
  { text: "Box Draw (Single)", value: 100, is_positive: false },
  { text: "Box Draw (10)",     value: 900, is_positive: false },
];

function defaultSettingsMap(darkMode = false) {
  return {
    goal:               DEFAULT_GOAL,
    dark_mode:          darkMode,
    quick_actions:      DEFAULT_QUICK_ACTIONS,
    income_categories:  [],
    expense_categories: [],
  };
}

// ── Achievement definitions (mirrors AchievementCalculator.kt) ─
const ACHIEVEMENT_DEFS = [
  { id: "getting_started", icon: "savings", name: "Getting Started",    desc: "Reach a balance of 1,000 coins",  check: (s) => s.balance >= 1000 },
  { id: "serious_saver",   icon: "trendingUpAch2", name: "Serious Saver",      desc: "Reach a balance of 5,000 coins",  check: (s) => s.balance >= 5000 },
  { id: "coin_hoarder",    icon: "accountBalance", name: "Coin Hoarder",       desc: "Reach a balance of 10,000 coins", check: (s) => s.balance >= 10000 },
  { id: "master_hoarder",  icon: "crown", name: "Master Hoarder",       desc: "Reach a balance of 25,000 coins", check: (s) => s.balance >= 25000 },
  { id: "goal_reached",    icon: "emojiEvents", name: "Epic Box Secured!",   desc: "Reach your goal balance",         check: (s) => s.goal > 0 && s.balance >= s.goal },
  { id: "first_coin",      icon: "dollarSign", name: "First Payday",        desc: "Record your first earning transaction", check: (s) => s.incomeCount >= 1 },
  { id: "active_tracker",  icon: "target", name: "Active Tracker",      desc: "Record 25 total transactions",    check: (s) => s.txnCount >= 25 },
  { id: "century_club",    icon: "zap", name: "Century Club",        desc: "Record 100 total transactions",   check: (s) => s.txnCount >= 100 },
  { id: "high_roller",     icon: "trophy", name: "High Roller",         desc: "Earn 1,000+ coins in a single transaction", check: (s) => s.maxSingleIncome >= 1000 },
  { id: "disciplined",     icon: "shield2", name: "Disciplined",         desc: "No spending for 7+ days",         check: (s) => s.daysSinceSpend >= 7 },
  { id: "streak_3",        icon: "flame", name: "3-Day Login Streak",  desc: "3 consecutive Login days",        check: (s) => s.loginStreak >= 3 },
  { id: "streak_7",        icon: "flame", name: "7-Day Login Streak",  desc: "7 consecutive Login days",        check: (s) => s.loginStreak >= 7 },
  { id: "streak_14",       icon: "flame", name: "14-Day Login Streak", desc: "14 consecutive Login days",       check: (s) => s.loginStreak >= 14 },
];

// ─────────────────────────────────────────────────────────────
//  CoinTrackerApp
// ─────────────────────────────────────────────────────────────
class CoinTrackerApp {
  constructor() {
    this.uid             = null;
    this.username        = null;
    this.role            = null;
    this.currentProfile  = "Default";
    this.userDataDoc     = null;  // full user_data/{uid} document
    this.allProfiles     = [];
    this.charts          = {};
    this.analyticsPeriod     = "lifetime";
    this.analyticsCustomFrom = null;
    this.analyticsCustomTo   = null;
    this.historyPage     = 1;
    this.historyFiltered = [];
    this._lastFocused    = null;
    this._toastTimeout   = null;
    this._writeLocks     = new Set();
    this._confirmCleanup = null;
  }

  // ── Bootstrap ─────────────────────────────────────────────────
  async init() {
    this.showLoading("Retrieving data from database...", "Connecting to Cloud Firestore...");
    onAuthStateChanged(auth, async (user) => {
      if (!user) {
        this.hideLoading();
        window.location.href = "/login.html";
        return;
      }
      this.uid = user.uid;
      try {
        await this.loadUserMeta();
        await this.loadUserData();
        this.setupEventListeners();
        this.createHiddenFileInput();
        this.updateAllUI();
        this.applySidebarPinnedState();
        this.checkBroadcast();
      } catch (err) {
        console.error("Failed to load user data from database:", err);
        this.showToast("Failed to retrieve data from database.", "error");
        this.updateSyncStatus("error", "Database error");
      } finally {
        this.hideLoading();
      }
    });
  }

  // ── Load user meta from users/{uid} ───────────────────────────
  async loadUserMeta() {
    const snap = await getDoc(doc(db, "users", this.uid));
    if (snap.exists()) {
      this.username = snap.data().username;
      this.role     = snap.data().role || "user";
    }
  }

  // ── Load full user_data/{uid} single document ─────────────────
  // Mobile stores everything here: { last_active_profile, profiles: { Name: { transactions, settings, last_updated } } }
  async loadUserData() {
    const snap = await getDoc(doc(db, "user_data", this.uid));
    if (snap.exists()) {
      this.userDataDoc    = snap.data();
      this.currentProfile = this.userDataDoc.last_active_profile || "Default";
    } else {
      // Brand new user — create structure
      const now = new Date().toISOString();
      this.userDataDoc = {
        last_active_profile: "Default",
        profiles: {
          Default: {
            transactions: [],
            settings:     defaultSettingsMap(localStorage.getItem("theme") === "dark"),
            last_updated: now,
          },
        },
      };
      await setDoc(doc(db, "user_data", this.uid), this.userDataDoc);
    }
    this.allProfiles = Object.keys(this.userDataDoc.profiles || {});
    if (!this.allProfiles.includes("Default")) this.allProfiles.unshift("Default");
    this.allProfiles.sort();
  }

  // ── Save full user_data doc back to Firestore ─────────────────
  async saveUserData() {
    this.updateSyncStatus("loading", "Saving to database...");
    this.userDataDoc.last_active_profile = this.currentProfile;
    const now = new Date().toISOString();
    if (this.userDataDoc.profiles?.[this.currentProfile]) {
      this.userDataDoc.profiles[this.currentProfile].last_updated = now;
    }
    try {
      await setDoc(doc(db, "user_data", this.uid), this.userDataDoc);
      this.updateSyncStatus("synced");
    } catch (err) {
      console.error("saveUserData error:", err);
      this.updateSyncStatus("error", "Save failed");
      throw err;
    }
  }

  // ── Current profile data ──────────────────────────────────────
  get profileData() {
    return this.userDataDoc?.profiles?.[this.currentProfile] || {};
  }

  // ── Computed getters (snake_case field names from mobile) ──────
  get transactions() {
    return this.profileData.transactions || [];
  }

  get settings() {
    return this.profileData.settings || defaultSettingsMap();
  }

  get goal() {
    const raw = this.settings.goal;
    const value = typeof raw === "number" ? raw : Number(raw);
    return Number.isFinite(value) && value > 0 ? value : 0;
  }

  // Mobile uses is_positive, not isPositive
  get quickActions() {
    return this.settings.quick_actions || DEFAULT_QUICK_ACTIONS;
  }

  get incomeCategories() {
    const cats = this.settings.income_categories || [];
    return cats.length ? cats : DEFAULT_INCOME_CATEGORIES;
  }

  get expenseCategories() {
    const cats = this.settings.expense_categories || [];
    return cats.length ? cats : DEFAULT_EXPENSE_CATEGORIES;
  }

  get balance() {
    return this.transactions.reduce((s, t) => s + t.amount, 0);
  }

  get progress() {
    const goal = this.goal;
    if (goal <= 0) return 0;
    return Math.min(100, Math.max(0, Math.round((this.balance / goal) * 100)));
  }

  get estimatedDays() {
    if (this.goal <= 0) return "N/A";
    if (this.balance >= this.goal) return 0;
    const needed = this.goal - this.balance;
    const txns   = this.transactions;
    if (!txns.length) return "N/A";

    const sevenDaysAgo   = Date.now() - 7 * 86400000;
    const recentEarnings = txns.filter((t) => t.amount > 0 && new Date(t.date).getTime() >= sevenDaysAgo);
    let rate;
    if (recentEarnings.length > 0) {
      rate = recentEarnings.reduce((s, t) => s + t.amount, 0) / 7;
    } else {
      const earningDays = new Set(txns.filter((t) => t.amount > 0).map((t) => t.date.slice(0, 10))).size;
      const totalEarned = txns.filter((t) => t.amount > 0).reduce((s, t) => s + t.amount, 0);
      rate = earningDays > 0 ? totalEarned / earningDays : 0;
    }
    return rate <= 0 ? "N/A" : Math.ceil(needed / rate);
  }

  get dashboardStats() {
    const now          = new Date();
    const todayStr     = now.toISOString().slice(0, 10);
    const monthStr     = todayStr.slice(0, 7);
    const weekStart    = new Date(now);
    weekStart.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    const weekStartStr = weekStart.toISOString().slice(0, 10);
    let today = 0, week = 0, month = 0;
    for (const t of this.transactions) {
      if (t.amount <= 0) continue;
      const d = t.date.slice(0, 10);
      if (d === todayStr)             today += t.amount;
      if (d >= weekStartStr)          week  += t.amount;
      if (d.slice(0, 7) === monthStr) month += t.amount;
    }
    return { today, week, month };
  }

  get sevenDayRate() {
    const sevenDaysAgo = Date.now() - 7 * 86400000;
    const recent = this.transactions.filter((t) => t.amount > 0 && new Date(t.date).getTime() >= sevenDaysAgo);
    const total  = recent.reduce((s, t) => s + t.amount, 0);
    return { rate: Math.round(total / 7), total };
  }

  get bestEarningWeek() {
    if (!this.transactions.some((t) => t.amount > 0)) return null;
    const weekMap = {};
    for (const t of this.transactions) {
      if (t.amount <= 0) continue;
      const d = new Date(t.date), mon = new Date(d);
      mon.setDate(d.getDate() - ((d.getDay() + 6) % 7));
      const key = mon.toISOString().slice(0, 10);
      weekMap[key] = (weekMap[key] || 0) + t.amount;
    }
    const best = Object.entries(weekMap).sort((a, b) => b[1] - a[1])[0];
    if (!best) return null;
    const from = new Date(best[0]), to = new Date(from);
    to.setDate(from.getDate() + 6);
    const fmt = (d) => `${d.getMonth() + 1}/${d.getDate()}`;
    return { amount: best[1], range: `${fmt(from)} – ${fmt(to)}` };
  }

  get achievements() {
    const txns = this.transactions;
    const loginDates = [...new Set(
      txns.filter((t) => t.source === "Login" && t.amount > 0).map((t) => t.date.slice(0, 10))
    )].sort();
    let streak = 0, maxStreak = 0, prev = null;
    for (const d of loginDates) {
      if (prev) {
        const diff = (new Date(d) - new Date(prev)) / 86400000;
        streak = diff === 1 ? streak + 1 : 1;
      } else { streak = 1; }
      maxStreak = Math.max(maxStreak, streak);
      prev = d;
    }
    const spends = txns.filter((t) => t.amount < 0).map((t) => new Date(t.date).getTime());
    const lastSpend = spends.length ? Math.max(...spends) : null;
    // -1 means never spent — won't trigger >= 7 check
    const daysSinceSpend = lastSpend ? Math.floor((Date.now() - lastSpend) / 86400000) : -1;

    const incomes = txns.filter((t) => t.amount > 0);
    const incomeCount = incomes.length;
    const txnCount = txns.length;
    const maxSingleIncome = incomes.length ? Math.max(...incomes.map((t) => t.amount)) : 0;
    const totalEarned = incomes.reduce((sum, t) => sum + t.amount, 0);

    const state = {
      balance: this.balance,
      goal: this.goal,
      loginStreak: maxStreak,
      daysSinceSpend,
      incomeCount,
      txnCount,
      maxSingleIncome,
      totalEarned,
    };
    return ACHIEVEMENT_DEFS.filter((a) => a.check(state));
  }

  get notificationAlerts() {
    const alerts = [], p = this.progress, goal = this.goal;
    if (goal > 0 && this.balance >= goal)
      alerts.push({ icon: getAlertIcon("🎉"), title: "Goal Reached!", sub: `You hit your goal of ${goal.toLocaleString()} coins!` });
    else if (goal > 0 && p >= 75)
      alerts.push({ icon: getAlertIcon("🔥"), title: "75% of your goal reached", sub: `Only ${(goal - this.balance).toLocaleString()} coins to go!` });
    else if (goal > 0 && p >= 50)
      alerts.push({ icon: getAlertIcon("⚡"), title: "Halfway there!", sub: "You're 50% of the way to your goal." });
    const stats = this.dashboardStats;
    if (stats.today >= 300)
      alerts.push({ icon: getAlertIcon("💪"), title: "Big day!", sub: `You earned ${stats.today.toLocaleString()} coins today!` });
    const est = this.estimatedDays;
    if (typeof est === "number" && est > 0 && est <= 7)
      alerts.push({ icon: getAlertIcon("⏰"), title: "Goal is close!", sub: `~${est} day${est === 1 ? "" : "s"} to reach your goal.` });
    const best = this.bestEarningWeek;
    if (best)
      alerts.push({ icon: getAlertIcon("🏆"), title: "Best Earning Week", sub: `${best.range}: ${best.amount.toLocaleString()} coins` });
    return alerts;
  }

  // ─────────────────────────────────────────────────────────────
  //  Settings helpers — read/write snake_case fields
  // ─────────────────────────────────────────────────────────────
  _setSettings(partial) {
    if (!this.userDataDoc.profiles[this.currentProfile]) return;
    this.userDataDoc.profiles[this.currentProfile].settings = {
      ...this.settings,
      ...partial,
    };
  }

  // ── Write safety helpers ──────────────────────────────────────
  _snapshotWriteState() {
    return JSON.parse(JSON.stringify({
      transactions: this.profileData.transactions || [],
      settings:     this.profileData.settings || {},
    }));
  }

  _restoreWriteState(snapshot) {
    const profile = this.userDataDoc?.profiles?.[this.currentProfile];
    if (!profile || !snapshot) return;
    profile.transactions = snapshot.transactions;
    profile.settings     = snapshot.settings;
  }

  _withAlpha(color, alpha) {
    const value = (color || "").trim();
    if (!value.startsWith("#")) return value || `rgba(232, 184, 74, ${alpha})`;
    let hex = value.slice(1);
    if (hex.length === 3) hex = hex.split("").map((c) => c + c).join("");
    const num = parseInt(hex.slice(0, 6), 16);
    if (Number.isNaN(num)) return `rgba(232, 184, 74, ${alpha})`;
    return `rgba(${(num >> 16) & 255}, ${(num >> 8) & 255}, ${num & 255}, ${alpha})`;
  }

  _setFieldError(input, errorId, message) {
    if (input) {
      input.setAttribute("aria-invalid", "true");
      if (errorId && !input.getAttribute("aria-describedby")) {
        input.setAttribute("aria-describedby", errorId);
      }
    }
    const err = errorId ? document.getElementById(errorId) : null;
    if (err) err.textContent = message;
  }

  _clearFieldError(input, errorId) {
    if (input) input.removeAttribute("aria-invalid");
    const err = errorId ? document.getElementById(errorId) : null;
    if (err) err.textContent = "";
  }

  _setAnalyticsRangeError(message, focusInput = null) {
    ["analyticsFrom", "analyticsTo"].forEach((id) => {
      document.getElementById(id)?.setAttribute("aria-invalid", "true");
    });
    const err = document.getElementById("analyticsRangeError");
    if (err) {
      err.textContent = message;
      err.hidden = false;
    }
    if (focusInput) focusInput.focus({ preventScroll: true });
  }

  _clearAnalyticsRangeError() {
    ["analyticsFrom", "analyticsTo"].forEach((id) => {
      document.getElementById(id)?.removeAttribute("aria-invalid");
    });
    const err = document.getElementById("analyticsRangeError");
    if (err) {
      err.textContent = "";
      err.hidden = true;
    }
  }

  _validateAmountInput(inputId, errorId) {
    const input = document.getElementById(inputId);
    if (!input) return null;
    const raw = input.value.trim();
    const amount = Number(raw);
    if (!raw || !Number.isInteger(amount) || amount < 1 || amount > 999999) {
      this._setFieldError(input, errorId, "Enter an amount between 1 and 999,999.");
      input.focus({ preventScroll: true });
      return null;
    }
    this._clearFieldError(input, errorId);
    return amount;
  }

  async _runWrite(key, button, pendingLabel, work, failureMessage) {
    if (this._writeLocks.has(key)) return null;
    this._writeLocks.add(key);

    const originalHTML     = button ? button.innerHTML : null;
    const wasDisabled      = button ? button.disabled : false;
    const originalAriaLabel = button ? button.getAttribute("aria-label") : null;

    if (button) {
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
      button.classList.add("is-pending");
      if (pendingLabel) button.textContent = pendingLabel;
    }

    try {
      await work();
      return true;
    } catch (err) {
      console.error(`Write failed [${key}]:`, err);
      if (failureMessage) this.showToast(failureMessage, "error");
      return false;
    } finally {
      if (button) {
        if (originalHTML !== null) button.innerHTML = originalHTML;
        button.disabled = wasDisabled;
        button.removeAttribute("aria-busy");
        button.classList.remove("is-pending");
        if (originalAriaLabel !== null) button.setAttribute("aria-label", originalAriaLabel);
      }
      this._writeLocks.delete(key);
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  Event listeners
  // ─────────────────────────────────────────────────────────────
  setupEventListeners() {
    document.getElementById("hamburgerBtn").addEventListener("click", () => this.toggleMobileNav());

    document.querySelectorAll("nav.nav > .nav-btn, nav.navbar > .nav-btn, .navbar > .nav-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        this.showPage(e.currentTarget.dataset.page);
        if (document.querySelector(".sidebar").classList.contains("nav-expanded")) this.toggleMobileNav();
      });
    });

    document.querySelectorAll(".mobile-nav-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => this.showPage(e.currentTarget.dataset.page));
    });

    // Top bar theme toggle
    document.getElementById("topBarThemeToggle").addEventListener("click", () => this.toggleTheme());

    // Top bar logo click - navigate to dashboard
    document.querySelector(".top-bar-logo")?.addEventListener("click", (e) => {
      e.preventDefault();
      this.showPage("dashboard");
    });

    // Top bar notifications button
    document.getElementById("topBarNotificationsBtn").addEventListener("click", () => this.showPage("notifications"));

    // Mobile theme toggle (shown in mobile sidebar)
    document.getElementById("mobileThemeToggle")?.addEventListener("click", () => this.toggleTheme());

    // Mobile logout button (shown in mobile sidebar)
    document.getElementById("mobileLogoutBtn")?.addEventListener("click", () => this.logout());

    // Mobile admin button
    document.getElementById("mobileAdminBtn")?.addEventListener("click", () => { window.location.href = "/admin.html"; });

    // Top bar profile dropdown
    document.getElementById("topBarProfileBtn").addEventListener("click", (e) => this.toggleProfileDropdown(e));
    document.getElementById("topBarProfileSelect").addEventListener("change", (e) => this.switchProfile(e.target.value));
    document.getElementById("topBarNewProfileBtn").addEventListener("click", () => this.showModal("profileModal"));
    document.getElementById("topBarAdminBtn").addEventListener("click", () => { window.location.href = "/admin.html"; });
    document.getElementById("topBarLogoutBtn").addEventListener("click", () => this.logout());

    // Close profile dropdown or mobile burger menu when clicking outside
    document.addEventListener("click", (e) => {
      const dropdown = document.getElementById("profileDropdownMenu");
      const profileBtn = document.getElementById("topBarProfileBtn");
      if (dropdown && dropdown.classList.contains("open") && !dropdown.contains(e.target) && e.target !== profileBtn) {
        dropdown.classList.remove("open");
        profileBtn.setAttribute("aria-expanded", "false");
      }
      const sidebar = document.querySelector(".sidebar");
      const hamburgerBtn = document.getElementById("hamburgerBtn");
      if (sidebar && sidebar.classList.contains("nav-expanded") && !sidebar.contains(e.target) && e.target !== hamburgerBtn && !hamburgerBtn.contains(e.target)) {
        this.toggleMobileNav();
      }
    });

    // Escape closes safe overlays and restores focus
    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      const dropdown = document.getElementById("profileDropdownMenu");
      const profileBtn = document.getElementById("topBarProfileBtn");
      if (dropdown && dropdown.classList.contains("open")) {
        dropdown.classList.remove("open");
        profileBtn?.setAttribute("aria-expanded", "false");
        profileBtn?.focus();
        return;
      }
      const sidebar = document.querySelector(".sidebar");
      if (sidebar && sidebar.classList.contains("nav-expanded")) {
        this.toggleMobileNav();
        document.getElementById("hamburgerBtn")?.focus();
      }
    });

    // Sidebar hover & collapse handling
    const sidebarEl = document.querySelector(".sidebar");
    if (sidebarEl) {
      sidebarEl.addEventListener("mouseleave", () => {
        sidebarEl.classList.remove("expanded");
        if (document.activeElement && sidebarEl.contains(document.activeElement)) {
          document.activeElement.blur();
        }
      });
    }

    // Sidebar toggle (desktop)
    document.getElementById("sidebarToggleBtn")?.addEventListener("click", () => this.toggleSidebarPinned());

    // Sidebar profile selector (mobile)
    document.getElementById("profileSelect").addEventListener("change", (e) => this.switchProfile(e.target.value));
    document.getElementById("newProfileBtn").addEventListener("click", () => {
      if (document.querySelector(".sidebar")?.classList.contains("nav-expanded")) this.toggleMobileNav();
      this.showModal("profileModal");
    });

    document.getElementById("addCoinsBtn").addEventListener("click", () => this.addFromDashboard());
    document.getElementById("spendCoinsBtn").addEventListener("click", () => this.spendFromDashboard());
    document.getElementById("customizeQuickActions").addEventListener("click", () => this.showPage("settings"));
    document.getElementById("viewAllAchievementsBtn")?.addEventListener("click", () => this.showPage("notifications"));
    document.getElementById("viewRecentHistoryBtn")?.addEventListener("click", () => this.showPage("history"));

    ["dateFrom", "dateTo", "historySourceFilter"].forEach((id) => {
      document.getElementById(id)?.addEventListener("change", () => this.buildHistory());
    });
    document.getElementById("historySearch")?.addEventListener("input", () => this.buildHistory());

    document.querySelectorAll(".chip[data-period]").forEach((chip) => {
      chip.addEventListener("click", () => {
        document.querySelectorAll(".chip[data-period]").forEach((c) => {
          c.classList.remove("active");
          c.setAttribute("aria-pressed", "false");
        });
        chip.classList.add("active");
        chip.setAttribute("aria-pressed", "true");
        this.analyticsPeriod = chip.dataset.period;
        this._clearAnalyticsRangeError();
        const picker = document.getElementById("customRangePicker");
        if (picker) picker.style.display = this.analyticsPeriod === "custom" ? "flex" : "none";
        this.updateAnalyticsUI();
      });
    });
    document.getElementById("applyRangeBtn")?.addEventListener("click", () => {
      const fromInput = document.getElementById("analyticsFrom");
      const toInput   = document.getElementById("analyticsTo");
      const from = fromInput?.value || "";
      const to   = toInput?.value || "";
      if (!from || !to) {
        this._setAnalyticsRangeError("Choose both a start and end date.", !from ? fromInput : toInput);
        return;
      }
      if (from > to) {
        this._setAnalyticsRangeError("Start date must be on or before end date.", fromInput);
        return;
      }
      this._clearAnalyticsRangeError();
      this.analyticsCustomFrom = from;
      this.analyticsCustomTo   = to;
      this.updateAnalyticsUI();
    });
    ["analyticsFrom", "analyticsTo"].forEach((id) => {
      document.getElementById(id)?.addEventListener("input", () => this._clearAnalyticsRangeError());
    });

    document.querySelectorAll(".tab-header").forEach((tab) => {
      tab.addEventListener("click", (e) => this.switchTab(e.currentTarget));
      tab.addEventListener("keydown", (e) => this._handleTabKeydown(e));
    });

    document.getElementById("setGoalBtn").addEventListener("click", () => this.setGoal());
    document.getElementById("addQuickActionBtn").addEventListener("click", () => this.addQuickAction());

    document.getElementById("addIncomeCategoryBtn").addEventListener("click",    () => this.addCategory("income"));
    document.getElementById("addExpenseCategoryBtn").addEventListener("click",   () => this.addCategory("expense"));
    document.getElementById("resetIncomeCategoriesBtn").addEventListener("click",  () => this.resetCategories("income"));
    document.getElementById("resetExpenseCategoriesBtn").addEventListener("click", () => this.resetCategories("expense"));

    document.getElementById("exportDataBtn").addEventListener("click", () => this.exportData());
    document.getElementById("importDataBtn").addEventListener("click", () => this.triggerImport());
    document.getElementById("deleteAllDataBtn").addEventListener("click", () => this.deleteAllData());
    document.getElementById("deleteProfileBtn").addEventListener("click", () => this.deleteCurrentProfile());

    document.getElementById("deleteAccountBtn").addEventListener("click", () => this.showModal("deleteAccountModal"));
    document.getElementById("confirmDeleteAccountBtn").addEventListener("click", () => this.deleteAccount());

    document.getElementById("createProfileBtn").addEventListener("click", () => this.createProfile());
    document.getElementById("supportBtn").addEventListener("click", () => this.showModal("supportModal"));
    document.querySelectorAll(".donation-card").forEach((card) => {
      const activate = () => this._copyDonationNumber(card);
      card.addEventListener("click", activate);
      card.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          activate();
        }
      });
    });

    document.querySelectorAll(".close-btn[data-modal]").forEach((btn) => {
      btn.addEventListener("click", () => {
        if (btn.dataset.modal === "confirmModal" && this._confirmCleanup) {
          this._confirmCleanup(false);
          return;
        }
        this.closeModal(btn.dataset.modal);
      });
    });

    [["addAmount", "addAmountError"], ["spendAmount", "spendAmountError"], ["transactionAmount", "transactionAmountError"]]
      .forEach(([inputId, errorId]) => {
        const input = document.getElementById(inputId);
        if (input) input.addEventListener("input", () => this._clearFieldError(input, errorId));
      });
    const goalInput = document.getElementById("goalInput");
    if (goalInput) goalInput.addEventListener("input", () => this._clearFieldError(goalInput, "goalInputError"));

    document.getElementById("saveTransactionBtn").addEventListener("click", () => this.saveEditedTransaction());

    // Notification read functionality
    document.getElementById("markAllReadBtn")?.addEventListener("click", () => this.markAllNotificationsRead());

    // Keyboard shortcuts help dialog
    document.getElementById("topBarShortcutsBtn")?.addEventListener("click", () => this.showModal("shortcutsModal"));
    document.getElementById("viewShortcutsBtn")?.addEventListener("click", () => this.showModal("shortcutsModal"));

    // Chart selection bar dismiss (v4.0.3)
    document.getElementById("chartSelectionDismissBtn")?.addEventListener("click", () => this._clearChartSelection());

    // Progress card share (v4.0.4)
    document.getElementById("shareCardBtn")?.addEventListener("click", () => this.shareProgressCard());

    this.setupKeyboardShortcuts();
  }

  setupKeyboardShortcuts() {
    let lastKey = null;
    let lastKeyTime = 0;

    window.addEventListener("keydown", (e) => {
      // Never trigger shortcuts if modifier keys are pressed
      if (e.ctrlKey || e.altKey || e.metaKey) return;

      const target = e.target;
      const tag = target && target.tagName ? target.tagName.toLowerCase() : "";
      const isEditable = tag === "input" || tag === "textarea" || tag === "select" || (target && target.isContentEditable);

      // Escape is handled by modal and dropdown escape listeners
      if (e.key === "Escape") return;

      // Do not trigger global shortcuts when typing inside an editable field
      if (isEditable) return;

      // Handle '?' to toggle shortcuts modal
      if (e.key === "?") {
        e.preventDefault();
        const shortcutsModal = document.getElementById("shortcutsModal");
        if (shortcutsModal && shortcutsModal.classList.contains("open")) {
          this.closeModal("shortcutsModal");
        } else {
          this.showModal("shortcutsModal");
        }
        return;
      }

      // If a modal is open, do not trigger navigation or search shortcuts
      if (document.querySelector(".modal.open")) return;

      // Handle '/' to focus History search
      if (e.key === "/") {
        e.preventDefault();
        this.showPage("history");
        setTimeout(() => {
          const searchInput = document.getElementById("historySearch");
          if (searchInput) {
            searchInput.focus();
            searchInput.select();
          }
        }, 50);
        return;
      }

      // Handle sequential 'g' navigation (g then d, a, h, n, s)
      const now = Date.now();
      const key = e.key.toLowerCase();

      if (key === "g") {
        lastKey = "g";
        lastKeyTime = now;
        return;
      }

      if (lastKey === "g" && (now - lastKeyTime) < 1200) {
        lastKey = null;
        if (key === "d") {
          e.preventDefault();
          this.showPage("dashboard");
        } else if (key === "a") {
          e.preventDefault();
          this.showPage("analytics");
        } else if (key === "h") {
          e.preventDefault();
          this.showPage("history");
        } else if (key === "n") {
          e.preventDefault();
          this.showPage("notifications");
        } else if (key === "s") {
          e.preventDefault();
          this.showPage("settings");
        }
      } else {
        lastKey = null;
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  //  Master UI update
  // ─────────────────────────────────────────────────────────────
  updateAllUI() {
    this.applyTheme();
    this.updateHeaderUI();
    this.updateBalanceUI();
    this.updateDashboardStatsUI();
    this.updateQuickActionsUI();
    this.populateCategoryDropdowns();
    this.updateAnalyticsUI();
    this.buildHistory();
    this.updateSettingsUI();
    this.updateAchievementsUI();
    this.updateRecentActivityUI();
    this.updateNotificationsUI();
    this.updateProfileDropdown();
  }

  applyTheme() {
    // Mobile uses dark_mode (snake_case)
    const dark  = this.settings.dark_mode ?? (localStorage.getItem("theme") === "dark");
    const theme = dark ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("theme", theme);
    const btn = document.getElementById("themeToggle");
    if (btn) {
      btn.innerHTML = dark ? `${getIcon('sun')} Light Mode` : `${getIcon('moon')} Dark Mode`;
    }
    // Update mobile theme toggle label/icon
    const mobileThemeBtn = document.getElementById("mobileThemeToggle");
    if (mobileThemeBtn) {
      const mobileIcon = document.getElementById("mobileThemeIcon");
      const mobileLabel = document.getElementById("mobileThemeLabel");
      if (mobileIcon) mobileIcon.innerHTML = dark ? getIcon('sun') : getIcon('moon');
      if (mobileLabel) mobileLabel.textContent = dark ? "Light mode" : "Dark mode";
      mobileThemeBtn.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
    }
    // Update top bar theme toggle icon
    const topBarThemeBtn = document.getElementById("topBarThemeToggle");
    if (topBarThemeBtn) {
      const topBarIcon = document.getElementById("topBarThemeIcon");
      if (topBarIcon) topBarIcon.innerHTML = dark ? getIcon('sun') : getIcon('moon');
      else topBarThemeBtn.innerHTML = dark ? getIcon('sun') : getIcon('moon');
      topBarThemeBtn.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
    }
    if (Object.keys(this.charts).length > 0) this.updateAnalyticsUI();
  }

  updateHeaderUI() {
    const el = document.getElementById("usernameDisplay");
    if (el) el.textContent = this.username || "";
    if (this.role === "admin") {
      const c = document.getElementById("adminPanelBtnContainer");
      if (c) c.style.display = "block";
      // Show admin button in top bar dropdown and mobile menu
      const adminBtn = document.getElementById("topBarAdminBtn");
      if (adminBtn) adminBtn.style.display = "flex";
      const mobileAdminBtn = document.getElementById("mobileAdminBtn");
      if (mobileAdminBtn) mobileAdminBtn.style.display = "flex";
    }
    // Update profile dropdown select in top bar
    this.updateTopBarProfileDropdown();
  }

  updateBalanceUI() {
    const bal = this.balance, goal = this.goal, prog = this.progress, est = this.estimatedDays;
    document.getElementById("balanceAmount").textContent    = `${bal.toLocaleString()} coins`;
    document.getElementById("goalText").textContent         = goal > 0
      ? `Goal: ${goal.toLocaleString()} coins`
      : "No goal set";
    document.getElementById("progressBar").style.width     = `${goal > 0 ? prog : 0}%`;
    document.getElementById("progressPercent").textContent = goal > 0 ? `${prog}%` : "—";

    const progressTrack = document.getElementById("goalProgressTrack");
    if (progressTrack) {
      progressTrack.setAttribute("aria-valuenow", String(goal > 0 ? prog : 0));
      progressTrack.setAttribute("aria-valuetext", goal > 0 ? `${prog}% of goal` : "No goal set");
    }

    const remainingEl = document.getElementById("remainingPill");
    if (remainingEl) {
      if (goal <= 0) {
        remainingEl.textContent = "No goal set";
        remainingEl.className = "status-pill neutral";
      } else if (bal >= goal) {
        remainingEl.textContent = "Goal achieved";
        remainingEl.className = "status-pill success";
      } else {
        const remaining = Math.max(0, goal - bal);
        remainingEl.textContent = `${remaining.toLocaleString()} to goal`;
        remainingEl.className = "status-pill neutral";
      }
    }

    const estEl = document.getElementById("goalEstimate");
    if (goal <= 0)          estEl.textContent = "Set a goal to see your estimate";
    else if (est === 0)     estEl.textContent = "Goal achieved";
    else if (est === "N/A") estEl.textContent = "Add earnings to see your estimate";
    else                    estEl.textContent = `Estimated time to goal: ~${est} day${est === 1 ? "" : "s"}`;
  }

  updateDashboardStatsUI() {
    const s = this.dashboardStats;
    document.getElementById("todayEarnings").textContent = `+${s.today.toLocaleString()}`;
    document.getElementById("weekEarnings").textContent  = `+${s.week.toLocaleString()}`;
    document.getElementById("monthEarnings").textContent = `+${s.month.toLocaleString()}`;
  }

  updateQuickActionsUI() {
    const grid = document.getElementById("quickActionsGrid");
    grid.innerHTML = "";
    const actions = this.quickActions;
    if (actions.length) {
      actions.forEach((action, index) => {
        const btn = document.createElement("button");
        // Mobile uses is_positive (snake_case)
        btn.className = `quick-btn ${action.is_positive ? "positive" : "negative"}`;
        btn.innerHTML = `<div class="quick-text">${escapeHtml(action.text)}</div>
                         <div class="quick-amount">${action.is_positive ? "+" : "-"}${action.value}</div>`;
        btn.onclick = async () => {
          const snapshot = this._snapshotWriteState();
          const profileName = this.currentProfile;
          let createdTxn = null;
          const ok = await this._runWrite(
            `quick:${index}`,
            btn,
            "Recording…",
            async () => {
              createdTxn = await this.addTransaction(action.is_positive ? action.value : -action.value, action.text);
            },
            `Could not record '${action.text}'. Please try again.`
          );
          if (ok === null) return;
          if (!ok) {
            this._restoreWriteState(snapshot);
            return;
          }
          if (createdTxn) {
            this.showUndoToast(`'${action.text}' recorded!`, () => {
              this._undoAddTransaction(profileName, createdTxn.id);
            });
          } else {
            this.showToast(`'${action.text}' recorded!`, "success");
          }
        };
        grid.appendChild(btn);
      });
    }

    // Append "+ New Action" shortcut tile
    const addTile = document.createElement("button");
    addTile.className = "quick-btn add-action-tile";
    addTile.style.borderStyle = "dashed";
    addTile.style.borderWidth = "2px";
    addTile.style.display = "flex";
    addTile.style.flexDirection = "column";
    addTile.style.alignItems = "center";
    addTile.style.justifyContent = "center";
    addTile.style.textAlign = "center";
    addTile.style.opacity = "0.75";
    addTile.setAttribute("type", "button");
    addTile.setAttribute("aria-label", "Create new quick action");
    addTile.innerHTML = `<div class="quick-text" style="color:var(--primary-color)">+ New Action</div>
                         <div class="quick-amount">Configure in settings</div>`;
    addTile.onclick = () => {
      this.showPage("settings");
      setTimeout(() => {
        const input = document.getElementById("quickActionText");
        if (input) {
          input.focus();
          input.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      }, 150);
    };
    grid.appendChild(addTile);
  }

  populateCategoryDropdowns() {
    const addSrc   = document.getElementById("addSource");
    const spendCat = document.getElementById("spendCategory");
    if (addSrc)   addSrc.innerHTML   = this.incomeCategories.map((c) => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join("");
    if (spendCat) spendCat.innerHTML = this.expenseCategories.map((c) => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join("");

    const allSources = [...new Set(this.transactions.map((t) => t.source))].sort();
    const sf = document.getElementById("historySourceFilter");
    const cur = sf.value;
    sf.innerHTML = '<option value="all">All Sources</option>' +
      allSources.map((s) => `<option value="${escapeHtml(s)}">${escapeHtml(s)}</option>`).join("");
    sf.value = allSources.includes(cur) ? cur : "all";
  }

  updateProfileDropdown() {
    const sel = document.getElementById("profileSelect");
    if (!sel) return;
    sel.innerHTML = this.allProfiles.map((p) =>
      `<option value="${escapeHtml(p)}" ${p === this.currentProfile ? "selected" : ""}>${escapeHtml(p)}</option>`
    ).join("");
  }

  updateTopBarProfileDropdown() {
    const sel = document.getElementById("topBarProfileSelect");
    if (!sel) return;
    sel.innerHTML = this.allProfiles.map((p) =>
      `<option value="${escapeHtml(p)}" ${p === this.currentProfile ? "selected" : ""}>${escapeHtml(p)}</option>`
    ).join("");
  }

  toggleProfileDropdown(e) {
    const dropdown = document.getElementById("profileDropdownMenu");
    const btn = document.getElementById("topBarProfileBtn");
    if (!dropdown || !btn) return;
    e?.stopPropagation();
    const isOpen = dropdown.classList.toggle("open");
    btn.setAttribute("aria-expanded", isOpen);
    if (isOpen) {
      // Debounce focus to avoid race with DOM insertion
      requestAnimationFrame(() => {
        const focusable = dropdown.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        if (focusable) focusable.focus();
      });
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  Notification Read Functionality
  // ─────────────────────────────────────────────────────────────

  getReadNotificationIds() {
    try {
      const stored = localStorage.getItem("cointracker_read_notifications");
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }

  saveReadNotificationIds(ids) {
    try {
      localStorage.setItem("cointracker_read_notifications", JSON.stringify(ids));
    } catch (e) {
      console.warn("Failed to save read notification IDs:", e);
    }
  }

  getNotificationIds() {
    // Stable content-derived IDs so read state never bleeds between alerts.
    const alerts = this.notificationAlerts;
    const achs = this.achievements;
    const ids = [];
    alerts.forEach((a) => ids.push(`alert-${hashString(`${a.title}|${a.sub}`)}`));
    achs.forEach((a) => ids.push(`achievement-${a.id}`));
    return ids;
  }

  getUnreadCount() {
    const readIds = new Set(this.getReadNotificationIds());
    const allIds = this.getNotificationIds();
    return allIds.filter(id => !readIds.has(id)).length;
  }

  updateNotificationBadges() {
    const count = this.getUnreadCount();
    const badges = [
      document.getElementById("achievementBadge"),
      document.getElementById("mobileAchievementBadge"),
      document.getElementById("topBarAchievementBadge"),
    ];
    badges.forEach(badge => {
      if (badge) {
        badge.style.display = count > 0 ? "inline-block" : "none";
        badge.textContent = count > 9 ? "9+" : String(count);
      }
    });
    const markAllBtn = document.getElementById("markAllReadBtn");
    if (markAllBtn) {
      markAllBtn.disabled = count === 0;
      markAllBtn.setAttribute("aria-disabled", count === 0 ? "true" : "false");
      markAllBtn.setAttribute("aria-label", count === 0 ? "No unread notifications" : "Mark all notifications as read");
    }
  }

  markNotificationsAsRead(notificationIds) {
    const readIds = new Set(this.getReadNotificationIds());
    notificationIds.forEach(id => readIds.add(id));
    this.saveReadNotificationIds([...readIds]);
    this.updateNotificationBadges();
    this.updateNotificationsUI(); // Re-render to show read state
  }

  markAllNotificationsRead() {
    const allIds = this.getNotificationIds();
    const unreadIds = allIds.filter((id) => !new Set(this.getReadNotificationIds()).has(id));
    if (!unreadIds.length) return;
    this.markNotificationsAsRead(unreadIds);
    this.showToast("All notifications marked as read", "success");
  }

  getAnalyticsTxns() {
    const now = new Date(); let from, to;
    if (this.analyticsPeriod === "monthly") {
      from = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
      to   = now.toISOString().slice(0, 10);
    } else if (this.analyticsPeriod === "weekly") {
      const mon = new Date(now);
      mon.setDate(now.getDate() - ((now.getDay() + 6) % 7));
      from = mon.toISOString().slice(0, 10);
      to   = now.toISOString().slice(0, 10);
    } else if (this.analyticsPeriod === "custom") {
      from = this.analyticsCustomFrom; to = this.analyticsCustomTo;
      if (!from || !to || from > to) return [];
    }
    if (!from || !to) return this.transactions;
    return this.transactions.filter((t) => t.date.slice(0, 10) >= from && t.date.slice(0, 10) <= to);
  }

  computeAnalytics(txns) {
    const totalEarnings = txns.filter((t) => t.amount > 0).reduce((s, t) => s + t.amount, 0);
    const totalSpending = txns.filter((t) => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0);
    const netBalance    = totalEarnings - totalSpending;
    const earningsBreakdown = {}, spendingBreakdown = {};
    txns.forEach((t) => {
      if (t.amount > 0) earningsBreakdown[t.source] = (earningsBreakdown[t.source] || 0) + t.amount;
      else              spendingBreakdown[t.source] = (spendingBreakdown[t.source] || 0) + Math.abs(t.amount);
    });
    const sorted = [...txns].sort((a, b) => new Date(a.date) - new Date(b.date));
    let running  = this.balance - txns.reduce((s, t) => s + t.amount, 0);
    const timeline = sorted.map((t) => { running += t.amount; return { date: t.date, balance: running }; });
    return { totalEarnings, totalSpending, netBalance, earningsBreakdown, spendingBreakdown, timeline };
  }

  updateAnalyticsUI() {
    const txns = this.getAnalyticsTxns(), a = this.computeAnalytics(txns);
    document.getElementById("totalEarnings").textContent = `+${a.totalEarnings.toLocaleString()}`;
    document.getElementById("totalSpending").textContent = `-${a.totalSpending.toLocaleString()}`;
    const net = a.netBalance;
    document.getElementById("netBalance").textContent = `${net >= 0 ? "+" : ""}${net.toLocaleString()}`;
    this.createOrUpdateChart("timelineChart", "line",
      a.timeline.map((p) => new Date(p.date).toLocaleDateString()),
      a.timeline.map((p) => p.balance));
    this.createOrUpdateChart("earningsChart", "doughnut",
      Object.keys(a.earningsBreakdown), Object.values(a.earningsBreakdown));
    this.createOrUpdateChart("spendingChart", "doughnut",
      Object.keys(a.spendingBreakdown), Object.values(a.spendingBreakdown));
    const { rate, total } = this.sevenDayRate;
    document.getElementById("sevenDayRate").textContent  = `${rate.toLocaleString()} coins/day`;
    document.getElementById("sevenDayTotal").textContent = `Total earned: ${total.toLocaleString()} coins`;
    const best = this.bestEarningWeek;
    const bestCard = document.getElementById("bestWeekCard");
    if (bestCard) {
      bestCard.style.display = best ? "block" : "none";
      if (best) {
        document.getElementById("bestWeekAmount").textContent = `${best.amount.toLocaleString()} coins`;
        document.getElementById("bestWeekRange").textContent  = best.range;
      }
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  History
  // ─────────────────────────────────────────────────────────────
  buildHistory() {
    const from   = document.getElementById("dateFrom").value;
    const to     = document.getElementById("dateTo").value;
    const search = document.getElementById("historySearch").value.toLowerCase();
    const source = document.getElementById("historySourceFilter").value;
    let txns = [...this.transactions].sort((a, b) => new Date(b.date) - new Date(a.date));
    if (from)             txns = txns.filter((t) => t.date.slice(0, 10) >= from);
    if (to)               txns = txns.filter((t) => t.date.slice(0, 10) <= to);
    if (source !== "all") txns = txns.filter((t) => t.source === source);
    if (search)           txns = txns.filter((t) =>
      t.source.toLowerCase().includes(search) || String(Math.abs(t.amount)).includes(search));
    this.historyFiltered = txns;
    this._renderHistoryActiveFilters();
    this.renderHistoryPage(1);
    const earned = txns.filter((t) => t.amount > 0).reduce((s, t) => s + t.amount, 0);
    const spent  = txns.filter((t) => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0);
    const el = document.getElementById("periodSummary");
    if (el) el.innerHTML = `<span class="amount-positive">Earned: +${earned.toLocaleString()}</span> / <span class="amount-negative">Spent: -${spent.toLocaleString()}</span>`;
  }

  getTypeBadge(isIncome) {
    if (isIncome) {
      return `<span class="status-pill income">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 10l7-7m0 0l7 7m-7-7v18"/></svg>
        Income
      </span>`;
    }
    return `<span class="status-pill expense">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 14l-7 7m0 0l-7-7m7 7V3"/></svg>
      Expense
    </span>`;
  }

  _renderHistoryActiveFilters() {
    const container = document.getElementById("historyActiveFilters");
    if (!container) return;
    const from = document.getElementById("dateFrom")?.value || "";
    const to = document.getElementById("dateTo")?.value || "";
    const source = document.getElementById("historySourceFilter")?.value || "all";
    const search = document.getElementById("historySearch")?.value.trim() || "";

    const filters = [];
    if (from) filters.push({ key: "dateFrom", label: `From: ${from}` });
    if (to) filters.push({ key: "dateTo", label: `To: ${to}` });
    if (source !== "all") filters.push({ key: "source", label: `Source: ${source}` });
    if (search) filters.push({ key: "search", label: `Search: “${search}”` });

    container.innerHTML = "";
    if (!filters.length) {
      container.hidden = true;
      container.setAttribute("aria-label", "No active history filters");
      return;
    }

    container.hidden = false;
    container.setAttribute("aria-label", "Active history filters");
    filters.forEach((filter) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = "chip filter-chip";
      chip.setAttribute("aria-label", `Remove filter ${filter.label}`);

      const text = document.createElement("span");
      text.textContent = filter.label;
      const remove = document.createElement("span");
      remove.className = "filter-chip-remove";
      remove.setAttribute("aria-hidden", "true");
      remove.textContent = "×";
      chip.append(text, remove);

      chip.addEventListener("click", () => {
        if (filter.key === "source") {
          const srcEl = document.getElementById("historySourceFilter");
          if (srcEl) srcEl.value = "all";
        } else {
          const input = document.getElementById(filter.key);
          if (input) input.value = "";
        }
        this.buildHistory();
      });
      container.appendChild(chip);
    });
  }

  clearHistoryFilters() {
    const fromEl = document.getElementById("dateFrom");
    const toEl = document.getElementById("dateTo");
    const srcEl = document.getElementById("historySourceFilter");
    const searchEl = document.getElementById("historySearch");
    if (fromEl) fromEl.value = "";
    if (toEl) toEl.value = "";
    if (srcEl) srcEl.value = "all";
    if (searchEl) searchEl.value = "";
    this.buildHistory();
  }

  renderHistoryPage(page) {
    this.historyPage = page;
    const totalPages = Math.ceil(this.historyFiltered.length / TRANSACTIONS_PER_PAGE);
    const start      = (page - 1) * TRANSACTIONS_PER_PAGE;
    const pageTxns   = this.historyFiltered.slice(start, start + TRANSACTIONS_PER_PAGE);
    const tbody      = document.getElementById("historyTableBody");
    tbody.innerHTML  = "";
    if (!pageTxns.length) {
      if (this.transactions.length === 0) {
        tbody.innerHTML = `
          <tr>
            <td colspan="6" class="p-0">
              <div class="flex flex-col items-center justify-center text-center py-14 px-4">
                <div class="w-14 h-14 rounded-full bg-[var(--surface-variant)] flex items-center justify-center text-[var(--muted-color)] mb-3">
                  <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6v6m0 0v6m0-6h6m-6 0H6"/></svg>
                </div>
                <h3 class="text-base font-semibold mb-1">No transactions recorded yet</h3>
                <p class="text-xs text-[var(--muted-color)] max-w-sm mb-4">Start by recording your earnings or expenses from the dashboard.</p>
                <button type="button" class="btn primary" onclick="document.querySelector('[data-page=\\'dashboard\\']').click();setTimeout(()=>document.getElementById('addAmount')?.focus(),150)">Add First Transaction</button>
              </div>
            </td>
          </tr>`;
      } else {
        tbody.innerHTML = `
          <tr>
            <td colspan="6" class="p-0">
              <div class="flex flex-col items-center justify-center text-center py-14 px-4">
                <div class="w-14 h-14 rounded-full bg-[var(--surface-variant)] flex items-center justify-center text-[var(--muted-color)] mb-3">
                  <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
                </div>
                <h3 class="text-base font-semibold mb-1">No transactions match your filters</h3>
                <p class="text-xs text-[var(--muted-color)] max-w-sm mb-4">Try adjusting your date range, source filter, or search keywords.</p>
                <button type="button" id="clearHistoryFiltersBtn" class="btn secondary">Clear Filters</button>
              </div>
            </td>
          </tr>`;
        const clrBtn = tbody.querySelector("#clearHistoryFiltersBtn");
        if (clrBtn) clrBtn.addEventListener("click", () => this.clearHistoryFilters());
      }
    } else {
      pageTxns.forEach((t) => {
        // Mobile stores previous_balance (snake_case)
        const cls      = t.amount >= 0 ? "amount-positive" : "amount-negative";
        const balAfter = (t.previous_balance || 0) + t.amount;
        const tr       = document.createElement("tr");
        tr.innerHTML = `
          <td>${new Date(t.date).toLocaleString()}</td>
          <td>${this.getTypeBadge(t.amount >= 0)}</td>
          <td>${escapeHtml(t.source)}</td>
          <td class="${cls}">${t.amount >= 0 ? "+" : ""}${t.amount.toLocaleString()}</td>
          <td>${balAfter.toLocaleString()}</td>
          <td class="history-actions">
            <button class="btn secondary btn-edit" data-id="${t.id}">${getIcon('edit')} Edit</button>
            <button class="btn danger btn-delete" data-id="${t.id}" aria-label="Delete transaction" title="Delete transaction">${getIcon('trash2')}</button>
          </td>`;
        tr.querySelector(".btn-edit").addEventListener("click",   () => this.showEditModal(t.id));
        tr.querySelector(".btn-delete").addEventListener("click", () => this.deleteTransaction(t.id));
        tbody.appendChild(tr);
      });
    }
    // Also render mobile card list
    this.renderHistoryCardList(pageTxns);
    this.renderPaginationControls(totalPages, page);
  }

  renderHistoryCardList(txns) {
    const container = document.getElementById("historyCardList");
    if (!container) return;
    if (!txns.length) {
      if (this.transactions.length === 0) {
        container.innerHTML = `
          <div class="flex flex-col items-center justify-center text-center py-12 px-4 rounded-xl border border-[var(--outline)] bg-[var(--surface-color)]">
            <div class="w-14 h-14 rounded-full bg-[var(--surface-variant)] flex items-center justify-center text-[var(--muted-color)] mb-3">
              <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6v6m0 0v6m0-6h6m-6 0H6"/></svg>
            </div>
            <h3 class="text-base font-semibold mb-1">No transactions recorded yet</h3>
            <p class="text-xs text-[var(--muted-color)] max-w-sm mb-4">Start by recording your earnings or expenses from the dashboard.</p>
            <button type="button" class="btn primary" onclick="document.querySelector('[data-page=\\'dashboard\\']').click();setTimeout(()=>document.getElementById('addAmount')?.focus(),150)">Add First Transaction</button>
          </div>`;
      } else {
        container.innerHTML = `
          <div class="flex flex-col items-center justify-center text-center py-12 px-4 rounded-xl border border-[var(--outline)] bg-[var(--surface-color)]">
            <div class="w-14 h-14 rounded-full bg-[var(--surface-variant)] flex items-center justify-center text-[var(--muted-color)] mb-3">
              <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
            </div>
            <h3 class="text-base font-semibold mb-1">No transactions match your filters</h3>
            <p class="text-xs text-[var(--muted-color)] max-w-sm mb-4">Try adjusting your date range, source filter, or search keywords.</p>
            <button type="button" id="clearHistoryFiltersMobileBtn" class="btn secondary">Clear Filters</button>
          </div>`;
        const clrMobileBtn = container.querySelector("#clearHistoryFiltersMobileBtn");
        if (clrMobileBtn) clrMobileBtn.addEventListener("click", () => this.clearHistoryFilters());
      }
      return;
    }
    container.innerHTML = txns.map((t) => {
      const cls      = t.amount >= 0 ? "amount-positive" : "amount-negative";
      const balAfter = (t.previous_balance || 0) + t.amount;
      return `
        <div class="history-card">
          <div class="history-card-header">
            <span class="history-card-date">${new Date(t.date).toLocaleString()}</span>
            ${this.getTypeBadge(t.amount >= 0)}
          </div>
          <div class="history-card-body">
            <div><span class="history-card-label">Source</span><span class="history-card-value">${escapeHtml(t.source)}</span></div>
            <div><span class="history-card-label">Amount</span><span class="history-card-value ${cls}">${t.amount >= 0 ? "+" : ""}${t.amount.toLocaleString()}</span></div>
            <div><span class="history-card-label">Balance After</span><span class="history-card-value">${balAfter.toLocaleString()}</span></div>
          </div>
          <div class="history-card-actions">
            <button class="btn secondary btn-edit" data-id="${t.id}">${getIcon('edit')} Edit</button>
            <button class="btn danger btn-delete" data-id="${t.id}" aria-label="Delete transaction" title="Delete transaction">${getIcon('trash2')}</button>
          </div>
        </div>
      `;
    }).join("");
    // Re-attach event listeners
    container.querySelectorAll(".btn-edit").forEach(btn => {
      btn.addEventListener("click", () => this.showEditModal(btn.dataset.id));
    });
    container.querySelectorAll(".btn-delete").forEach(btn => {
      btn.addEventListener("click", () => this.deleteTransaction(btn.dataset.id));
    });
  }

  renderPaginationControls(totalPages, currentPage) {
    const html = this._paginationHTML(totalPages, currentPage);
    ["paginationControlsTop", "paginationControlsBottom"].forEach((id) => {
      const el = document.getElementById(id);
      if (!el) return;
      el.innerHTML = html;
      el.querySelectorAll("button[data-page]").forEach((btn) => {
        btn.addEventListener("click", () => this.renderHistoryPage(parseInt(btn.dataset.page)));
      });
    });
  }

  _paginationHTML(totalPages, cp) {
    if (totalPages <= 1) return "";
    let html = `<button class="btn secondary" ${cp === 1 ? "disabled" : ""} data-page="${cp - 1}">Previous</button>`;
    const start = Math.max(1, cp - 2), end = Math.min(totalPages, cp + 2);
    if (start > 1) html += `<button class="btn secondary" data-page="1">1</button><span>...</span>`;
    for (let i = start; i <= end; i++)
      html += `<button class="btn ${i === cp ? "primary" : "secondary"}" data-page="${i}">${i}</button>`;
    if (end < totalPages)
      html += `<span>...</span><button class="btn secondary" data-page="${totalPages}">${totalPages}</button>`;
    html += `<button class="btn secondary" ${cp === totalPages ? "disabled" : ""} data-page="${cp + 1}">Next</button>`;
    return html;
  }

  // ─────────────────────────────────────────────────────────────
  //  Settings UI
  // ─────────────────────────────────────────────────────────────
  updateSettingsUI() {
    const goal = this.goal;
    const goalInput = document.getElementById("goalInput");
    if (goalInput) goalInput.value = goal > 0 ? goal : "";
    document.getElementById("currentGoalText").textContent = goal > 0
      ? `Current Goal: ${goal.toLocaleString()} coins`
      : "No goal set. Enter a target to start tracking progress.";
    document.getElementById("goalProgressText").textContent = goal > 0
      ? `You are ${this.progress}% of the way to your goal.`
      : "";
    this._clearFieldError(goalInput, "goalInputError");
    this.renderQuickActionList();
    this.renderCategoryList("income");
    this.renderCategoryList("expense");
    const delBtn = document.getElementById("deleteProfileBtn");
    if (delBtn) delBtn.disabled = this.currentProfile === "Default";

    // Update About section ("Me" details)
    const aboutUsername = document.getElementById("aboutUsername");
    if (aboutUsername) aboutUsername.textContent = this.username || "User";
    const aboutEmail = document.getElementById("aboutUserEmail");
    if (aboutEmail) aboutEmail.textContent = auth.currentUser?.email || (this.username ? `${this.username}@cointracker.app` : "Authenticated");
    const aboutRole = document.getElementById("aboutUserRoleBadge");
    if (aboutRole) {
      const roleText = (this.role || "user").toUpperCase();
      aboutRole.textContent = roleText;
      if (this.role === "admin") {
        aboutRole.className = "status-pill warning uppercase";
      } else {
        aboutRole.className = "status-pill neutral uppercase";
      }
    }
    const aboutProfile = document.getElementById("aboutActiveProfile");
    if (aboutProfile) aboutProfile.textContent = this.currentProfile;
    const aboutUid = document.getElementById("aboutUserId");
    if (aboutUid) aboutUid.textContent = this.uid ? `${this.uid.slice(0, 10)}…` : "N/A";
  }

  renderQuickActionList() {
    const listEl = document.getElementById("quickActionList");
    if (!listEl) return;
    const actions = this.quickActions;
    if (!actions.length) {
      listEl.innerHTML = `<p class="muted-text">No quick actions yet.</p>
        <button type="button" class="btn primary quick-action-empty-btn">Create your first quick action</button>`;
      listEl.querySelector(".quick-action-empty-btn")?.addEventListener("click", () => {
        document.getElementById("quickActionText")?.focus();
      });
      return;
    }
    listEl.innerHTML = "";
    actions.forEach((action, i) => {
      const item = document.createElement("div");
      item.className = "quick-action-list-item";
      item.innerHTML = `
        <div class="quick-action-details">
          <span class="quick-action-text">${escapeHtml(action.text)}</span>
          <span class="quick-action-amount ${action.is_positive ? "positive" : "negative"}">${action.is_positive ? "+" : "-"}${action.value}</span>
        </div>
        <button type="button" class="delete-btn" data-index="${i}" aria-label="Delete quick action">Delete</button>`;
      item.querySelector(".delete-btn").addEventListener("click", () => this.deleteQuickAction(i));
      listEl.appendChild(item);
    });
  }

  renderCategoryList(type) {
    const isIncome = type === "income";
    const cats     = isIncome ? this.incomeCategories : this.expenseCategories;
    const listEl   = document.getElementById(isIncome ? "incomeCategoryList" : "expenseCategoryList");
    if (!listEl) return;
    listEl.innerHTML = "";
    if (!cats.length) {
      listEl.innerHTML = `<p class="muted-text">No ${isIncome ? "income" : "expense"} categories yet.</p>
        <button type="button" class="btn primary category-empty-btn">Add your first ${isIncome ? "income" : "expense"} category</button>`;
      listEl.querySelector(".category-empty-btn")?.addEventListener("click", () => {
        document.getElementById(isIncome ? "newIncomeCategory" : "newExpenseCategory")?.focus();
      });
      return;
    }
    cats.forEach((cat, i) => {
      const item = document.createElement("div");
      item.className = "category-item";
      item.innerHTML = `<span>${escapeHtml(cat)}</span><button type="button" class="delete-btn" aria-label="Delete ${isIncome ? "income" : "expense"} category ${escapeHtml(cat)}">×</button>`;
      item.querySelector(".delete-btn").addEventListener("click", () => this.deleteCategory(type, i));
      listEl.appendChild(item);
    });
  }

  // ─────────────────────────────────────────────────────────────
  //  Achievements & Notifications
  // ─────────────────────────────────────────────────────────────
  updateAchievementsUI() {
    const achs = this.achievements;
    const grid = document.getElementById("achievements-grid");
    const noMsg = document.getElementById("no-achievements-msg");
    if (!grid) return;
    grid.innerHTML = "";
    if (!achs.length) {
      if (noMsg) { noMsg.style.display = "block"; grid.appendChild(noMsg); }
    } else {
      if (noMsg) noMsg.style.display = "none";
      achs.forEach((a) => {
        const div = document.createElement("div");
        div.className = "achievement-item";
        div.innerHTML = `<div class="achievement-icon">${getIcon(a.icon)}</div>
                         <div>
                           <div class="achievement-name">${escapeHtml(a.name)}</div>
                           <div class="achievement-desc">${escapeHtml(a.desc)}</div>
                         </div>`;
        grid.appendChild(div);
      });
    }
    // Badge updates are now handled by updateNotificationBadges() based on unread count
  }

  updateRecentActivityUI() {
    const listEl = document.getElementById("recentActivityList");
    if (!listEl) return;
    const recent = [...this.transactions]
      .sort((a, b) => new Date(b.date) - new Date(a.date))
      .slice(0, 3);
    if (!recent.length) {
      listEl.innerHTML = `<p class="muted-text m-0">No transactions yet. Add your first entry to see it here.</p>`;
      return;
    }
    listEl.innerHTML = recent.map((t) => {
      const isIncome = t.amount >= 0;
      const amount = `${isIncome ? "+" : ""}${t.amount.toLocaleString()}`;
      return `
        <div class="recent-activity-row">
          <span class="recent-activity-icon" aria-hidden="true">${isIncome ? getIcon("dollarSign") : getIcon("shoppingCart")}</span>
          <div class="recent-activity-info">
            <div class="recent-activity-source">${escapeHtml(t.source)}</div>
            <div class="recent-activity-date">${new Date(t.date).toLocaleDateString()}</div>
          </div>
          <span class="recent-activity-amount ${isIncome ? "positive" : "negative"}">${amount}</span>
        </div>`;
    }).join("");
  }

  updateNotificationsUI() {
    const readIds = new Set(this.getReadNotificationIds());
    const alerts = this.notificationAlerts;
    const achs = this.achievements;

    const alertsEl = document.getElementById("alertsList");
    if (alertsEl) {
      alertsEl.innerHTML = alerts.length === 0
        ? `<p class="muted-text">No alerts right now. Keep tracking!</p>`
        : alerts.map((a) => {
            const id = `alert-${hashString(`${a.title}|${a.sub}`)}`;
            const isRead = readIds.has(id);
            const state = isRead
              ? `<span class="status-pill neutral notif-state">Read</span>`
              : `<span class="status-pill income notif-state">New</span>`;
            return `
              <div class="alert-item ${isRead ? 'read' : ''}" data-notif-id="${id}">
                <div class="alert-icon">${a.icon}</div>
                <div class="alert-text">
                  <div class="alert-title">${escapeHtml(a.title)}</div>
                  <div class="alert-sub">${escapeHtml(a.sub)}</div>
                </div>
                ${state}
              </div>`;
          }).join("");
    }

    const notifEl = document.getElementById("notif-achievements-list");
    if (notifEl) {
      notifEl.innerHTML = achs.length === 0
        ? `<div class="empty-achievements"><p>No achievements unlocked yet.</p>
           <p class="muted-text" style="margin-top:6px">Add transactions to start earning badges!</p></div>`
        : achs.map((a) => {
            const id = `achievement-${a.id}`;
            const isRead = readIds.has(id);
            const state = isRead
              ? `<span class="status-pill neutral notif-state">Read</span>`
              : `<span class="status-pill income notif-state">New</span>`;
            return `
              <div class="notif-achievement-item ${isRead ? 'read' : ''}" data-notif-id="${id}">
                <div class="notif-achievement-icon">${getIcon(a.icon)}</div>
                <div class="notif-achievement-info">
                  <div class="notif-achievement-name">${escapeHtml(a.name)}</div>
                  <div class="notif-achievement-desc">${escapeHtml(a.desc)}</div>
                </div>
                ${state}
              </div>`;
          }).join("");
    }

    // Update badges
    this.updateNotificationBadges();
  }

  // ─────────────────────────────────────────────────────────────
  //  Transactions — write snake_case to match mobile
  // ─────────────────────────────────────────────────────────────
  async addTransaction(amount, source) {
    if (!amount || amount === 0 || !source) { this.showToast("Invalid amount or source.", "error"); return; }
    const txn = {
      id:               crypto.randomUUID(),
      date:             new Date().toISOString(),
      amount:           Math.round(amount),
      source,
      previous_balance: this.balance,  // snake_case matches mobile
    };
    this.userDataDoc.profiles[this.currentProfile].transactions.push(txn);
    this._recalcPreviousBalances();
    await this.saveUserData();
    this.updateAllUI();
    return txn;
  }

  async addFromDashboard() {
    const input = document.getElementById("addAmount");
    const amount = this._validateAmountInput("addAmount", "addAmountError");
    if (amount === null) return;
    const src = document.getElementById("addSource").value;
    const snapshot = this._snapshotWriteState();
    const profileName = this.currentProfile;
    let createdTxn = null;
    const ok = await this._runWrite(
      "add",
      document.getElementById("addCoinsBtn"),
      "Adding…",
      async () => {
        createdTxn = await this.addTransaction(amount, src);
      },
      "Could not add coins. Please try again."
    );
    if (ok === null) return;
    if (!ok) {
      this._restoreWriteState(snapshot);
      return;
    }
    input.value = "";
    this._clearFieldError(input, "addAmountError");
    if (createdTxn) {
      this.showUndoToast(`Added ${amount.toLocaleString()} coins!`, () => {
        this._undoAddTransaction(profileName, createdTxn.id);
      });
    } else {
      this.showToast(`Added ${amount.toLocaleString()} coins!`, "success");
    }
  }

  async spendFromDashboard() {
    const input = document.getElementById("spendAmount");
    const amount = this._validateAmountInput("spendAmount", "spendAmountError");
    if (amount === null) return;
    const cat = document.getElementById("spendCategory").value;
    const snapshot = this._snapshotWriteState();
    const profileName = this.currentProfile;
    let createdTxn = null;
    const ok = await this._runWrite(
      "spend",
      document.getElementById("spendCoinsBtn"),
      "Spending…",
      async () => {
        createdTxn = await this.addTransaction(-amount, cat);
      },
      "Could not record spending. Please try again."
    );
    if (ok === null) return;
    if (!ok) {
      this._restoreWriteState(snapshot);
      return;
    }
    input.value = "";
    this._clearFieldError(input, "spendAmountError");
    if (createdTxn) {
      this.showUndoToast(`Spent ${amount.toLocaleString()} coins!`, () => {
        this._undoAddTransaction(profileName, createdTxn.id);
      });
    } else {
      this.showToast(`Spent ${amount.toLocaleString()} coins!`, "success");
    }
  }

  showEditModal(transactionId) {
    const t = this.transactions.find((t) => t.id === transactionId);
    if (!t) return;
    document.getElementById("transactionId").value     = transactionId;
    document.getElementById("transactionAmount").value = Math.abs(t.amount);
    document.getElementById("transactionSource").value = t.source;
    document.getElementById("sourceLabel").textContent = t.amount >= 0 ? "Source" : "Category";
    const d = new Date(t.date);
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    document.getElementById("transactionDate").value = d.toISOString().slice(0, 16);
    this.showModal("transactionModal");
  }

  async saveEditedTransaction() {
    const id = document.getElementById("transactionId").value;
    const amount = this._validateAmountInput("transactionAmount", "transactionAmountError");
    if (amount === null) return;
    const source = document.getElementById("transactionSource").value;
    const dateValue = document.getElementById("transactionDate").value;
    const dateObj = dateValue ? new Date(dateValue) : null;
    if (!id || !source.trim()) {
      this.showToast("Fill all fields correctly.", "error");
      return;
    }
    if (!dateObj || Number.isNaN(dateObj.getTime())) {
      this.showToast("Enter a valid date and time.", "error");
      return;
    }
    const txns = this.userDataDoc.profiles[this.currentProfile].transactions;
    const idx  = txns.findIndex((t) => t.id === id);
    if (idx === -1) {
      this.showToast("Transaction not found.", "error");
      return;
    }
    const snapshot = this._snapshotWriteState();
    const ok = await this._runWrite(
      "edit",
      document.getElementById("saveTransactionBtn"),
      "Saving…",
      async () => {
        const sign = txns[idx].amount >= 0 ? 1 : -1;
        txns[idx] = { ...txns[idx], amount: sign * amount, source, date: dateObj.toISOString() };
        this._recalcPreviousBalances();
        await this.saveUserData();
      },
      "Could not save changes. Please try again."
    );
    if (ok === null) return;
    if (!ok) {
      this._restoreWriteState(snapshot);
      return;
    }
    this._clearFieldError(document.getElementById("transactionAmount"), "transactionAmountError");
    this.closeModal("transactionModal");
    this.showToast("Transaction updated!", "success");
    this.updateAllUI();
  }

  async deleteTransaction(transactionId) {
    if (!await this.confirm("Delete this transaction?")) return;
    const profileName = this.currentProfile;
    const profile = this.userDataDoc.profiles[profileName];
    const deletedTx = profile.transactions.find((t) => t.id === transactionId);
    profile.transactions = profile.transactions.filter((t) => t.id !== transactionId);
    this._recalcPreviousBalances();
    await this.saveUserData();
    this.updateAllUI();
    // UNDO snackbar
    if (deletedTx) {
      this.showUndoToast("Transaction deleted", async () => {
        const targetProfile = this.userDataDoc.profiles[profileName];
        if (!targetProfile) return;
        if (!targetProfile.transactions.some((t) => t.id === deletedTx.id)) {
          targetProfile.transactions.push(deletedTx);
          this._recalcPreviousBalances();
          await this.saveUserData();
          this.updateAllUI();
          this.showToast("Transaction restored.", "success");
        }
      });
    }
  }

  async _undoAddTransaction(profileName, transactionId) {
    const profile = this.userDataDoc.profiles[profileName];
    if (!profile) return;
    const idx = profile.transactions.findIndex((t) => t.id === transactionId);
    if (idx === -1) {
      this.showToast("Transaction no longer exists.", "error");
      return;
    }
    profile.transactions.splice(idx, 1);
    this._recalcPreviousBalances();
    await this.saveUserData();
    this.updateAllUI();
    this.showToast("Transaction undone.", "success");
  }

  showUndoToast(message, onUndo) {
    const toast = document.getElementById("toast");
    if (!toast) return;
    if (this._toastTimeout) {
      clearTimeout(this._toastTimeout);
      this._toastTimeout = null;
    }
    toast.textContent = "";
    const text = document.createElement("span");
    text.className = "toast-message";
    text.textContent = message;
    const undoBtn = document.createElement("button");
    undoBtn.type = "button";
    undoBtn.className = "toast-undo";
    undoBtn.setAttribute("aria-label", `Undo: ${message}`);
    undoBtn.textContent = "Undo";
    toast.append(text, undoBtn);
    toast.className = "toast success show";
    this._toastTimeout = setTimeout(() => {
      toast.classList.remove("show");
      this._toastTimeout = null;
    }, 8000);
    undoBtn.addEventListener("click", () => {
      if (this._toastTimeout) {
        clearTimeout(this._toastTimeout);
        this._toastTimeout = null;
      }
      toast.classList.remove("show");
      onUndo();
    });
  }

  _recalcPreviousBalances() {
    const profile = this.userDataDoc.profiles[this.currentProfile];
    if (!profile) return;
    const sorted = [...profile.transactions].sort((a, b) => new Date(a.date) - new Date(b.date));
    let running = 0;
    sorted.forEach((t) => { t.previous_balance = running; running += t.amount; });
    profile.transactions = sorted;
  }

  // ─────────────────────────────────────────────────────────────
  //  Settings actions — all snake_case
  // ─────────────────────────────────────────────────────────────
  async setGoal() {
    const input = document.getElementById("goalInput");
    const val = Number(input?.value);
    if (!input || !Number.isInteger(val) || val <= 0) {
      this._setFieldError(input, "goalInputError", "Enter a goal of at least 1 coin.");
      input?.focus({ preventScroll: true });
      return;
    }
    this._clearFieldError(input, "goalInputError");
    this._setSettings({ goal: val });
    await this.saveUserData();
    this.showToast("Goal updated!", "success");
    this.updateAllUI();
  }

  async toggleTheme() {
    const newDark = !this.settings.dark_mode;
    this._setSettings({ dark_mode: newDark });
    await this.saveUserData();
    this.applyTheme();
  }

  async addQuickAction() {
    const text       = document.getElementById("quickActionText").value.trim();
    const amount     = parseInt(document.getElementById("quickActionAmount").value);
    const is_positive = document.getElementById("quickActionType").value === "positive";
    if (!text || !amount || amount <= 0) { this.showToast("Enter valid action text and amount.", "error"); return; }
    const actions = [...this.quickActions, { text, value: amount, is_positive }];
    this._setSettings({ quick_actions: actions });
    await this.saveUserData();
    document.getElementById("quickActionText").value   = "";
    document.getElementById("quickActionAmount").value = "";
    this.showToast("Quick action added!", "success");
    this.updateAllUI();
  }

  async deleteQuickAction(index) {
    const actions = this.quickActions.filter((_, i) => i !== index);
    this._setSettings({ quick_actions: actions });
    await this.saveUserData();
    this.showToast("Quick action removed.", "success");
    this.updateAllUI();
  }

  async addCategory(type) {
    const isIncome = type === "income";
    const inputId  = isIncome ? "newIncomeCategory" : "newExpenseCategory";
    const name     = document.getElementById(inputId).value.trim();
    if (!name) { this.showToast("Category name is required.", "error"); return; }
    const list = [...(isIncome ? this.incomeCategories : this.expenseCategories)];
    if (list.includes(name)) { this.showToast("Category already exists.", "error"); return; }
    list.push(name);
    this._setSettings(isIncome ? { income_categories: list } : { expense_categories: list });
    await this.saveUserData();
    document.getElementById(inputId).value = "";
    this.showToast(`Category '${name}' added.`, "success");
    this.updateAllUI();
  }

  async deleteCategory(type, index) {
    const isIncome = type === "income";
    const list = [...(isIncome ? this.incomeCategories : this.expenseCategories)];
    list.splice(index, 1);
    this._setSettings(isIncome ? { income_categories: list } : { expense_categories: list });
    await this.saveUserData();
    this.showToast("Category removed.", "success");
    this.updateAllUI();
  }

  async resetCategories(type) {
    const isIncome = type === "income";
    this._setSettings(isIncome ? { income_categories: [] } : { expense_categories: [] });
    await this.saveUserData();
    this.showToast("Categories reset to defaults.", "success");
    this.updateAllUI();
  }

  // ─────────────────────────────────────────────────────────────
  //  Profiles — nested inside single user_data doc (matches mobile)
  // ─────────────────────────────────────────────────────────────
  async switchProfile(name) {
    this.showLoading(`Retrieving profile '${name}'...`, "Syncing records from database...");
    try {
      this.currentProfile = name;
      this.userDataDoc.last_active_profile = name;
      await this.saveUserData();
      this.updateAllUI();
      this.showToast(`Switched to ${name}.`, "success");
    } catch (err) {
      console.error("switchProfile error:", err);
      this.showToast("Failed to switch profile.", "error");
    } finally {
      this.hideLoading();
    }
  }

  async createProfile() {
    const name = document.getElementById("newProfileName").value.trim();
    if (!name || name.length < 2 || /[/.#$[\]]/.test(name)) {
      this.showToast("Invalid name (min 2 chars, no special chars).", "error"); return;
    }
    if (this.allProfiles.includes(name)) { this.showToast("Profile already exists.", "error"); return; }
    const now = new Date().toISOString();
    this.userDataDoc.profiles[name] = {
      transactions: [],
      settings:     defaultSettingsMap(this.settings.dark_mode),
      last_updated: now,
    };
    this.allProfiles = Object.keys(this.userDataDoc.profiles).sort();
    this.currentProfile = name;
    this.userDataDoc.last_active_profile = name;
    await this.saveUserData();
    this.closeModal("profileModal");
    this.showToast(`Profile '${name}' created!`, "success");
    this.updateAllUI();
  }

  async deleteCurrentProfile() {
    if (this.currentProfile === "Default") { this.showToast("Cannot delete the Default profile.", "error"); return; }
    if (!await this.confirm(`Delete profile '${this.currentProfile}'? This cannot be undone.`)) return;
    delete this.userDataDoc.profiles[this.currentProfile];
    this.allProfiles = Object.keys(this.userDataDoc.profiles).sort();
    this.currentProfile = "Default";
    this.userDataDoc.last_active_profile = "Default";
    await this.saveUserData();
    this.showToast("Profile deleted. Switched to Default.", "success");
    this.updateAllUI();
  }

  // ─────────────────────────────────────────────────────────────
  //  Data management
  // ─────────────────────────────────────────────────────────────
  exportData() {
    try {
      const blob = new Blob([JSON.stringify(this.transactions, null, 2)], { type: "application/json" });
      const url  = URL.createObjectURL(blob);
      const a    = Object.assign(document.createElement("a"), {
        href: url, download: `coin_tracker_${this.currentProfile}_${new Date().toISOString().slice(0, 10)}.json`,
      });
      document.body.appendChild(a); a.click(); document.body.removeChild(a);
      URL.revokeObjectURL(url);
      this.showToast("Data exported!", "success");
    } catch { this.showToast("Export failed.", "error"); }
  }

  createHiddenFileInput() {
    const fi = Object.assign(document.createElement("input"), {
      type: "file", id: "jsonImporter", accept: ".json,application/json",
    });
    fi.style.display = "none";
    fi.addEventListener("change", (e) => this.handleFileImport(e));
    document.body.appendChild(fi);
  }

  triggerImport() { document.getElementById("jsonImporter").click(); }

  async handleFileImport(e) {
    const file = e.target.files[0];
    if (!file) return;
    e.target.value = null;
    this.showLoading("Importing and updating database...", "Writing transactions to Cloud Firestore...");
    try {
      const data = JSON.parse(await file.text());
      if (!Array.isArray(data)) { this.showToast("Invalid file. Must be a JSON array.", "error"); return; }
      this.userDataDoc.profiles[this.currentProfile].transactions = data;
      this._recalcPreviousBalances();
      await this.saveUserData();
      this.showToast(`Imported ${data.length} transactions!`, "success");
      this.updateAllUI();
    } catch {
      this.showToast("Invalid or corrupt JSON file.", "error");
    } finally {
      this.hideLoading();
    }
  }

  async deleteAllData() {
    if (!await this.confirm("Delete ALL data for ALL profiles? This cannot be undone.")) return;
    this.showLoading("Clearing records from database...", "Updating Cloud Firestore...");
    try {
      const now = new Date().toISOString();
      this.userDataDoc = {
        last_active_profile: "Default",
        profiles: {
          Default: {
            transactions: [],
            settings:     defaultSettingsMap(this.settings.dark_mode),
            last_updated: now,
          },
        },
      };
      this.allProfiles    = ["Default"];
      this.currentProfile = "Default";
      await this.saveUserData();
      this.showToast("All data deleted.", "success");
      this.updateAllUI();
    } finally {
      this.hideLoading();
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  Account deletion
  // ─────────────────────────────────────────────────────────────
  async deleteAccount() {
    const password = document.getElementById("deleteAccountPassword").value;
    if (!password) { this.showToast("Password required.", "error"); return; }
    try {
      const user       = auth.currentUser;
      const credential = EmailAuthProvider.credential(user.email, password);
      await reauthenticateWithCredential(user, credential);
      await deleteDoc(doc(db, "user_data", this.uid));
      await deleteDoc(doc(db, "users",     this.uid));
      await deleteUser(user);
      window.location.href = "/login.html";
    } catch (err) {
      const msg = err.code === "auth/wrong-password" || err.code === "auth/invalid-credential"
        ? "Incorrect password." : "Failed to delete account.";
      this.showToast(msg, "error");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  Broadcast
  // ─────────────────────────────────────────────────────────────
  async checkBroadcast() {
    try {
      const snap = await getDoc(doc(db, "app_config", "broadcast"));
      if (snap.exists() && snap.data().message) this.showToast(snap.data().message, "broadcast");
    } catch { /* non-fatal */ }
  }

  async logout() { await signOut(auth); window.location.href = "/login.html"; }

  // ─────────────────────────────────────────────────────────────
  //  Charts
  // ─────────────────────────────────────────────────────────────
  createOrUpdateChart(canvasId, type, labels, data) {
    if (this.charts[canvasId]) { this.charts[canvasId].destroy(); delete this.charts[canvasId]; }
    let ctx = document.getElementById(canvasId);
    if (!ctx) return;
    
    // Handle empty data
    if (!data || !data.length || data.every(d => d === 0)) {
      const container = ctx.parentElement;
      if (container) {
        const canvasLabel = ctx.getAttribute("aria-label") || "Analytics chart";
        container.innerHTML = "";
        const newCanvas = document.createElement("canvas");
        newCanvas.id = canvasId;
        newCanvas.setAttribute("role", "img");
        newCanvas.setAttribute("aria-label", canvasLabel);
        container.appendChild(newCanvas);

        const overlay = document.createElement("div");
        overlay.className = "chart-empty-overlay";
        overlay.innerHTML = `
          <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          <p>No data for this period</p>`;
        const emptyBtn = document.createElement("button");
        emptyBtn.type = "button";
        emptyBtn.className = "btn primary";
        emptyBtn.textContent = "Add transactions to see analytics";
        emptyBtn.addEventListener("click", () => {
          this.showPage("dashboard");
          document.querySelector('[data-page="dashboard"]')?.focus();
        });
        overlay.appendChild(emptyBtn);
        container.appendChild(overlay);
        ctx = newCanvas;
      }
      // Clear summary for empty state
      this.updateChartSummary(canvasId, [], []);
      return;
    }
    
    ctx.parentElement?.querySelector(".chart-empty-overlay")?.remove();
    const s       = getComputedStyle(document.documentElement);
    const text    = s.getPropertyValue("--text-primary").trim();
    const grid    = s.getPropertyValue("--border").trim();
    const brand   = s.getPropertyValue("--brand").trim();
    const income  = s.getPropertyValue("--income").trim();
    const expense = s.getPropertyValue("--expense").trim();
    const info    = s.getPropertyValue("--info").trim();
    const warning = s.getPropertyValue("--warning").trim();
    const palette = [brand, income, info, warning, expense];
    const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    this.charts[canvasId] = new Chart(ctx, {
      type,
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: type === "doughnut" ? palette : brand,
          borderColor: type === "line" ? brand : "transparent",
          borderWidth: type === "line" ? 2.5 : 2,
          tension: type === "line" ? 0.35 : 0.1,
          fill: false,
          pointBackgroundColor: type === "line" ? brand : undefined,
          pointBorderColor: type === "line" ? brand : undefined,
          pointBorderWidth: 0,
          pointRadius: type === "line" ? 3 : 0,
          pointHoverRadius: 6,
        }],
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        animation: prefersReducedMotion ? false : undefined,
        plugins: {
          legend: { display: type === "doughnut", position: "bottom", labels: { color: text } },
          tooltip: {
            callbacks: {
              label: (item) => {
                const val = Number(item.raw || 0).toLocaleString();
                return type === "line" ? ` Balance: ${val} coins` : ` ${item.label}: ${val} coins`;
              }
            }
          }
        },
        scales: type !== "doughnut" ? {
          y: { ticks: { color: text }, grid: { color: grid } },
          x: { ticks: { color: text }, grid: { color: grid } },
        } : {},
        onClick: (event, elements) => {
          if (!elements || !elements.length) return;
          const idx = elements[0].index;
          if (canvasId === "timelineChart") {
            const dateLabel = labels[idx];
            this._showChartSelection("timelineChart", dateLabel, null);
          } else if (canvasId === "earningsChart" || canvasId === "spendingChart") {
            const sourceLabel = labels[idx];
            this._showChartSelection(canvasId, null, sourceLabel);
          }
        },
      },
    });

    // Populate aria-live summary for accessibility
    this.updateChartSummary(canvasId, labels, data);
  }

  // Helper to update chart summary for screen readers
  updateChartSummary(canvasId, labels, data) {
    const summaryEl = document.getElementById(`${canvasId}Summary`);
    if (!summaryEl) return;
    if (!data || !data.length) {
      summaryEl.textContent = "No data for this period";
      return;
    }

    let summary = "";
    if (canvasId === "timelineChart") {
      // Balance trend: [min] to [max] over [period]
      const min = Math.min(...data);
      const max = Math.max(...data);
      const period = this.analyticsPeriod === "lifetime" ? "all time" : this.analyticsPeriod;
      summary = `Balance trend: ${min.toLocaleString()} to ${max.toLocaleString()} over ${period}`;
    } else if (canvasId === "earningsChart") {
      // Earnings breakdown: [top 3 sources with amounts]
      const pairs = labels.map((label, i) => ({ label, value: data[i] }))
        .filter(p => p.value > 0)
        .sort((a, b) => b.value - a.value)
        .slice(0, 3);
      if (pairs.length > 0) {
        const parts = pairs.map(p => `${p.label}: ${p.value.toLocaleString()}`);
        summary = `Earnings breakdown: ${parts.join(", ")}`;
      } else {
        summary = "No earnings data for this period";
      }
    } else if (canvasId === "spendingChart") {
      // Spending breakdown: [top 3 categories with amounts]
      const pairs = labels.map((label, i) => ({ label, value: data[i] }))
        .filter(p => p.value > 0)
        .sort((a, b) => b.value - a.value)
        .slice(0, 3);
      if (pairs.length > 0) {
        const parts = pairs.map(p => `${p.label}: ${p.value.toLocaleString()}`);
        summary = `Spending breakdown: ${parts.join(", ")}`;
      } else {
        summary = "No spending data for this period";
      }
    }
    summaryEl.textContent = summary;
  }

  // ─────────────────────────────────────────────────────────────
  //  Chart-to-History navigation (v4.0.3)
  // ─────────────────────────────────────────────────────────────

  /**
   * Show the chart selection bar with a description and a "View matching
   * transactions" button that navigates to History with pre-set filters.
   * @param {string} chartId - 'timelineChart' | 'earningsChart' | 'spendingChart'
   * @param {string|null} dateLabel - Localised date string from timeline (e.g. "1/11/2026")
   * @param {string|null} source   - Source/category label from doughnut charts
   */
  _showChartSelection(chartId, dateLabel, source) {
    const bar = document.getElementById("chartSelectionBar");
    const desc = document.getElementById("chartSelectionDesc");
    const btn  = document.getElementById("chartSelectionViewBtn");
    if (!bar || !desc || !btn) return;

    let descText = "";
    if (chartId === "timelineChart" && dateLabel) {
      // Try to resolve the localised date label back to YYYY-MM-DD for the filter
      let isoDate = "";
      try {
        // The label is produced by toLocaleDateString() — parse it back
        const parsed = new Date(dateLabel);
        if (!isNaN(parsed.getTime())) {
          isoDate = parsed.toISOString().slice(0, 10);
        }
      } catch (_) { /* ignored */ }
      descText = `Balance on ${dateLabel}`;
      btn.onclick = () => {
        this._clearChartSelection();
        const fromEl = document.getElementById("dateFrom");
        const toEl   = document.getElementById("dateTo");
        const srcEl  = document.getElementById("historySourceFilter");
        if (fromEl) fromEl.value = isoDate;
        if (toEl)   toEl.value   = isoDate;
        if (srcEl)  srcEl.value  = "all";
        this.showPage("history");
        this.buildHistory();
        document.getElementById("historySearch")?.focus();
      };
    } else if (source) {
      const chartLabel = chartId === "earningsChart" ? "earnings" : "spending";
      descText = `${source} — ${chartLabel}`;
      btn.onclick = () => {
        this._clearChartSelection();
        const srcEl  = document.getElementById("historySourceFilter");
        const fromEl = document.getElementById("dateFrom");
        const toEl   = document.getElementById("dateTo");
        // Apply analytics period dates so the filter matches the visible chart
        if (fromEl) fromEl.value = "";
        if (toEl)   toEl.value   = "";
        if (srcEl) {
          // Set to source if it exists as an option, otherwise fall back to search
          const hasOption = [...srcEl.options].some(o => o.value === source);
          if (hasOption) {
            srcEl.value = source;
          } else {
            srcEl.value = "all";
            const searchEl = document.getElementById("historySearch");
            if (searchEl) searchEl.value = source;
          }
        }
        this.showPage("history");
        this.buildHistory();
        document.getElementById("historySearch")?.focus();
      };
    } else {
      return;
    }

    desc.textContent = descText;
    bar.hidden = false;
    bar.removeAttribute("aria-hidden");
    btn.focus();
  }

  /** Dismiss the chart selection bar and clear its state. */
  _clearChartSelection() {
    const bar = document.getElementById("chartSelectionBar");
    if (bar) {
      bar.hidden = true;
      bar.setAttribute("aria-hidden", "true");
    }
  }

  // ─────────────────────────────────────────────────────────────
  //  UI helpers
  // ─────────────────────────────────────────────────────────────
  showPage(pageId) {
    document.querySelectorAll(".page").forEach((p) => p.classList.remove("active"));
    document.getElementById(pageId)?.classList.add("active");
    document.querySelectorAll("nav.nav > .nav-btn, nav.navbar > .nav-btn, .navbar > .nav-btn").forEach((b) => {
      const isActive = b.dataset.page === pageId;
      b.classList.toggle("active", isActive);
      if (isActive) b.setAttribute("aria-current", "page");
      else b.removeAttribute("aria-current");
    });
    document.querySelectorAll(".mobile-nav-btn").forEach((b) => {
      const isActive = b.dataset.page === pageId;
      b.classList.toggle("active", isActive);
      if (isActive) b.setAttribute("aria-current", "page");
      else b.removeAttribute("aria-current");
    });
    if (pageId === "analytics")     this.updateAnalyticsUI();
    if (pageId === "notifications") {
      this.updateNotificationsUI();
    }
  }

  switchTab(tabEl) {
    const tablist = tabEl.closest('[role="tablist"]');
    if (!tablist) return;
    const tabs = [...tablist.querySelectorAll(".tab-header")];
    tabs.forEach((t) => {
      const isActive = t === tabEl;
      t.classList.toggle("active", isActive);
      t.setAttribute("aria-selected", isActive ? "true" : "false");
      t.setAttribute("tabindex", isActive ? "0" : "-1");
    });
    const content = tablist.parentElement.querySelector(".tab-content");
    content?.querySelectorAll(".tab-pane").forEach((p) => p.classList.remove("active"));
    const pane = document.getElementById(tabEl.dataset.tab);
    if (pane) pane.classList.add("active");
  }

  _handleTabKeydown(event) {
    const tablist = event.currentTarget.closest('[role="tablist"]');
    if (!tablist) return;
    const tabs = [...tablist.querySelectorAll(".tab-header")];
    const currentIndex = tabs.indexOf(event.currentTarget);
    let nextIndex = null;

    if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % tabs.length;
    else if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
    else if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = tabs.length - 1;
    else return;

    event.preventDefault();
    const nextTab = tabs[nextIndex];
    this.switchTab(nextTab);
    nextTab.focus();
  }

  _copyDonationNumber(card) {
    const num = card.dataset.number;
    if (!num) return;
    navigator.clipboard.writeText(num).then(() => this.showToast(`Copied: ${num}`, "success"));
    card.style.transform = "scale(0.96)";
    setTimeout(() => (card.style.transform = ""), 150);
  }

  toggleMobileNav() {
    const sidebar = document.querySelector(".sidebar");
    sidebar.classList.toggle("nav-expanded");
    const expanded = sidebar.classList.contains("nav-expanded");
    const hamburger = document.getElementById("hamburgerBtn");
    hamburger.setAttribute("aria-expanded", expanded);
    hamburger.setAttribute("aria-label", expanded ? "Close navigation menu" : "Open navigation menu");
  }

  // Sidebar pinned state (desktop)
  getSidebarPinned() {
    try {
      return localStorage.getItem("cointracker_sidebar_pinned") === "true";
    } catch {
      return false; // Default to unpinned (hover only) on desktop
    }
  }

  setSidebarPinned(pinned) {
    try {
      localStorage.setItem("cointracker_sidebar_pinned", String(pinned));
    } catch (e) {
      console.warn("Failed to save sidebar pinned state:", e);
    }
  }

  applySidebarPinnedState() {
    const sidebar = document.querySelector(".sidebar");
    const toggleBtn = document.getElementById("sidebarToggleBtn");
    if (!sidebar) return;

    const pinned = this.getSidebarPinned();
    if (pinned) {
      sidebar.classList.add("expanded");
      if (toggleBtn) {
        toggleBtn.setAttribute("aria-label", "Collapse sidebar");
        toggleBtn.setAttribute("aria-expanded", "true");
        toggleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>`;
      }
    } else {
      sidebar.classList.remove("expanded");
      if (toggleBtn) {
        toggleBtn.setAttribute("aria-label", "Expand sidebar");
        toggleBtn.setAttribute("aria-expanded", "false");
        toggleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>`;
      }
    }
  }

  toggleSidebarPinned() {
    const sidebar = document.querySelector(".sidebar");
    const toggleBtn = document.getElementById("sidebarToggleBtn");
    if (!sidebar) return;

    const currentlyPinned = sidebar.classList.contains("expanded");
    const newPinned = !currentlyPinned;

    this.setSidebarPinned(newPinned);
    this.applySidebarPinnedState();
  }

  showModal(id) {
    const m = document.getElementById(id);
    if (!m) return;
    this._lastFocused = document.activeElement;
    m.removeAttribute("hidden");
    m.classList.add("open");
    m.style.removeProperty("display");
    // Focus management
    const focusable = m.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    if (focusable) focusable.focus();
    // Trap focus - store bound ref for cleanup
    this._trapFocusRef = this._trapFocus.bind(this, m);
    this._handleEscapeRef = this._handleEscape.bind(this, id);
    m.addEventListener('keydown', this._trapFocusRef);
    m.addEventListener('keydown', this._handleEscapeRef);
    // Prevent body scroll
    document.body.style.overflow = 'hidden';
  }

  closeModal(id) {
    const m = document.getElementById(id);
    if (!m) return;
    m.classList.remove("open");
    m.setAttribute("hidden", "");
    m.style.removeProperty("display");
    // Clean up using stored refs
    if (this._trapFocusRef) m.removeEventListener('keydown', this._trapFocusRef);
    if (this._handleEscapeRef) m.removeEventListener('keydown', this._handleEscapeRef);
    this._trapFocusRef = null;
    this._handleEscapeRef = null;
    document.body.style.overflow = '';
    // Restore focus
    if (this._lastFocused && document.contains(this._lastFocused)) {
      this._lastFocused.focus();
    }
    this._lastFocused = null;
  }

  // Generic confirm modal (replaces native confirm)
  confirm(message) {
    return new Promise((resolve) => {
      const modal = document.getElementById("confirmModal");
      if (!modal) { resolve(false); return; }
      document.getElementById("confirmModalMessage").textContent = message;
      this._confirmResolver = resolve;
      this.showModal("confirmModal");

      const confirmBtn = document.getElementById("confirmModalConfirm");
      const cancelBtn  = document.getElementById("confirmModalCancel");

      const finish = (value) => {
        confirmBtn.removeEventListener("click", onConfirm);
        cancelBtn.removeEventListener("click", onCancel);
        this._confirmResolver = null;
        this._confirmCleanup = null;
        this.closeModal("confirmModal");
        resolve(value);
      };
      const onConfirm = () => finish(true);
      const onCancel  = () => finish(false);

      this._confirmCleanup = finish;
      confirmBtn.addEventListener("click", onConfirm);
      cancelBtn.addEventListener("click", onCancel);
    });
  }

  _trapFocus(modal, e) {
    if (e.key !== 'Tab') return;
    const focusable = modal.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  _handleEscape(id, e) {
    if (e.key !== 'Escape') return;
    if (id === "confirmModal" && this._confirmCleanup) {
      e.preventDefault();
      this._confirmCleanup(false);
      return;
    }
    this.closeModal(id);
  }

  // ─────────────────────────────────────────────────────────────
  //  Progress card generator (v4.0.4)
  // ─────────────────────────────────────────────────────────────

  /**
   * Draw an 900×500 branded progress card onto an offscreen canvas and
   * return it as a Blob (PNG). Uses only the Canvas 2D API — no new deps.
   * @returns {Promise<Blob>}
   */
  async generateProgressCard() {
    const W = 900, H = 500;
    const canvas = document.createElement("canvas");
    canvas.width  = W * window.devicePixelRatio;
    canvas.height = H * window.devicePixelRatio;
    canvas.style.width  = W + "px";
    canvas.style.height = H + "px";
    const ctx = canvas.getContext("2d");
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);

    // ── Read design tokens from the live stylesheet ──
    const cs       = getComputedStyle(document.documentElement);
    const BG       = cs.getPropertyValue("--surface").trim()       || "#1a1a1a";
    const SURFACE  = cs.getPropertyValue("--surface-subtle").trim() || "#222222";
    const BRAND    = cs.getPropertyValue("--brand").trim()          || "#C9A84C";
    const INCOME   = cs.getPropertyValue("--income").trim()         || "#4ade80";
    const EXPENSE  = cs.getPropertyValue("--expense").trim()        || "#f87171";
    const TEXT1    = cs.getPropertyValue("--text-primary").trim()   || "#f5f5f5";
    const TEXT2    = cs.getPropertyValue("--text-secondary").trim() || "#a3a3a3";
    const BORDER   = cs.getPropertyValue("--border").trim()         || "#2e2e2e";

    // ── Helpers ──
    const fmtNum = (n) => n.toLocaleString();
    const pad    = 40;

    // ── Background ──
    ctx.fillStyle = BG;
    ctx.fillRect(0, 0, W, H);

    // Subtle grid dots
    ctx.fillStyle = BORDER;
    for (let x = pad; x < W - pad; x += 32) {
      for (let y = pad; y < H - pad; y += 32) {
        ctx.beginPath(); ctx.arc(x, y, 1, 0, Math.PI * 2); ctx.fill();
      }
    }

    // ── Left accent bar ──
    ctx.fillStyle = BRAND;
    ctx.fillRect(0, 0, 6, H);

    // ── App name + profile ──
    ctx.fillStyle = BRAND;
    ctx.font      = "bold 15px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    ctx.fillText("Coin Tracker", pad + 8, pad + 18);
    ctx.fillStyle = TEXT2;
    ctx.font      = "13px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    const profileName = this.currentProfile || "Default";
    ctx.fillText(profileName, pad + 8, pad + 38);

    // ── Balance ──
    const bal = this.balance;
    ctx.fillStyle = TEXT1;
    ctx.font      = "bold 56px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    ctx.fillText(`${fmtNum(bal)}`, pad + 8, 140);
    ctx.fillStyle = TEXT2;
    ctx.font      = "14px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    ctx.fillText("coins", pad + 8, 162);

    // ── Progress bar ──
    const goal    = this.goal;
    const prog    = this.progress;  // 0–100
    const barY    = 196, barH = 14, barW = W - pad * 2 - 16;
    const radius  = barH / 2;

    // Track
    ctx.fillStyle = SURFACE;
    this._roundRect(ctx, pad + 8, barY, barW, barH, radius);
    ctx.fill();

    // Fill
    const fillW = Math.max(radius * 2, (prog / 100) * barW);
    ctx.fillStyle = BRAND;
    this._roundRect(ctx, pad + 8, barY, fillW, barH, radius);
    ctx.fill();

    // Milestone ticks (25, 50, 75%)
    ctx.fillStyle = BG;
    [0.25, 0.5, 0.75].forEach((pct) => {
      const tickX = pad + 8 + pct * barW;
      ctx.fillRect(tickX - 1, barY + 2, 2, barH - 4);
    });

    // Progress label
    ctx.fillStyle = BRAND;
    ctx.font      = "bold 13px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    ctx.fillText(`${prog}%`, pad + 8 + barW + 8, barY + 10);

    // Goal text
    ctx.fillStyle = TEXT2;
    ctx.font      = "13px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    const goalStr = goal > 0 ? `Goal: ${fmtNum(goal)} coins` : "No goal set";
    ctx.fillText(goalStr, pad + 8, barY + barH + 20);

    // ── Stat grid (3 cols) ──
    const stats = this.dashboardStats;
    const { rate: sevenRate } = this.sevenDayRate;
    const best = this.bestEarningWeek;
    const est  = this.estimatedDays;

    const cols = [
      { label: "Today",       value: `+${fmtNum(stats.today)}`,   color: INCOME  },
      { label: "This week",   value: `+${fmtNum(stats.week)}`,    color: INCOME  },
      { label: "This month",  value: `+${fmtNum(stats.month)}`,   color: INCOME  },
      { label: "7-day rate",  value: `${fmtNum(sevenRate)}/day`,  color: BRAND   },
      { label: "Best week",   value: best ? `${fmtNum(best.amount)}` : "—", color: BRAND },
      { label: "Est. days",   value: est === "N/A" ? "—" : (est === 0 ? "Done!" : `${est} days`), color: goal > 0 ? TEXT1 : TEXT2 },
    ];

    const colW  = (W - pad * 2 - 16) / 3;
    const boxY  = 270;
    const boxH  = 90;

    cols.forEach((col, i) => {
      const row  = Math.floor(i / 3);
      const col_ = i % 3;
      const bx   = pad + 8 + col_ * colW;
      const by   = boxY + row * (boxH + 12);
      const bw   = colW - 12;

      // Box background
      ctx.fillStyle = SURFACE;
      this._roundRect(ctx, bx, by, bw, boxH, 10);
      ctx.fill();

      // Label
      ctx.fillStyle = TEXT2;
      ctx.font      = "12px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
      ctx.fillText(col.label, bx + 14, by + 26);

      // Value
      ctx.fillStyle = col.color;
      ctx.font      = "bold 22px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
      ctx.fillText(col.value, bx + 14, by + 60);
    });

    // ── Footer ──
    const today = new Date().toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
    ctx.fillStyle = TEXT2;
    ctx.font      = "12px -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
    ctx.fillText(`Generated ${today}`, pad + 8, H - 18);

    return new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
  }

  /** Rounded-rect path helper (CanvasRenderingContext2D has native roundRect but coverage varies) */
  _roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  /**
   * Generate the progress card and either share it via Web Share API
   * (supported on mobile browsers) or trigger a PNG download as fallback.
   */
  async shareProgressCard() {
    const btn = document.getElementById("shareCardBtn");
    if (btn) { btn.disabled = true; btn.textContent = "Generating…"; }
    try {
      const blob = await this.generateProgressCard();
      const file = new File([blob], `coin-tracker-progress-${Date.now()}.png`, { type: "image/png" });
      const canShare = typeof navigator.share === "function" &&
        navigator.canShare && navigator.canShare({ files: [file] });

      if (canShare) {
        await navigator.share({
          title: "My Coin Tracker Progress",
          text:  `${this.balance.toLocaleString()} coins — ${this.progress}% of goal`,
          files: [file],
        });
      } else {
        // Download fallback
        const url = URL.createObjectURL(blob);
        const a   = document.createElement("a");
        a.href    = url;
        a.download = file.name;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
        this.showToast("Progress card saved!", "success");
      }
    } catch (err) {
      if (err.name !== "AbortError") {
        this.showToast("Could not generate card. Try again.", "error");
      }
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = "Share progress card"; }
    }
  }

  showToast(message, type = "success") {
    const toast = document.getElementById("toast");
    if (!toast) return;
    if (this._toastTimeout) clearTimeout(this._toastTimeout);

    let iconSvg = "";
    if (type === "success") {
      iconSvg = `<svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/></svg>`;
    } else if (type === "error") {
      iconSvg = `<svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>`;
    } else {
      iconSvg = `<svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5.882V19.24a1.76 1.76 0 01-3.417.592l-2.147-6.15M18 13a3 3 0 100-6M5.436 13.683A4.001 4.001 0 017 6h1.832c4.1 0 7.625-1.234 9.168-3v14c-1.543-1.766-5.067-3-9.168-3H7a3.988 3.988 0 01-1.564-.317z"/></svg>`;
    }

    toast.innerHTML = `
      ${iconSvg}
      <button type="button" class="toast-dismiss" aria-label="Dismiss">&times;</button>
    `;
    const messageEl = document.createElement("span");
    messageEl.textContent = message;
    toast.insertBefore(messageEl, toast.querySelector(".toast-dismiss"));
    toast.className = `toast ${type} show`;

    const dismissBtn = toast.querySelector(".toast-dismiss");
    if (dismissBtn) {
      dismissBtn.onclick = (e) => {
        e.stopPropagation();
        if (this._toastTimeout) clearTimeout(this._toastTimeout);
        toast.classList.remove("show");
      };
    }

    this._toastTimeout = setTimeout(() => toast.classList.remove("show"), 3500);
  }

  showLoading(message = "Retrieving data from database...", subtext = "Connecting to Cloud Firestore...") {
    const overlay = document.getElementById("loadingOverlay");
    if (!overlay) return;
    const msgEl = document.getElementById("loadingMessage");
    const subEl = document.getElementById("loadingSubtext");
    if (msgEl) msgEl.textContent = message;
    if (subEl) subEl.textContent = subtext;
    overlay.removeAttribute("hidden");
    overlay.style.display = "flex";
    requestAnimationFrame(() => {
      overlay.style.opacity = "1";
      overlay.style.pointerEvents = "auto";
    });
    this.updateSyncStatus("loading", message);
  }

  hideLoading() {
    const overlay = document.getElementById("loadingOverlay");
    if (!overlay) return;
    overlay.style.opacity = "0";
    overlay.style.pointerEvents = "none";
    setTimeout(() => {
      overlay.setAttribute("hidden", "");
      overlay.style.display = "none";
    }, 300);
    this.updateSyncStatus("synced");
  }

  updateSyncStatus(status, label) {
    const dots = [document.getElementById("dbSyncDot"), document.getElementById("mobileDbSyncDot")];
    const texts = [document.getElementById("dbSyncText"), document.getElementById("mobileDbSyncText")];

    dots.forEach((dot) => {
      if (!dot) return;
      dot.className = dot.id === "mobileDbSyncDot"
        ? "w-1.5 h-1.5 rounded-full"
        : "w-2 h-2 rounded-full";
      if (status === "loading") {
        dot.style.background = "var(--warning)";
        dot.classList.add("animate-pulse");
      } else if (status === "error") {
        dot.style.background = "var(--expense)";
      } else {
        dot.style.background = "var(--income)";
      }
    });

    texts.forEach((text) => {
      if (!text) return;
      if (status === "loading") {
        text.textContent = text.id === "mobileDbSyncText" ? "Retrieving..." : (label || "Retrieving data...");
      } else if (status === "error") {
        text.textContent = text.id === "mobileDbSyncText" ? "Error" : (label || "Database disconnected");
      } else {
        text.textContent = text.id === "mobileDbSyncText" ? "Synced" : (label || "Database synced");
      }
    });
  }
}

function showLoading(message, subtext) {
  if (window.app) window.app.showLoading(message, subtext);
}

function hideLoading() {
  if (window.app) window.app.hideLoading();
}

const app = new CoinTrackerApp();
window.app = app;
app.init();
