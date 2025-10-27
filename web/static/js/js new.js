class CoinTrackerApp {
  constructor() {
    this.data = {
      transactions: [],
      settings: {},
      profile: "Default",
      analytics: {},
      dashboard_stats: {},
    };
    this.charts = {};
  }

  async init() {
    this.setupEventListeners();
    await this.refreshDataAndUpdateUI();
  }

  setupEventListeners() {
    document
      .querySelectorAll(".nav-btn")
      .forEach((btn) =>
        btn.addEventListener("click", (e) =>
          this.showPage(e.currentTarget.dataset.page)
        )
      );
    document
      .getElementById("themeToggle")
      .addEventListener("click", () => this.toggleTheme());
    document
      .getElementById("profileSelect")
      .addEventListener("change", (e) => this.switchProfile(e.target.value));
    document
      .getElementById("newProfileBtn")
      .addEventListener("click", () => this.showProfileModal());
    document
      .getElementById("addCoinsBtn")
      .addEventListener("click", () => this.addFromDashboard());
    document
      .getElementById("spendCoinsBtn")
      .addEventListener("click", () => this.spendFromDashboard());
    document
      .getElementById("setGoalBtn")
      .addEventListener("click", () => this.setGoal());
    document
      .querySelectorAll(".tab-header")
      .forEach((tab) =>
        tab.addEventListener("click", (e) => this.switchTab(e.currentTarget))
      );
    document
      .getElementById("historyTableBody")
      .addEventListener("contextmenu", (e) => this.showContextMenu(e));
    document
      .getElementById("dateFrom")
      .addEventListener("change", () => this.filterHistory());
    document
      .getElementById("dateTo")
      .addEventListener("change", () => this.filterHistory());
    document
      .getElementById("historySearch")
      .addEventListener("input", () => this.filterHistory());
    // Placeholder listeners for new settings buttons
    document
      .getElementById("manageQuickActionsBtn")
      .addEventListener("click", () =>
        this.showToast("Customize Quick Actions coming soon!", "success")
      );
    document
      .getElementById("exportDataBtn")
      .addEventListener("click", () =>
        this.showToast("Export feature coming soon!", "success")
      );
    document
      .getElementById("importDataBtn")
      .addEventListener("click", () =>
        this.showToast("Import feature coming soon!", "success")
      );
    document
      .getElementById("createBackupBtn")
      .addEventListener("click", () =>
        this.showToast("Backup feature coming soon!", "success")
      );
  }

  async apiCall(endpoint, method = "GET", body = null) {
    try {
      const options = {
        method,
        headers: { "Content-Type": "application/json" },
      };
      if (body) options.body = JSON.stringify(body);
      const response = await fetch(endpoint, options);
      if (!response.ok)
        throw new Error(`HTTP error! status: ${response.status}`);
      return await response.json();
    } catch (error) {
      console.error(`API call to ${endpoint} failed:`, error);
      this.showToast("An error occurred. Please try again.", "error");
      return null;
    }
  }

  async refreshDataAndUpdateUI() {
    const data = await this.apiCall("/api/data");
    if (data) this.data = data;

    const profilesData = await this.apiCall("/api/profiles");
    if (profilesData)
      this.updateProfileDropdown(
        profilesData.profiles,
        profilesData.current_profile
      );

    this.updateAllUI();
  }

  updateAllUI() {
    this.applyTheme(this.data.settings.dark_mode);
    this.updateBalanceAndGoal();
    this.updateDashboardStats();
    this.updateQuickActions();
    this.updateHistoryTable();
    this.updateAnalytics();
    this.updateSettingsPage();
  }

  applyTheme(isDarkMode) {
    document.documentElement.setAttribute(
      "data-theme",
      isDarkMode ? "dark" : "light"
    );
    document.getElementById("themeToggle").textContent = isDarkMode
      ? "☀️ Light Mode"
      : "🌙 Switch Mode";
    if (Object.keys(this.charts).length > 0) this.updateAnalytics();
  }

  updateBalanceAndGoal() {
    document.getElementById(
      "balanceAmount"
    ).textContent = `${this.data.balance.toLocaleString()} coins`;
    document.getElementById(
      "goalText"
    ).textContent = `Goal: ${this.data.goal.toLocaleString()} coins`;
    document.getElementById(
      "progressBar"
    ).style.width = `${this.data.progress}%`;
    document.getElementById(
      "progressPercent"
    ).textContent = `${this.data.progress}%`;
  }

  updateDashboardStats() {
    const stats = this.data.dashboard_stats;
    document.getElementById(
      "todayEarnings"
    ).textContent = `+${stats.today.toLocaleString()}`;
    document.getElementById(
      "weekEarnings"
    ).textContent = `+${stats.week.toLocaleString()}`;
    document.getElementById(
      "monthEarnings"
    ).textContent = `+${stats.month.toLocaleString()}`;
  }

  updateQuickActions() {
    const grid = document.querySelector(".quick-actions-grid");
    grid.innerHTML = "";
    this.data.settings.quick_actions.forEach((action) => {
      const btn = document.createElement("button");
      btn.className = "quick-btn";
      btn.innerHTML = `<div class="quick-text">${
        action.text
      }</div><div class="quick-amount">${action.is_positive ? "+" : "-"}${
        action.value
      }</div>`;
      btn.onclick = async () => {
        const amount = action.is_positive ? action.value : -action.value;
        const result = await this.apiCall("/api/add-transaction", "POST", {
          amount,
          source: action.text,
          date: new Date().toISOString(),
        });
        if (result.success) this.refreshDataAndUpdateUI();
      };
      grid.appendChild(btn);
    });
  }

  updateHistoryTable() {
    const tbody = document.getElementById("historyTableBody");
    tbody.innerHTML = "";
    const sortedTransactions = [...this.data.transactions].sort(
      (a, b) => new Date(b.date) - new Date(a.date)
    );

    const allSources = [
      ...new Set(this.data.transactions.map((t) => t.source)),
    ].sort();
    const sourceFilter = document.getElementById("historySourceFilter");
    const currentSource = sourceFilter.value;
    sourceFilter.innerHTML = '<option value="all">All Sources</option>';
    allSources.forEach((source) => {
      const option = document.createElement("option");
      option.value = source;
      option.textContent = source;
      sourceFilter.appendChild(option);
    });
    sourceFilter.value = currentSource;

    sortedTransactions.forEach((t) => {
      const tr = document.createElement("tr");
      tr.dataset.id = t.id;
      const amountClass = t.amount >= 0 ? "amount-positive" : "amount-negative";
      const balanceAfter = (t.previous_balance || 0) + t.amount;
      tr.innerHTML = `
                <td>${new Date(t.date).toLocaleString()}</td>
                <td>${t.amount >= 0 ? "Income" : "Expense"}</td>
                <td>${t.source}</td>
                <td class="${amountClass}">${
        t.amount >= 0 ? "+" : ""
      }${t.amount.toLocaleString()}</td>
                <td>${balanceAfter.toLocaleString()}</td>
            `;
      tbody.appendChild(tr);
    });
    this.filterHistory();
  }

  filterHistory() {
    const fromDate = document.getElementById("dateFrom").value;
    const toDate = document.getElementById("dateTo").value;
    const searchTerm = document
      .getElementById("historySearch")
      .value.toLowerCase();
    const sourceFilter = document.getElementById("historySourceFilter").value;
    let periodEarned = 0;

    Array.from(document.getElementById("historyTableBody").rows).forEach(
      (row) => {
        const rowDate = new Date(row.cells[0].textContent)
          .toISOString()
          .split("T")[0];
        const source = row.cells[2].textContent;
        const amountText = row.cells[3].textContent;
        const amount = parseInt(amountText.replace(/[+,]/g, ""));
        const dateMatch =
          (!fromDate || rowDate >= fromDate) && (!toDate || rowDate <= toDate);
        const sourceMatch = sourceFilter === "all" || source === sourceFilter;
        const searchMatch =
          source.toLowerCase().includes(searchTerm) ||
          amountText.includes(searchTerm);
        row.style.display = dateMatch && searchMatch ? "" : "none";
        if (row.style.display !== "none" && amount > 0) periodEarned += amount;
      }
    );
    document.getElementById(
      "periodSummary"
    ).textContent = `Earned in Period: ${periodEarned.toLocaleString()} coins`;
  }

  updateAnalytics() {
    const analytics = this.data.analytics;
    document.getElementById(
      "totalEarnings"
    ).textContent = `+${analytics.total_earnings.toLocaleString()}`;
    document.getElementById(
      "totalSpending"
    ).textContent = `-${analytics.total_spending.toLocaleString()}`;
    document.getElementById("netBalance").textContent = `${
      analytics.net_balance >= 0 ? "+" : ""
    }${analytics.net_balance.toLocaleString()}`;
    this.createOrUpdateChart(
      "earningsChart",
      "doughnut",
      Object.keys(analytics.earnings_breakdown),
      Object.values(analytics.earnings_breakdown)
    );
    this.createOrUpdateChart(
      "spendingChart",
      "bar",
      Object.keys(analytics.spending_breakdown),
      Object.values(analytics.spending_breakdown)
    );
    this.createOrUpdateChart(
      "timelineChart",
      "line",
      analytics.timeline.map((p) => new Date(p.date).toLocaleDateString()),
      analytics.timeline.map((p) => p.balance)
    );
  }

  updateSettingsPage() {
    document.getElementById("goalInput").value = this.data.settings.goal;
    document.getElementById(
      "currentGoalText"
    ).textContent = `Current Goal: ${this.data.goal.toLocaleString()} coins`;
    document.getElementById(
      "goalProgressText"
    ).textContent = `You are ${this.data.progress}% of the way towards your current goal.`;
    document.getElementById("onlineStatus").textContent = this.data.settings
      .firebase_available
      ? "✅ Online (Firebase)"
      : "❌ Offline (Local Storage)";
  }

  createOrUpdateChart(canvasId, type, labels, data) {
    if (this.charts[canvasId]) this.charts[canvasId].destroy();
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;
    const textColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--text-color");
    const gridColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--border-color");
    this.charts[canvasId] = new Chart(ctx, {
      type,
      data: {
        labels,
        datasets: [
          {
            data,
            backgroundColor:
              type === "doughnut"
                ? ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6"]
                : type === "line"
                ? "rgba(59, 130, 246, 0.1)"
                : "#ef4444",
            borderColor: type === "line" ? "#3b82f6" : "transparent",
            borderWidth: 2,
            tension: 0.1,
            fill: type === "line",
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            display: type === "doughnut",
            position: "bottom",
            labels: { color: textColor },
          },
        },
        scales:
          type !== "doughnut"
            ? {
                y: { ticks: { color: textColor }, grid: { color: gridColor } },
                x: { ticks: { color: textColor }, grid: { color: gridColor } },
              }
            : {},
      },
    });
  }

  showPage(pageId) {
    document
      .querySelectorAll(".page")
      .forEach((p) => p.classList.remove("active"));
    document.getElementById(pageId).classList.add("active");
    document
      .querySelectorAll(".nav-btn")
      .forEach((btn) =>
        btn.classList.toggle("active", btn.dataset.page === pageId)
      );
  }

  switchTab(tabElement) {
    const parent = tabElement.parentElement;
    parent
      .querySelectorAll(".tab-header")
      .forEach((t) => t.classList.remove("active"));
    tabElement.classList.add("active");
    parent.nextElementSibling
      .querySelectorAll(".tab-pane")
      .forEach((p) => p.classList.remove("active"));
    document.getElementById(tabElement.dataset.tab).classList.add("active");
  }

  async toggleTheme() {
    const newDarkMode = !this.data.settings.dark_mode;
    await this.apiCall("/api/update-settings", "POST", {
      dark_mode: newDarkMode,
    });
    this.data.settings.dark_mode = newDarkMode;
    this.applyTheme(newDarkMode);
  }

  async setGoal() {
    const goal = parseInt(document.getElementById("goalInput").value);
    if (!isNaN(goal) && goal >= 0) {
      const result = await this.apiCall("/api/update-settings", "POST", {
        goal,
      });
      if (result.success) {
        this.showToast("Goal updated!", "success");
        this.refreshDataAndUpdateUI();
      }
    } else {
      this.showToast("Please enter a valid goal amount.", "error");
    }
  }

  async addFromDashboard() {
    const amountEl = document.getElementById("addAmount");
    const sourceEl = document.getElementById("addSource");
    const amount = parseInt(amountEl.value);
    const source = sourceEl.value;
    if (!amount || amount <= 0 || !source)
      return this.showToast(
        "Please provide a valid amount and source.",
        "error"
      );

    const result = await this.apiCall("/api/add-transaction", "POST", {
      amount,
      source,
      date: new Date().toISOString(),
    });
    if (result.success) {
      this.showToast(`Added ${amount} coins!`, "success");
      amountEl.value = "";
      this.refreshDataAndUpdateUI();
    }
  }

  async spendFromDashboard() {
    const amountEl = document.getElementById("spendAmount");
    const sourceEl = document.getElementById("spendCategory");
    const amount = parseInt(amountEl.value);
    const source = sourceEl.value;
    if (!amount || amount <= 0 || !source)
      return this.showToast(
        "Please provide a valid amount and category.",
        "error"
      );

    const result = await this.apiCall("/api/add-transaction", "POST", {
      amount: -amount,
      source,
      date: new Date().toISOString(),
    });
    if (result.success) {
      this.showToast(`Spent ${amount} coins.`, "success");
      amountEl.value = "";
      this.refreshDataAndUpdateUI();
    }
  }

  showTransactionModal(isIncome, transactionId = null) {
    const modal = document.getElementById("transactionModal");
    const transaction = transactionId
      ? this.data.transactions.find((t) => t.id === transactionId)
      : null;
    modal.querySelector(".modal-title").textContent = transaction
      ? "Edit Transaction"
      : isIncome
      ? "Add Coins"
      : "Spend Coins";
    modal.querySelector("#sourceLabel").textContent = isIncome
      ? "Source"
      : "Category";
    document.getElementById("transactionId").value = transactionId || "";
    document.getElementById("transactionAmount").value = transaction
      ? Math.abs(transaction.amount)
      : "";
    document.getElementById("transactionSource").value = transaction
      ? transaction.source
      : "";
    document.getElementById("transactionDate").value = (
      transaction ? new Date(transaction.date) : new Date()
    )
      .toISOString()
      .slice(0, 16);
    modal.querySelector(".close").onclick = () =>
      (modal.style.display = "none");
    modal.querySelector("#saveTransactionBtn").onclick = () =>
      this.saveTransaction(isIncome);
    modal.style.display = "block";
  }

  async saveTransaction(isIncomeDefault) {
    const id = document.getElementById("transactionId").value;
    let amount = parseInt(document.getElementById("transactionAmount").value);
    const source = document.getElementById("transactionSource").value;
    const date = new Date(
      document.getElementById("transactionDate").value
    ).toISOString();
    if (isNaN(amount) || amount <= 0 || !source)
      return this.showToast("Please fill all fields correctly.", "error");
    const isIncome = id
      ? this.data.transactions.find((t) => t.id === id).amount > 0
      : isIncomeDefault;
    if (!isIncome) amount = -amount;
    const endpoint = id
      ? `/api/update-transaction/${id}`
      : "/api/add-transaction";
    const result = await this.apiCall(endpoint, "POST", {
      amount,
      source,
      date,
    });
    if (result && result.success) {
      this.showToast(result.message, "success");
      document.getElementById("transactionModal").style.display = "none";
      this.refreshDataAndUpdateUI();
    }
  }

  showContextMenu(event) {
    event.preventDefault();
    const row = event.target.closest("tr");
    if (!row || !row.dataset.id) return;
    this.removeContextMenu();
    const menu = document.createElement("div");
    menu.className = "context-menu";
    menu.style.top = `${event.pageY}px`;
    menu.style.left = `${event.pageX}px`;
    menu.innerHTML = `<div class="context-menu-item" data-action="edit">✎ Edit</div><div class="context-menu-item" data-action="delete">🗑️ Delete</div>`;
    document.body.appendChild(menu);
    menu.querySelector('[data-action="edit"]').onclick = () => {
      const isIncome =
        this.data.transactions.find((t) => t.id === row.dataset.id).amount > 0;
      this.showTransactionModal(isIncome, row.dataset.id);
      this.removeContextMenu();
    };
    menu.querySelector('[data-action="delete"]').onclick = async () => {
      if (confirm("Delete transaction?")) {
        const result = await this.apiCall(
          `/api/delete-transaction/${row.dataset.id}`,
          "POST"
        );
        if (result.success) this.refreshDataAndUpdateUI();
      }
      this.removeContextMenu();
    };
    document.addEventListener("click", () => this.removeContextMenu(), {
      once: true,
    });
  }

  removeContextMenu() {
    document.querySelector(".context-menu")?.remove();
  }

  updateProfileDropdown(profiles, currentProfile) {
    const select = document.getElementById("profileSelect");
    select.innerHTML = profiles
      .map(
        (p) =>
          `<option value="${p}" ${
            p === currentProfile ? "selected" : ""
          }>${p}</option>`
      )
      .join("");
  }

  async switchProfile(profileName) {
    await this.apiCall("/api/switch-profile", "POST", {
      profile_name: profileName,
    });
    this.refreshDataAndUpdateUI();
  }

  showProfileModal() {
    const modal = document.getElementById("profileModal");
    modal.querySelector(".close").onclick = () =>
      (modal.style.display = "none");
    modal.querySelector("#createProfileBtn").onclick = () =>
      this.createProfile();
    modal.style.display = "block";
    document.getElementById("newProfileName").value = "";
  }

  async createProfile() {
    const name = document.getElementById("newProfileName").value;
    if (name && name.length > 1) {
      const result = await this.apiCall("/api/create-profile", "POST", {
        profile_name: name,
      });
      if (result.success) {
        document.getElementById("profileModal").style.display = "none";
        this.refreshDataAndUpdateUI();
      } else {
        this.showToast(result.error || "Failed to create profile.", "error");
      }
    }
  }

  showToast(message, type = "success") {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.className = `toast ${type} show`;
    setTimeout(() => toast.classList.remove("show"), 3000);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  const app = new CoinTrackerApp();
  app.init();
});
