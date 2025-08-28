import sys
import json
import os
from datetime import datetime, date
from collections import defaultdict

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QLineEdit, QComboBox, QTableWidget,
    QTableWidgetItem, QHeaderView, QMessageBox, QFrame, QFileDialog,
    QInputDialog, QDialog, QTabWidget, QDateEdit, QCheckBox, QProgressBar
)
from PyQt5.QtCore import Qt, QSize, QDate, QDateTime
from PyQt5.QtGui import QFont, QColor, QIcon, QPixmap, QIntValidator, QPainter

# Charts
from PyQt5.QtChart import QChart, QChartView, QPieSeries

# ---------------------------
# Theme Palettes (Light/Dark)
# ---------------------------

LIGHT = {
    "bg": "#f8f9fa",
    "card": "#ffffff",
    "border": "#dee2e6",
    "text": "#2c3e50",
    "muted": "#6c757d",
    "primary": "#3498db",
    "primaryDark": "#2980b9",
    "success": "#2ecc71",
    "successDark": "#27ae60",
    "danger": "#e74c3c",
    "dangerDark": "#c0392b",
    "warning": "#f39c12",
    "accent": "#9b59b6",
    "accentDark": "#8e44ad",
    "tableHeader": "#f8f9fa",
}

DARK = {
    "bg": "#1f2937",            # slate-800
    "card": "#111827",          # gray-900
    "border": "#374151",        # gray-700
    "text": "#e5e7eb",          # gray-200
    "muted": "#9ca3af",         # gray-400
    "primary": "#56759c",       # blue-400
    "primaryDark": "#3b82f6",   # blue-500
    "success": "#34d399",       # emerald-400
    "successDark": "#10b981",   # emerald-500
    "danger": "#f87171",        # red-400
    "dangerDark": "#ef4444",    # red-500
    "warning": "#f59e0b",       # amber-500
    "accent": "#a78bfa",        # violet-400
    "accentDark": "#8b5cf6",    # violet-500
    "tableHeader": "#111827",
}

def dt_now_iso():
    return datetime.now().isoformat()

# ---------------------------
# Breakdown Dialog (with charts)
# ---------------------------

class BreakdownDialog(QDialog):
    def __init__(self, tracker, palette, parent=None):
        super().__init__(parent)
        self.tracker = tracker
        self.palette = palette
        self.setWindowTitle("Coin Source Breakdown")
        self.setGeometry(400, 400, 900, 750)
        self.setMinimumSize(640, 420)

        self.setStyleSheet(f"""
            QDialog {{
                background-color: {self.palette['bg']};
                color: {self.palette['text']};
            }}
            QLabel {{
                color: {self.palette['text']};
            }}
        """)

        layout = QVBoxLayout(self)

        tabs = QTabWidget()
        tabs.setStyleSheet(f"""
            QTabWidget::pane {{
                border: 1px solid {self.palette['border']};
                border-radius: 8px;
                background: {self.palette['card']};
            }}
            QTabBar::tab {{
                padding: 8px 12px;
                margin: 2px;
                border: 1px solid {self.palette['border']};
                border-bottom: none;
                background: {self.palette['bg']};
                color: {self.palette['text']};
                border-top-left-radius: 8px;
                border-top-right-radius: 8px;
            }}
            QTabBar::tab:selected {{
                background: {self.palette['card']};
                color: {self.palette['text']};
            }}
        """)

        # Summary (earnings)
        self.summary_tab = QWidget()
        self.summary_layout = QVBoxLayout(self.summary_tab)

        summary_title = QLabel("Coin Sources — Earnings")
        summary_title.setFont(QFont("Arial", 14, QFont.Bold))
        summary_title.setAlignment(Qt.AlignCenter)
        self.summary_layout.addWidget(summary_title)

        self.summary_table = self._styled_table(["Source", "Total Coins"])
        self.summary_layout.addWidget(self.summary_table)

        # Add chart container
        self.summary_chart_view = QChartView()
        self.summary_chart_view.setRenderHint(QPainter.Antialiasing)
        self.summary_layout.addWidget(self.summary_chart_view)

        # Spending tab
        self.spending_tab = QWidget()
        self.spending_layout = QVBoxLayout(self.spending_tab)

        spending_title = QLabel("Spending Summary")
        spending_title.setFont(QFont("Arial", 14, QFont.Bold))
        spending_title.setAlignment(Qt.AlignCenter)
        self.spending_layout.addWidget(spending_title)

        self.spending_table = self._styled_table(["Source", "Coins Spent"])
        self.spending_layout.addWidget(self.spending_table)

        self.spending_chart_view = QChartView()
        self.spending_chart_view.setRenderHint(QPainter.Antialiasing)
        self.spending_layout.addWidget(self.spending_chart_view)

        tabs.addTab(self.summary_tab, "Earnings")
        tabs.addTab(self.spending_tab, "Spending")

        layout.addWidget(tabs)

        close_btn = QPushButton("Close")
        close_btn.clicked.connect(self.accept)
        close_btn.setStyleSheet(f"""
            QPushButton {{
                background-color: {self.palette['primary']};
                color: {'#111827' if self.palette is LIGHT else '#0b1220'};
                font-weight: bold;
                border-radius: 6px;
                padding: 8px 16px;
            }}
            QPushButton:hover {{
                background-color: {self.palette['primaryDark']};
            }}
        """)
        layout.addWidget(close_btn, alignment=Qt.AlignRight)

        self.update_data()

    def _styled_table(self, headers):
        table = QTableWidget()
        table.setColumnCount(len(headers))
        table.setHorizontalHeaderLabels(headers)
        table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        table.verticalHeader().setVisible(False)
        table.setEditTriggers(QTableWidget.NoEditTriggers)
        table.setStyleSheet(f"""
            QTableWidget {{
                border: 1px solid {self.palette['border']};
                border-radius: 10px;
                gridline-color: {self.palette['border']};
                background-color: {self.palette['card']};
                color: {self.palette['text']};
            }}
            QHeaderView::section {{
                background-color: {self.palette['tableHeader']};
                padding: 8px;
                font-weight: bold;
                border: none;
                color: {self.palette['text']};
            }}
        """)
        return table

    def update_data(self):
        # Tables
        gains = self.tracker.get_source_breakdown()
        self.summary_table.setRowCount(len(gains))
        for row, (source, amount) in enumerate(gains.items()):
            s_item = QTableWidgetItem(source)
            a_item = QTableWidgetItem(f"{amount} coins")
            s_item.setTextAlignment(Qt.AlignCenter)
            a_item.setTextAlignment(Qt.AlignCenter)
            a_item.setForeground(QColor(self.palette["success"]))
            self.summary_table.setItem(row, 0, s_item)
            self.summary_table.setItem(row, 1, a_item)

        spending = self.tracker.get_spending_breakdown()
        self.spending_table.setRowCount(len(spending))
        for row, (source, amount) in enumerate(spending.items()):
            s_item = QTableWidgetItem(source)
            a_item = QTableWidgetItem(f"{amount} coins")
            s_item.setTextAlignment(Qt.AlignCenter)
            a_item.setTextAlignment(Qt.AlignCenter)
            a_item.setForeground(QColor(self.palette["danger"]))
            self.spending_table.setItem(row, 0, s_item)
            self.spending_table.setItem(row, 1, a_item)

        # Charts
        self._update_pie_chart(self.summary_chart_view, gains, "Earnings Breakdown")
        self._update_pie_chart(self.spending_chart_view, spending, "Spending Breakdown")

    def _update_pie_chart(self, chart_view, data_map, title):
        series = QPieSeries()
        total = sum(data_map.values())
        if total == 0:
            # empty dataset indicator
            series.append("No Data", 1)

        for source, amt in data_map.items():
            if amt > 0:
                series.append(source, amt)

        # Emphasize largest slice
        if series.slices():
            biggest = max(series.slices(), key=lambda s: s.value())
            biggest.setExploded(True)
            biggest.setLabelVisible(True)

        chart = QChart()
        chart.addSeries(series)
        chart.setTitle(title)
        chart.legend().setAlignment(Qt.AlignBottom)
        # Chart theming via palette
        chart.setBackgroundBrush(QColor(self.palette["card"]))
        chart.setTitleBrush(QColor(self.palette["text"]))
        chart.legend().setLabelColor(QColor(self.palette["text"]))

        chart_view.setChart(chart)


# ---------------------------
# Data / Profile
# ---------------------------

class CoinTracker:
    def __init__(self, profile_name="Default"):
        self.profile_name = profile_name
        self.data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        self.data_file = os.path.join(self.data_dir, f"{profile_name}.json")
        self.transactions = []
        # settings saved per profile
        self.settings = {
            "goal": 0,          # integer
            "dark_mode": False  # bool
        }
        self.load_data()

    def load_data(self):
        try:
            with open(self.data_file, 'r') as f:
                data = json.load(f)
                self.transactions = data.get('transactions', [])
                settings = data.get('settings', {})
                # merge defaults
                self.settings.update(settings)
        except (FileNotFoundError, json.JSONDecodeError):
            self.transactions = []

    def save_data(self):
        os.makedirs(self.data_dir, exist_ok=True)
        data = {
            "profile_name": self.profile_name,
            "last_updated": dt_now_iso(),
            "transactions": self.transactions,
            "settings": self.settings
        }
        with open(self.data_file, 'w') as f:
            json.dump(data, f, indent=2)

    def add_coins(self, amount, source):
        if amount == 0:
            return False
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
        return sum(t['amount'] for t in self.transactions)

    def get_transaction_history(self):
        return self.transactions.copy()

    def get_source_breakdown(self):
        breakdown = defaultdict(int)
        for t in self.transactions:
            if t['amount'] > 0:
                breakdown[t['source']] += t['amount']
        return breakdown

    def get_spending_breakdown(self):
        breakdown = defaultdict(int)
        for t in self.transactions:
            if t['amount'] < 0:
                src = t['source']
                if src == "Box Draws":
                    src = "Spending (Box Draws)"
                breakdown[src] += abs(t['amount'])
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

    # Settings helpers
    def set_goal(self, goal_value: int):
        self.settings["goal"] = max(0, int(goal_value))
        self.save_data()

    def get_goal(self) -> int:
        return int(self.settings.get("goal", 0))

    def set_dark_mode(self, enabled: bool):
        self.settings["dark_mode"] = bool(enabled)
        self.save_data()

    def get_dark_mode(self) -> bool:
        return bool(self.settings.get("dark_mode", False))


# ---------------------------
# Main Window
# ---------------------------

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        # Profiles
        self.profiles = self.load_profiles()
        self.current_profile = "Default" if not self.profiles else self.profiles[0]
        self.tracker = CoinTracker(self.current_profile)

        # Palette
        self.palette_colors = DARK if self.tracker.get_dark_mode() else LIGHT

        self.setWindowTitle("Coin Tracker")
        self.setGeometry(200, 100, 1100, 800)
        self.setMinimumSize(1000, 700)
        self.setWindowIcon(self.create_icon())

        # Central widget
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout(central_widget)
        main_layout.setContentsMargins(20, 20, 20, 20)
        main_layout.setSpacing(15)

        self.apply_app_styles()

        # Top bar (logo, profile, dark mode)
        top_bar = QWidget()
        top_layout = QHBoxLayout(top_bar)
        top_layout.setContentsMargins(0, 0, 0, 0)

        logo_label = QLabel()
        logo_label.setPixmap(self.create_icon().pixmap(32, 32))

        self.profile_combo = QComboBox()
        self.profile_combo.addItems(self.profiles)
        self.profile_combo.setCurrentText(self.current_profile)
        self.profile_combo.setStyleSheet(self.combo_style())
        self.profile_combo.currentTextChanged.connect(self.change_profile)

        new_profile_btn = QPushButton("+ New Profile")
        new_profile_btn.setStyleSheet(self.primary_btn_style())
        new_profile_btn.clicked.connect(self.create_new_profile)

        self.dark_toggle = QCheckBox("Dark Mode")
        self.dark_toggle.setChecked(self.tracker.get_dark_mode())
        self.dark_toggle.stateChanged.connect(self.toggle_dark_mode)
        self.dark_toggle.setStyleSheet(f"color: {self.palette_colors['text']};")

        top_layout.addWidget(logo_label)
        top_layout.addStretch()
        top_layout.addWidget(QLabel("Profile:"))
        top_layout.addWidget(self.profile_combo, 1)
        top_layout.addWidget(new_profile_btn)
        top_layout.addSpacing(12)
        top_layout.addWidget(self.dark_toggle)

        main_layout.addWidget(top_bar)

        # Balance + Goal section
        balance_frame = QFrame()
        balance_frame.setStyleSheet(f"""
            QFrame {{
                background-color: {self.palette_colors['primary']};
                border-radius: 16px;
                padding: 20px;
            }}
            QLabel {{
                color: #0b1220;
            }}
        """)
        balance_layout = QVBoxLayout(balance_frame)
        balance_layout.setAlignment(Qt.AlignCenter)

        balance_title = QLabel("CURRENT COIN BALANCE")
        balance_title.setFont(QFont("Arial", 14, QFont.Bold))
        balance_title.setAlignment(Qt.AlignCenter)

        self.balance_label = QLabel(f"{self.tracker.get_balance()} coins")
        self.balance_label.setFont(QFont("Arial", 36, QFont.Bold))
        self.balance_label.setAlignment(Qt.AlignCenter)

        # Goal controls
        goal_row = QHBoxLayout()
        goal_row.setSpacing(10)
        self.goal_input = QLineEdit()
        self.goal_input.setValidator(QIntValidator(0, 10_000_000))
        self.goal_input.setPlaceholderText("Set goal (e.g., 2500)")
        self.goal_input.setText(str(self.tracker.get_goal() or "" ))
        self.goal_input.setStyleSheet(self.input_style())

        set_goal_btn = QPushButton("Set Goal")
        set_goal_btn.setStyleSheet(self.success_btn_style())
        set_goal_btn.clicked.connect(self.set_goal_clicked)

        self.goal_progress = QProgressBar()
        self.goal_progress.setMinimum(0)
        self.goal_progress.setMaximum(100)
        self.goal_progress.setValue(0)
        self.goal_progress.setTextVisible(True)
        self.goal_progress.setStyleSheet(f"""
            QProgressBar {{
                background-color: {self.palette_colors['card']};
                border: 1px solid {self.palette_colors['border']};
                border-radius: 8px;
                height: 18px;
                color: {self.palette_colors['text']};
            }}
            QProgressBar::chunk {{
                background-color: {self.palette_colors['success']};
                border-radius: 8px;
            }}
        """)

        goal_row.addStretch()
        goal_row.addWidget(QLabel("Goal:"))
        goal_row.addWidget(self.goal_input)
        goal_row.addWidget(set_goal_btn)
        goal_row.addStretch()

        # View breakdown button
        view_breakdown_btn = QPushButton("View Coin Breakdown")
        view_breakdown_btn.setStyleSheet(f"""
            QPushButton {{
                background-color: {self.palette_colors['accent']};
                color: {'#111827' if self.palette_colors is LIGHT else '#0b1220'};
                border-radius: 8px;
                padding: 8px 14px;
                margin-top: 10px;
                font-weight: 600;
            }}
            QPushButton:hover {{
                background-color: {self.palette_colors['accentDark']};
            }}
        """)
        view_breakdown_btn.clicked.connect(self.show_breakdown)

        balance_layout.addWidget(balance_title)
        balance_layout.addWidget(self.balance_label)
        balance_layout.addLayout(goal_row)
        balance_layout.addWidget(self.goal_progress)
        balance_layout.addWidget(view_breakdown_btn)

        main_layout.addWidget(balance_frame)

        # Add coins section with quick buttons
        add_coins_frame = QFrame()
        add_coins_frame.setStyleSheet(f"""
            QFrame {{
                background-color: {self.palette_colors['card']};
                border-radius: 12px;
                padding: 14px;
                border: 1px solid {self.palette_colors['border']};
            }}
        """)
        add_coins_layout = QHBoxLayout(add_coins_frame)

        # Amount
        amount_layout = QVBoxLayout()
        amount_layout.setSpacing(6)
        amount_label = QLabel("Amount:")
        amount_label.setStyleSheet(f"color: {self.palette_colors['text']};")

        amount_row = QHBoxLayout()
        self.amount_input = QLineEdit()
        self.amount_input.setPlaceholderText("Enter number of coins")
        self.amount_input.setStyleSheet(self.input_style())
        self.amount_input.setValidator(QIntValidator(-1000000, 1000000))

        # Quick buttons
        quicks = [
            ("+10", 10),
            ("+20", 20),
            ("+50", 50),
            ("-100", -100),
        ]
        qb_row = QHBoxLayout()
        qb_row.setSpacing(6)
        for label, val in quicks:
            btn = QPushButton(label)
            btn.setStyleSheet(self.secondary_btn_style())
            btn.clicked.connect(lambda _, v=val: self.quick_add(v))
            qb_row.addWidget(btn)

        amount_row.addWidget(self.amount_input, 1)

        amount_layout.addWidget(amount_label)
        amount_layout.addLayout(amount_row)
        amount_layout.addLayout(qb_row)

        # Source
        source_layout = QVBoxLayout()
        source_layout.setSpacing(6)
        source_label = QLabel("Source:")
        source_label.setStyleSheet(f"color: {self.palette_colors['text']};")
        self.source_combo = QComboBox()
        self.source_combo.addItems([
            "Event Reward", "Login", "Daily Games", "Achievements",
            "Box Draws", "Others", "Ads"
        ])
        self.source_combo.setStyleSheet(self.combo_style())
        self.source_combo.currentIndexChanged.connect(self.source_changed)

        source_layout.addWidget(source_label)
        source_layout.addWidget(self.source_combo)

        # Add button
        self.add_button = QPushButton("Add Transaction")
        self.add_button.setStyleSheet(self.success_btn_style())
        self.add_button.setFont(QFont("Arial", 12))
        self.add_button.setMinimumHeight(46)
        self.add_button.clicked.connect(self.add_coins)

        add_coins_layout.addLayout(amount_layout, 2)
        add_coins_layout.addSpacing(15)
        add_coins_layout.addLayout(source_layout, 1)
        add_coins_layout.addSpacing(15)
        add_coins_layout.addWidget(self.add_button, 1)

        main_layout.addWidget(add_coins_frame)

        # Filters + History
        history_frame = QFrame()
        history_frame.setStyleSheet("border: none;")
        history_layout = QVBoxLayout(history_frame)

        # Filters bar
        filter_row1 = QHBoxLayout()
        filter_row1.setSpacing(8)

        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Search (source contains...)")
        self.search_input.setStyleSheet(self.input_style())
        self.search_input.textChanged.connect(self.apply_filters)

        self.source_filter = QComboBox()
        self.source_filter.addItems(["All", "Event Reward", "Login", "Daily Games", "Achievements", "Box Draws", "Others"])
        self.source_filter.setStyleSheet(self.combo_style())
        self.source_filter.currentIndexChanged.connect(self.apply_filters)

        self.amount_type_filter = QComboBox()
        self.amount_type_filter.addItems(["All", "Earnings", "Spending"])
        self.amount_type_filter.setStyleSheet(self.combo_style())
        self.amount_type_filter.currentIndexChanged.connect(self.apply_filters)

        self.start_date = QDateEdit()
        self.start_date.setCalendarPopup(True)
        self.start_date.setDate(QDate.currentDate().addMonths(-1))
        self.start_date.dateChanged.connect(self.apply_filters)
        self.start_date.setStyleSheet(self.input_style())

        self.end_date = QDateEdit()
        self.end_date.setCalendarPopup(True)
        self.end_date.setDate(QDate.currentDate())
        self.end_date.dateChanged.connect(self.apply_filters)
        self.end_date.setStyleSheet(self.input_style())

        reset_filters_btn = QPushButton("Reset Filters")
        reset_filters_btn.setStyleSheet(self.secondary_btn_style())
        reset_filters_btn.clicked.connect(self.reset_filters)

        filter_row1.addWidget(QLabel("Search:"))
        filter_row1.addWidget(self.search_input, 1)
        filter_row1.addWidget(QLabel("Source:"))
        filter_row1.addWidget(self.source_filter)
        filter_row1.addWidget(QLabel("Type:"))
        filter_row1.addWidget(self.amount_type_filter)
        filter_row1.addWidget(QLabel("From:"))
        filter_row1.addWidget(self.start_date)
        filter_row1.addWidget(QLabel("To:"))
        filter_row1.addWidget(self.end_date)
        filter_row1.addWidget(reset_filters_btn)

        history_title_layout = QHBoxLayout()
        history_title = QLabel("Transaction History")
        history_title.setFont(QFont("Arial", 14, QFont.Bold))

        export_button = QPushButton("Export JSON")
        export_button.setStyleSheet(self.accent_btn_style())
        export_button.clicked.connect(self.export_data)

        import_button = QPushButton("Import JSON")
        import_button.setStyleSheet(self.primary_btn_style())
        import_button.clicked.connect(self.import_data)

        history_title_layout.addWidget(history_title)
        history_title_layout.addStretch()
        history_title_layout.addWidget(import_button)
        history_title_layout.addWidget(export_button)

        self.history_table = QTableWidget()
        self.history_table.setColumnCount(4)
        self.history_table.setHorizontalHeaderLabels(["Date", "Amount", "Previous Balance", "Source"])
        self.history_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.history_table.verticalHeader().setVisible(False)
        self.history_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.history_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.history_table.setSortingEnabled(True)  # enable column sorting
        self.history_table.setStyleSheet(f"""
            QTableWidget {{
                border: 1px solid {self.palette_colors['border']};
                border-radius: 10px;
                gridline-color: {self.palette_colors['border']};
                background-color: {self.palette_colors['card']};
                color: {self.palette_colors['text']};
            }}
            QHeaderView::section {{
                background-color: {self.palette_colors['tableHeader']};
                padding: 8px;
                font-weight: bold;
                border: none;
                color: {self.palette_colors['text']};
            }}
            QTableWidget::item:selected {{
                background-color: {self.palette_colors['bg']};
            }}
        """)

        # Add striping via alternating row colors
        self.history_table.setAlternatingRowColors(True)
        self.history_table.setStyleSheet(self.history_table.styleSheet() + f"""
            QTableWidget {{
                alternate-background-color: {('#f2f2f2' if self.palette_colors is LIGHT else '#0f172a')};
            }}
        """)

        history_layout.addLayout(filter_row1)
        history_layout.addLayout(history_title_layout)
        history_layout.addWidget(self.history_table)

        main_layout.addWidget(history_frame)

        # Status bar
        self.statusBar().setStyleSheet(f"color: {self.palette_colors['text']};")
        self.statusBar().showMessage("Ready")

        # Load data
        self.update_balance()
        self.load_transactions()   # populates table
        self.apply_filters()       # apply initial filter window
        self.update_goal_progress()

    # ---------- Styles

    def input_style(self):
        p = self.palette_colors
        return f"""
            QLineEdit {{
                padding: 8px 10px;
                border-radius: 8px;
                border: 1px solid {p['border']};
                background: {p['card']};
                color: {p['text']};
                selection-background-color: {p['primary']};
            }}
        """

    def combo_style(self):
        p = self.palette_colors
        return f"""
            QComboBox {{
                padding: 6px 10px;
                border-radius: 8px;
                border: 1px solid {p['border']};
                background: {p['card']};
                color: {p['text']};
            }}
            QComboBox QAbstractItemView {{
                background: {p['card']};
                color: {p['text']};
                selection-background-color: {p['primary']};
                border: 1px solid {p['border']};
            }}
        """

    def primary_btn_style(self):
        p = self.palette_colors
        return f"""
            QPushButton {{
                background-color: {p['primary']};
                color: {'#111827' if p is LIGHT else '#0b1220'};
                border-radius: 8px;
                padding: 8px 12px;
                font-weight: 600;
            }}
            QPushButton:hover {{
                background-color: {p['primaryDark']};
            }}
        """

    def success_btn_style(self):
        p = self.palette_colors
        return f"""
            QPushButton {{
                background-color: {p['success']};
                color: {'#111827' if p is LIGHT else '#0b1220'};
                border-radius: 8px;
                padding: 10px 16px;
                font-weight: 700;
            }}
            QPushButton:hover {{
                background-color: {p['successDark']};
            }}
        """

    def secondary_btn_style(self):
        p = self.palette_colors
        return f"""
            QPushButton {{
                background-color: {p['bg']};
                color: {p['text']};
                border: 1px solid {p['border']};
                border-radius: 8px;
                padding: 6px 10px;
            }}
            QPushButton:hover {{
                border-color: {p['primary']};
            }}
        """

    def accent_btn_style(self):
        p = self.palette_colors
        return f"""
            QPushButton {{
                background-color: {p['accent']};
                color: {'#111827' if p is LIGHT else '#0b1220'};
                border-radius: 8px;
                padding: 8px 12px;
                font-weight: 600;
            }}
            QPushButton:hover {{
                background-color: {p['accentDark']};
            }}
        """

    def apply_app_styles(self):
        p = self.palette_colors
        self.setStyleSheet(f"""
            QMainWindow {{
                background-color: {p['bg']};
                color: {p['text']};
            }}
            QLabel {{
                color: {p['text']};
            }}
        """)

    # ---------- Icon

    def create_icon(self):
        pixmap = QPixmap(64, 64)
        pixmap.fill(Qt.transparent)

        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.Antialiasing)
        # coin circle
        painter.setBrush(QColor(241, 196, 15))
        painter.setPen(Qt.NoPen)
        painter.drawEllipse(8, 8, 48, 48)
        # C letter
        painter.setFont(QFont("Arial", 24, QFont.Bold))
        painter.setPen(QColor(44, 62, 80))
        painter.drawText(pixmap.rect(), Qt.AlignCenter, "C")
        painter.end()

        return QIcon(pixmap)

    # ---------- Profiles

    def load_profiles(self):
        data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        if not os.path.exists(data_dir):
            return ["Default"]

        profiles = []
        for file in os.listdir(data_dir):
            if file.endswith('.json'):
                profile_name = file[:-5]
                try:
                    with open(os.path.join(data_dir, file), 'r') as f:
                        data = json.load(f)
                        profiles.append(data.get('profile_name', profile_name))
                except:
                    profiles.append(profile_name)

        return profiles if profiles else ["Default"]

    def change_profile(self, profile_name):
        self.current_profile = profile_name
        self.tracker = CoinTracker(profile_name)
        self.palette_colors = DARK if self.tracker.get_dark_mode() else LIGHT
        self.apply_app_styles()
        self.refresh_all_styles()
        self.update_balance()
        self.load_transactions()
        self.reset_filters()
        self.goal_input.setText(str(self.tracker.get_goal() or ""))
        self.update_goal_progress()
        self.statusBar().showMessage(f"Switched to profile: {profile_name}", 2000)

    def create_new_profile(self):
        name, ok = QInputDialog.getText(
            self,
            "New Profile",
            "Enter profile name:",
            QLineEdit.Normal
        )
        if ok and name:
            if name in self.profiles:
                QMessageBox.warning(self, "Error", "Profile already exists!")
                return
            self.profiles.append(name)
            self.profile_combo.addItem(name)
            self.profile_combo.setCurrentText(name)

            self.tracker = CoinTracker(name)
            self.tracker.save_data()
            self.statusBar().showMessage(f"Created new profile: {name}", 3000)

    # ---------- Dark mode

    def toggle_dark_mode(self, state):
        enabled = (state == Qt.Checked)
        self.tracker.set_dark_mode(enabled)
        self.palette_colors = DARK if enabled else LIGHT
        self.apply_app_styles()
        self.refresh_all_styles()
        self.update_balance()
        self.update_goal_progress()
        # refresh breakdown dialog styles next time it's opened

    def refresh_all_styles(self):
        # Re-apply critical stylesheet-driven components
        self.profile_combo.setStyleSheet(self.combo_style())
        self.goal_input.setStyleSheet(self.input_style())
        self.amount_input.setStyleSheet(self.input_style())
        self.source_combo.setStyleSheet(self.combo_style())
        self.search_input.setStyleSheet(self.input_style())
        self.start_date.setStyleSheet(self.input_style())
        self.end_date.setStyleSheet(self.input_style())
        self.start_date.setStyleSheet(self.input_style())
        self.end_date.setStyleSheet(self.input_style())
        self.goal_progress.setStyleSheet(f"""
            QProgressBar {{
                background-color: {self.palette_colors['card']};
                border: 1px solid {self.palette_colors['border']};
                border-radius: 8px;
                height: 18px;
                color: {self.palette_colors['text']};
            }}
            QProgressBar::chunk {{
                background-color: {self.palette_colors['success']};
                border-radius: 8px;
            }}
        """)

        # Update history table style (including alternating color)
        self.history_table.setStyleSheet(f"""
            QTableWidget {{
                border: 1px solid {self.palette_colors['border']};
                border-radius: 10px;
                gridline-color: {self.palette_colors['border']};
                background-color: {self.palette_colors['card']};
                color: {self.palette_colors['text']};
                alternate-background-color: {('#f2f2f2' if self.palette_colors is LIGHT else '#0f172a')};
            }}
            QHeaderView::section {{
                background-color: {self.palette_colors['tableHeader']};
                padding: 8px;
                font-weight: bold;
                border: none;
                color: {self.palette_colors['text']};
            }}
        """)
        self.statusBar().setStyleSheet(f"color: {self.palette_colors['text']};")

    # ---------- Breakdown

    def show_breakdown(self):
        dialog = BreakdownDialog(self.tracker, self.palette_colors, self)
        dialog.exec_()

    # ---------- Add/Update

    def source_changed(self, index):
        source = self.source_combo.currentText()
        if source == "Box Draws":
            self.add_button.setText("Use Coins")
            self.add_button.setStyleSheet(f"""
                QPushButton {{
                    background-color: {self.palette_colors['danger']};
                    color: {'#111827' if self.palette_colors is LIGHT else '#0b1220'};
                    font-weight: bold;
                    border-radius: 8px;
                    padding: 10px 20px;
                }}
                QPushButton:hover {{
                    background-color: {self.palette_colors['dangerDark']};
                }}
            """)
        else:
            self.add_button.setText("Add Transaction")
            self.add_button.setStyleSheet(self.success_btn_style())

    def quick_add(self, delta):
        # Put delta into the field or add to current field value if present
        cur = self.amount_input.text().strip()
        if cur:
            try:
                val = int(cur)
            except ValueError:
                val = 0
        else:
            val = 0
        val += delta
        self.amount_input.setText(str(val))

    def add_coins(self):
        amount_text = self.amount_input.text()
        if not amount_text:
            QMessageBox.warning(self, "Input Error", "Please enter an amount")
            return

        try:
            amount = int(amount_text)
        except ValueError:
            QMessageBox.warning(self, "Input Error", "Please enter a valid number")
            return

        source = self.source_combo.currentText()
        if source == "Box Draws":
            if amount <= 0:
                QMessageBox.warning(self, "Input Error", "For Box Draws, enter a positive number of coins to use")
                return
            amount = -amount

        if self.tracker.add_coins(amount, source):
            self.update_balance()
            self.load_transactions()
            self.apply_filters()
            self.amount_input.clear()
            self.source_combo.setCurrentIndex(0)
            self.update_goal_progress()

            if amount > 0:
                self.statusBar().showMessage(f"Added {amount} coins from {source}", 3000)
            else:
                self.statusBar().showMessage(f"Used {-amount} coins for {source}", 3000)
        else:
            QMessageBox.warning(self, "Input Error", "Amount cannot be zero")

    def update_balance(self):
        balance = self.tracker.get_balance()
        self.balance_label.setText(f"{balance} coins")

        # Color hint by balance
        if balance == 0:
            color = self.palette_colors["danger"]
        elif balance < 50:
            color = self.palette_colors["warning"]
        else:
            color = self.palette_colors["success"]
        self.balance_label.setStyleSheet(f"color: {color};")

    # ---------- Goal

    def set_goal_clicked(self):
        text = self.goal_input.text().strip()
        if text == "":
            self.tracker.set_goal(0)
        else:
            try:
                goal = int(text)
            except ValueError:
                QMessageBox.warning(self, "Goal", "Enter a valid integer goal.")
                return
            self.tracker.set_goal(goal)
        self.update_goal_progress()
        self.statusBar().showMessage("Goal updated.", 2500)

    def update_goal_progress(self):
        goal = self.tracker.get_goal()
        bal = self.tracker.get_balance()
        if goal <= 0:
            self.goal_progress.setMaximum(100)
            self.goal_progress.setValue(0)
            self.goal_progress.setFormat("No goal set")
            return
        pct = max(0, min(100, int((bal / goal) * 100)))
        self.goal_progress.setMaximum(100)
        self.goal_progress.setValue(pct)
        self.goal_progress.setFormat(f"{pct}% of {goal}")

    # ---------- History / Filters

    def reset_filters(self):
        self.search_input.clear()
        self.source_filter.setCurrentIndex(0)  # All
        self.amount_type_filter.setCurrentIndex(0)  # All
        self.start_date.setDate(QDate.currentDate().addMonths(-1))
        self.end_date.setDate(QDate.currentDate())
        self.apply_filters()

    def load_transactions(self):
        transactions = self.tracker.get_transaction_history()
        # Sort newest first before populating; sorting is also enabled in the table
        transactions.sort(key=lambda x: x['date'], reverse=True)

        self.history_table.setRowCount(len(transactions))
        for row, t in enumerate(transactions):
            dt = datetime.fromisoformat(t['date'])
            date_str = dt.strftime("%Y-%m-%d %H:%M")
            amount = t['amount']

            date_item = QTableWidgetItem(date_str)
            # set a sortable key (QDateTime) for proper date sorting
            date_item.setData(Qt.UserRole, QDateTime(dt))

            if amount >= 0:
                amount_text = f"+{amount}"
                color = self.palette_colors["success"]
            else:
                amount_text = f"{amount}"
                color = self.palette_colors["danger"]

            amount_item = QTableWidgetItem(amount_text)
            amount_item.setData(Qt.UserRole, amount)  # for numeric sort
            amount_item.setForeground(QColor(color))

            balance_item = QTableWidgetItem(str(t['previous_balance']))
            balance_item.setData(Qt.UserRole, int(t['previous_balance']))

            source_item = QTableWidgetItem(t['source'])

            for item in [date_item, amount_item, balance_item, source_item]:
                item.setTextAlignment(Qt.AlignCenter)

            self.history_table.setItem(row, 0, date_item)
            self.history_table.setItem(row, 1, amount_item)
            self.history_table.setItem(row, 2, balance_item)
            self.history_table.setItem(row, 3, source_item)

    def apply_filters(self):
        # Rebuild visible table from transactions with filters
        txs = self.tracker.get_transaction_history()

        # Gather filter values
        q = self.search_input.text().strip().lower()
        src = self.source_filter.currentText()
        typ = self.amount_type_filter.currentText()

        start = self.start_date.date().toPyDate()
        end = self.end_date.date().toPyDate()

        filtered = []
        for t in txs:
            dt = datetime.fromisoformat(t['date'])
            d = dt.date()
            if d < start or d > end:
                continue
            if src != "All" and t['source'] != src:
                continue
            if typ == "Earnings" and t['amount'] < 0:
                continue
            if typ == "Spending" and t['amount'] >= 0:
                continue
            if q and q not in t['source'].lower():
                continue
            filtered.append(t)

        # sort newest first
        filtered.sort(key=lambda x: x['date'], reverse=True)
        self.history_table.setRowCount(len(filtered))
        for row, t in enumerate(filtered):
            dt = datetime.fromisoformat(t['date'])
            date_str = dt.strftime("%Y-%m-%d %H:%M")
            amount = t['amount']

            date_item = QTableWidgetItem(date_str)
            date_item.setData(Qt.UserRole, QDateTime(dt))

            if amount >= 0:
                amount_text = f"+{amount}"
                color = self.palette_colors["success"]
            else:
                amount_text = f"{amount}"
                color = self.palette_colors["danger"]

            amount_item = QTableWidgetItem(amount_text)
            amount_item.setData(Qt.UserRole, amount)
            amount_item.setForeground(QColor(color))

            balance_item = QTableWidgetItem(str(t['previous_balance']))
            balance_item.setData(Qt.UserRole, int(t['previous_balance']))

            source_item = QTableWidgetItem(t['source'])

            for item in [date_item, amount_item, balance_item, source_item]:
                item.setTextAlignment(Qt.AlignCenter)

            self.history_table.setItem(row, 0, date_item)
            self.history_table.setItem(row, 1, amount_item)
            self.history_table.setItem(row, 2, balance_item)
            self.history_table.setItem(row, 3, source_item)

    # ---------- Import/Export

    def export_data(self):
        file_path, _ = QFileDialog.getSaveFileName(
            self, "Export Coin Data", "", "JSON Files (*.json)"
        )
        if file_path:
            if self.tracker.export_data(file_path):
                self.statusBar().showMessage(f"Data exported to {file_path}", 5000)
                QMessageBox.information(self, "Export Successful", "Coin data exported successfully!")
            else:
                QMessageBox.warning(self, "Export Failed", "Could not export data")

    def import_data(self):
        file_path, _ = QFileDialog.getOpenFileName(
            self, "Import Coin Data", "", "JSON Files (*.json)"
        )
        if file_path:
            if self.tracker.import_data(file_path):
                self.update_balance()
                self.load_transactions()
                self.apply_filters()
                self.statusBar().showMessage(f"Data imported from {file_path}", 5000)
                QMessageBox.information(self, "Import Successful", "Coin data imported successfully!")
            else:
                QMessageBox.warning(self, "Import Failed", "Could not import data")


# ---------------------------
# App Bootstrap
# ---------------------------

if __name__ == "__main__":
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    app.setFont(QFont("Segoe UI", 10))
    window = MainWindow()
    window.setGeometry(200, 100, 1200, 950)
    window.show()
    sys.exit(app.exec_())