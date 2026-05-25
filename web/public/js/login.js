// ─────────────────────────────────────────────────────────────
//  login.js  —  Firebase Auth + Firestore (matches mobile app)
//  Mobile stores everything in ONE doc: user_data/{uid}
//  Field names are snake_case matching FirestoreRepository.kt
// ─────────────────────────────────────────────────────────────

import { initializeApp }                    from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getAuth, signInWithEmailAndPassword, createUserWithEmailAndPassword }
                                             from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getFirestore, doc, getDoc, setDoc, collection, query, where, getDocs, limit }
                                             from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { firebaseConfig } from "/firebase-config.js";

const app  = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db   = getFirestore(app);

const toEmail = (u) => `${u.trim().toLowerCase()}@cointracker.app`;

// Default quick actions — matches defaultQuickActions() in Models.kt
const DEFAULT_QUICK_ACTIONS = [
  { text: "Event Reward",      value: 50,  is_positive: true  },
  { text: "Ads",               value: 10,  is_positive: true  },
  { text: "Daily Games",       value: 100, is_positive: true  },
  { text: "Login",             value: 50,  is_positive: true  },
  { text: "Campaign Reward",   value: 50,  is_positive: true  },
  { text: "Box Draw (Single)", value: 100, is_positive: false },
  { text: "Box Draw (10)",     value: 900, is_positive: false },
];

// Default settings — matches settingsToMap() in FirestoreRepository.kt
function defaultSettingsMap() {
  return {
    goal:               13500,
    dark_mode:          false,
    quick_actions:      DEFAULT_QUICK_ACTIONS,
    income_categories:  [],
    expense_categories: [],
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
let currentTheme = document.documentElement.getAttribute("data-theme") || "light";
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
  usernameEl.value = passwordEl.value = confirmPwEl.value = "";
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
    errorEl.textContent = "Passwords do not match."; return;
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
  const lower = username.toLowerCase();
  const email = toEmail(lower);
  const now   = new Date().toISOString();

  // 1. Check username not taken (matches mobile: query by "username" field)
  const existing = await getDocs(
    query(collection(db, "users"), where("username", "==", lower), limit(1))
  );
  if (!existing.empty) {
    errorEl.textContent = "Username already taken."; return;
  }

  // 2. Create Firebase Auth
  let cred;
  try {
    cred = await createUserWithEmailAndPassword(auth, email, password);
  } catch (err) { throw err; }

  const uid = cred.user.uid;

  // 3. Write Firestore — rollback auth if fails
  try {
    // users/{uid} — matches mobile exactly
    await setDoc(doc(db, "users", uid), {
      username:   lower,
      role:       "user",
      created_at: now,
    });

    // user_data/{uid} — ONE document, profiles nested inside (matches mobile)
    await setDoc(doc(db, "user_data", uid), {
      last_active_profile: "Default",
      profiles: {
        Default: {
          transactions: [],
          settings:     defaultSettingsMap(),
          last_updated: now,
        },
      },
    });

  } catch (err) {
    console.error("Firestore write failed, rolling back:", err);
    await cred.user.delete().catch(() => {});
    throw new Error("Account setup failed. Please try again.");
  }

  showToast(`Welcome, ${lower}! Account created.`, "success");
  setTimeout(() => { window.location.href = "/"; }, 900);
}

// ── Login ─────────────────────────────────────────────────────
async function handleLogin(username, password) {
  const lower = username.toLowerCase();
  const email = toEmail(lower);

  // Check user exists by querying users collection (matches mobile)
  const snap = await getDocs(
    query(collection(db, "users"), where("username", "==", lower), limit(1))
  );
  if (snap.empty) {
    errorEl.textContent = "Username not found."; return;
  }

  await signInWithEmailAndPassword(auth, email, password);
  showToast(`Welcome back, ${lower}!`, "success");
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
    "auth/user-not-found":         "Username not found.",
    "auth/wrong-password":         "Incorrect password.",
    "auth/invalid-credential":     "Incorrect username or password.",
    "auth/too-many-requests":      "Too many attempts. Try again later.",
    "auth/network-request-failed": "Network error. Check your connection.",
    "auth/email-already-in-use":   "Username already taken.",
    "auth/weak-password":          "Password too weak (min 4 chars).",
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
