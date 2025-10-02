const firebaseConfig = {
  apiKey: "AIzaSyCeGnK0DXvo4AkRcg4tE1cHSX8KB48gf5c",
  authDomain: "cointracker-80302.firebaseapp.com",
  projectId: "cointracker-80302",
  storageBucket: "cointracker-80302.firebasestorage.app",
  messagingSenderId: "458179656219",
  appId: "1:458179656219:web:a4f4486fdf4317516896be",
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();
// For simplicity, we'll use a single user document.
// In a real app, you would use Firebase Auth to get the current user's ID.
const userId = "default_user";

class CoinTrackerApp {
  constructor() {
    this.currentPage = "dashboard";
    this.transactions = [];
    this.settings = { goal: 13500 };
    this.earningsChart = null;
    this.unsubscribe = null; // To detach the real-time listener later

    this.init();
  }

  init() {
    this.setupEventListeners();
    this.listenForData(); // Main function to get and sync data
  }

  setupEventListeners() {
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.addEventListener("click", (e) =>
        this.showPage(e.currentTarget.dataset.page)
      );
    });
    document
      .getElementById("themeToggle")
      .addEventListener("click", () => this.toggleTheme());
    document.querySelectorAll(".quick-btn").forEach((btn) => {
      btn.addEventListener("click", (e) => {
        const { action, amount } = e.currentTarget.dataset;
        const isPositive = e.currentTarget.classList.contains("positive");
        this.addTransaction(
          isPositive ? parseInt(amount) : -parseInt(amount),
          action
        );
      });
    });
    document.getElementById("addCoinsBtn").addEventListener("click", () => {
      const amount = document.getElementById("addAmount").value;
      const source = document.getElementById("addSource").value;
      if (amount && source)
        this.addTransaction(parseInt(amount), source, "add");
    });
    document.getElementById("spendCoinsBtn").addEventListener("click", () => {
      const amount = document.getElementById("spendAmount").value;
      const category = document.getElementById("spendCategory").value;
      if (amount && category)
        this.addTransaction(-parseInt(amount), category, "spend");
    });
    document.getElementById("setGoalBtn").addEventListener("click", () => {
      const goal = document.getElementById("goalInput").value;
      if (goal) this.setGoal(parseInt(goal));
    });
    document
      .getElementById("historySearch")
      .addEventListener("input", () => this.renderHistory());
    document
      .getElementById("historySourceFilter")
      .addEventListener("change", () => this.renderHistory());
  }

  listenForData() {
    const docRef = db.collection("users").doc(userId);

    if (this.unsubscribe) this.unsubscribe(); // Detach any old listener

    this.unsubscribe = docRef.onSnapshot(
      (doc) => {
        if (doc.exists) {
          const data = doc.data();
          this.transactions = data.transactions || [];
          this.settings = data.settings || { goal: 13500 };
          this.updateAllUI();
        } else {
          // If the document doesn't exist, create it with default data.
          docRef.set({ transactions: [], settings: { goal: 13500 } });
        }
      },
      (error) => {
        console.error("Error listening to data:", error);
        this.showToast("Connection to database failed.", "error");
      }
    );
  }

  updateAllUI() {
    const balance = this.transactions.reduce((sum, t) => sum + t.amount, 0);
    const earnings = this.transactions
      .filter((t) => t.amount > 0)
      .reduce((sum, t) => sum + t.amount, 0);
    const spending = Math.abs(
      this.transactions
        .filter((t) => t.amount < 0)
        .reduce((sum, t) => sum + t.amount, 0)
    );

    this.updateBalance(balance);
    this.renderHistory(earnings, spending);
    this.updateAnalytics(balance, earnings, spending);
  }

  async addTransaction(amount, source, formType = null) {
    const newTransaction = {
      amount: amount,
      source: source,
      date: new Date().toISOString(),
    };
    try {
      await db
        .collection("users")
        .doc(userId)
        .update({
          transactions:
            firebase.firestore.FieldValue.arrayUnion(newTransaction),
        });
      this.showToast("Transaction saved!", "success");
      if (formType) this.clearForm(formType);
    } catch (e) {
      console.error("Error adding transaction:", e);
      this.showToast("Could not save transaction.", "error");
    }
  }

  async setGoal(goal) {
    try {
      await db
        .collection("users")
        .doc(userId)
        .set(
          {
            settings: { goal: goal },
          },
          { merge: true }
        );
      this.showToast("Goal updated!", "success");
    } catch (e) {
      console.error("Error setting goal:", e);
      this.showToast("Could not update goal.", "error");
    }
  }

  updateBalance(balance) {
    const goal = this.settings.goal;
    const progress =
      goal > 0 ? Math.min(100, Math.floor((balance / goal) * 100)) : 0;

    document.getElementById(
      "balanceAmount"
    ).textContent = `${balance.toLocaleString()} coins`;
    document.getElementById(
      "goalText"
    ).textContent = `Progress to Goal: ${goal.toLocaleString()} coins`;
    document.getElementById("progressBar").style.width = `${progress}%`;
    document.getElementById("progressText").textContent = `${progress}%`;
    document.getElementById(
      "currentGoal"
    ).textContent = `Current goal: ${goal.toLocaleString()} coins`;
    document.getElementById("goalInput").value = goal;
  }

  renderHistory(totalEarned, totalSpent) {
    // Update history stats
    document.getElementById("totalTransactions").textContent =
      this.transactions.length;
    document.getElementById(
      "historyEarned"
    ).textContent = `+${totalEarned.toLocaleString()}`;
    document.getElementById(
      "historySpent"
    ).textContent = `-${totalSpent.toLocaleString()}`;

    // Populate filter dropdown
    const sources = [...new Set(this.transactions.map((t) => t.source))].sort();
    const filterSelect = document.getElementById("historySourceFilter");
    // Preserve current selection while repopulating
    const currentFilter = filterSelect.value;
    filterSelect.innerHTML = '<option value="all">All Sources</option>';
    sources.forEach((source) => {
      const option = document.createElement("option");
      option.value = source;
      option.textContent = source;
      filterSelect.appendChild(option);
    });
    filterSelect.value = currentFilter;

    // Filter and render table
    const searchText = document
      .getElementById("historySearch")
      .value.toLowerCase();
    const sourceFilter = filterSelect.value;

    const filtered = this.transactions.filter((t) => {
      const searchMatch =
        t.source.toLowerCase().includes(searchText) ||
        t.amount.toString().includes(searchText);
      const sourceMatch = sourceFilter === "all" || t.source === sourceFilter;
      return searchMatch && sourceMatch;
    });

    const tableBody = document.getElementById("historyTableBody");
    tableBody.innerHTML = "";
    filtered
      .sort((a, b) => new Date(b.date) - new Date(a.date))
      .forEach((t) => {
        const row = tableBody.insertRow();
        const amountClass =
          t.amount >= 0 ? "amount-positive" : "amount-negative";
        const amountText =
          t.amount >= 0
            ? `+${t.amount.toLocaleString()}`
            : t.amount.toLocaleString();

        row.innerHTML = `
            <td>${new Date(t.date).toLocaleString()}</td>
            <td class="${amountClass}">${amountText}</td>
            <td>${t.source}</td>
        `;
      });
  }

  updateAnalytics(balance, earnings, spending) {
    document.getElementById(
      "totalEarnings"
    ).textContent = `+${earnings.toLocaleString()}`;
    document.getElementById(
      "totalSpending"
    ).textContent = `-${spending.toLocaleString()}`;
    document.getElementById("netBalance").textContent = `${
      balance >= 0 ? "+" : ""
    }${balance.toLocaleString()}`;

    const breakdown = this.transactions
      .filter((t) => t.amount > 0)
      .reduce((acc, t) => {
        acc[t.source] = (acc[t.source] || 0) + t.amount;
        return acc;
      }, {});

    const breakdownContainer = document.getElementById("sourceBreakdown");
    breakdownContainer.innerHTML = "";
    Object.entries(breakdown)
      .sort((a, b) => b[1] - a[1])
      .forEach(([source, amount]) => {
        const percentage =
          earnings > 0 ? ((amount / earnings) * 100).toFixed(1) : 0;
        const item = document.createElement("div");
        item.textContent = `• ${source}: ${amount.toLocaleString()} coins (${percentage}%)`;
        breakdownContainer.appendChild(item);
      });

    this.updateEarningsChart(breakdown);
  }

  updateEarningsChart(breakdown) {
    const ctx = document.getElementById("earningsChart").getContext("2d");
    const labels = Object.keys(breakdown);
    const data = Object.values(breakdown);

    if (this.earningsChart) this.earningsChart.destroy();

    this.earningsChart = new Chart(ctx, {
      type: "pie",
      data: {
        labels,
        datasets: [
          {
            data,
            backgroundColor: [
              "#3b82f6",
              "#10b981",
              "#f59e0b",
              "#ef4444",
              "#8b5cf6",
              "#06b6d4",
              "#d946ef",
            ],
            borderWidth: 2,
            borderColor: getComputedStyle(
              document.documentElement
            ).getPropertyValue("--card-bg"),
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
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

  // Helper functions
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
    document
      .querySelectorAll(".page")
      .forEach((p) => p.classList.remove("active"));
    document.getElementById(page).classList.add("active");
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.page === page);
    });
    this.currentPage = page;
  }

  toggleTheme() {
    const newTheme =
      document.documentElement.getAttribute("data-theme") === "light"
        ? "dark"
        : "light";
    document.documentElement.setAttribute("data-theme", newTheme);
    document.getElementById("themeToggle").textContent =
      newTheme === "light" ? "🌙 Dark Mode" : "☀️ Light Mode";
    if (this.earningsChart) this.updateAllUI(); // Redraw chart with new theme colors
  }

  showToast(message, type = "success") {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.className = `toast ${type} show`;
    setTimeout(() => toast.classList.remove("show"), 3000);
  }
}

// Initialize the app when the page loads
document.addEventListener("DOMContentLoaded", () => new CoinTrackerApp());
