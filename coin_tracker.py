import sys
import json
import os
from datetime import datetime, date
from collections import defaultdict

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QLineEdit, QComboBox, QTableWidget,
    QTableWidgetItem, QHeaderView, QMessageBox, QFrame, QFileDialog,
    QInputDialog, QDialog, QTabWidget, QDateEdit, QCheckBox, QProgressBar,
    QStackedWidget, QGridLayout, QScrollArea
)
from PyQt5.QtCore import Qt, QSize, QDate, QDateTime
from PyQt5.QtGui import QFont, QColor, QIcon, QPixmap, QIntValidator, QPainter, QPen

# Charts
try:
    from PyQt5.QtChart import QChart, QChartView, QPieSeries, QBarSeries, QBarSet, QBarCategoryAxis, QValueAxis, QPieSlice
    QTCHART_AVAILABLE = True
except ImportError:
    QTCHART_AVAILABLE = False
    print("QtCharts not available - charts will be disabled")

# ---------------------------
# Online Database (Firebase)
# ---------------------------

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False
    print("Firebase not available - using local storage only")

# ---------------------------
# Theme Palettes (Light/Dark)
# ---------------------------

LIGHT = {
    "bg": "#f8fafc", # Lighter background
    "card": "#ffffff",
    "border": "#e2e8f0",
    "text": "#0f172a", # Darker text for contrast
    "muted": "#64748b",
    "primary": "#3b82f6",
    "primaryDark": "#2563eb",
    "primaryLight": "#eff6ff", # For hover and active states
    "success": "#10b981",
    "successDark": "#059669",
    "danger": "#ef4444",
    "dangerDark": "#dc2626",
    "warning": "#f59e0b",
    "accent": "#8b5cf6",
    "accentDark": "#7c3aed",
    "tableHeader": "#f1f5f9",
    "sidebar": "#ffffff",
}

DARK = {
    "bg": "#0f172a",
    "card": "#1e293b",
    "border": "#334155",
    "text": "#e2e8f0", # Slightly less bright text
    "muted": "#94a3b8",
    "primary": "#60a5fa",
    "primaryDark": "#3b82f6",
    "primaryLight": "#1e293b", # Different hover for dark
    "success": "#34d399",
    "successDark": "#10b981",
    "danger": "#f87171",
    "dangerDark": "#ef4444",
    "warning": "#fbbf24",
    "accent": "#a78bfa",
    "accentDark": "#8b5cf6",
    "tableHeader": "#1e293b",
    "sidebar": "#1e293b",
}

def dt_now_iso():
    return datetime.now().isoformat()

# ---------------------------
# Online Coin Tracker
# ---------------------------

class OnlineCoinTracker:
    def __init__(self, profile_name="Default", user_id="default_user"):
        self.profile_name = profile_name
        self.user_id = user_id
        self.db = None
        self.transactions = []
        self.settings = {
            "goal": 13500,
            "dark_mode": False
        }
        
        if FIREBASE_AVAILABLE:
            self.initialize_firebase()
        self.load_data()

    def initialize_firebase(self):
        try:
            if not firebase_admin._apps:
                if getattr(sys, 'frozen', False):
                    base_path = os.path.dirname(sys.executable)
                else:
                    base_path = os.path.dirname(os.path.abspath(__file__))
                
                key_file = os.path.join(base_path, "firebase-key.json")
                
                if not os.path.exists(key_file):
                    print(f"Firebase key file not found at: {key_file}")
                    self.db = None
                    return
                    
                cred = credentials.Certificate(key_file)
                firebase_admin.initialize_app(cred)
            self.db = firestore.client()
            print("✅ Firebase initialized successfully")
        except Exception as e:
            print(f"❌ Firebase init error: {e}")
            self.db = None

    def validate_and_fix_data(self):
        valid_transactions = []
        for i, transaction in enumerate(self.transactions):
            if not isinstance(transaction, dict):
                print(f"Removing invalid transaction at index {i}: {transaction}")
                continue
            required_fields = ['date', 'amount', 'source', 'previous_balance']
            if not all(field in transaction for field in required_fields):
                print(f"Removing incomplete transaction at index {i}: {transaction}")
                continue
            try:
                transaction['amount'] = int(transaction['amount'])
                transaction['previous_balance'] = int(transaction['previous_balance'])
            except (ValueError, TypeError):
                print(f"Removing transaction with invalid numbers at index {i}: {transaction}")
                continue
            valid_transactions.append(transaction)
        if len(valid_transactions) != len(self.transactions):
            print(f"Fixed data: {len(self.transactions)} -> {len(valid_transactions)} transactions")
            self.transactions = valid_transactions
            self.save_data()

    def load_data(self):
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                doc = doc_ref.get()
                if doc.exists:
                    data = doc.to_dict()
                    profile_data = data.get(self.profile_name, data) # Handle old and new structures
                    self.transactions = profile_data.get('transactions', [])
                    self.settings.update(profile_data.get('settings', {}))
                    self.validate_and_fix_data()
                    print(f"Loaded {len(self.transactions)} transactions from Firebase")
                else:
                    self.save_data() # Create if not exists
            except Exception as e:
                print(f"Online load error: {e}")
                self.load_local_data()
        else:
            self.load_local_data()

    def save_data(self):
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                profile_data = {
                    'transactions': self.transactions,
                    'settings': self.settings,
                    'last_updated': dt_now_iso()
                }
                doc_ref.set({self.profile_name: profile_data}, merge=True)
                print(f"✅ Saved {len(self.transactions)} transactions to Firebase")
            except Exception as e:
                print(f"❌ Online save error: {e}")
                self.save_local_data()
        else:
            self.save_local_data()

    def load_local_data(self):
        data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        data_file = os.path.join(data_dir, f"{self.profile_name}.json")
        try:
            with open(data_file, 'r') as f:
                data = json.load(f)
                self.transactions = data.get('transactions', [])
                self.settings.update(data.get('settings', {}))
                self.validate_and_fix_data()
                print(f"Loaded {len(self.transactions)} transactions from local storage")
        except (FileNotFoundError, json.JSONDecodeError):
            self.transactions = []

    def save_local_data(self):
        data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        os.makedirs(data_dir, exist_ok=True)
        data_file = os.path.join(data_dir, f"{self.profile_name}.json")
        data = {
            "profile_name": self.profile_name,
            "last_updated": dt_now_iso(),
            "transactions": self.transactions,
            "settings": self.settings
        }
        with open(data_file, 'w') as f:
            json.dump(data, f, indent=2)

    def add_coins(self, amount, source):
        if amount == 0: return False
        transaction = {
            "date": dt_now_iso(),
            "amount": amount,
            "source": source,
            "previous_balance": self.get_balance()
        }
        self.transactions.append(transaction)
        self.save_data()
        return True

    def get_balance(self):
        return sum(int(t.get('amount', 0)) for t in self.transactions if isinstance(t, dict))

    def get_transaction_history(self):
        return sorted(self.transactions, key=lambda x: x.get('date', ''), reverse=True)

    def get_source_breakdown(self):
        breakdown = defaultdict(int)
        for t in self.transactions:
            if isinstance(t, dict) and t.get('amount', 0) > 0:
                breakdown[t['source']] += t['amount']
        return breakdown

    def get_spending_breakdown(self):
        breakdown = defaultdict(int)
        for t in self.transactions:
            if isinstance(t, dict) and t.get('amount', 0) < 0:
                breakdown[t['source']] += abs(t['amount'])
        return breakdown

    def export_data(self, file_path):
        try:
            with open(file_path, 'w') as f:
                json.dump(self.transactions, f, indent=2)
            return True
        except Exception as e:
            print(f"Export error: {e}")
            return False

    def import_data(self, file_path):
        try:
            with open(file_path, 'r') as f:
                new_transactions = json.load(f)
                self.transactions.extend(new_transactions)
                self.save_data()
            return True
        except Exception as e:
            print(f"Import error: {e}")
            return False

    def set_goal(self, goal_value: int):
        self.settings["goal"] = max(0, int(goal_value))
        self.save_data()

    def get_goal(self) -> int:
        return int(self.settings.get("goal", 13500))

    def set_dark_mode(self, enabled: bool):
        self.settings["dark_mode"] = bool(enabled)
        self.save_data()

    def get_dark_mode(self) -> bool:
        return bool(self.settings.get("dark_mode", False))

# ---------------------------
# Modern UI Components
# ---------------------------

class ModernCard(QFrame):
    def __init__(self, palette, parent=None):
        super().__init__(parent)
        self.setPalette(palette)

    def setPalette(self, palette):
        self.palette = palette
        self.setStyleSheet(f"""
            ModernCard {{
                background-color: {self.palette['card']};
                border: 1px solid {self.palette['border']};
                border-radius: 12px;
            }}
        """)

class QuickActionButton(QPushButton):
    def __init__(self, text, value, is_positive=True, palette=None, parent=None):
        super().__init__(parent)
        self.text = text
        self.value = value
        self.is_positive = is_positive
        
        layout = QVBoxLayout(self)
        layout.setSpacing(8)  # Increased spacing
        layout.setContentsMargins(16, 12, 16, 12)  # Larger margins
        
        self.icon_text = QLabel("+" if is_positive else "–")
        self.icon_text.setAlignment(Qt.AlignCenter)
        self.icon_text.setMinimumHeight(30)  # Larger icon
        
        self.title = QLabel(text)
        self.title.setAlignment(Qt.AlignCenter)
        self.title.setWordWrap(True)  # Allow text to wrap
        
        self.amount = QLabel(f"{'+' if is_positive else '-'}{value}")
        self.amount.setAlignment(Qt.AlignCenter)
        
        layout.addWidget(self.icon_text)
        layout.addWidget(self.title)
        layout.addWidget(self.amount)
        
        # Larger button size
        self.setFixedSize(120, 120)  # Increased from 90x90

    def setPalette(self, palette):
        self.palette = palette
        self.icon_text.setStyleSheet(f"font-size: 20px; font-weight: bold; color: {self.palette['primary' if self.is_positive else 'danger']}; background: transparent;")
        self.title.setStyleSheet(f"font-size: 11px; color: {self.palette['text']}; font-weight: 500; background: transparent;")
        self.amount.setStyleSheet(f"font-size: 9px; color: {self.palette['muted']}; background: transparent;")
        self.setStyleSheet(f"""
            QPushButton {{
                background-color: transparent;
                border: 1px solid {self.palette['border']};
                border-radius: 8px;
            }}
            QPushButton:hover {{
                background-color: {self.palette['primary'] + '20'};
                border-color: {self.palette['primary']};
            }}
        """)

# ---------------------------
# Main Window with Modern UI
# ---------------------------

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        
        self.profiles = self.load_profiles()
        self.current_profile = self.profiles[0] if self.profiles else "Default"
        self.tracker = OnlineCoinTracker(self.current_profile)
        self.palette_colors = DARK if self.tracker.get_dark_mode() else LIGHT
        
        self.setWindowTitle("Coin Tracker")
        self.setWindowIcon(self.create_icon())
        self.setGeometry(100, 100, 1400, 900)
        self.setMinimumSize(1200, 800)
        
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QHBoxLayout(central_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)
        
        self.sidebar = self.create_sidebar()
        main_layout.addWidget(self.sidebar)
        
        self.stacked_widget = QStackedWidget()
        main_layout.addWidget(self.stacked_widget, 1)
        
        # Keep references to custom widgets that need theme updates
        self.themed_widgets = []
        self.quick_action_buttons = []
        self.exploded_slice = None

        self.dashboard_page = self.create_dashboard_page()
        self.analytics_page = self.create_analytics_page()
        self.history_page = self.create_history_page()
        self.settings_page = self.create_settings_page()
        
        self.stacked_widget.addWidget(self.dashboard_page)
        self.stacked_widget.addWidget(self.analytics_page)
        self.stacked_widget.addWidget(self.history_page)
        self.stacked_widget.addWidget(self.settings_page)
        
        self.apply_theme()
        self.update_all_data()
        self.update_active_nav("Dashboard")

    def create_sidebar(self):
        sidebar = QFrame()
        sidebar.setFixedWidth(260)
        
        layout = QVBoxLayout(sidebar)
        layout.setContentsMargins(20, 20, 20, 30)
        layout.setSpacing(15)
        
        logo_layout = QHBoxLayout()
        logo_icon = QLabel()
        logo_icon.setPixmap(self.create_icon().pixmap(32, 32))
        logo_text = QLabel("Coin Tracker")
        logo_text.setObjectName("SidebarTitle")
        logo_layout.addWidget(logo_icon)
        logo_layout.addWidget(logo_text)
        logo_layout.addStretch()
        
        self.dark_toggle = QPushButton("🌙")
        self.dark_toggle.setFixedSize(32, 32)
        self.dark_toggle.setObjectName("ThemeToggle")
        self.dark_toggle.clicked.connect(self.toggle_dark_mode)
        logo_layout.addWidget(self.dark_toggle)
        layout.addLayout(logo_layout)
        
        layout.addSpacing(20)

        nav_items = {
            "Dashboard": ("📊 Dashboard", self.show_dashboard),
            "Analytics": ("📈 Analytics", self.show_analytics),
            "History": ("📋 History", self.show_history),
            "Settings": ("⚙️ Settings", self.show_settings)
        }
        
        self.nav_buttons = {}
        for name, (text, callback) in nav_items.items():
            btn = QPushButton(text)
            btn.setFixedHeight(45)
            btn.setObjectName("NavButton")
            btn.clicked.connect(callback)
            layout.addWidget(btn)
            self.nav_buttons[name] = btn
        
        layout.addStretch()
        
        profile_label = QLabel("PROFILE")
        profile_label.setObjectName("MutedLabel")
        layout.addWidget(profile_label)

        self.profile_combo = QComboBox()
        self.profile_combo.addItems(self.profiles)
        self.profile_combo.setCurrentText(self.current_profile)
        self.profile_combo.currentTextChanged.connect(self.change_profile)
        
        new_profile_btn = QPushButton("＋ New Profile")
        new_profile_btn.clicked.connect(self.create_new_profile)
        
        layout.addWidget(self.profile_combo)
        layout.addWidget(new_profile_btn)
        
        return sidebar

    def create_dashboard_page(self):
        page = QScrollArea()
        page.setWidgetResizable(True)
        content = QWidget()
        page.setWidget(content)
        layout = QVBoxLayout(content)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(30)
        
        # Header
        header = QLabel("Dashboard Overview")
        header.setObjectName("PageTitle")
        layout.addWidget(header)

        # Balance Card
        balance_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(balance_card)
        balance_layout = QVBoxLayout(balance_card)
        balance_layout.setSpacing(15)
        
        goal_layout = QHBoxLayout()
        self.goal_label = QLabel()
        goal_layout.addWidget(self.goal_label)
        goal_layout.addStretch()
        
        balance_title = QLabel("Current Balance")
        self.balance_label = QLabel("0 coins")
        self.balance_label.setObjectName("BalanceLabel")
        
        self.goal_progress = QProgressBar()
        self.goal_progress.setTextVisible(False)
        self.goal_progress.setFixedHeight(12)
        
        balance_layout.addLayout(goal_layout)
        balance_layout.addWidget(balance_title)
        balance_layout.addWidget(self.balance_label)
        balance_layout.addWidget(self.goal_progress)
        layout.addWidget(balance_card)
        
        # Quick Actions
        actions_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(actions_card)
        actions_layout = QVBoxLayout(actions_card)
        actions_layout.setSpacing(20)
        actions_title = QLabel("Quick Actions")
        actions_title.setObjectName("CardTitle")
        actions_layout.addWidget(actions_title)
        
        actions_grid = QGridLayout()
        actions_grid.setSpacing(20)  # Increased spacing between buttons
        actions_grid.setContentsMargins(10, 10, 10, 10)

        quick_actions_data = [
            ("Event Rewards", 50, True), 
            ("Ads", 10, True), 
            ("Box Draw", 100, False), 
            ("Daily Games", 100, True), 
            ("Login", 10, True), 
            ("Login", 50, True)
        ]
        
        self.quick_action_buttons.clear()
        for i, (text, value, is_positive) in enumerate(quick_actions_data):
            btn = QuickActionButton(text, value, is_positive, self.palette_colors)
            btn.clicked.connect(lambda ch, t=text, v=value, p=is_positive: self.quick_action(t, v, p))
            actions_grid.addWidget(btn, i // 3, i % 3)  # 3 columns instead of 4
        
        actions_layout.addLayout(actions_grid)
        layout.addWidget(actions_card)
        
        # Transaction Forms
        transaction_row = QHBoxLayout()
        transaction_row.setSpacing(30)
        
        add_card = self.create_transaction_card("Add Coins", True)
        spend_card = self.create_transaction_card("Spend Coins", False)
        
        transaction_row.addWidget(add_card)
        transaction_row.addWidget(spend_card)
        layout.addLayout(transaction_row)
        layout.addStretch()
        return page

    def create_transaction_card(self, title_text, is_add):
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(15)

        title = QLabel(title_text)
        title.setObjectName("CardTitle")
        
        amount_input = QLineEdit(placeholderText="Enter amount")
        amount_input.setValidator(QIntValidator(0, 1000000))

        combo = QComboBox()
        if is_add:
            self.amount_input = amount_input
            self.source_combo = combo
            combo.addItems(["Event Reward", "Login", "Daily Games", "Achievements", "Ads", "Other"])
            btn = QPushButton("Add Coins")
            btn.setObjectName("SuccessButton")
            btn.clicked.connect(self.add_coins)
        else:
            self.spend_amount_input = amount_input
            self.category_combo = combo
            combo.addItems(["Box Draw", "Store Purchase", "Upgrade", "Other"])
            btn = QPushButton("Spend Coins")
            btn.setObjectName("DangerButton")
            btn.clicked.connect(self.spend_coins)

        layout.addWidget(title)
        layout.addWidget(QLabel("Amount"))
        layout.addWidget(amount_input)
        layout.addWidget(QLabel("Source" if is_add else "Category"))
        layout.addWidget(combo)
        layout.addStretch()
        layout.addWidget(btn)
        return card

    def create_analytics_page(self):
        page = QScrollArea()
        page.setWidgetResizable(True)
        content = QWidget()
        page.setWidget(content)
        layout = QVBoxLayout(content)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(30)
        
        title = QLabel("Analytics")
        title.setObjectName("PageTitle")
        layout.addWidget(title)
        
        stats_row = QHBoxLayout()
        stats_row.setSpacing(30)
        
        self.total_earnings_label = self.create_stat_card("Total Earnings", "+0")
        self.total_spending_label = self.create_stat_card("Total Spending", "-0")
        self.net_balance_label = self.create_stat_card("Net Balance", "0")
        
        stats_row.addWidget(self.total_earnings_label.parentWidget())
        stats_row.addWidget(self.total_spending_label.parentWidget())
        stats_row.addWidget(self.net_balance_label.parentWidget())
        layout.addLayout(stats_row)
        
        charts_row = QHBoxLayout()
        charts_row.setSpacing(30)
        
        earnings_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(earnings_card)
        earnings_layout = QVBoxLayout(earnings_card)
        earnings_title = QLabel("Earnings by Source")
        earnings_title.setObjectName("CardTitle")
        earnings_layout.addWidget(earnings_title)
        
        if QTCHART_AVAILABLE:
            self.pie_chart = self.create_earnings_pie_chart()
            self.chart_view = QChartView(self.pie_chart)
            self.chart_view.setRenderHint(QPainter.Antialiasing)
            self.chart_view.setMinimumHeight(350)
            earnings_layout.addWidget(self.chart_view)
        else:
            no_chart_label = QLabel("Charts not available.\nInstall PyQtChart for visualizations.")
            no_chart_label.setAlignment(Qt.AlignCenter)
            earnings_layout.addWidget(no_chart_label)
        
        charts_row.addWidget(earnings_card, 2) # Give more space to chart
        
        source_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(source_card)
        source_layout = QVBoxLayout(source_card)
        source_title = QLabel("Spending Breakdown")
        source_title.setObjectName("CardTitle")
        source_layout.addWidget(source_title)
        
        self.spending_breakdown_layout = QVBoxLayout()
        source_layout.addLayout(self.spending_breakdown_layout)
        source_layout.addStretch()

        charts_row.addWidget(source_card, 1)
        layout.addLayout(charts_row)
        layout.addStretch()
        return page

    def create_stat_card(self, title_text, initial_value):
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        title_label = QLabel(title_text)
        value_label = QLabel(initial_value)
        value_label.setObjectName("StatLabel")
        layout.addWidget(title_label)
        layout.addWidget(value_label)
        return value_label

    def handle_pie_hover(self, slice, state):
        if self.exploded_slice:
            self.exploded_slice.setExploded(False)
            self.exploded_slice.setLabelFont(QFont("Segoe UI", 8))
        if state:
            slice.setExploded(True)
            slice.setLabelFont(QFont("Segoe UI", 10, QFont.Bold))
            self.exploded_slice = slice
        else:
            self.exploded_slice = None

    def create_earnings_pie_chart(self):
        chart = QChart()
        chart.setAnimationOptions(QChart.SeriesAnimations)
        chart.legend().setVisible(False)
        return chart

    def update_pie_chart(self):
        if not QTCHART_AVAILABLE: return
        
        self.pie_chart.removeAllSeries()
        breakdown = self.tracker.get_source_breakdown()
        
        series = QPieSeries()
        series.hovered.connect(self.handle_pie_hover)
        
        if not breakdown:
            self.pie_chart.setTitle("No earnings data available")
        else:
            self.pie_chart.setTitle("")
            total_earnings = sum(breakdown.values())
            for source, amount in breakdown.items():
                percentage = (amount / total_earnings) * 100 if total_earnings > 0 else 0
                slice_label = f"{source}\n{percentage:.1f}%"
                pie_slice = QPieSlice(slice_label, amount)
                pie_slice.setLabelFont(QFont("Segoe UI", 8))
                series.append(pie_slice)

        series.setLabelsVisible(True)
        series.setLabelsPosition(QPieSlice.LabelOutside)
        
        self.pie_chart.addSeries(series)
        self.apply_chart_theme(self.pie_chart)
        
    def create_history_page(self):
        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(20)
        
        title = QLabel("Transaction History")
        title.setObjectName("PageTitle")
        
        filter_row = QHBoxLayout()
        self.history_search = QLineEdit(placeholderText="Search by source or amount...")
        self.history_search.textChanged.connect(self.filter_history)
        
        self.history_source_filter = QComboBox()
        self.history_source_filter.currentTextChanged.connect(self.filter_history)
        
        filter_row.addWidget(self.history_search, 1)
        filter_row.addWidget(QLabel("Filter by source:"))
        filter_row.addWidget(self.history_source_filter)
        
        self.history_table = QTableWidget()
        self.history_table.setColumnCount(4)
        self.history_table.setHorizontalHeaderLabels(["Date", "Source/Category", "Amount", "Balance After"])
        self.history_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.history_table.verticalHeader().setVisible(False)
        self.history_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.history_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.history_table.setAlternatingRowColors(True)

        layout.addWidget(title)
        layout.addLayout(filter_row)
        layout.addWidget(self.history_table)
        return page

    def create_settings_page(self):
        page = QScrollArea()
        page.setWidgetResizable(True)
        content = QWidget()
        page.setWidget(content)
        layout = QVBoxLayout(content)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(30)
        
        title = QLabel("Settings")
        title.setObjectName("PageTitle")
        layout.addWidget(title)
        
        # Goal Setting
        goal_card = self.create_settings_card("Goal Setting", "Set your coin collection target.")
        goal_layout = goal_card.layout()
        
        self.goal_input = QLineEdit(str(self.tracker.get_goal()), placeholderText="Enter goal amount")
        self.goal_input.setValidator(QIntValidator(0, 10000000))
        set_goal_btn = QPushButton("Set Goal")
        set_goal_btn.setObjectName("PrimaryButton")
        set_goal_btn.clicked.connect(self.set_goal_clicked)
        
        goal_input_layout = QHBoxLayout()
        goal_input_layout.addWidget(self.goal_input, 1)
        goal_input_layout.addWidget(set_goal_btn)
        goal_layout.addLayout(goal_input_layout)
        layout.addWidget(goal_card)
        
        # Data Management
        data_card = self.create_settings_card("Data Management", "Export or import your transaction data.")
        data_layout = data_card.layout()
        data_buttons_layout = QHBoxLayout()
        
        export_btn = QPushButton("Export Data")
        import_btn = QPushButton("Import Data")
        export_btn.clicked.connect(self.export_data)
        import_btn.clicked.connect(self.import_data)
        
        data_buttons_layout.addWidget(export_btn)
        data_buttons_layout.addWidget(import_btn)
        data_layout.addLayout(data_buttons_layout)
        layout.addWidget(data_card)
        
        # Online Status
        online_card = self.create_settings_card("Online Sync", "Firebase connection status.")
        online_layout = online_card.layout()
        online_status = QLabel("✅ Connected to Firebase" if FIREBASE_AVAILABLE and self.tracker.db else "❌ Offline (using local storage)")
        online_layout.addWidget(online_status)
        layout.addWidget(online_card)
        
        layout.addStretch()
        return page

    def create_settings_card(self, title_text, desc_text):
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(15)
        title = QLabel(title_text)
        title.setObjectName("CardTitle")
        desc = QLabel(desc_text)
        layout.addWidget(title)
        layout.addWidget(desc)
        return card

    # Navigation methods
    def show_page(self, index, name):
        self.stacked_widget.setCurrentIndex(index)
        self.update_active_nav(name)
        self.update_all_data()

    def show_dashboard(self): self.show_page(0, "Dashboard")
    def show_analytics(self): self.show_page(1, "Analytics")
    def show_history(self): self.show_page(2, "History")
    def show_settings(self): self.show_page(3, "Settings")

    def update_all_data(self):
        """Update all UI elements with the latest data from the tracker."""
        self.update_balance_and_goal()
        self.load_transaction_history()
        self.update_analytics_stats()
        self.update_pie_chart()
        self.update_spending_breakdown()

    def load_transaction_history(self):
        transactions = self.tracker.get_transaction_history() # Already sorted
        
        # Update filter dropdown
        sources = sorted(list(set(t['source'] for t in transactions)))
        current_filter = self.history_source_filter.currentText()
        self.history_source_filter.blockSignals(True)
        self.history_source_filter.clear()
        self.history_source_filter.addItems(["All Sources"] + sources)
        self.history_source_filter.setCurrentText(current_filter)
        self.history_source_filter.blockSignals(False)

        self.filter_history() # Apply current filters

    def filter_history(self):
        search_text = self.history_search.text().lower()
        source_filter = self.history_source_filter.currentText()
        
        transactions = self.tracker.get_transaction_history()
        
        filtered = []
        for t in transactions:
            source_match = (source_filter == "All Sources" or t['source'] == source_filter)
            search_match = (search_text in t['source'].lower() or search_text in str(t['amount']))
            if source_match and search_match:
                filtered.append(t)
        
        self.history_table.setRowCount(len(filtered))
        balance_after = self.tracker.get_balance()

        for row, t in enumerate(reversed(filtered)): # Show oldest first to calculate running balance
            dt = datetime.fromisoformat(t['date'])
            date_item = QTableWidgetItem(dt.strftime("%b %d, %Y, %I:%M %p"))
            amount = t['amount']
            amount_item = QTableWidgetItem(f"{'+' if amount >= 0 else ''}{amount:,}")
            source_item = QTableWidgetItem(t['source'])
            balance_item = QTableWidgetItem(f"{balance_after:,}")

            amount_item.setForeground(QColor(self.palette_colors['success' if amount >= 0 else 'danger']))
            
            self.history_table.setItem(len(filtered) - 1 - row, 0, date_item)
            self.history_table.setItem(len(filtered) - 1 - row, 1, source_item)
            self.history_table.setItem(len(filtered) - 1 - row, 2, amount_item)
            self.history_table.setItem(len(filtered) - 1 - row, 3, balance_item)

            balance_after -= amount

    # Style and Theme methods
    def apply_theme(self):
        p = self.palette_colors
        is_dark = self.tracker.get_dark_mode()
        self.dark_toggle.setText("☀️" if is_dark else "🌙")
        
        # Update stylesheets for all widgets
        self.setStyleSheet(f"""
            QMainWindow, QWidget {{ background-color: {p['bg']}; color: {p['text']}; }}
            QScrollArea {{ background-color: {p['bg']}; border: none; }}
            QLabel {{ color: {p['text']}; background: transparent; }}
            #PageTitle {{ font-size: 28px; font-weight: bold; }}
            #SidebarTitle {{ font-size: 20px; font-weight: bold; color: {p['text']}; }}
            #CardTitle {{ font-size: 18px; font-weight: 500; }}
            #BalanceLabel {{ font-size: 48px; font-weight: bold; }}
            #StatLabel {{ font-size: 24px; font-weight: bold; }}
            #MutedLabel {{ color: {p['muted']}; font-size: 10px; font-weight: bold; }}
            QLineEdit, QComboBox {{
                padding: 10px 12px; border-radius: 8px; border: 1px solid {p['border']};
                background-color: {p['card'] if is_dark else '#ffffff'}; color: {p['text']}; font-size: 14px;
            }}
            QLineEdit:focus, QComboBox:focus {{ border-color: {p['primary']}; }}
            QComboBox::drop-down {{ border: none; }}
            QComboBox QAbstractItemView {{ background: {p['card']}; color: {p['text']}; selection-background-color: {p['primary']}; border: 1px solid {p['border']}; }}
            QPushButton {{
                background-color: {p['card']}; color: {p['text']}; border: 1px solid {p['border']};
                border-radius: 8px; padding: 10px 16px; font-size: 14px; font-weight: 500;
            }}
            QPushButton:hover {{ background-color: {p['primary'] + '20'}; border-color: {p['primary']}; }}
            #PrimaryButton {{ background-color: {p['primary']}; color: white; border: none; }}
            #PrimaryButton:hover {{ background-color: {p['primaryDark']}; }}
            #SuccessButton {{ background-color: {p['success']}; color: white; border: none; }}
            #SuccessButton:hover {{ background-color: {p['successDark']}; }}
            #DangerButton {{ background-color: {p['danger']}; color: white; border: none; }}
            #DangerButton:hover {{ background-color: {p['dangerDark']}; }}
            #ThemeToggle {{ font-size: 16px; }}
            #NavButton {{ text-align: left; padding-left: 15px; background: transparent; border: none; }}
            #NavButton:hover {{ color: {p['primary']}; }}
            QProgressBar {{ border: none; border-radius: 6px; background-color: {p['border']}; height: 12px; }}
            QProgressBar::chunk {{ background-color: {p['success']}; border-radius: 6px; }}
            QTableWidget {{
                border: 1px solid {p['border']}; border-radius: 8px; gridline-color: {p['border']};
                background-color: {p['card']}; color: {p['text']}; alternate-background-color: {p['bg']};
            }}
            QHeaderView::section {{
                background-color: {p['tableHeader']}; padding: 12px; font-weight: bold;
                border: none; border-bottom: 1px solid {p['border']}; color: {p['text']};
            }}
            QScrollBar:vertical {{ border: none; background: {p['bg']}; width: 8px; }}
            QScrollBar::handle:vertical {{ background: {p['border']}; border-radius: 4px; }}
            QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0px; }}
        """)
        
        self.sidebar.setStyleSheet(f"background-color: {p['sidebar']}; border-right: 1px solid {p['border']};")

        # Update custom widgets
        for widget in self.themed_widgets:
            widget.setPalette(p)
        for button in self.quick_action_buttons:
            button.setPalette(p)
        
        # Update chart theme
        if QTCHART_AVAILABLE:
            self.apply_chart_theme(self.pie_chart)
        
        self.update_active_nav(self.stacked_widget.currentWidget().objectName())

    def apply_chart_theme(self, chart):
        p = self.palette_colors
        chart.setBackgroundBrush(QColor(p['card']))
        chart.setTitleBrush(QColor(p['text']))
        # Customize series colors for better visibility
        if chart.series():
            series = chart.series()[0]
            for s in series.slices():
                s.setLabelColor(QColor(p['text']))

    def update_active_nav(self, name):
        p = self.palette_colors
        for btn_name, button in self.nav_buttons.items():
            if btn_name == name:
                button.setStyleSheet(f"""
                    background-color: {p['primaryLight']}; color: {p['primary']}; border-radius: 8px;
                    text-align: left; padding-left: 15px; font-weight: bold;
                """)
            else:
                button.setStyleSheet(f"""
                    background-color: transparent; color: {p['muted']}; border: none;
                    text-align: left; padding-left: 15px; font-weight: 500;
                """)
                button.style().unpolish(button)
                button.style().polish(button)
    
    def create_icon(self):
        pixmap = QPixmap(64, 64)
        pixmap.fill(Qt.transparent)
        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.Antialiasing)
        painter.setBrush(QColor("#f59e0b")) # Orange/gold color
        painter.setPen(Qt.NoPen)
        painter.drawEllipse(4, 4, 56, 56)
        painter.setFont(QFont("Segoe UI", 30, QFont.Bold))
        painter.setPen(QColor("#ffffff"))
        painter.drawText(pixmap.rect(), Qt.AlignCenter, "C")
        painter.end()
        return QIcon(pixmap)

    # Core functionality methods
    def load_profiles(self):
        data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        if not os.path.exists(data_dir): return ["Default"]
        profiles = [f[:-5] for f in os.listdir(data_dir) if f.endswith('.json')]
        return profiles if profiles else ["Default"]

    def change_profile(self, profile_name):
        self.current_profile = profile_name
        self.tracker = OnlineCoinTracker(profile_name)
        self.palette_colors = DARK if self.tracker.get_dark_mode() else LIGHT
        self.goal_input.setText(str(self.tracker.get_goal()))
        self.apply_theme()
        self.update_all_data()

    def create_new_profile(self):
        name, ok = QInputDialog.getText(self, "New Profile", "Enter new profile name:")
        if ok and name:
            if name in self.profiles:
                QMessageBox.warning(self, "Error", "Profile already exists!")
                return
            self.profiles.append(name)
            self.profile_combo.addItem(name)
            self.profile_combo.setCurrentText(name) # This will trigger change_profile

    def toggle_dark_mode(self):
        new_dark_mode = not self.tracker.get_dark_mode()
        self.tracker.set_dark_mode(new_dark_mode)
        self.palette_colors = DARK if new_dark_mode else LIGHT
        self.apply_theme()

    def quick_action(self, action, amount, is_positive):
        if is_positive:
            self.tracker.add_coins(amount, action)
        else:
            self.tracker.add_coins(-amount, action)
        QMessageBox.information(self, "Success", f"Quick action '{action}' for {amount} coins recorded.")
        self.update_all_data()

    def add_coins(self):
        self.process_transaction(self.amount_input, self.source_combo, is_add=True)

    def spend_coins(self):
        self.process_transaction(self.spend_amount_input, self.category_combo, is_add=False)

    def process_transaction(self, amount_input, source_combo, is_add):
        amount_text = amount_input.text()
        if not amount_text:
            QMessageBox.warning(self, "Input Error", "Please enter an amount.")
            return
        try:
            amount = int(amount_text)
            source = source_combo.currentText()
            final_amount = amount if is_add else -amount
            
            if self.tracker.add_coins(final_amount, source):
                amount_input.clear()
                verb = "Added" if is_add else "Spent"
                QMessageBox.information(self, "Success", f"{verb} {amount} coins.")
                self.update_all_data()
        except ValueError:
            QMessageBox.warning(self, "Input Error", "Please enter a valid number.")

    def update_balance_and_goal(self):
        balance = self.tracker.get_balance()
        goal = self.tracker.get_goal()
        self.balance_label.setText(f"{balance:,} coins")
        self.goal_label.setText(f"Goal: {goal:,} coins")
        if goal > 0:
            pct = max(0, min(100, int((balance / goal) * 100)))
            self.goal_progress.setValue(pct)
            self.goal_progress.setFormat(f"{pct}%")
        else:
            self.goal_progress.setValue(0)
            self.goal_progress.setFormat("No goal set")

    def update_analytics_stats(self):
        earnings = sum(t['amount'] for t in self.tracker.transactions if t.get('amount',0) > 0)
        spending = abs(sum(t['amount'] for t in self.tracker.transactions if t.get('amount',0) < 0))
        net = earnings - spending
        self.total_earnings_label.setText(f"+{earnings:,}")
        self.total_spending_label.setText(f"-{spending:,}")
        self.net_balance_label.setText(f"{net:+,}")
        self.total_earnings_label.setStyleSheet(f"color: {self.palette_colors['success']}; font-size: 24px; font-weight: bold;")
        self.total_spending_label.setStyleSheet(f"color: {self.palette_colors['danger']}; font-size: 24px; font-weight: bold;")
        self.net_balance_label.setStyleSheet(f"color: {self.palette_colors['primary']}; font-size: 24px; font-weight: bold;")

    def update_spending_breakdown(self):
        # Clear previous breakdown
        while self.spending_breakdown_layout.count():
            child = self.spending_breakdown_layout.takeAt(0)
            if child.widget():
                child.widget().deleteLater()
        
        breakdown = self.tracker.get_spending_breakdown()
        if not breakdown:
            self.spending_breakdown_layout.addWidget(QLabel("No spending data available."))
            return
            
        total = sum(breakdown.values())
        for source, amount in breakdown.items():
            percentage = (amount / total) * 100 if total > 0 else 0
            item = QLabel(f"• {source}: {amount:,} ({percentage:.1f}%)")
            self.spending_breakdown_layout.addWidget(item)

    def set_goal_clicked(self):
        if self.goal_input.text():
            try:
                goal = int(self.goal_input.text())
                self.tracker.set_goal(goal)
                self.update_balance_and_goal()
                QMessageBox.information(self, "Goal Set", f"Goal updated to {goal:,} coins.")
            except ValueError:
                QMessageBox.warning(self, "Input Error", "Please enter a valid number.")

    def export_data(self):
        path, _ = QFileDialog.getSaveFileName(self, "Export Data", "", "JSON Files (*.json)")
        if path and self.tracker.export_data(path):
            QMessageBox.information(self, "Success", "Data exported successfully!")

    def import_data(self):
        path, _ = QFileDialog.getOpenFileName(self, "Import Data", "", "JSON Files (*.json)")
        if path and self.tracker.import_data(path):
            self.update_all_data()
            QMessageBox.information(self, "Success", "Data imported successfully!")

# ---------------------------
# App Bootstrap
# ---------------------------

if __name__ == "__main__":
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    app.setFont(QFont("Segoe UI", 10))
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())
