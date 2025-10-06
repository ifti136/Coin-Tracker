class CoinTrackerApp {
  constructor() {
    this.currentPage = "dashboard";
    this.transactions = [];
    this.settings = { goal: 13500, dark_mode: false };
    this.earningsChart = null;

    this.init();
  }

  init() {
    this.setupEventListeners();
    this.loadData();
    this.showPage("dashboard");
  }

  setupEventListeners() {
    // Navigation
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        const page = e.currentTarget.dataset.page;
        this.showPage(page);
      });
    });

    // Theme toggle
    document.getElementById("themeToggle").addEventListener("click", () => {
      this.toggleTheme();
    });

    // Quick actions
    document.querySelectorAll(".quick-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        const action = e.currentTarget.dataset.action;
        const amount = parseInt(e.currentTarget.dataset.amount);
        const isPositive = e.currentTarget.classList.contains("positive");

        if (isPositive) {
          this.addCoins(amount, action);
        } else {
          this.spendCoins(amount, action);
        }
      });
    });

    // Add coins
    document.getElementById("addCoinsBtn").addEventListener("click", () => {
      const amount = document.getElementById("addAmount").value;
      const source = document.getElementById("addSource").value;

      if (amount && source) {
        this.addCoins(parseInt(amount), source);
      } else {
        this.showToast("Please enter amount and select source", "error");
      }
    });

    // Spend coins
    document.getElementById("spendCoinsBtn").addEventListener("click", () => {
      const amount = document.getElementById("spendAmount").value;
      const category = document.getElementById("spendCategory").value;

      if (amount && category) {
        this.spendCoins(parseInt(amount), category);
      } else {
        this.showToast("Please enter amount and select category", "error");
      }
    });

    // Set goal
    document.getElementById("setGoalBtn").addEventListener("click", () => {
      const goal = document.getElementById("goalInput").value;
      if (goal) {
        this.setGoal(parseInt(goal));
      } else {
        this.showToast("Please enter a goal amount", "error");
      }
    });

    // History filters
    document.getElementById("historySearch").addEventListener("input", () => {
      this.filterHistory();
    });

    document
      .getElementById("historySourceFilter")
      .addEventListener("change", () => {
        this.filterHistory();
      });
  }

  async loadData() {
    try {
      const [balanceRes, transactionsRes, analyticsRes] = await Promise.all([
        fetch("/api/balance"),
        fetch("/api/transactions"),
        fetch("/api/analytics"),
      ]);

      if (balanceRes.ok && transactionsRes.ok && analyticsRes.ok) {
        const balanceData = await balanceRes.json();
        this.transactions = await transactionsRes.json();
        const analyticsData = await analyticsRes.json();

        this.updateBalance(balanceData);
        this.updateTransactions();
        this.updateAnalytics(analyticsData);
      }
    } catch (error) {
      console.error("Error loading data:", error);
      this.showToast("Error loading data", "error");
    }
  }

  updateBalance(data) {
    document.getElementById(
      "balanceAmount"
    ).textContent = `${data.balance.toLocaleString()} coins`;
    document.getElementById(
      "goalText"
    ).textContent = `Progress to Goal: ${data.goal.toLocaleString()} coins`;

    const progressBar = document.getElementById("progressBar");
    const progressText = document.getElementById("progressText");

    progressBar.style.width = `${data.progress}%`;
    progressText.textContent = `${data.progress}%`;

    // Update goal in settings
    document.getElementById(
      "currentGoal"
    ).textContent = `Current goal: ${data.goal.toLocaleString()} coins`;
    document.getElementById("goalInput").value = data.goal;
  }

  updateTransactions() {
    const tableBody = document.getElementById("historyTableBody");
    tableBody.innerHTML = "";

    let totalEarned = 0;
    let totalSpent = 0;

    this.transactions.forEach((transaction) => {
      const row = document.createElement("tr");
      const date = new Date(transaction.date).toLocaleString();
      const amount = transaction.amount;
      const amountClass = amount >= 0 ? "amount-positive" : "amount-negative";
      const amountText =
        amount >= 0 ? `+${amount.toLocaleString()}` : amount.toLocaleString();

      if (amount > 0) totalEarned += amount;
      if (amount < 0) totalSpent += Math.abs(amount);

      row.innerHTML = `
                <td>${date}</td>
                <td class="${amountClass}">${amountText}</td>
                <td>${transaction.source}</td>
            `;
      tableBody.appendChild(row);
    });

    // Update stats
    document.getElementById("totalTransactions").textContent =
      this.transactions.length.toLocaleString();
    document.getElementById(
      "historyEarned"
    ).textContent = `+${totalEarned.toLocaleString()}`;
    document.getElementById(
      "historySpent"
    ).textContent = `-${totalSpent.toLocaleString()}`;
  }

  updateAnalytics(data) {
    // Update stats
    document.getElementById(
      "totalEarnings"
    ).textContent = `+${data.total_earnings.toLocaleString()}`;
    document.getElementById(
      "totalSpending"
    ).textContent = `-${data.total_spending.toLocaleString()}`;
    document.getElementById("netBalance").textContent = `${
      data.net_balance >= 0 ? "+" : ""
    }${data.net_balance.toLocaleString()}`;

    // Update source breakdown
    const breakdownContainer = document.getElementById("sourceBreakdown");
    breakdownContainer.innerHTML = "";

    const total = data.total_earnings;
    Object.entries(data.breakdown).forEach(([source, amount]) => {
      const percentage = total > 0 ? ((amount / total) * 100).toFixed(1) : 0;
      const item = document.createElement("div");
      item.textContent = `• ${source}: ${amount.toLocaleString()} coins (${percentage}%)`;
      breakdownContainer.appendChild(item);
    });

    // Update chart
    this.updateEarningsChart(data.breakdown);
  }

  updateEarningsChart(breakdown) {
    const ctx = document.getElementById("earningsChart").getContext("2d");

    if (this.earningsChart) {
      this.earningsChart.destroy();
    }

    const labels = Object.keys(breakdown);
    const data = Object.values(breakdown);
    const colors = [
      "#3b82f6",
      "#10b981",
      "#f59e0b",
      "#ef4444",
      "#8b5cf6",
      "#06b6d4",
    ];

    this.earningsChart = new Chart(ctx, {
      type: "pie",
      data: {
        labels: labels,
        datasets: [
          {
            data: data,
            backgroundColor: colors,
            borderWidth: 2,
            borderColor: getComputedStyle(
              document.documentElement
            ).getPropertyValue("--bg-color"),
          },
        ],
      },
      options: {
        responsive: true,
        plugins: {
          legend: {
            position: "bottom",
            labels: {
              color: getComputedStyle(
                document.documentElement
              ).getPropertyValue("--text-color"),
              padding: 20,
            },
          },
        },
      },
    });
  }

  filterHistory() {
    const searchText = document
      .getElementById("historySearch")
      .value.toLowerCase();
    const sourceFilter = document.getElementById("historySourceFilter").value;

    const tableBody = document.getElementById("historyTableBody");
    const rows = tableBody.getElementsByTagName("tr");

    for (let row of rows) {
      const source = row.cells[2].textContent;
      const amount = row.cells[1].textContent;

      const sourceMatch = sourceFilter === "all" || source === sourceFilter;
      const searchMatch =
        source.toLowerCase().includes(searchText) ||
        amount.includes(searchText);

      row.style.display = sourceMatch && searchMatch ? "" : "none";
    }
  }

  async addCoins(amount, source) {
    try {
      const response = await fetch("/api/add-transaction", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ amount, source }),
      });

      const result = await response.json();

      if (result.success) {
        this.showToast(result.message, "success");
        this.clearForm("add");
        this.loadData(); // Reload all data
      } else {
        this.showToast(result.error, "error");
      }
    } catch (error) {
      console.error("Error adding coins:", error);
      this.showToast("Error adding coins", "error");
    }
  }

  async spendCoins(amount, category) {
    try {
      const response = await fetch("/api/spend-coins", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ amount, category }),
      });

      const result = await response.json();

      if (result.success) {
        this.showToast(result.message, "success");
        this.clearForm("spend");
        this.loadData(); // Reload all data
      } else {
        this.showToast(result.error, "error");
      }
    } catch (error) {
      console.error("Error spending coins:", error);
      this.showToast("Error spending coins", "error");
    }
  }

  async setGoal(goal) {
    try {
      const response = await fetch("/api/set-goal", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ goal }),
      });

      const result = await response.json();

      if (result.success) {
        this.showToast(result.message, "success");
        this.loadData(); // Reload all data
      } else {
        this.showToast(result.error, "error");
      }
    } catch (error) {
      console.error("Error setting goal:", error);
      this.showToast("Error setting goal", "error");
    }
  }

  clearForm(type) {
    if (type === "add") {
      document.getElementById("addAmount").value = "";
      document.getElementById("addSource").selectedIndex = 0;
    } else if (type === "spend") {
      document.getElementById("spendAmount").value = "";
      document.getElementById("spendCategory").selectedIndex = 0;
    }
  }

  showPage(page) {
    // Hide all pages
    document.querySelectorAll(".page").forEach((p) => {
      p.classList.remove("active");
    });

    // Remove active class from all nav buttons
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.classList.remove("active");
    });

    // Show selected page
    document.getElementById(page).classList.add("active");

    // Activate corresponding nav button
    document.querySelector(`[data-page="${page}"]`).classList.add("active");

    this.currentPage = page;

    // Load specific data if needed
    if (page === "analytics") {
      this.loadAnalytics();
    } else if (page === "history") {
      this.loadHistory();
    }
  }

  toggleTheme() {
    const currentTheme = document.documentElement.getAttribute("data-theme");
    const newTheme = currentTheme === "light" ? "dark" : "light";

    document.documentElement.setAttribute("data-theme", newTheme);

    const toggleBtn = document.getElementById("themeToggle");
    toggleBtn.textContent =
      newTheme === "light" ? "🌙 Dark Mode" : "☀️ Light Mode";

    // Update chart colors if exists
    if (this.earningsChart) {
      this.earningsChart.update();
    }
  }

  showToast(message, type = "success") {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.className = `toast ${type} show`;

    setTimeout(() => {
      toast.classList.remove("show");
    }, 3000);
  }

  async loadAnalytics() {
    try {
      const response = await fetch("/api/analytics");
      if (response.ok) {
        const data = await response.json();
        this.updateAnalytics(data);
      }
    } catch (error) {
      console.error("Error loading analytics:", error);
    }
  }

  async loadHistory() {
    try {
      const response = await fetch("/api/transactions");
      if (response.ok) {
        this.transactions = await response.json();
        this.updateTransactions();
      }
    } catch (error) {
      console.error("Error loading history:", error);
    }
  }
}

// Initialize the app when the page loads
document.addEventListener("DOMContentLoaded", () => {
  new CoinTrackerApp();
});