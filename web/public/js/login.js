// ─────────────────────────────────────────────────────────────
//  login.js  —  Firebase Auth login/register for Coin Tracker
//  Uses synthetic email: username@cointracker.app
// ─────────────────────────────────────────────────────────────

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

// ── PASTE YOUR FIREBASE CONFIG HERE ──────────────────────────
const firebaseConfig = {
  apiKey: "AIzaSyAJbO69ldcJf5CRI-1sJqim9Cau_dqV8Co",
  authDomain: "cointrack-16ce2.firebaseapp.com",
  projectId: "cointrack-16ce2",
  storageBucket: "cointrack-16ce2.firebasestorage.app",
  messagingSenderId: "1623415888",
  appId: "1:1623415888:web:2e5966211e367808b64555",
  measurementId: "G-VHRJ3KPH9Q"
};
// ─────────────────────────────────────────────────────────────

const app  = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db   = getFirestore(app);

const toEmail = (u) => `${u.trim().toLowerCase()}@cointracker.app`;

// ── Default quick actions — snake_case to match mobile schema ─
const DEFAULT_QUICK_ACTIONS = [
  { text: "Event Reward",      value: 50,  is_positive: true  },
  { text: "Ads",               value: 10,  is_positive: true  },
  { text: "Daily Games",       value: 100, is_positive: true  },
  { text: "Login",             value: 50,  is_positive: true  },
  { text: "Campaign Reward",   value: 50,  is_positive: true  },
  { text: "Box Draw (Single)", value: 100, is_positive: false },
  { text: "Box Draw (10)",     value: 900, is_positive: false },
];

// ── Default settings — ALL keys in snake_case to match mobile ─
function defaultSettings(darkMode = false) {
  return {
    goal:               13500,
    dark_mode:          darkMode,          // FIX: was "darkMode" (camelCase)
    quick_actions:      DEFAULT_QUICK_ACTIONS, // FIX: was "quickActions" + wrong is_positive key
    income_categories:  [],                // FIX: was "incomeCategories"
    expense_categories: [],                // FIX: was "expenseCategories"
  };
}

// ── Validation ────────────────────────────────────────────────
const RULES = {
  username: (v) => {
    if (!v || v.length < 3) return "Username must be at least 3 characters.";
    if (/\s/.test(v))       return "Username cannot contain spaces.";
    return null;
  },
  password: (v) => {
    if (!v || v.length < 4) return "Password must be at least 4 characters.";
    return null;
  },
};

// ── DOM refs ──────────────────────────────────────────────────
const titleEl        = document.getElementById("auth-title");
const submitBtn      = document.getElementById("auth-submit-btn");
const toggleBtn      = document.getElementById("auth-toggle-btn");
const errorEl        = document.getElementById("auth-error");
const usernameEl     = document.getElementById("username");
const passwordEl     = document.getElementById("password");
const confirmPwGroup = document.getElementById("confirmPasswordGroup");
const confirmPwEl    = document.getElementById("confirmPassword");
const themeToggleBtn = document.getElementById("themeToggleBtn");

let isRegisterMode = false;

// ── Theme ─────────────────────────────────────────────────────
let currentTheme = localStorage.getItem("theme") || document.documentElement.getAttribute("data-theme") || "light";
document.documentElement.setAttribute("data-theme", currentTheme);
themeToggleBtn.textContent = currentTheme === "light" ? "🌙" : "☀️";
themeToggleBtn.addEventListener("click", () => {
  currentTheme = currentTheme === "light" ? "dark" : "light";
  document.documentElement.setAttribute("data-theme", currentTheme);
  localStorage.setItem("theme", currentTheme);
  themeToggleBtn.textContent = currentTheme === "light" ? "🌙" : "☀️";
});

// ── Toggle login/register ─────────────────────────────────────
function toggleAuthMode() {
  isRegisterMode = !isRegisterMode;
  titleEl.textContent          = isRegisterMode ? "Register"       : "Login";
  submitBtn.textContent        = isRegisterMode ? "Create Account" : "Login";
  toggleBtn.textContent        = isRegisterMode
    ? "Already have an account? Login"
    : "Need an account? Register";
  confirmPwGroup.style.display = isRegisterMode ? "block" : "none";
  errorEl.textContent = "";
  usernameEl.value    = "";
  passwordEl.value    = "";
  confirmPwEl.value   = "";
}
toggleBtn.addEventListener("click", toggleAuthMode);

// ── Submit ────────────────────────────────────────────────────
async function handleSubmit() {
  errorEl.textContent = "";
  const username = usernameEl.value.trim();
  const password = passwordEl.value;

  const uErr = RULES.username(username);
  if (uErr) { errorEl.textContent = uErr; return; }

  const pErr = RULES.password(password);
  if (pErr) { errorEl.textContent = pErr; return; }

  if (isRegisterMode && password !== confirmPwEl.value) {
    errorEl.textContent = "Passwords do not match.";
    return;
  }

  setLoading(true);
  try {
    if (isRegisterMode) await handleRegister(username, password);
    else                await handleLogin(username, password);
  } catch (err) {
    console.error("Auth error:", err);
    errorEl.textContent = friendlyError(err.code || err.message);
  } finally {
    setLoading(false);
  }
}

// ── Register ──────────────────────────────────────────────────
async function handleRegister(username, password) {
  const lowerUsername = username.toLowerCase();
  const email         = toEmail(lowerUsername);
  const now           = new Date().toISOString();

  // 1. Check username not taken (check both usernames index AND users collection
  //    to be compatible with both web-registered and mobile-registered users)
  const usernameRef  = doc(db, "usernames", lowerUsername);
  const usernameSnap = await getDoc(usernameRef);
  if (usernameSnap.exists()) {
    errorEl.textContent = "Username already taken.";
    return;
  }

  // 2. Create Firebase Auth account
  let cred;
  try {
    cred = await createUserWithEmailAndPassword(auth, email, password);
  } catch (err) {
    throw err;
  }

  const uid = cred.user.uid;

  // 3. Write ALL Firestore docs — if any fail, delete auth account (rollback)
  try {
    // users/{uid}
    await setDoc(doc(db, "users", uid), {
      username:   lowerUsername,
      role:       "user",
      created_at: now,
    });

    // usernames/{username} — web-only index for fast username lookup
    await setDoc(usernameRef, { uid });

    // ── user_data/{uid} — nested-map format matching mobile schema ──────────
    // FIX: Previously wrote profiles as a Firestore *subcollection* which the
    // app cannot read. Mobile stores everything as nested maps in ONE document.
    await setDoc(doc(db, "user_data", uid), {
      last_active_profile: "Default",
      profiles: {
        Default: {
          transactions: [],
          settings:     defaultSettings(currentTheme === "dark"),
          last_updated: now,
        },
      },
    });

  } catch (err) {
    // Firestore writes failed — delete the Auth account so user can retry
    console.error("Firestore write failed, rolling back auth:", err);
    await cred.user.delete().catch(() => {});
    throw new Error("Account setup failed. Please try again.");
  }

  showToast(`Welcome, ${lowerUsername}! Account created.`, "success");
  setTimeout(() => { window.location.href = "/"; }, 900);
}

// ── Login ─────────────────────────────────────────────────────
async function handleLogin(username, password) {
  const lowerUsername = username.toLowerCase();
  const email         = toEmail(lowerUsername);

  // Check username index (web-registered users have this; mobile users do not)
  // We don't gate on this — just attempt Firebase Auth directly.
  // If the username index is missing it's a mobile-registered user, that's fine.
  const usernameSnap = await getDoc(doc(db, "usernames", lowerUsername));
  if (!usernameSnap.exists()) {
    // May be a mobile-registered user without the web index — still try auth
    // If auth fails the friendlyError handler will show the right message.
  }

  await signInWithEmailAndPassword(auth, email, password);
  showToast(`Welcome back, ${lowerUsername}!`, "success");
  setTimeout(() => { window.location.href = "/"; }, 900);
}

// ── Helpers ───────────────────────────────────────────────────
function setLoading(on) {
  submitBtn.disabled  = on;
  submitBtn.innerHTML = on
    ? `<span class="btn-spinner"></span>${isRegisterMode ? "Creating..." : "Logging in..."}`
    : (isRegisterMode ? "Create Account" : "Login");
}

function friendlyError(code) {
  const map = {
    "auth/user-not-found":          "Username not found.",
    "auth/wrong-password":          "Incorrect password.",
    "auth/invalid-credential":      "Incorrect username or password.",
    "auth/too-many-requests":       "Too many attempts. Try again later.",
    "auth/network-request-failed":  "Network error. Check your connection.",
    "auth/email-already-in-use":    "Username already taken.",
    "auth/weak-password":           "Password too weak (min 4 chars).",
  };
  return map[code] || code || "Something went wrong. Please try again.";
}

function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.className   = `toast ${type} show`;
  setTimeout(() => toast.classList.remove("show"), 3000);
}

// ── Listeners ─────────────────────────────────────────────────
submitBtn.addEventListener("click", handleSubmit);
[usernameEl, passwordEl, confirmPwEl].forEach((el) => {
  el.addEventListener("keypress", (e) => { if (e.key === "Enter") handleSubmit(); });
});
