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
import { getIcon, getAchievementIcon, getAlertIcon } from "/js/icons.js";

const firebaseApp = initializeApp(firebaseConfig);
const auth        = getAuth(firebaseApp);
const db          = getFirestore(firebaseApp);

// ── Defaults (match Models.kt + FirestoreRepository.kt) ──────
const DEFAULT_INCOME_CATEGORIES  = ["Event Reward","Login","Daily Games","Campaign Reward","Ads","Achievements","Other"];
const DEFAULT_EXPENSE_CATEGORIES = ["Box Draw","Manager Purchase","Pack Purchase","Store Purchase","Other"];
const DEFAULT_GOAL               = 13500;
const TRANSACTIONS_PER_PAGE      = 10;

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
  { id: "goal_reached",    icon: "emojiEvents", name: "Epic Box Secured!",   desc: "Reach your goal balance",         check: (s) => s.balance >= s.goal },
  { id: "disciplined",     icon: "shield2", name: "Disciplined",         desc: "No spending for 7+ days",         check: (s) => s.daysSinceSpend >= 7 },
  { id: "streak_3",        icon: "flame", name: "3-Day Login Streak",  desc: "3 consecutive Login days",        check: (s) => s.loginStreak >= 3 },
  { id: "streak_7",        icon: "flame", name: "7-Day Login Streak",  desc: "7 consecutive Login days",        check: (s) => s.loginStreak >= 7 },
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
  }

  // ── Bootstrap ─────────────────────────────────────────────────
  async init() {
    onAuthStateChanged(auth, async (user) => {
      if (!user) { window.location.href = "/login.html"; return; }
      this.uid = user.uid;
      await this.loadUserMeta();
      await this.loadUserData();
      this.setupEventListeners();
      this.createHiddenFileInput();
      this.updateAllUI();
      this.applySidebarPinnedState();
      this.checkBroadcast();
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
    this.userDataDoc.last_active_profile = this.currentProfile;
    const now = new Date().toISOString();
    if (this.userDataDoc.profiles?.[this.currentProfile]) {
      this.userDataDoc.profiles[this.currentProfile].last_updated = now;
    }
    await setDoc(doc(db, "user_data", this.uid), this.userDataDoc);
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
    return this.settings.goal || DEFAULT_GOAL;
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
    return this.goal > 0 ? Math.min(100, Math.round((this.balance / this.goal) * 100)) : 0;
  }

  get estimatedDays() {
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
    const state = { balance: this.balance, goal: this.goal, loginStreak: maxStreak, daysSinceSpend };
    return ACHIEVEMENT_DEFS.filter((a) => a.check(state));
  }

  get notificationAlerts() {
    const alerts = [], p = this.progress;
    if (this.balance >= this.goal)
      alerts.push({ icon: getAlertIcon("🎉"), title: "Goal Reached!", sub: `You hit your goal of ${this.goal.toLocaleString()} coins!` });
    else if (p >= 75)
      alerts.push({ icon: getAlertIcon("🔥"), title: "75% of your goal reached", sub: `Only ${(this.goal - this.balance).toLocaleString()} coins to go!` });
    else if (p >= 50)
      alerts.push({ icon: getAlertIcon("⚡"), title: "Halfway there!", sub: "You're 50% of the way to your goal." });
    const stats = this.dashboardStats;
    if (stats.today >= 300)
      alerts.push({ icon: getAlertIcon("💪"), title: "Big day!", sub: `You earned ${stats.today.toLocaleString()} coins today!` });
    const est = this.estimatedDays;
    if (typeof est === "number" && est > 0 && est <= 7)
      alerts.push({ icon: getAlertIcon("⏰"), title: "Goal is close!", sub: `~${est} day${est === 1 ? "" : "s"} to reach your goal.` });
    const best = this.bestEarningWeek;
    if (best)
      alerts.push({ icon: "🏆", title: "Best Earning Week", sub: `${best.range}: ${best.amount.toLocaleString()} coins` });
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

  // ─────────────────────────────────────────────────────────────
  //  Event listeners
  // ─────────────────────────────────────────────────────────────
  setupEventListeners() {
    document.getElementById("hamburgerBtn").addEventListener("click", () => this.toggleMobileNav());

    document.querySelectorAll("nav.nav > .nav-btn").forEach((btn) => {
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
    document.getElementById("mobileThemeToggle").addEventListener("click", () => this.toggleTheme());

    // Mobile logout button (shown in mobile sidebar)
    document.getElementById("mobileLogoutBtn").addEventListener("click", () => this.logout());

    // Top bar profile dropdown
    document.getElementById("topBarProfileBtn").addEventListener("click", (e) => this.toggleProfileDropdown(e));
    document.getElementById("topBarProfileSelect").addEventListener("change", (e) => this.switchProfile(e.target.value));
    document.getElementById("topBarNewProfileBtn").addEventListener("click", () => this.showModal("profileModal"));
    document.getElementById("topBarAdminBtn").addEventListener("click", () => { window.location.href = "/admin.html"; });
    document.getElementById("topBarLogoutBtn").addEventListener("click", () => this.logout());

    // Close profile dropdown when clicking outside
    document.addEventListener("click", (e) => {
      const dropdown = document.getElementById("profileDropdownMenu");
      const profileBtn = document.getElementById("topBarProfileBtn");
      if (dropdown && dropdown.classList.contains("open") && !dropdown.contains(e.target) && e.target !== profileBtn) {
        dropdown.classList.remove("open");
        profileBtn.setAttribute("aria-expanded", "false");
      }
    });

    // Sidebar toggle (desktop) - persist expanded/collapsed state
    document.getElementById("sidebarToggleBtn")?.addEventListener("click", () => this.toggleSidebarPinned());

    // Sidebar profile selector (mobile)
    document.getElementById("profileSelect").addEventListener("change", (e) => this.switchProfile(e.target.value));
    document.getElementById("newProfileBtn").addEventListener("click", () => this.showModal("profileModal"));

    document.getElementById("addCoinsBtn").addEventListener("click", () => this.addFromDashboard());
    document.getElementById("spendCoinsBtn").addEventListener("click", () => this.spendFromDashboard());
    document.getElementById("customizeQuickActions").addEventListener("click", () => this.showPage("settings"));

    ["dateFrom", "dateTo", "historySourceFilter"].forEach((id) => {
      document.getElementById(id)?.addEventListener("change", () => this.buildHistory());
    });
    document.getElementById("historySearch")?.addEventListener("input", () => this.buildHistory());

    document.querySelectorAll(".chip[data-period]").forEach((chip) => {
      chip.addEventListener("click", () => {
        document.querySelectorAll(".chip[data-period]").forEach((c) => c.classList.remove("active"));
        chip.classList.add("active");
        this.analyticsPeriod = chip.dataset.period;
        const picker = document.getElementById("customRangePicker");
        if (picker) picker.style.display = this.analyticsPeriod === "custom" ? "flex" : "none";
        if (this.analyticsPeriod !== "custom") this.updateAnalyticsUI();
      });
    });
    document.getElementById("applyRangeBtn")?.addEventListener("click", () => {
      this.analyticsCustomFrom = document.getElementById("analyticsFrom").value;
      this.analyticsCustomTo   = document.getElementById("analyticsTo").value;
      this.updateAnalyticsUI();
    });

    document.querySelectorAll(".tab-header").forEach((tab) => {
      tab.addEventListener("click", (e) => this.switchTab(e.currentTarget));
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
      card.addEventListener("click", () => {
        const num = card.dataset.number;
        navigator.clipboard.writeText(num).then(() => this.showToast(`Copied: ${num}`, "success"));
        card.style.transform = "scale(0.96)";
        setTimeout(() => (card.style.transform = ""), 150);
      });
    });

    document.querySelectorAll(".close-btn[data-modal]").forEach((btn) => {
      btn.addEventListener("click", () => this.closeModal(btn.dataset.modal));
    });

    document.getElementById("saveTransactionBtn").addEventListener("click", () => this.saveEditedTransaction());

    // Notification read functionality
    document.getElementById("markAllReadBtn")?.addEventListener("click", () => this.markAllNotificationsRead());
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
    // Update top bar theme toggle icon
    const topBarThemeBtn = document.getElementById("topBarThemeToggle");
    if (topBarThemeBtn) {
      topBarThemeBtn.innerHTML = dark ? getIcon('sun') : getIcon('moon');
    }
    if (Object.keys(this.charts).length > 0) this.updateAnalyticsUI();
  }

  updateHeaderUI() {
    const el = document.getElementById("usernameDisplay");
    if (el) el.textContent = this.username || "";
    if (this.role === "admin") {
      const c = document.getElementById("adminPanelBtnContainer");
      if (c) c.style.display = "block";
      // Show admin button in top bar dropdown
      const adminBtn = document.getElementById("topBarAdminBtn");
      if (adminBtn) adminBtn.style.display = "flex";
    }
    // Update profile dropdown select in top bar
    this.updateTopBarProfileDropdown();
  }

  updateBalanceUI() {
    const bal = this.balance, prog = this.progress, est = this.estimatedDays;
    document.getElementById("balanceAmount").textContent    = `${bal.toLocaleString()} coins`;
    document.getElementById("goalText").textContent         = `Goal: ${this.goal.toLocaleString()} coins`;
    document.getElementById("progressBar").style.width     = `${prog}%`;
    document.getElementById("progressPercent").textContent = `${prog}%`;
    const estEl = document.getElementById("goalEstimate");
    if (est === 0)          estEl.innerHTML = `${getIcon('partyPopper')} Goal reached!`;
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
    if (!actions.length) {
      grid.innerHTML = `<p style="color:var(--muted-color);font-size:13px">No quick actions yet. Add them in Settings.</p>`;
      return;
    }
    actions.forEach((action) => {
      const btn = document.createElement("button");
      // Mobile uses is_positive (snake_case)
      btn.className = `quick-btn ${action.is_positive ? "positive" : "negative"}`;
      btn.innerHTML = `<div class="quick-text">${action.text}</div>
                       <div class="quick-amount">${action.is_positive ? "+" : "-"}${action.value}</div>`;
      btn.onclick = async () => {
        btn.classList.add("is-processing");
        await this.addTransaction(action.is_positive ? action.value : -action.value, action.text);
        btn.classList.remove("is-processing");
        this.showToast(`'${action.text}' recorded!`, "success");
      };
      grid.appendChild(btn);
    });
  }

  populateCategoryDropdowns() {
    const addSrc   = document.getElementById("addSource");
    const spendCat = document.getElementById("spendCategory");
    if (addSrc)   addSrc.innerHTML   = this.incomeCategories.map((c) => `<option value="${c}">${c}</option>`).join("");
    if (spendCat) spendCat.innerHTML = this.expenseCategories.map((c) => `<option value="${c}">${c}</option>`).join("");

    const allSources = [...new Set(this.transactions.map((t) => t.source))].sort();
    const sf = document.getElementById("historySourceFilter");
    const cur = sf.value;
    sf.innerHTML = '<option value="all">All Sources</option>' +
      allSources.map((s) => `<option value="${s}">${s}</option>`).join("");
    sf.value = allSources.includes(cur) ? cur : "all";
  }

  updateProfileDropdown() {
    const sel = document.getElementById("profileSelect");
    if (!sel) return;
    sel.innerHTML = this.allProfiles.map((p) =>
      `<option value="${p}" ${p === this.currentProfile ? "selected" : ""}>${p}</option>`
    ).join("");
  }

  updateTopBarProfileDropdown() {
    const sel = document.getElementById("topBarProfileSelect");
    if (!sel) return;
    sel.innerHTML = this.allProfiles.map((p) =>
      `<option value="${p}" ${p === this.currentProfile ? "selected" : ""}>${p}</option>`
    ).join("");
  }

  toggleProfileDropdown(e) {
    const dropdown = document.getElementById("profileDropdownMenu");
    const btn = document.getElementById("topBarProfileBtn");
    if (!dropdown || !btn) return;
    const isOpen = dropdown.classList.toggle("open");
    btn.setAttribute("aria-expanded", isOpen);
    if (isOpen) {
      // Focus first focusable element
      const focusable = dropdown.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
      if (focusable) focusable.focus();
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
    // Generate stable IDs for current notifications based on their content
    const alerts = this.notificationAlerts;
    const achs = this.achievements;
    const ids = [];
    alerts.forEach((a, i) => ids.push(`alert-${i}-${a.title.replace(/\s+/g, '-')}`));
    achs.forEach((a, i) => ids.push(`achievement-${a.id}`));
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
    this.markNotificationsAsRead(allIds);
    this.showToast("All notifications marked as read", "success");
  }

  // Called when notifications page is shown
  onNotificationsPageShown() {
    const allIds = this.getNotificationIds();
    this.markNotificationsAsRead(allIds);
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
    this.renderHistoryPage(1);
    const earned = txns.filter((t) => t.amount > 0).reduce((s, t) => s + t.amount, 0);
    const spent  = txns.filter((t) => t.amount < 0).reduce((s, t) => s + Math.abs(t.amount), 0);
    const el = document.getElementById("periodSummary");
    if (el) el.innerHTML = `<span class="amount-positive">Earned: +${earned.toLocaleString()}</span> / <span class="amount-negative">Spent: -${spent.toLocaleString()}</span>`;
  }

  renderHistoryPage(page) {
    this.historyPage = page;
    const totalPages = Math.ceil(this.historyFiltered.length / TRANSACTIONS_PER_PAGE);
    const start      = (page - 1) * TRANSACTIONS_PER_PAGE;
    const pageTxns   = this.historyFiltered.slice(start, start + TRANSACTIONS_PER_PAGE);
    const tbody      = document.getElementById("historyTableBody");
    tbody.innerHTML  = "";
    if (!pageTxns.length) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--muted-color);padding:30px">No transactions found.<br><button class="btn primary" style="margin-top:12px" onclick="document.getElementById('addAmount').focus();document.querySelector('[data-page=\"dashboard\"]').click()">Add your first transaction</button></td></tr>`;
    } else {
      pageTxns.forEach((t) => {
        // Mobile stores previous_balance (snake_case)
        const cls      = t.amount >= 0 ? "amount-positive" : "amount-negative";
        const balAfter = (t.previous_balance || 0) + t.amount;
        const tr       = document.createElement("tr");
        tr.innerHTML = `
          <td>${new Date(t.date).toLocaleString()}</td>
          <td>${t.amount >= 0 ? "Income" : "Expense"}</td>
          <td>${t.source}</td>
          <td class="${cls}">${t.amount >= 0 ? "+" : ""}${t.amount.toLocaleString()}</td>
          <td>${balAfter.toLocaleString()}</td>
          <td class="history-actions">
            <button class="btn secondary btn-edit" data-id="${t.id}">${getIcon('edit')} Edit</button>
            <button class="btn danger btn-delete" data-id="${t.id}">${getIcon('trash2')}</button>
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
      container.innerHTML = `<p style="text-align:center;color:var(--muted-color);padding:30px">No transactions found.<br><button class="btn primary" style="margin-top:12px" onclick="document.getElementById('addAmount').focus();document.querySelector('[data-page=\"dashboard\"]').click()">Add your first transaction</button></p>`;
      return;
    }
    container.innerHTML = txns.map((t) => {
      const cls      = t.amount >= 0 ? "amount-positive" : "amount-negative";
      const balAfter = (t.previous_balance || 0) + t.amount;
      const typeCls  = t.amount >= 0 ? "income" : "expense";
      const typeLabel = t.amount >= 0 ? "Income" : "Expense";
      return `
        <div class="history-card">
          <div class="history-card-header">
            <span class="history-card-date">${new Date(t.date).toLocaleString()}</span>
            <span class="history-card-type ${typeCls}">${typeLabel}</span>
          </div>
          <div class="history-card-body">
            <div><span class="history-card-label">Source</span><span class="history-card-value">${t.source}</span></div>
            <div><span class="history-card-label">Amount</span><span class="history-card-value ${cls}">${t.amount >= 0 ? "+" : ""}${t.amount.toLocaleString()}</span></div>
            <div><span class="history-card-label">Balance After</span><span class="history-card-value">${balAfter.toLocaleString()}</span></div>
          </div>
          <div class="history-card-actions">
            <button class="btn secondary btn-edit" data-id="${t.id}">${getIcon('edit')} Edit</button>
            <button class="btn danger btn-delete" data-id="${t.id}">${getIcon('trash2')}</button>
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
    document.getElementById("goalInput").value              = this.goal;
    document.getElementById("currentGoalText").textContent  = `Current Goal: ${this.goal.toLocaleString()} coins`;
    document.getElementById("goalProgressText").textContent = `You are ${this.progress}% of the way to your goal.`;
    this.renderQuickActionList();
    this.renderCategoryList("income");
    this.renderCategoryList("expense");
    const delBtn = document.getElementById("deleteProfileBtn");
    if (delBtn) delBtn.disabled = this.currentProfile === "Default";
  }

  renderQuickActionList() {
    const listEl = document.getElementById("quickActionList");
    if (!listEl) return;
    const actions = this.quickActions;
    if (!actions.length) { 
      listEl.innerHTML = `<p style="color:var(--muted-color)">No quick actions yet.</p>
        <button class="btn primary" style="margin-top:12px;width:100%" onclick="document.getElementById('quickActionText').focus()">Create your first quick action</button>`; 
      return; 
    }
    listEl.innerHTML = "";
    actions.forEach((action, i) => {
      const item = document.createElement("div");
      item.className = "quick-action-list-item";
      item.innerHTML = `
        <div class="quick-action-details">
          <span class="quick-action-text">${action.text}</span>
          <span class="quick-action-amount ${action.is_positive ? "positive" : "negative"}">${action.is_positive ? "+" : "-"}${action.value}</span>
        </div>
        <button class="delete-btn" data-index="${i}">Delete</button>`;
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
      listEl.innerHTML = `<p style="color:var(--muted-color);margin-bottom:12px">No ${isIncome ? "income" : "expense"} categories yet.</p>
        <button class="btn primary" style="width:100%" onclick="document.getElementById('${isIncome ? "newIncomeCategory" : "newExpenseCategory"}').focus()">Add your first ${isIncome ? "income" : "expense"} category</button>`;
      return;
    }
    cats.forEach((cat, i) => {
      const item = document.createElement("div");
      item.className = "category-item";
      item.innerHTML = `<span>${cat}</span><button class="delete-btn">×</button>`;
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
                         <div class="achievement-name">${a.name}</div>
                         <div class="achievement-desc">${a.desc}</div>`;
        grid.appendChild(div);
      });
    }
    // Badge updates are now handled by updateNotificationBadges() based on unread count
  }

  updateNotificationsUI() {
    const readIds = new Set(this.getReadNotificationIds());
    const alerts = this.notificationAlerts;
    const achs = this.achievements;

    const alertsEl = document.getElementById("alertsList");
    if (alertsEl) {
      alertsEl.innerHTML = alerts.length === 0
        ? `<p class="muted-text">No alerts right now. Keep tracking!</p>`
        : alerts.map((a, i) => {
            const id = `alert-${i}-${a.title.replace(/\s+/g, '-')}`;
            const isRead = readIds.has(id);
            return `
              <div class="alert-item ${isRead ? 'read' : ''}" data-notif-id="${id}">
                <div class="alert-icon">${a.icon}</div>
                <div class="alert-text">
                  <div class="alert-title">${a.title}</div>
                  <div class="alert-sub">${a.sub}</div>
                </div>
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
            return `
              <div class="notif-achievement-item ${isRead ? 'read' : ''}" data-notif-id="${id}">
                <div class="notif-achievement-icon">${getIcon(a.icon)}</div>
                <div class="notif-achievement-info">
                  <div class="notif-achievement-name">${a.name}</div>
                  <div class="notif-achievement-desc">${a.desc}</div>
                </div>
                <div class="notif-achievement-check">✓</div>
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
    const amt = parseInt(document.getElementById("addAmount").value);
    const src = document.getElementById("addSource").value;
    if (!amt || amt <= 0 || amt > 999999) { this.showToast("Enter a valid amount (1–999,999).", "error"); return; }
    await this.addTransaction(amt, src);
    document.getElementById("addAmount").value = "";
    this.showToast(`Added ${amt.toLocaleString()} coins!`, "success");
  }

  async spendFromDashboard() {
    const amt = parseInt(document.getElementById("spendAmount").value);
    const cat = document.getElementById("spendCategory").value;
    if (!amt || amt <= 0 || amt > 999999) { this.showToast("Enter a valid amount (1–999,999).", "error"); return; }
    await this.addTransaction(-amt, cat);
    document.getElementById("spendAmount").value = "";
    this.showToast(`Spent ${amt.toLocaleString()} coins!`, "success");
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
    const id     = document.getElementById("transactionId").value;
    const amount = parseInt(document.getElementById("transactionAmount").value);
    const source = document.getElementById("transactionSource").value;
    const date   = new Date(document.getElementById("transactionDate").value).toISOString();
    if (!id || !amount || amount <= 0 || !source) { this.showToast("Fill all fields correctly.", "error"); return; }
    const txns = this.userDataDoc.profiles[this.currentProfile].transactions;
    const idx  = txns.findIndex((t) => t.id === id);
    if (idx === -1) return;
    const sign = txns[idx].amount >= 0 ? 1 : -1;
    txns[idx] = { ...txns[idx], amount: sign * amount, source, date };
    this._recalcPreviousBalances();
    await this.saveUserData();
    this.closeModal("transactionModal");
    this.showToast("Transaction updated!", "success");
    this.updateAllUI();
  }

  async deleteTransaction(transactionId) {
    if (!await this.confirm("Delete this transaction?")) return;
    const profile = this.userDataDoc.profiles[this.currentProfile];
    const deletedTx = profile.transactions.find((t) => t.id === transactionId);
    profile.transactions = profile.transactions.filter((t) => t.id !== transactionId);
    this._recalcPreviousBalances();
    await this.saveUserData();
    this.showToast("Transaction deleted.", "success");
    // UNDO snackbar
    if (deletedTx) {
      this.showUndoToast("Transaction deleted", () => {
        this.userDataDoc.profiles[this.currentProfile].transactions.push(deletedTx);
        this._recalcPreviousBalances();
        this.saveUserData();
        this.updateAllUI();
      });
    }
    this.updateAllUI();
  }

  showUndoToast(message, onUndo) {
    const toast = document.getElementById("toast");
    if (!toast) return;
    toast.innerHTML = `${message} <button class="toast-undo" style="margin-left:12px;padding:4px 12px;border:none;border-radius:4px;background:var(--primary-color);color:white;cursor:pointer">UNDO</button>`;
    toast.className = `toast success show`;
    const undoBtn = toast.querySelector(".toast-undo");
    const timeout = setTimeout(() => {
      toast.classList.remove("show");
    }, 5000);
    undoBtn.onclick = () => {
      clearTimeout(timeout);
      toast.classList.remove("show");
      onUndo();
    };
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
    const val = parseInt(document.getElementById("goalInput").value);
    if (!val || val <= 0) { this.showToast("Enter a valid goal.", "error"); return; }
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
    this.showToast(`Loading ${name}...`, "success");
    this.currentProfile = name;
    this.userDataDoc.last_active_profile = name;
    await this.saveUserData();
    this.updateAllUI();
    this.showToast(`Switched to ${name}.`, "success");
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
    try {
      const data = JSON.parse(await file.text());
      if (!Array.isArray(data)) { this.showToast("Invalid file. Must be a JSON array.", "error"); return; }
      this.userDataDoc.profiles[this.currentProfile].transactions = data;
      this._recalcPreviousBalances();
      await this.saveUserData();
      this.showToast(`Imported ${data.length} transactions!`, "success");
      this.updateAllUI();
    } catch { this.showToast("Invalid or corrupt JSON file.", "error"); }
  }

  async deleteAllData() {
    if (!await this.confirm("Delete ALL data for ALL profiles? This cannot be undone.")) return;
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
        // Replace canvas with a new one to preserve the element for future updates
        const newCanvas = document.createElement('canvas');
        newCanvas.id = canvasId;
        newCanvas.style.width = '100%';
        newCanvas.style.height = '100%';
        container.innerHTML = '';
        container.appendChild(newCanvas);
        ctx = newCanvas;
        
        // Show empty state message
        const messageDiv = document.createElement('div');
        messageDiv.style.cssText = 'display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;min-height:300px;color:var(--muted-color);text-align:center;padding:20px';
        messageDiv.innerHTML = `
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="margin-bottom:16px;opacity:0.5"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          <p style="font-size:14px;font-weight:500;margin-bottom:8px">No data for this period</p>
          <button class="btn primary" style="margin-top:12px" onclick="document.querySelector('[data-page=\"dashboard\"]').click()">Add transactions to see analytics</button>
        `;
        container.appendChild(messageDiv);
      }
      // Clear summary for empty state
      this.updateChartSummary(canvasId, "");
      return;
    }
    
    const s       = getComputedStyle(document.documentElement);
    const text    = s.getPropertyValue("--text-color").trim();
    const grid    = s.getPropertyValue("--border-color").trim();
    const primary = s.getPropertyValue("--primary-color").trim();
    const success = s.getPropertyValue("--success-color").trim();
    const danger  = s.getPropertyValue("--danger-color").trim();
    const palette = [primary, success, "#f59e0b", danger, "#8b5cf6", "#06b6d4", "#ec4899"];
    this.charts[canvasId] = new Chart(ctx, {
      type,
      data: {
        labels,
        datasets: [{
          data,
          backgroundColor: type === "doughnut" ? palette : type === "line" ? "rgba(59,130,246,0.1)" : danger,
          borderColor: type === "line" ? primary : "transparent",
          borderWidth: 2, tension: 0.1, fill: type === "line",
        }],
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        plugins: { legend: { display: type === "doughnut", position: "bottom", labels: { color: text } } },
        scales: type !== "doughnut" ? {
          y: { ticks: { color: text }, grid: { color: grid } },
          x: { ticks: { color: text }, grid: { color: grid } },
        } : {},
      },
    });

    // Populate aria-live summary for accessibility
    this.updateChartSummary(canvasId, labels, data);
  }

  // Helper to update chart summary for screen readers
  updateChartSummary(canvasId, labels, data) {
    const summaryEl = document.getElementById(`${canvasId}Summary`);
    if (!summaryEl) return;

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
  //  UI helpers
  // ─────────────────────────────────────────────────────────────
  showPage(pageId) {
    document.querySelectorAll(".page").forEach((p) => p.classList.remove("active"));
    document.getElementById(pageId)?.classList.add("active");
    document.querySelectorAll("nav.nav > .nav-btn").forEach((b) =>
      b.classList.toggle("active", b.dataset.page === pageId));
    document.querySelectorAll(".mobile-nav-btn").forEach((b) =>
      b.classList.toggle("active", b.dataset.page === pageId));
    if (pageId === "analytics")     this.updateAnalyticsUI();
    if (pageId === "notifications") {
      this.updateNotificationsUI();
      this.onNotificationsPageShown();
    }
  }

  switchTab(tabEl) {
    tabEl.parentElement.querySelectorAll(".tab-header").forEach((t) => t.classList.remove("active"));
    tabEl.classList.add("active");
    tabEl.closest(".analytics-tabs").querySelectorAll(".tab-pane").forEach((p) => p.classList.remove("active"));
    document.getElementById(tabEl.dataset.tab)?.classList.add("active");
  }

  toggleMobileNav() {
    const sidebar = document.querySelector(".sidebar");
    sidebar.classList.toggle("nav-expanded");
    document.getElementById("hamburgerBtn")
      .setAttribute("aria-expanded", sidebar.classList.contains("nav-expanded"));
  }

  // Sidebar pinned state (desktop) - persists user preference
  getSidebarPinned() {
    try {
      return localStorage.getItem("cointracker_sidebar_pinned") === "true";
    } catch {
      return true; // Default to pinned (expanded) on desktop
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
    if (!sidebar || !toggleBtn) return;

    const isDesktop = window.matchMedia("(min-width: 1025px)").matches;
    if (!isDesktop) return; // Only apply on desktop >1024px

    const pinned = this.getSidebarPinned();
    if (pinned) {
      sidebar.classList.add("expanded");
      toggleBtn.setAttribute("aria-label", "Collapse sidebar");
      toggleBtn.setAttribute("aria-expanded", "true");
      toggleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>`;
    } else {
      sidebar.classList.remove("expanded");
      toggleBtn.setAttribute("aria-label", "Expand sidebar");
      toggleBtn.setAttribute("aria-expanded", "false");
      toggleBtn.innerHTML = `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>`;
    }
  }

  toggleSidebarPinned() {
    const sidebar = document.querySelector(".sidebar");
    const toggleBtn = document.getElementById("sidebarToggleBtn");
    if (!sidebar || !toggleBtn) return;

    const currentlyPinned = sidebar.classList.contains("expanded");
    const newPinned = !currentlyPinned;

    this.setSidebarPinned(newPinned);
    this.applySidebarPinnedState();
  }

  showModal(id) {
    const m = document.getElementById(id);
    if (!m) return;
    m.style.display = "block";
    // Focus management
    this._lastFocused = document.activeElement;
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
    m.style.display = "none";
    // Restore focus
    if (this._lastFocused) this._lastFocused.focus();
    // Clean up using stored refs
    if (this._trapFocusRef) m.removeEventListener('keydown', this._trapFocusRef);
    if (this._handleEscapeRef) m.removeEventListener('keydown', this._handleEscapeRef);
    document.body.style.overflow = '';
  }

  // Generic confirm modal (replaces native confirm)
  confirm(message) {
    return new Promise((resolve) => {
      this._confirmResolver = resolve; // Store resolver for Escape handling
      const modal = document.getElementById("confirmModal");
      if (!modal) { resolve(false); return; }
      document.getElementById("confirmModalMessage").textContent = message;
      modal.style.display = "block";
      this._lastFocused = document.activeElement;
      const focusable = modal.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
      if (focusable) focusable.focus();
      // Store bound refs for cleanup
      this._trapFocusRef = this._trapFocus.bind(this, modal);
      this._handleEscapeRef = this._handleEscape.bind(this, "confirmModal");
      modal.addEventListener('keydown', this._trapFocusRef);
      modal.addEventListener('keydown', this._handleEscapeRef);
      document.body.style.overflow = 'hidden';

      const cleanup = () => {
        modal.style.display = "none";
        modal.removeEventListener('keydown', this._trapFocusRef);
        modal.removeEventListener('keydown', this._handleEscapeRef);
        document.body.style.overflow = '';
        if (this._lastFocused) this._lastFocused.focus();
        this._confirmResolver = null;
      };

      const onConfirm = () => {
        cleanup();
        document.getElementById("confirmModalConfirm").removeEventListener("click", onConfirm);
        document.getElementById("confirmModalCancel").removeEventListener("click", onCancel);
        resolve(true);
      };
      const onCancel = () => {
        cleanup();
        document.getElementById("confirmModalConfirm").removeEventListener("click", onConfirm);
        document.getElementById("confirmModalCancel").removeEventListener("click", onCancel);
        resolve(false);
      };
      document.getElementById("confirmModalConfirm").addEventListener("click", onConfirm);
      document.getElementById("confirmModalCancel").addEventListener("click", onCancel);
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
    if (e.key === 'Escape') {
      if (id === "confirmModal" && this._confirmResolver) {
        this._confirmResolver(false);
        this._confirmResolver = null;
      }
      this.closeModal(id);
    }
  }

  showToast(message, type = "success") {
    const toast = document.getElementById("toast");
    if (!toast) return;
    if (this._toastTimeout) clearTimeout(this._toastTimeout);
    toast.textContent = message;
    toast.className   = `toast ${type} show`;
    this._toastTimeout = setTimeout(() => toast.classList.remove("show"), 3000);
  }
}

const app = new CoinTrackerApp();
app.init();
