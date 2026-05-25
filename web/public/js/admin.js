// ─────────────────────────────────────────────────────────────
//  admin.js  —  Admin Panel (Firebase)
//  Reads Firestore directly. No Flask /api/* calls.
//  Access: users with role = "admin" in users/{uid} only.
// ─────────────────────────────────────────────────────────────

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signOut,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  deleteDoc,
  collection,
  getDocs,
  query,
  orderBy,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { firebaseConfig } from "/firebase-config.js";

const firebaseApp = initializeApp(firebaseConfig);
const auth        = getAuth(firebaseApp);
const db          = getFirestore(firebaseApp);

// ── State ─────────────────────────────────────────────────────
let currentAdminUid = null;
let currentAdminUsername = null;
let allUsers      = [];
let filteredUsers = [];
let currentPage   = 1;
const ROWS_PER_PAGE = 15;
let sortColumn    = "created_at";
let sortDirection = "desc";

// ── Boot ──────────────────────────────────────────────────────
onAuthStateChanged(auth, async (user) => {
  if (!user) { window.location.href = "/login.html"; return; }

  // Verify admin role from Firestore
  const userSnap = await getDoc(doc(db, "users", user.uid));
  if (!userSnap.exists() || userSnap.data().role !== "admin") {
    window.location.href = "/";
    return;
  }

  currentAdminUid      = user.uid;
  currentAdminUsername = userSnap.data().username;

  const el = document.getElementById("adminUsername");
  if (el) el.textContent = `Logged in as: ${currentAdminUsername}`;

  setupEventListeners();
  applyTheme();
  await Promise.all([loadStats(), loadUsers(), loadBroadcast()]);
});

// ── Event listeners ───────────────────────────────────────────
function setupEventListeners() {
  document.getElementById("setBroadcastBtn")
    .addEventListener("click", setBroadcast);

  document.getElementById("userSearch")
    .addEventListener("input", filterAndRender);

  document.getElementById("themeToggle")
    .addEventListener("click", toggleTheme);

  // Sortable column headers
  document.querySelectorAll("th[data-sort]").forEach((th) => {
    th.addEventListener("click", () => {
      const col = th.dataset.sort;
      if (sortColumn === col) sortDirection = sortDirection === "asc" ? "desc" : "asc";
      else { sortColumn = col; sortDirection = "asc"; }
      currentPage = 1;
      sortUsers();
      renderTablePage();
      updateSortIndicators();
    });
  });
}

// ── Theme ─────────────────────────────────────────────────────
function applyTheme() {
  const theme = localStorage.getItem("theme") || "light";
  document.documentElement.setAttribute("data-theme", theme);
  document.getElementById("themeToggle").textContent = theme === "dark" ? "☀️" : "🌙";
}

function toggleTheme() {
  const current = document.documentElement.getAttribute("data-theme") || "light";
  const next = current === "dark" ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", next);
  localStorage.setItem("theme", next);
  document.getElementById("themeToggle").textContent = next === "dark" ? "☀️" : "🌙";
}

// ─────────────────────────────────────────────────────────────
//  Stats
// ─────────────────────────────────────────────────────────────
async function loadStats() {
  try {
    // Read all user_data docs to aggregate stats
    const udSnap = await getDocs(collection(db, "user_data"));
    let totalCoins = 0, totalTransactions = 0;

    for (const udDoc of udSnap.docs) {
      const profilesSnap = await getDocs(
        collection(db, "user_data", udDoc.id, "profiles")
      );
      for (const profileDoc of profilesSnap.docs) {
        const txns = profileDoc.data().transactions || [];
        totalTransactions += txns.length;
        totalCoins += txns
          .filter((t) => t.amount > 0)
          .reduce((s, t) => s + t.amount, 0);
      }
    }

    const usersSnap = await getDocs(collection(db, "users"));
    const totalUsers = usersSnap.size;

    document.getElementById("totalUsers").textContent        = totalUsers.toLocaleString();
    document.getElementById("totalCoins").textContent        = totalCoins.toLocaleString();
    document.getElementById("totalTransactions").textContent = totalTransactions.toLocaleString();
  } catch (err) {
    console.error("loadStats error:", err);
    showToast("Failed to load stats.", "error");
  }
}

// ─────────────────────────────────────────────────────────────
//  Users
// ─────────────────────────────────────────────────────────────
async function loadUsers() {
  try {
    const usersSnap = await getDocs(collection(db, "users"));
    const rows = [];

    for (const userDoc of usersSnap.docs) {
      const uid  = userDoc.id;
      const data = userDoc.data();

      // Read Default profile for balance + txn count
      let balance = 0, txnCount = 0, lastUpdated = "N/A";
      try {
        const profileSnap = await getDocs(collection(db, "user_data", uid, "profiles"));
        for (const p of profileSnap.docs) {
          const txns = p.data().transactions || [];
          txnCount  += txns.length;
          balance   += txns.reduce((s, t) => s + t.amount, 0);
          if (p.data().last_updated && (lastUpdated === "N/A" || p.data().last_updated > lastUpdated)) {
            lastUpdated = p.data().last_updated;
          }
        }
      } catch { /* user may have no data yet */ }

      rows.push({
        uid,
        username:    data.username || uid,
        role:        data.role || "user",
        created_at:  data.created_at || "N/A",
        last_updated: lastUpdated,
        balance,
        txn_count: txnCount,
      });
    }

    allUsers = rows;
    filteredUsers = [...allUsers];
    sortUsers();
    renderTablePage();
    updateSortIndicators();
    renderNewUsersChart(allUsers);
  } catch (err) {
    console.error("loadUsers error:", err);
    showToast("Failed to load users.", "error");
  }
}

// ─────────────────────────────────────────────────────────────
//  New users bar chart (last 7 days — matches mobile spec)
// ─────────────────────────────────────────────────────────────
function renderNewUsersChart(users) {
  const container = document.getElementById("newUsersChart");
  if (!container) return;

  // Build counts for last 7 days
  const days = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push({
      label: d.toLocaleDateString(undefined, { weekday: "short" }),
      dateStr: d.toISOString().slice(0, 10),
      count: 0,
    });
  }

  users.forEach((u) => {
    if (!u.created_at || u.created_at === "N/A") return;
    const dateStr = u.created_at.slice(0, 10);
    const day = days.find((d) => d.dateStr === dateStr);
    if (day) day.count++;
  });

  const maxCount = Math.max(...days.map((d) => d.count), 1);

  container.innerHTML = days.map((day) => {
    const pct = Math.round((day.count / maxCount) * 100);
    const showInside = pct > 20;
    return `
      <div class="bar-row">
        <div class="bar-day-label">${day.label}</div>
        <div class="bar-track">
          <div class="bar-fill" style="width:${pct}%">
            ${showInside && day.count > 0 ? `<span class="bar-count">${day.count}</span>` : ""}
          </div>
        </div>
        ${!showInside && day.count > 0 ? `<span class="bar-count outside">${day.count}</span>` : ""}
        ${day.count === 0 ? `<span class="bar-count outside" style="color:var(--muted-color)">0</span>` : ""}
      </div>`;
  }).join("");
}

// ─────────────────────────────────────────────────────────────
//  Table rendering
// ─────────────────────────────────────────────────────────────
function renderTablePage() {
  const tbody = document.getElementById("userTableBody");
  const start = (currentPage - 1) * ROWS_PER_PAGE;
  const pageUsers = filteredUsers.slice(start, start + ROWS_PER_PAGE);

  if (pageUsers.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:30px;color:var(--muted-color)">No users found.</td></tr>`;
    renderPaginationControls();
    return;
  }

  tbody.innerHTML = "";
  pageUsers.forEach((u) => {
    const isMe      = u.uid === currentAdminUid;
    const balClass  = u.balance > 0 ? "amount-positive" : u.balance < 0 ? "amount-negative" : "";
    const createdOn = u.created_at === "N/A" ? "N/A" : new Date(u.created_at).toLocaleDateString();
    const lastActive = u.last_updated === "N/A" ? "N/A" : new Date(u.last_updated).toLocaleString();

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>
        ${u.username}
        ${isMe ? '<span class="you-badge">You</span>' : ""}
      </td>
      <td class="${balClass}">${u.balance.toLocaleString()}</td>
      <td>${u.txn_count}</td>
      <td>${createdOn}</td>
      <td>${lastActive}</td>
      <td>
        ${isMe
          ? `<span style="color:var(--muted-color);font-size:12px">—</span>`
          : `<button class="btn danger btn-delete-user" data-uid="${u.uid}" data-username="${u.username}">🗑 Delete</button>`
        }
      </td>`;

    if (!isMe) {
      tr.querySelector(".btn-delete-user").addEventListener("click", (e) => {
        const uid      = e.currentTarget.dataset.uid;
        const username = e.currentTarget.dataset.username;
        if (confirm(`Delete user '${username}'?\nThis permanently deletes all their data.`)) {
          deleteUserById(uid, username);
        }
      });
    }

    tbody.appendChild(tr);
  });

  renderPaginationControls();
}

function renderPaginationControls() {
  const totalPages = Math.ceil(filteredUsers.length / ROWS_PER_PAGE);
  const ids = ["paginationControlsTop", "paginationControlsBottom"];

  if (totalPages <= 1) { ids.forEach((id) => { document.getElementById(id).innerHTML = ""; }); return; }

  let html = `<button class="btn secondary" ${currentPage === 1 ? "disabled" : ""} data-page="${currentPage - 1}">Previous</button>`;
  const start = Math.max(1, currentPage - 2);
  const end   = Math.min(totalPages, currentPage + 2);
  if (start > 1) html += `<button class="btn secondary" data-page="1">1</button><span>...</span>`;
  for (let i = start; i <= end; i++)
    html += `<button class="btn ${i === currentPage ? "primary" : "secondary"}" data-page="${i}">${i}</button>`;
  if (end < totalPages)
    html += `<span>...</span><button class="btn secondary" data-page="${totalPages}">${totalPages}</button>`;
  html += `<button class="btn secondary" ${currentPage === totalPages ? "disabled" : ""} data-page="${currentPage + 1}">Next</button>`;

  ids.forEach((id) => {
    const el = document.getElementById(id);
    el.innerHTML = html;
    el.querySelectorAll("button[data-page]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const p = parseInt(btn.dataset.page);
        if (p !== currentPage) { currentPage = p; renderTablePage(); }
      });
    });
  });
}

function filterAndRender() {
  const search = document.getElementById("userSearch").value.toLowerCase().trim();
  filteredUsers = search
    ? allUsers.filter((u) => u.username.toLowerCase().includes(search))
    : [...allUsers];
  sortUsers();
  currentPage = 1;
  renderTablePage();
}

function sortUsers() {
  filteredUsers.sort((a, b) => {
    let vA = a[sortColumn], vB = b[sortColumn];
    if (sortColumn === "balance" || sortColumn === "txn_count") {
      vA = Number(vA) || 0;
      vB = Number(vB) || 0;
    } else if (sortColumn === "created_at" || sortColumn === "last_updated") {
      vA = vA === "N/A" ? 0 : new Date(vA).getTime();
      vB = vB === "N/A" ? 0 : new Date(vB).getTime();
    } else {
      vA = String(vA).toLowerCase();
      vB = String(vB).toLowerCase();
    }
    if (vA < vB) return sortDirection === "asc" ? -1 : 1;
    if (vA > vB) return sortDirection === "asc" ?  1 : -1;
    return 0;
  });
}

function updateSortIndicators() {
  document.querySelectorAll("th[data-sort]").forEach((th) => {
    th.classList.remove("sort-asc", "sort-desc");
    if (th.dataset.sort === sortColumn)
      th.classList.add(sortDirection === "asc" ? "sort-asc" : "sort-desc");
  });
}

// ─────────────────────────────────────────────────────────────
//  Delete user
// ─────────────────────────────────────────────────────────────
async function deleteUserById(uid, username) {
  try {
    // Delete all profile subcollection docs
    const profilesSnap = await getDocs(collection(db, "user_data", uid, "profiles"));
    for (const p of profilesSnap.docs) await deleteDoc(p.ref);

    // Delete user_data doc
    await deleteDoc(doc(db, "user_data", uid));

    // Delete users doc
    await deleteDoc(doc(db, "users", uid));

    // Delete username index
    await deleteDoc(doc(db, "usernames", username));

    // Remove from local state and re-render
    allUsers = allUsers.filter((u) => u.uid !== uid);
    filterAndRender();
    showToast(`User '${username}' deleted.`, "success");
    loadStats(); // refresh totals
  } catch (err) {
    console.error("deleteUserById error:", err);
    showToast("Failed to delete user.", "error");
  }
}

// ─────────────────────────────────────────────────────────────
//  Broadcast
// ─────────────────────────────────────────────────────────────
async function loadBroadcast() {
  try {
    const snap = await getDoc(doc(db, "app_config", "broadcast"));
    const msg  = snap.exists() ? snap.data().message || "" : "";
    const el   = document.getElementById("currentBroadcast");
    if (el) el.textContent = msg ? `Current: "${msg}"` : "No broadcast set.";
    document.getElementById("broadcastMessage").value = msg;
  } catch { /* non-fatal */ }
}

async function setBroadcast() {
  const message = document.getElementById("broadcastMessage").value.trim();
  try {
    await setDoc(doc(db, "app_config", "broadcast"), { message });
    const el = document.getElementById("currentBroadcast");
    if (el) el.textContent = message ? `Current: "${message}"` : "No broadcast set.";
    showToast(message ? "Broadcast updated!" : "Broadcast cleared.", "success");
  } catch (err) {
    console.error("setBroadcast error:", err);
    showToast("Failed to update broadcast.", "error");
  }
}

// ─────────────────────────────────────────────────────────────
//  Toast
// ─────────────────────────────────────────────────────────────
function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.className   = `toast ${type} show`;
  setTimeout(() => toast.classList.remove("show"), 3000);
}
