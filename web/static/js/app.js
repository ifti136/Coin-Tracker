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
    await this.loadInitialData();
    this.createHiddenFileInput(); // Create the file input for imports
  }

  setupEventListeners() {
    // --- MODIFICATION: Updated Mobile Nav Listeners ---
    document
      .getElementById("hamburgerBtn")
      .addEventListener("click", () => this.toggleMobileNav());

    // --- Page Navigation (FIXED) ---
    // This selector only targets the .nav-btn elements inside the main <nav>
    document.querySelectorAll("nav.nav > .nav-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        this.showPage(e.currentTarget.dataset.page);
        // If the collapsible menu is open, close it upon navigation
        if (
          document.querySelector(".sidebar").classList.contains("nav-expanded")
        ) {
          this.toggleMobileNav();
        }
      });
    });

    // --- Core App Listeners ---
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

    // --- History Filters ---
    document
      .getElementById("dateFrom")
      .addEventListener("change", () => this.filterHistory());
    document
      .getElementById("dateTo")
      .addEventListener("change", () => this.filterHistory());
    document
      .getElementById("historySourceFilter")
      .addEventListener("change", () => this.filterHistory());
    document
      .getElementById("historySearch")
      .addEventListener("input", () => this.filterHistory());

    // --- Settings Page (NEW) ---
    const addQuickActionBtn = document.getElementById("addQuickActionBtn");
    if(addQuickActionBtn) {
        addQuickActionBtn.addEventListener("click", () => this.addNewQuickAction());
    }
    
    // This button is just for show now
    const customizeBtn = document.getElementById("customizeQuickActions");
    if(customizeBtn) {
        customizeBtn.addEventListener('click', () => this.showPage('settings'));
    }


    // --- Data Management (Updated) ---
    document
      .getElementById("exportDataBtn")
      .addEventListener("click", () => this.exportData());
    document
      .getElementById("importDataBtn")
      .addEventListener("click", () => this.triggerImport());

    // --- Logout Button ---
    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
      logoutBtn.addEventListener("click", () => this.logout());
    }
  }

  // --- NEW: Import/Export Methods ---

  /**
   * Creates a hidden file input element and adds it to the page.
   * This is used to trigger the file selection dialog for importing.
   */
  createHiddenFileInput() {
    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.id = "jsonImporter";
    fileInput.accept = ".json,application/json";
    fileInput.style.display = "none";
    fileInput.addEventListener("change", (e) => this.handleFileImport(e));
    document.body.appendChild(fileInput);
  }

  /**
   * Triggers a click on the hidden file input.
   */
  triggerImport() {
    document.getElementById("jsonImporter").click();
  }

  /**
   * Handles the file selection from the hidden input.
   * Reads the file as text and passes it to be processed.
   * @param {Event} event - The 'change' event from the file input.
   */
  handleFileImport(event) {
    const file = event.target.files[0];
    if (!file) {
      return; // User cancelled
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const data = JSON.parse(e.target.result);
        this.processImportedData(data);
      } catch (error) {
        this.showToast("Invalid or corrupt JSON file.", "error");
        console.error("Failed to parse imported file:", error);
      }
    };
    reader.readAsText(file);

    // Reset the input value to allow importing the same file again
    event.target.value = null;
  }

  /**
   * Validates the parsed JSON data and sends it to the backend.
   * @param {object} data - The parsed data from the JSON file.
   */
  async processImportedData(data) {
    // Basic validation
    if (
      !data ||
      typeof data.transactions === "undefined" ||
      typeof data.settings === "undefined"
    ) {
      this.showToast(
        "Invalid file format. Missing 'transactions' or 'settings'.",
        "error"
      );
      return;
    }

    this.showToast("Importing data...", "success");

    // Send the entire valid data object to the backend
    // NOTE: This uses the /api/import-data endpoint from app.py
    const result = await this.apiCall("/api/import-data", "POST", data);

    if (result && result.success) {
      this.data = result.data; // Server returns the new full data object
      this.updateAllUI();

      // Update profile dropdown in case new profiles were imported
      const profilesData = await this.apiCall("/api/profiles");
      if (profilesData) {
        this.updateProfileDropdown(
          profilesData.profiles,
          profilesData.current_profile
        );
      }

      this.showToast("Data imported successfully!", "success");
    }
    // apiCall will show an error toast if it fails
  }

  /**
   * Exports the current `this.data` object as a downloadable JSON file.
   */
  exportData() {
    try {
      // Use the full data object from the last /api/data call
      const dataToExport = {
        settings: this.data.settings,
        transactions: this.data.transactions
      };

      const dataStr = JSON.stringify(dataToExport, null, 2); // Pretty-print JSON
      const blob = new Blob([dataStr], { type: "application/json" });
      const url = URL.createObjectURL(blob);

      const a = document.createElement("a");
      const profileName = this.data.profile || "Default";
      const date = new Date().toISOString().split("T")[0];
      a.href = url;
      a.download = `coin_tracker_export_${profileName}_${date}.json`;

      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);

      URL.revokeObjectURL(url);
      this.showToast("Data exported successfully!", "success");
    } catch (error) {
      this.showToast("Failed to export data.", "error");
      console.error("Export error:", error);
    }
  }

  // --- Core Class Methods ---
  
  /**
   * MODIFICATION: Rewritten to handle the new collapsible menu.
   * Toggles a class on the sidebar to expand/collapse the navigation content.
   */
  toggleMobileNav() {
    const sidebar = document.querySelector(".sidebar");
    const hamburger = document.getElementById("hamburgerBtn");

    // Toggle a class that expands the navigation content within the sidebar
    sidebar.classList.toggle("nav-expanded");

    // Optional: add a class to the button for styling (e.g., to rotate the icon)
    hamburger.classList.toggle("open");

    // Accessibility
    const expanded = sidebar.classList.contains("nav-expanded");
    hamburger.setAttribute("aria-expanded", expanded ? "true" : "false");
  }

  async apiCall(endpoint, method = "GET", body = null) {
    try {
      const options = {
        method,
        headers: { "Content-Type": "application/json" },
      };
      if (body) options.body = JSON.stringify(body);
      const response = await fetch(endpoint, options);
      
      if (response.status === 401) {
        // Unauthorized, redirect to login
        this.showToast("Session expired. Please log in.", "error");
        window.location.href = "/login";
        return null;
      }
      
      const responseData = await response.json();
      if (!response.ok) {
        // Pass server error message if available
        throw new Error(
          responseData.error || `HTTP error! status: ${response.status}`
        );
      }
      return responseData;
    } catch (error) {
      console.error(`API call to ${endpoint} failed:`, error);
      // Show the specific error message from the server or a generic one
      this.showToast(
        error.message || "An error occurred. Please try again.",
        "error"
      );
      return null;
    }
  }

  async loadInitialData() {
    const data = await this.apiCall("/api/data");
    if (data) {
      this.data = data;
    } else {
      // If data loading fails (e.g., 401 redirect), stop executing
      return; 
    }

    const profilesData = await this.apiCall("/api/profiles");
    if (profilesData)
      this.updateProfileDropdown(
        profilesData.profiles,
        profilesData.current_profile
      );
    
    // Load user data
    const userData = await this.apiCall("/api/user");
    const usernameDisplay = document.getElementById("usernameDisplay");
    if (userData && userData.username && usernameDisplay) {
      usernameDisplay.textContent = userData.username;
    }

    this.updateAllUI();
  }

  updateAllUI() {
    if (!this.data) {
      console.error("No data available to update UI.");
      return;
    }
    this.applyTheme(this.data.settings.dark_mode);
    this.updateBalanceAndGoalUI(
      this.data.balance,
      this.data.goal,
      this.data.progress
    );
    this.updateDashboardStatsUI(this.data.dashboard_stats);
    this.updateQuickActionsUI(this.data.settings.quick_actions);
    this.updateHistoryTableUI(this.data.transactions);
    this.updateAnalyticsUI(this.data.analytics);
    this.updateSettingsPageUI(
      this.data.settings,
      this.data.goal,
      this.data.progress
    );
  }

  applyTheme(isDarkMode) {
    document.documentElement.setAttribute(
      "data-theme",
      isDarkMode ? "dark" : "light"
    );
    document.getElementById("themeToggle").textContent = isDarkMode
      ? "☀️ Light Mode"
      : "🌙 Switch Mode";
    // Re-render charts with new theme colors if they exist
    if (this.data.analytics && Object.keys(this.charts).length > 0) {
      this.updateAnalyticsUI(this.data.analytics);
    }
  }

  // --- TARGETED UI UPDATE FUNCTIONS ---
  // These functions are more efficient as they only update specific parts of the DOM.

  updateBalanceAndGoalUI(balance, goal, progress) {
    document.getElementById(
      "balanceAmount"
    ).textContent = `${balance.toLocaleString()} coins`;
    document.getElementById(
      "goalText"
    ).textContent = `Goal: ${goal.toLocaleString()} coins`;
    document.getElementById("progressBar").style.width = `${progress}%`;
    document.getElementById("progressPercent").textContent = `${progress}%`;
  }

  updateDashboardStatsUI(stats) {
    if (!stats) return;
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

  updateQuickActionsUI(quick_actions) {
    if (!quick_actions) return;
    const grid = document.querySelector(".quick-actions-grid");
    grid.innerHTML = "";
    quick_actions.forEach((action) => {
      const btn = document.createElement("button");
      btn.className = `quick-btn ${
        action.is_positive ? "positive" : "negative"
      }`;
      btn.innerHTML = `<div class="quick-text">${
        action.text
      }</div><div class="quick-amount">${action.is_positive ? "+" : "-"}${
        action.value
      }</div>`;

      btn.onclick = async () => {
        btn.classList.add("is-processing"); // Visual feedback
        const amount = action.is_positive ? action.value : -action.value;
        const result = await this.apiCall("/api/add-transaction", "POST", {
          amount,
          source: action.text,
          date: new Date().toISOString(),
        });
        btn.classList.remove("is-processing"); // Remove feedback

        if (result && result.success) {
          this.showToast(`Quick action '${action.text}' recorded.`, "success");
          this.data = result.data; // Update local state with fresh data from server
          this.updateAllUI(); // Refresh UI with new state
        }
      };
      grid.appendChild(btn);
    });
  }

  updateHistoryTableUI(transactions) {
    if (!transactions) return;
    const tbody = document.getElementById("historyTableBody");
    tbody.innerHTML = "";
    const sortedTransactions = [...transactions].sort(
      (a, b) => new Date(b.date) - new Date(a.date)
    );

    const allSources = [...new Set(transactions.map((t) => t.source))].sort();
    const sourceFilter = document.getElementById("historySourceFilter");
    const currentSource = sourceFilter.value;
    sourceFilter.innerHTML = '<option value="all">All Sources</option>';
    allSources.forEach((source) => {
      const option = document.createElement("option");
      option.value = source;
      option.textContent = source;
      sourceFilter.appendChild(option);
    });
    // Try to preserve the filter
    if (allSources.includes(currentSource)) {
      sourceFilter.value = currentSource;
    }

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

  updateAnalyticsUI(analytics) {
    if (!analytics) return;
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

  updateSettingsPageUI(settings, goal, progress) {
    if (!settings) return;
    document.getElementById("goalInput").value = settings.goal;
    document.getElementById(
      "currentGoalText"
    ).textContent = `Current Goal: ${goal.toLocaleString()} coins`;
    document.getElementById(
      "goalProgressText"
    ).textContent = `You are ${progress}% of the way towards your current goal.`;
    document.getElementById("onlineStatus").textContent =
      settings.firebase_available
        ? "✅ Online (Firebase)"
        : "❌ Offline (Local Storage)";
    
    // NEW: Populate the settings page quick action list
    this.renderQuickActionSettingsList();
  }

  // --- Other methods ---

  filterHistory() {
    const fromDate = document.getElementById("dateFrom").value;
    const toDate = document.getElementById("dateTo").value;
    const searchTerm = document
      .getElementById("historySearch")
      .value.toLowerCase();
    const sourceFilter = document.getElementById("historySourceFilter").value;
    let periodEarned = 0,
      visibleCount = 0;

    Array.from(document.getElementById("historyTableBody").rows).forEach(
      (row) => {
        const rowDate = new Date(row.cells[0].textContent)
          .toISOString()
          .split("T")[0];
        const type = row.cells[1].textContent.toLowerCase();
        const source = row.cells[2].textContent.toLowerCase();
        const amountText = row.cells[3].textContent;
        const amount = parseInt(amountText.replace(/[+,]/g, ""));

        const dateMatch =
          (!fromDate || rowDate >= fromDate) && (!toDate || rowDate <= toDate);
        const sourceMatch =
          sourceFilter === "all" || row.cells[2].textContent === sourceFilter;
        const searchMatch =
          source.includes(searchTerm) ||
          amountText.includes(searchTerm) ||
          type.includes(searchTerm);

        if (dateMatch && sourceMatch && searchMatch) {
          row.style.display = "";
          visibleCount++;
          if (amount > 0) periodEarned += amount;
        } else {
          row.style.display = "none";
        }
      }
    );
    document.getElementById(
      "periodSummary"
    ).textContent = `Showing ${visibleCount} transactions. Earned in Period: ${periodEarned.toLocaleString()} coins`;
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
    if (result && result.success) {
      this.showToast(`Added ${amount} coins!`, "success");
      amountEl.value = ""; // Clear input
      this.data = result.data; // Update local state
      this.updateAllUI(); // Refresh UI
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
    if (result && result.success) {
      this.showToast(`Spent ${amount} coins.`, "success");
      amountEl.value = ""; // Clear input
      this.data = result.data; // Update local state
      this.updateAllUI(); // Refresh UI
    }
  }

  async setGoal() {
    const goalInput = document.getElementById("goalInput");
    const goal = parseInt(goalInput.value);
    if (!isNaN(goal) && goal >= 0) {
      const result = await this.apiCall("/api/update-settings", "POST", {
        goal,
      });
      if (result && result.success) {
        this.showToast("Goal updated!", "success");
        // Update local state from server response
        this.data.settings.goal = result.data.settings.goal;
        this.data.goal = result.data.goal;
        this.data.progress = result.data.progress;
        // Targeted UI updates
        this.updateBalanceAndGoalUI(
          this.data.balance,
          this.data.goal,
          this.data.progress
        );
        this.updateSettingsPageUI(
          this.data.settings,
          this.data.goal,
          this.data.progress
        );
      }
    } else {
      this.showToast("Please enter a valid goal amount.", "error");
      goalInput.value = this.data.goal; // Reset to old value
    }
  }

  async switchProfile(profileName) {
    this.showToast(`Loading profile: ${profileName}...`, "success");
    const result = await this.apiCall("/api/switch-profile", "POST", {
      profile_name: profileName,
    });
    if (result && result.success) {
      await this.loadInitialData(); // Reload all data for the new profile
      this.showToast(`Switched to profile: ${profileName}`, "success");
    }
    // apiCall shows error on failure
  }

  /**
   * **FIXED**: Optimistically updates the theme for a responsive UI
   * and *then* sends the API call in the background.
   */
  async toggleTheme() {
    // 1. Optimistically update the local state and UI
    const newDarkMode = !this.data.settings.dark_mode;
    this.data.settings.dark_mode = newDarkMode;
    this.applyTheme(newDarkMode); // <-- This is called IMMEDIATELY

    // 2. Send the API call in the background to persist the change
    try {
      const result = await this.apiCall("/api/update-settings", "POST", {
        dark_mode: newDarkMode,
      });
      if (!result || !result.success) {
        // If it fails, roll back the change and notify user
        this.showToast("Failed to save theme. Reverting.", "error");
        this.data.settings.dark_mode = !newDarkMode;
        this.applyTheme(!newDarkMode);
      }
      // If it succeeds, do nothing, the UI is already correct
    } catch (error) {
      // Handle network failure
      this.showToast("Failed to save theme. Reverting.", "error");
      this.data.settings.dark_mode = !newDarkMode;
      this.applyTheme(!newDarkMode);
    }
  }

  createOrUpdateChart(canvasId, type, labels, data) {
    if (this.charts[canvasId]) {
      this.charts[canvasId].destroy();
    }
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;

    // Get colors from CSS variables
    const textColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--text-color");
    const gridColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--border-color");
    const primaryColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--primary-color");
    const successColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--success-color");
    const dangerColor = getComputedStyle(
      document.documentElement
    ).getPropertyValue("--danger-color");

    this.charts[canvasId] = new Chart(ctx, {
      type,
      data: {
        labels,
        datasets: [
          {
            data,
            backgroundColor:
              type === "doughnut"
                ? [
                    primaryColor,
                    successColor,
                    "#f59e0b",
                    dangerColor,
                    "#8b5cf6",
                  ]
                : type === "line"
                ? "rgba(59, 130, 246, 0.1)"
                : dangerColor,
            borderColor: type === "line" ? primaryColor : "transparent",
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
                y: {
                  ticks: { color: textColor },
                  grid: { color: gridColor },
                },
                x: {
                  ticks: { color: textColor },
                  grid: { color: gridColor },
                },
              }
            : {},
      },
    });
  }

  showPage(pageId) {
    // Add guard clause
    if (!pageId) {
      console.error("showPage called with no pageId");
      return;
    }
    
    document
      .querySelectorAll(".page")
      .forEach((p) => p.classList.remove("active"));
    
    const pageElement = document.getElementById(pageId);
    if (pageElement) {
        pageElement.classList.add("active");
    } else {
        console.error(`Page with id "${pageId}" not found.`);
    }

    // --- THIS IS THE FIX ---
    // Use a specific selector to only target buttons in the main nav
    document
      .querySelectorAll("nav.nav > .nav-btn")
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

  // --- Modals and Context Menus ---

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

    let isIncome = isIncomeDefault;
    if (id) {
      // If editing, base 'isIncome' on the original transaction
      const originalTransaction = this.data.transactions.find(
        (t) => t.id === id
      );
      if (originalTransaction) {
        isIncome = originalTransaction.amount > 0;
      }
    }

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
      this.data = result.data; // Update local state
      this.updateAllUI(); // Refresh UI
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
      const transaction = this.data.transactions.find(
        (t) => t.id === row.dataset.id
      );
      if (transaction) {
        this.showTransactionModal(transaction.amount > 0, row.dataset.id);
      }
      this.removeContextMenu();
    };
    menu.querySelector('[data-action="delete"]').onclick = async () => {
      // We can't use window.confirm, so we'll just delete directly.
      this.showToast("Deleting transaction...", "success");
      const result = await this.apiCall(
        `/api/delete-transaction/${row.dataset.id}`,
        "POST"
      );
      if (result && result.success) {
        this.showToast(result.message, "success");
        this.data = result.data; // Update local state
        this.updateAllUI(); // Refresh UI
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
    if (!name || name.length < 2) {
      return this.showToast(
        "Profile name must be at least 2 characters.",
        "error"
      );
    }

    const result = await this.apiCall("/api/create-profile", "POST", {
      profile_name: name,
    });
    if (result && result.success) {
      document.getElementById("profileModal").style.display = "none";
      this.showToast(`Profile '${name}' created!`, "success");
      // Reload all data and switch to the new profile
      await this.loadInitialData();
      // The server should ideally set the new profile as active
      // but we'll manually update the dropdown just in case
      this.updateProfileDropdown(result.profiles, result.current_profile);
    }
    // apiCall shows error on failure
  }

  // --- NEW: Quick Action Management Methods ---

  /**
   * Renders the list of quick actions on the Settings page
   */
  renderQuickActionSettingsList() {
    const listEl = document.getElementById("quickActionList");
    if (!listEl) return;
    
    const actions = this.data.settings.quick_actions || [];
    listEl.innerHTML = ""; // Clear list
    
    if (actions.length === 0) {
        listEl.innerHTML = "<p>No quick actions added yet.</p>";
        return;
    }

    actions.forEach((action, index) => {
      const item = document.createElement("div");
      item.className = "quick-action-list-item";

      const isPositive = action.is_positive;
      const amountClass = isPositive ? "positive" : "negative";
      const amountSign = isPositive ? "+" : "-";

      item.innerHTML = `
        <div class="quick-action-details">
          <span class="quick-action-text">${action.text}</span>
          <span class="quick-action-amount ${amountClass}">
            ${amountSign}${action.value}
          </span>
        </div>
        <button class="delete-btn" data-index="${index}">Delete</button>
      `;
      
      item.querySelector(".delete-btn").addEventListener("click", () => {
          this.deleteQuickAction(index);
      });
      
      listEl.appendChild(item);
    });
  }

  /**
   * Adds a new quick action from the Settings page form
   */
  async addNewQuickAction() {
    const textEl = document.getElementById("quickActionText");
    const amountEl = document.getElementById("quickActionAmount");
    const typeEl = document.getElementById("quickActionType");

    const text = textEl.value;
    const amount = parseInt(amountEl.value);
    const isPositive = typeEl.value === "positive";

    if (!text || !amount || amount <= 0) {
      this.showToast("Please enter valid text and amount.", "error");
      return;
    }

    const newAction = {
      text: text,
      value: amount,
      is_positive: isPositive,
    };

    const result = await this.apiCall("/api/add-quick-action", "POST", newAction);
    
    if (result && result.success) {
        this.showToast("Quick Action added!", "success");
        this.data = result.data; // Refresh all data from server
        this.updateAllUI(); // Redraw everything
        
        // Clear inputs
        textEl.value = "";
        amountEl.value = "";
    }
  }

  /**
   * Deletes a quick action by its index
   */
  async deleteQuickAction(index) {
    this.showToast("Deleting action...", "success");
    const result = await this.apiCall("/api/delete-quick-action", "POST", { index });
    
    if (result && result.success) {
        this.showToast("Quick Action removed!", "success");
        this.data = result.data; // Refresh all data from server
        this.updateAllUI(); // Redraw everything
    }
  }

  async logout() {
    this.showToast("Logging out...", "success");
    const result = await this.apiCall("/api/logout", "POST");
    if (result && result.success) {
      window.location.href = "/login";
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


