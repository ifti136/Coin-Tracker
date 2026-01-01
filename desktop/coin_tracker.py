import sys
import json
import os
import uuid
from datetime import datetime, date, timedelta
from collections import defaultdict

from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QLineEdit, QComboBox, QTableWidget,
    QTableWidgetItem, QHeaderView, QMessageBox, QFrame, QFileDialog,
    QInputDialog, QDialog, QTabWidget, QDateEdit, QProgressBar,
    QStackedWidget, QGridLayout, QScrollArea, QMenu, QGraphicsOpacityEffect,
    QDialogButtonBox # Added for QuickActionsDialog
)
from PyQt5.QtCore import Qt, QSize, QDate, QDateTime, QTimer, QPropertyAnimation, QRect, QEasingCurve
from PyQt5.QtGui import QFont, QColor, QIcon, QPixmap, QIntValidator, QPainter, QPen, QBrush, QRadialGradient

# Charts
try:
    from PyQt5.QtChart import (
        QChart, QChartView, QPieSeries, QBarSeries, QBarSet,
        QBarCategoryAxis, QValueAxis, QPieSlice, QLineSeries, QDateTimeAxis
    )
    QTCHART_AVAILABLE = True
except ImportError:
    QTCHART_AVAILABLE = False
    print("QtCharts not available - charts will be disabled")

# Firebase
try:
    import firebase_admin
    from firebase_admin import credentials, firestore
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False
    print("Firebase not available - using local storage only")

# --------------------------
# COLOR PALETTES (FIXED)
# --------------------------

LIGHT = {
    "bg": "#f8fafc",
    "card": "#ffffff",
    "border": "#e2e8f0",
    "text": "#0f172a", # Dark text for light bg
    "muted": "#64748b",
    "primary": "#3b82f6",
    "primaryDark": "#2563eb",
    "primaryLight": "#3b82f620",
    "success": "#10b981", # Green for success
    "successDark": "#059669",
    "danger": "#ef4444",
    "dangerDark": "#dc2626",
    "warning": "#f59e0b",
    "accent": "#8b5cf6",
    "tableHeader": "#f1f5f9",
    "sidebar": "#ffffff",
    "gradientStart": "#3b82f6",
    "gradientEnd": "#2563eb",
    "progressChunkLight": "#10b981", # Success color chunk
    "progressBgLight": "#d1fae5", # Light green background
    "balanceCardText": "#0f172a", # White text on gradient
}

DARK = {
    "bg": "#1a1d23",
    "card": "#252a33",
    "border": "#3a4152",
    "text": "#e2e8f0", # Light text for dark bg
    "muted": "#94a3b8",
    "primary": "#0096ff",
    "primaryDark": "#0077cc",
    "primaryLight": "#0096ff20",
    "success": "#34d399", # Lighter green for dark mode success
    "successDark": "#10b981",
    "danger": "#ff6b6b",
    "dangerDark": "#ff4757",
    "warning": "#ffd93d",
    "accent": "#a78bfa",
    "tableHeader": "#2d3440",
    "sidebar": "#1f232b",
    "gradientStart": "#0096ff",
    "gradientEnd": "#0077cc",
    # Use SUCCESS color for progress bar chunk in dark mode
    "progressChunkDark": "#34d399", # Success color chunk
    "progressBgDark": "#34d39930", # Semi-transparent success bg
    "balanceCardText": "#ffffff", # White text on gradient
}

def dt_now_iso():
    return datetime.now().isoformat()

# --------------------------
# DATA HANDLER
# --------------------------

class OnlineCoinTracker:
    def __init__(self, profile_name="Default", user_id="default_user"):
        self.profile_name = profile_name
        self.user_id = user_id
        self.db = None
        self.transactions = []
        self.settings = {
            "goal": 13500,
            "dark_mode": False,
            "quick_actions": [
                {"text": "Event Reward", "value": 50, "is_positive": True},
                {"text": "Ads", "value": 10, "is_positive": True},
                {"text": "Daily Games", "value": 100, "is_positive": True},
                {"text": "Login", "value": 50, "is_positive": True},
                {"text": "Campaign Reward", "value": 50, "is_positive": True},
                {"text": "Box Draw (Single Spin)", "value": 100, "is_positive": False},
                {"text": "Box Draw (10 Spins)", "value": 900, "is_positive": False}
            ]
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

    @staticmethod
    def get_last_active_profile(user_id="default_user"):
        """Reads the last active profile name from the database."""
        if FIREBASE_AVAILABLE and firebase_admin._apps:
            try:
                db = firestore.client()
                doc_ref = db.collection('users').document(user_id)
                doc = doc_ref.get()
                if doc.exists:
                    return doc.to_dict().get('last_active_profile', 'Default')
            except Exception as e:
                print(f"Error fetching last active profile: {e}")
        return 'Default'

    def set_last_active_profile(self):
        """Saves the current profile name as the last active one in the database."""
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                doc_ref.set({'last_active_profile': self.profile_name}, merge=True)
                print(f"Set last active profile to: {self.profile_name}")
            except Exception as e:
                print(f"Error setting last active profile: {e}")

    def validate_and_fix_data(self):
        valid_transactions = []
        needs_save = False
        loaded_settings = self.settings.copy() 

        default_quick_actions = [
            {"text": "Event Reward", "value": 50, "is_positive": True}, 
            {"text": "Ads", "value": 10, "is_positive": True},
            {"text": "Daily Games", "value": 100, "is_positive": True}, 
            {"text": "Login", "value": 50, "is_positive": True},
            {"text": "Campaign Reward", "value": 50, "is_positive": True},
            {"text": "Box Draw (Single Spin)", "value": 100, "is_positive": False},
            {"text": "Box Draw (10 Spins)", "value": 900, "is_positive": False} 
        ]
        if not isinstance(loaded_settings.get("quick_actions"), list):
             loaded_settings["quick_actions"] = default_quick_actions
             needs_save = True
        
        for i, transaction in enumerate(self.transactions):
            if not isinstance(transaction, dict):
                needs_save = True
                continue
            if 'id' not in transaction or not transaction['id']:
                transaction['id'] = str(uuid.uuid4())
                needs_save = True
            if not all(k in transaction for k in ['date', 'amount', 'source']):
                needs_save = True
                continue
            try:
                transaction['amount'] = int(transaction['amount'])
            except (ValueError, TypeError):
                needs_save = True
                continue
            valid_transactions.append(transaction)

        self.transactions = valid_transactions
        if needs_save or self.settings != loaded_settings:
            self.settings = loaded_settings
            needs_save = True

        if needs_save:
            print("Data validated, saving...")
            self.save_data(recalculate=True)

    def recalculate_balances(self):
        self.transactions.sort(key=lambda x: x.get('date', ''))
        balance = 0
        for t in self.transactions:
            t['previous_balance'] = balance
            balance += t.get('amount', 0)

    def load_data(self):
        default_settings = self.settings.copy()
        loaded_settings = {}

        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                doc = doc_ref.get()
                if doc.exists:
                    data = doc.to_dict()
                    profiles_data = data.get('profiles', {})
                    profile_data = profiles_data.get(self.profile_name, {})
                    self.transactions = profile_data.get('transactions', [])
                    loaded_settings = profile_data.get('settings', {})
            except Exception as e:
                print(f"Online load error for profile '{self.profile_name}': {e}")
                self.load_local_data() 
        else:
            self.load_local_data() 

        default_settings.update(loaded_settings)
        self.settings = default_settings
        
        self.validate_and_fix_data()

    def save_data(self, recalculate=True):
        if recalculate:
            self.recalculate_balances()

        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                doc = doc_ref.get()

                profiles_data = doc.to_dict().get('profiles', {}) if doc.exists else {}

                profile_data = {
                    'transactions': self.transactions,
                    'settings': self.settings,
                    'last_updated': dt_now_iso()
                }

                profiles_data[self.profile_name] = profile_data
                doc_ref.set({'profiles': profiles_data, 'last_updated': dt_now_iso()}, merge=True)
            except Exception as e:
                print(f"❌ Online save error for profile '{self.profile_name}': {e}")
                self.save_local_data(recalculate=False)
        else:
            self.save_local_data(recalculate=False)

    def load_local_data(self):
        default_settings = self.settings.copy()
        loaded_settings = {}
        data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        data_file = os.path.join(data_dir, f"{self.profile_name}.json")
        try:
            with open(data_file, 'r') as f:
                data = json.load(f)
                self.transactions = data.get('transactions', [])
                loaded_settings = data.get('settings', {})
        except (FileNotFoundError, json.JSONDecodeError):
            self.transactions = []
        
        default_settings.update(loaded_settings)
        self.settings = default_settings

    def save_local_data(self, recalculate=True):
        if recalculate:
            self.recalculate_balances()
        data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
        os.makedirs(data_dir, exist_ok=True)
        data_file = os.path.join(data_dir, f"{self.profile_name}.json")
        data = {
            "profile_name": self.profile_name,
            "last_updated": dt_now_iso(),
            "transactions": self.transactions,
            "settings": self.settings
        }
        try:
            with open(data_file, 'w') as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            print(f"Error saving data locally for profile '{self.profile_name}': {e}")

    def add_transaction(self, amount, source, date=None):
        if amount == 0: return False
        transaction = {"id": str(uuid.uuid4()), "date": date or dt_now_iso(), "amount": amount, "source": source}
        self.transactions.append(transaction)
        self.save_data(recalculate=True)
        return True

    def update_transaction(self, transaction_id, new_data):
        for t in self.transactions:
            if t.get('id') == transaction_id:
                t.update(new_data)
                self.save_data(recalculate=True)
                return True
        return False

    def delete_transaction(self, transaction_id):
        initial_len = len(self.transactions)
        self.transactions = [t for t in self.transactions if t.get('id') != transaction_id]
        if len(self.transactions) < initial_len:
            self.save_data(recalculate=True)
            return True
        return False

    def get_balance(self):
        if self.transactions:
            last_t = self.transactions[-1]
            return last_t.get('previous_balance', 0) + last_t.get('amount', 0)
        return 0

    def get_transaction_history(self):
        return self.transactions

    def get_source_breakdown(self):
        breakdown = defaultdict(int)
        for t in self.transactions:
            if t.get('amount', 0) > 0: breakdown[t['source']] += t['amount']
        return breakdown

    def get_spending_breakdown(self):
        breakdown = defaultdict(int)
        for t in self.transactions:
            if t.get('amount', 0) < 0: breakdown[t['source']] += abs(t['amount'])
        return breakdown

    def get_balance_timeline(self):
        timeline = []
        if not self.transactions: return timeline
        for t in self.transactions:
            try:
                timeline.append({'date': datetime.fromisoformat(t['date']), 'balance': t.get('previous_balance', 0) + t.get('amount', 0)})
            except ValueError:
                 print(f"Skipping timeline point due to invalid date: {t.get('date')}")
        return timeline

    def export_data(self, file_path):
        try:
            with open(file_path, 'w') as f:
                json.dump({'transactions': self.transactions, 'settings': self.settings}, f, indent=2)
            return True
        except Exception as e: print(f"Export error: {e}"); return False

    def import_data(self, file_path):
        try:
            with open(file_path, 'r') as f:
                data = json.load(f)
                self.transactions = data.get('transactions', [])
                self.settings.update(data.get('settings', {}))
                self.validate_and_fix_data()
            return True
        except Exception as e: print(f"Import error: {e}"); return False

    def set_goal(self, goal_value: int):
        self.settings["goal"] = max(0, int(goal_value))
        self.save_data(recalculate=False)

    def get_goal(self) -> int:
        return int(self.settings.get("goal", 13500))

    def set_dark_mode(self, enabled: bool):
        self.settings["dark_mode"] = bool(enabled)
        self.save_data(recalculate=False)

    def get_dark_mode(self) -> bool:
        return bool(self.settings.get("dark_mode", False))

    @staticmethod
    def get_profile_names(user_id="default_user"):
        profiles = ['Default']
        if FIREBASE_AVAILABLE and firebase_admin._apps:
            try:
                db = firestore.client()
                if not db: raise Exception("Firebase client not available")
                doc = db.collection('users').document(user_id).get()
                if doc.exists:
                    profiles.extend([p for p in doc.to_dict().get('profiles', {}).keys() if p != 'Default'])
            except Exception as e: print(f"Firebase profiles error: {e}")
        try:
            data_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker')
            if os.path.exists(data_dir):
                local_profiles = [f[:-5] for f in os.listdir(data_dir) if f.endswith('.json')]
                for lp in local_profiles:
                    if lp not in profiles: profiles.append(lp)
        except Exception as e: print(f"Error reading local profiles: {e}")
        return sorted(list(set(profiles)))

# --------------------------
# CUSTOM WIDGETS
# --------------------------

# ... (ToastNotification remains the same) ...
class ToastNotification(QFrame):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFrameShape(QFrame.StyledPanel)
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.Tool)
        self.setAttribute(Qt.WA_TranslucentBackground)
        self.setAttribute(Qt.WA_DeleteOnClose)

        self.layout = QHBoxLayout(self)
        self.layout.setContentsMargins(15, 10, 15, 10)

        self.icon_label = QLabel()
        self.text_label = QLabel()
        self.layout.addWidget(self.icon_label)
        self.layout.addWidget(self.text_label)

        self.opacity_effect = QGraphicsOpacityEffect(self)
        self.setGraphicsEffect(self.opacity_effect)
        self.opacity_anim = QPropertyAnimation(self.opacity_effect, b"opacity")
        self.timer = QTimer(self)
        self.timer.setSingleShot(True)
        self.timer.timeout.connect(self.fade_out)

    def set_theme(self, palette, type="success"):
        if type == "success":
            bg_color = palette['success']
            text_color = "#ffffff"
            self.icon_label.setText("✅")
        else:
            bg_color = palette['danger']
            text_color = "#ffffff"
            self.icon_label.setText("❌")

        self.setStyleSheet(f"""
            ToastNotification {{
                background-color: {bg_color};
                color: {text_color};
                border-radius: 8px;
            }}
            QLabel {{
                color: {text_color};
                font-size: 14px;
                background: transparent;
            }}
        """)

    def show_toast(self, message, type, palette):
        self.text_label.setText(message)
        self.set_theme(palette, type)
        self.adjustSize()

        parent_rect = self.parent().geometry()
        self.move(
            parent_rect.x() + (parent_rect.width() - self.width()) // 2,
            parent_rect.y() + parent_rect.height() - self.height() - 30
        )

        self.opacity_effect.setOpacity(0.0)
        self.show()

        self.opacity_anim.stop()
        self.opacity_anim.setDuration(300)
        self.opacity_anim.setStartValue(0.0)
        self.opacity_anim.setEndValue(0.95)
        self.opacity_anim.setEasingCurve(QEasingCurve.InOutQuad)
        self.opacity_anim.start()

        self.timer.start(2500)

    def fade_out(self):
        self.opacity_anim.stop()
        self.opacity_anim.setDuration(500)
        self.opacity_anim.setStartValue(self.opacity_effect.opacity())
        self.opacity_anim.setEndValue(0.0)
        self.opacity_anim.setEasingCurve(QEasingCurve.InOutQuad)
        try: self.opacity_anim.finished.disconnect()
        except TypeError: pass
        self.opacity_anim.finished.connect(self.close)
        self.opacity_anim.start()

    def closeEvent(self, event):
        self.deleteLater()
        super().closeEvent(event)


class ModernCard(QFrame):
    def __init__(self, palette, parent=None, has_border=True):
        super().__init__(parent)
        self.palette = palette
        self.has_border = has_border  # Store the border preference
        self.setFrameShape(QFrame.StyledPanel)
        self.apply_style()

    def setPalette(self, palette):
        self.palette = palette
        self.apply_style()
        self.update()

    def apply_style(self):
        # Conditionally add the border style based on the has_border flag
        border_style = f"border: 1px solid {self.palette['border']};" if self.has_border else "border: none;"
        self.setStyleSheet(f"""
            ModernCard {{
                background-color: {self.palette['card']};
                {border_style}
                border-radius: 12px;
            }}
        """)
        
class ModernQuickActionButton(QPushButton):
    def __init__(self, text, value, is_positive=True, palette=None, parent=None):
        super().__init__(parent)
        self.text_str = text
        self.value_str = f"+{value}" if is_positive else f"-{value}"
        self.is_positive = is_positive
        self.palette = palette or DARK
        self.setFixedSize(140, 80)
        self.setCursor(Qt.PointingHandCursor)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)

        rect = self.rect().adjusted(1, 1, -1, -1)

        if self.underMouse():
            bg_color = QColor(self.palette['primary'] + '20')
            border_color = QColor(self.palette['primary'])
        else:
            bg_color = QColor(self.palette['card'])
            border_color = QColor(self.palette['border'])

        painter.setBrush(bg_color)
        painter.setPen(QPen(border_color, 1))
        painter.drawRoundedRect(rect, 10, 10) # Slightly smaller radius

        painter.setFont(QFont("Segoe UI", 10, QFont.Medium)) # Slightly larger text
        painter.setPen(QColor(self.palette['text']))
        text_rect = QRect(0, 15, self.width(), 30) # Adjusted position
        painter.drawText(text_rect, Qt.AlignCenter | Qt.TextWordWrap, self.text_str)

        value_color = self.palette['success'] if self.is_positive else self.palette['danger']
        painter.setPen(QColor(value_color))
        painter.setFont(QFont("Segoe UI", 11, QFont.Bold)) # Larger value
        value_rect = QRect(0, 45, self.width(), 30) # Adjusted position
        painter.drawText(value_rect, Qt.AlignCenter, self.value_str)

    def setPalette(self, palette):
        self.palette = palette
        self.update()


class TransactionDialog(QDialog):
    def __init__(self, palette, transaction=None, parent=None):
        super().__init__(parent)
        self.transaction = transaction
        self.is_income = transaction['amount'] >= 0 if transaction else True

        title = "Edit Transaction" if transaction else ("Add Coins" if self.is_income else "Spend Coins")
        self.setWindowTitle(title)
        self.setModal(True)
        self.setMinimumWidth(400)

        self.layout = QVBoxLayout(self)
        self.layout.setSpacing(15)

        title_label = QLabel(title)
        title_label.setObjectName("DialogTitle")
        self.layout.addWidget(title_label)

        if not transaction:
            self.type_combo = QComboBox()
            self.type_combo.addItems(["💰 Income", "💸 Expense"])
            self.type_combo.setCurrentIndex(0 if self.is_income else 1)
            self.type_combo.currentIndexChanged.connect(self.toggle_type)
            self.layout.addWidget(self.type_combo)

        self.layout.addWidget(QLabel("Date & Time"))
        self.date_edit = QDateTimeEdit(self)
        self.date_edit.setDateTime(QDateTime.currentDateTime())
        self.date_edit.setCalendarPopup(True)
        self.date_edit.setFixedWidth(self.width() - 40)
        self.layout.addWidget(self.date_edit)

        self.layout.addWidget(QLabel("Amount"))
        self.amount_input = QLineEdit()
        self.amount_input.setValidator(QIntValidator(1, 1000000))
        self.amount_input.setPlaceholderText("e.g., 50")
        self.layout.addWidget(self.amount_input)

        self.source_label = QLabel("Source")
        self.layout.addWidget(self.source_label)
        self.source_combo = QComboBox()
        self.source_combo.setEditable(True)
        self.layout.addWidget(self.source_combo)

        button_layout = QHBoxLayout()
        self.cancel_btn = QPushButton("Cancel")
        self.cancel_btn.clicked.connect(self.reject)
        self.submit_btn = QPushButton("Save Transaction")
        self.submit_btn.setObjectName("ModernPrimaryButton")
        self.submit_btn.clicked.connect(self.validate_and_accept)

        button_layout.addStretch()
        button_layout.addWidget(self.cancel_btn)
        button_layout.addWidget(self.submit_btn)
        self.layout.addLayout(button_layout)

        self.toggle_type(0 if self.is_income else 1)

        if transaction:
            self.set_data(transaction)
            if hasattr(self, 'type_combo'):
                self.type_combo.setEnabled(False)

        self.setStyleSheet(self.get_stylesheet(palette))

    def get_stylesheet(self, p):
        parent_palette = self.parent().palette_colors if self.parent() else LIGHT
        return f"""
            QDialog {{ background-color: {p['card']}; border: 1px solid {p['border']}; border-radius: 12px; }}
            QLabel {{ color: {p['text']}; font-size: 14px; background: transparent; }}
            #DialogTitle {{ font-size: 20px; font-weight: 600; }}
            QLineEdit, QComboBox, QDateTimeEdit {{
                padding: 10px; border: 1px solid {p['border']}; border-radius: 8px;
                background-color: {p['bg']}; color: {p['text']}; font-size: 14px;
            }}
            QLineEdit:focus, QComboBox:focus, QDateTimeEdit:focus {{ border-color: {p['primary']}; }}
            QPushButton {{
                padding: 10px 16px; border: 1px solid {p['border']}; border-radius: 8px;
                font-weight: 500; background-color: {p['card']}; color: {p['text']};
            }}
            QPushButton:hover {{ background-color: {p['bg']}; }}
            #ModernPrimaryButton {{ background-color: {p['primary']}; color: white; border: none; }}
            #ModernPrimaryButton:hover {{ background-color: {p['primaryDark']}; }}
            QComboBox QAbstractItemView {{
                 background-color: {parent_palette.get('card', '#ffffff')}; /* Safe access */
                 color: {parent_palette.get('text', '#000000')};
                 selection-background-color: {parent_palette.get('primary', '#3b82f6')};
                 border: 1px solid {parent_palette.get('border', '#e2e8f0')};
             }}
        """

    def toggle_type(self, index):
        self.is_income = (index == 0)
        self.source_combo.clear()
        if self.is_income:
            self.source_label.setText("Source")
            common_sources = ["Event Reward", "Login", "Daily Games", "Achievements", "Ads", "Campaign Reward", "Other"]
            self.source_combo.addItems(common_sources)
        else:
            self.source_label.setText("Category")
            common_categories = ["Box Draw", "Store Purchase", "Pack Purchase", "Manager Purchase" "Other"]
            self.source_combo.addItems(common_categories)
        self.update_combo_style() # Update style after items are added

    def update_combo_style(self):
         p = self.parent().palette_colors if self.parent() else LIGHT # Fallback
         self.source_combo.setStyleSheet(f"""
             QComboBox QAbstractItemView {{
                 background-color: {p.get('card', '#ffffff')};
                 color: {p.get('text', '#000000')};
                 selection-background-color: {p.get('primary', '#3b82f6')};
                 border: 1px solid {p.get('border', '#e2e8f0')};
                 padding: 5px;
             }}
         """)

    def set_data(self, t):
        self.amount_input.setText(str(abs(t['amount'])))
        self.source_combo.setCurrentText(t['source'])
        try:
            self.date_edit.setDateTime(datetime.fromisoformat(t['date']))
        except ValueError:
             print(f"Warning: Could not parse date {t['date']} for editing.")
             self.date_edit.setDateTime(QDateTime.currentDateTime())

    def validate_and_accept(self):
        amount_text = self.amount_input.text()
        source_text = self.source_combo.currentText()
        if not amount_text:
             self.show_validation_error("Amount cannot be empty.")
             return
        try:
             amount_val = int(amount_text)
             if amount_val <= 0:
                  self.show_validation_error("Amount must be a positive number.")
                  return
        except ValueError:
             self.show_validation_error("Please enter a valid number for amount.")
             return

        if not source_text.strip():
             self.show_validation_error("Please enter or select a source/category.")
             return
        self.accept()

    def show_validation_error(self, message):
         if hasattr(self.parent(), 'show_toast'):
              self.parent().show_toast(message, "error")
         else:
              QMessageBox.warning(self, "Input Error", message)


    def get_transaction_data(self):
        amount = int(self.amount_input.text())
        if not self.is_income:
            amount = -amount
        return {
            'amount': amount,
            'source': self.source_combo.currentText().strip(),
            'date': self.date_edit.dateTime().toString(Qt.ISODateWithMs)
        }

# --------------------------
# QUICK ACTIONS DIALOG (IMPLEMENTED - No Icon)
# --------------------------
class QuickActionsDialog(QDialog):
    def __init__(self, current_actions, palette, parent=None):
        super().__init__(parent)
        self.current_actions = [a.copy() for a in current_actions]
        self.palette = palette
        self.setWindowTitle("Customize Quick Actions")
        self.setModal(True)
        self.setMinimumSize(550, 400) # Adjusted size

        self.layout = QVBoxLayout(self)

        title = QLabel("Customize Quick Actions")
        title.setObjectName("DialogTitle")
        self.layout.addWidget(title)

        self.table = QTableWidget()
        self.table.setColumnCount(4) # Text, Value, Type, Actions (No Icon)
        self.table.setHorizontalHeaderLabels(["Text", "Amount", "Type", ""])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeToContents) # Actions column
        self.table.verticalHeader().setVisible(False)
        self.layout.addWidget(self.table)

        self.populate_table()

        add_layout = QHBoxLayout()
        self.new_text = QLineEdit(placeholderText="Button Text")
        self.new_value = QLineEdit(placeholderText="Amount")
        self.new_value.setValidator(QIntValidator(1, 99999))
        self.new_type = QComboBox()
        self.new_type.addItems(["💰 Income", "💸 Expense"])
        add_btn = QPushButton("Add Action")
        add_btn.setObjectName("ModernPrimaryButton")
        add_btn.clicked.connect(self.add_new_action)

        add_layout.addWidget(self.new_text)
        add_layout.addWidget(self.new_value)
        add_layout.addWidget(self.new_type)
        add_layout.addWidget(add_btn)
        self.layout.addLayout(add_layout)

        self.button_box = QDialogButtonBox(QDialogButtonBox.Save | QDialogButtonBox.Cancel)
        self.button_box.accepted.connect(self.accept)
        self.button_box.rejected.connect(self.reject)
        self.layout.addWidget(self.button_box)

        self.setStyleSheet(self.get_dialog_style())

    def get_dialog_style(self):
         p = self.palette
         return f"""
             QDialog {{ background-color: {p['card']}; border: 1px solid {p['border']}; border-radius: 12px; }}
             QLabel {{ color: {p['text']}; font-size: 14px; background: transparent; }}
             #DialogTitle {{ font-size: 20px; font-weight: 600; }}
             QLineEdit, QComboBox {{
                 padding: 8px; border: 1px solid {p['border']}; border-radius: 6px;
                 background-color: {p['bg']}; color: {p['text']}; font-size: 13px;
             }}
             QPushButton {{
                 padding: 8px 14px; border: 1px solid {p['border']}; border-radius: 6px;
                 font-weight: 500; background-color: {p['card']}; color: {p['text']};
             }}
             QPushButton:hover {{ background-color: {p['bg']}; }}
             #ModernPrimaryButton {{ background-color: {p['primary']}; color: white; border: none; }}
             #ModernPrimaryButton:hover {{ background-color: {p['primaryDark']}; }}
             QTableWidget {{ background-color: {p['bg']}; border: 1px solid {p['border']}; gridline-color: {p['border']}; }}
             QHeaderView::section {{ background-color: {p['tableHeader']}; color: {p['muted']}; padding: 8px; border: none; font-weight: 600; text-transform: uppercase; font-size: 11px; }}
         """


    def populate_table(self):
        self.table.setRowCount(len(self.current_actions))
        for row, action in enumerate(self.current_actions):
            text = QTableWidgetItem(action.get('text', ''))
            value = QTableWidgetItem(str(action.get('value', '')))
            type_str = "💰 Income" if action.get('is_positive', True) else "💸 Expense"
            type_item = QTableWidgetItem(type_str)
            type_item.setTextAlignment(Qt.AlignCenter)

            self.table.setItem(row, 0, text)
            self.table.setItem(row, 1, value)
            self.table.setItem(row, 2, type_item)

            delete_btn = QPushButton("🗑️")
            delete_btn.setToolTip("Delete Action")
            delete_btn.setStyleSheet(f"border: none; background: transparent; color: {self.palette.get('danger', '#ef4444')}; font-size: 16px;")
            delete_btn.setCursor(Qt.PointingHandCursor)
            delete_btn.clicked.connect(lambda _, r=row: self.delete_action(r))
            cell_widget = QWidget()
            cell_layout = QHBoxLayout(cell_widget)
            cell_layout.addWidget(delete_btn)
            cell_layout.setAlignment(Qt.AlignCenter)
            cell_layout.setContentsMargins(0,0,0,0)
            self.table.setCellWidget(row, 3, cell_widget) # Column index 3


    def add_new_action(self):
        text = self.new_text.text().strip()
        value_str = self.new_value.text().strip()

        if not text or not value_str: # Check only text and value
            QMessageBox.warning(self, "Input Error", "Please fill in Text and Amount.")
            return

        try:
            value = int(value_str)
            if value <= 0: raise ValueError
        except ValueError:
            QMessageBox.warning(self, "Input Error", "Please enter a valid positive amount.")
            return

        is_positive = self.new_type.currentText() == "💰 Income"

        new_action = {"text": text, "value": value, "is_positive": is_positive}
        self.current_actions.append(new_action)
        self.populate_table() # Refresh table
        self.new_text.clear()
        self.new_value.clear()
        self.new_type.setCurrentIndex(0)


    def delete_action(self, row):
         if 0 <= row < len(self.current_actions):
             del self.current_actions[row]
             self.populate_table() # Refresh table


    def get_updated_actions(self):
         return self.current_actions


# --------------------------
# MAIN WINDOW (FIXED)
# --------------------------

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        # --- THIS INITIALIZATION LOGIC IS MODIFIED ---
        # 1. Get the primary profile from the database first
        primary_profile = OnlineCoinTracker.get_last_active_profile()

        # 2. Get all available profiles
        self.profiles = self.get_profile_names()
        
        # 3. Set current profile, with a fallback to 'Default'
        self.current_profile = primary_profile if primary_profile in self.profiles else "Default"
        
        # 4. Initialize the tracker with the determined profile
        self.tracker = OnlineCoinTracker(self.current_profile)
        # --- END OF MODIFIED LOGIC ---

        self.palette_colors = DARK if self.tracker.get_dark_mode() else LIGHT
        self.toast_widget = None

        self.setWindowTitle("Coin Tracker")
        self.setWindowIcon(self.create_modern_icon())
        self.setGeometry(100, 100, 1400, 900)
        self.setMinimumSize(1200, 800)

        central_widget = QWidget()
        central_widget.setObjectName("CentralWidget")
        self.setCentralWidget(central_widget)
        main_layout = QHBoxLayout(central_widget)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        self.sidebar = self.create_modern_sidebar()
        main_layout.addWidget(self.sidebar)

        self.stacked_widget = QStackedWidget()
        main_layout.addWidget(self.stacked_widget, 1)

        self.themed_widgets = []
        self.quick_action_buttons = []
        self.chart_views = {}

        self.dashboard_page = self.create_modern_dashboard()
        self.analytics_page = self.create_modern_analytics()
        self.history_page = self.create_modern_history()
        self.settings_page = self.create_modern_settings()

        self.stacked_widget.addWidget(self.dashboard_page)
        self.stacked_widget.addWidget(self.analytics_page)
        self.stacked_widget.addWidget(self.history_page)
        self.stacked_widget.addWidget(self.settings_page)

        self.update_all_data()
        self.apply_modern_theme()
        self.update_active_nav("Dashboard")

    @staticmethod
    def get_profile_names():
        return OnlineCoinTracker.get_profile_names()

    def create_modern_sidebar(self):
        sidebar = QFrame()
        sidebar.setFixedWidth(280)
        sidebar.setObjectName("ModernSidebar")

        layout = QVBoxLayout(sidebar)
        layout.setContentsMargins(20, 30, 20, 30)
        layout.setSpacing(25)

        logo_layout = QHBoxLayout()
        logo_icon = QLabel()
        logo_icon.setPixmap(self.create_modern_icon().pixmap(40, 40))
        logo_text = QLabel("Coin Tracker")
        logo_text.setObjectName("SidebarTitle")

        logo_layout.addWidget(logo_icon)
        logo_layout.addWidget(logo_text)
        layout.addLayout(logo_layout)

        layout.addSpacing(30)

        nav_items = {
            "Dashboard": ("📊", "Dashboard", self.show_dashboard),
            "Analytics": ("📈", "Analytics", self.show_analytics),
            "History": ("📋", "History", self.show_history),
            "Settings": ("⚙️", "Settings", self.show_settings)
        }

        self.nav_buttons = {}
        for name, (icon, text, callback) in nav_items.items():
            btn = self.create_modern_nav_button(icon, text)
            btn.setObjectName(name)
            btn.clicked.connect(callback)
            layout.addWidget(btn)
            self.nav_buttons[name] = btn

        layout.addStretch()

        profile_section = QFrame()
        profile_section.setObjectName("ProfileSection")
        profile_layout = QVBoxLayout(profile_section)
        profile_layout.setContentsMargins(15, 15, 15, 15)
        profile_layout.setSpacing(10)

        profile_label = QLabel("PROFILE")
        profile_label.setObjectName("MutedLabel")
        profile_layout.addWidget(profile_label)

        self.profile_combo = QComboBox()
        self.profile_combo.addItems(self.profiles)
        self.profile_combo.setCurrentText(self.current_profile)
        self.profile_combo.currentTextChanged.connect(self.change_profile)

        new_profile_btn = QPushButton("＋ New Profile")
        new_profile_btn.setObjectName("ModernSecondaryButton")
        new_profile_btn.clicked.connect(self.create_new_profile)

        profile_layout.addWidget(self.profile_combo)
        profile_layout.addWidget(new_profile_btn)
        layout.addWidget(profile_section)

        self.dark_toggle = QPushButton("🌙 Switch Mode")
        self.dark_toggle.setObjectName("ModernThemeToggle")
        self.dark_toggle.clicked.connect(self.toggle_dark_mode)
        layout.addWidget(self.dark_toggle)

        return sidebar
        
    def change_profile(self, profile_name):
        if not profile_name or profile_name == self.current_profile: return
        print(f"Changing profile to: {profile_name}")
        self.current_profile = profile_name
        self.tracker = OnlineCoinTracker(self.current_profile)
        
        # --- NEWLY ADDED LINE ---
        # Save the new profile choice to the database
        self.tracker.set_last_active_profile()
        # --- END OF NEW LINE ---
        
        self.palette_colors = DARK if self.tracker.get_dark_mode() else LIGHT
        self.rebuild_analytics_page()
        self.apply_modern_theme()
        self.update_all_data()
        self.update_modern_quick_actions()
        if hasattr(self, 'goal_input'):
            self.goal_input.setText(str(self.tracker.get_goal()))
        self.show_toast(f"Switched to profile: {profile_name}", "success")
        
    def create_modern_nav_button(self, icon, text):
        # ... (Nav button creation remains the same) ...
        btn = QPushButton()
        btn.setFixedHeight(50)
        btn.setObjectName("ModernNavButton")

        layout = QHBoxLayout(btn)
        layout.setContentsMargins(15, 0, 15, 0)
        layout.setSpacing(12)

        icon_label = QLabel(icon)
        icon_label.setObjectName("NavIcon")
        text_label = QLabel(text)
        text_label.setObjectName("NavText")

        layout.addWidget(icon_label)
        layout.addWidget(text_label)
        layout.addStretch()

        return btn


    def create_modern_dashboard(self):
        page = QScrollArea()
        page.setWidgetResizable(True)
        page.setObjectName("Dashboard")
        content = QWidget()
        page.setWidget(content)
        layout = QVBoxLayout(content)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(30)

        header_layout = QHBoxLayout()
        header = QLabel("Dashboard Overview")
        header.setObjectName("ModernPageTitle")
        header_layout.addWidget(header)
        header_layout.addStretch()
        stats_widget = self.create_quick_stats()
        header_layout.addWidget(stats_widget)
        layout.addLayout(header_layout)

        balance_card = ModernCard(self.palette_colors)
        balance_card.setObjectName("BalanceCard")
        self.themed_widgets.append(balance_card)
        balance_layout = QVBoxLayout(balance_card)
        balance_layout.setSpacing(15)

        progress_layout = QHBoxLayout()
        self.goal_label = QLabel("Goal: 13,500 coins")
        self.goal_label.setObjectName("GoalLabel")
        progress_layout.addWidget(self.goal_label)
        progress_layout.addStretch()
        self.progress_percent = QLabel("0%")
        self.progress_percent.setObjectName("ProgressPercent")
        progress_layout.addWidget(self.progress_percent)

        balance_title = QLabel("Current Balance")
        balance_title.setObjectName("BalanceTitle")
        self.balance_label = QLabel("0 coins")
        self.balance_label.setObjectName("BalanceLabel")
        self.goal_progress = QProgressBar()
        self.goal_progress.setTextVisible(False) # Keep text hidden on bar itself
        self.goal_progress.setFixedHeight(12)

        balance_layout.addLayout(progress_layout)
        balance_layout.addWidget(balance_title)
        balance_layout.addWidget(self.balance_label)
        balance_layout.addWidget(self.goal_progress)
        layout.addWidget(balance_card)

        actions_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(actions_card)
        actions_layout = QVBoxLayout(actions_card)
        actions_layout.setSpacing(15)
        actions_header = QHBoxLayout()
        actions_title = QLabel("Quick Actions")
        actions_title.setObjectName("ModernCardTitle")
        actions_header.addWidget(actions_title)
        actions_header.addStretch()
        edit_actions_btn = QPushButton("✎ Customize")
        edit_actions_btn.setObjectName("ModernSecondaryButton")
        edit_actions_btn.setFixedSize(100, 30)
        edit_actions_btn.clicked.connect(self.edit_quick_actions)
        actions_header.addWidget(edit_actions_btn)
        actions_layout.addLayout(actions_header)

        self.actions_grid = QGridLayout()
        self.actions_grid.setSpacing(15)
        self.actions_grid.setContentsMargins(10, 10, 10, 10)
        self.update_modern_quick_actions()
        actions_layout.addLayout(self.actions_grid)
        layout.addWidget(actions_card)

        # --- Reverted Transaction Input ---
        transaction_row = QHBoxLayout()
        transaction_row.setSpacing(25)

        # Use helper to create input cards instead of action cards
        add_card = self.create_transaction_input_card("Add Coins", True)
        spend_card = self.create_transaction_input_card("Spend Coins", False)

        transaction_row.addWidget(add_card)
        transaction_row.addWidget(spend_card)
        layout.addLayout(transaction_row)
        # --- End Reverted Transaction Input ---

        recent_card = self.create_recent_transactions_card()
        layout.addWidget(recent_card)
        layout.addStretch()
        return page

    # --- New Helper for Transaction Input Cards ---
    def create_transaction_input_card(self, title_text, is_add):
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(10)

        title = QLabel(title_text)
        title.setObjectName("ModernCardTitle")
        layout.addWidget(title)

        layout.addWidget(QLabel("Amount"))
        amount_input = QLineEdit(placeholderText="e.g., 50")
        amount_input.setValidator(QIntValidator(1, 1000000))
        layout.addWidget(amount_input)

        layout.addWidget(QLabel("Source" if is_add else "Category"))
        source_combo = QComboBox()
        source_combo.setEditable(True)
        if is_add:
            source_combo.addItems(["Event Reward", "Login", "Daily Games", "Achievements", "Ads", "Campaign Reward", "Other"])
        else:
            source_combo.addItems(["Box Draw", "Store Purchase", "Pack Purchase", "Manager Purchase" "Other"])
        layout.addWidget(source_combo)

        layout.addStretch() # Push button to bottom

        button_text = "Add Coins" if is_add else "Spend Coins"
        button_name = "ModernPrimaryButton" if is_add else "ModernDangerButton"
        btn = QPushButton(button_text)
        btn.setObjectName(button_name)
        btn.setFixedHeight(35)

        # Store references and connect signals
        if is_add:
            self.add_amount_input = amount_input
            self.add_source_combo = source_combo
            btn.clicked.connect(self.add_coins_from_dashboard)
        else:
            self.spend_amount_input = amount_input
            self.spend_source_combo = source_combo
            btn.clicked.connect(self.spend_coins_from_dashboard)

        layout.addWidget(btn)
        return card

    # --- New methods to handle dashboard inputs ---
    def add_coins_from_dashboard(self):
        amount_text = self.add_amount_input.text()
        source = self.add_source_combo.currentText().strip()
        try:
            amount = int(amount_text)
            if amount <= 0: raise ValueError
            if not source:
                 self.show_toast("Please select or enter a source.", "error")
                 return

            if self.tracker.add_transaction(amount, source):
                self.show_toast(f"Added {amount:,} coins", "success")
                self.add_amount_input.clear() # Clear input on success
                self.update_all_data()
            else:
                 self.show_toast("Failed to add transaction.", "error")
        except ValueError:
            self.show_toast("Please enter a valid positive amount.", "error")

    def spend_coins_from_dashboard(self):
        amount_text = self.spend_amount_input.text()
        source = self.spend_source_combo.currentText().strip()
        try:
            amount = int(amount_text)
            if amount <= 0: raise ValueError
            if not source:
                 self.show_toast("Please select or enter a category.", "error")
                 return

            if self.tracker.add_transaction(-amount, source): # Spend is negative amount
                self.show_toast(f"Spent {amount:,} coins", "success")
                self.spend_amount_input.clear() # Clear input on success
                self.update_all_data()
            else:
                 self.show_toast("Failed to record spending.", "error")
        except ValueError:
            self.show_toast("Please enter a valid positive amount.", "error")


    def create_quick_stats(self):
        # ... (Quick stats creation remains the same) ...
        stats = QFrame()
        stats.setObjectName("QuickStats")
        layout = QHBoxLayout(stats)
        layout.setSpacing(20)
        self.today_stat = self.create_mini_stat("Today", "+0", "success")
        self.week_stat = self.create_mini_stat("This Week", "+0", "primary")
        self.month_stat = self.create_mini_stat("This Month", "+0", "accent")
        layout.addWidget(self.today_stat)
        layout.addWidget(self.week_stat)
        layout.addWidget(self.month_stat)
        return stats


    def create_mini_stat(self, label, value, color_name):
        # ... (Mini stat creation remains the same) ...
        widget = QFrame()
        widget.setObjectName("MiniStat")
        layout = QVBoxLayout(widget)
        layout.setAlignment(Qt.AlignCenter)
        value_label = QLabel(value)
        value_label.setObjectName(f"MiniStatValue{color_name.capitalize()}")
        label_label = QLabel(label)
        label_label.setObjectName("MiniStatLabel")
        layout.addWidget(value_label)
        layout.addWidget(label_label)
        return widget


    def create_modern_action_card(self, icon, title, description, button_type, callback):
        # ... (Action card creation remains the same - now used less) ...
        card = ModernCard(self.palette_colors)
        card.setObjectName("ActionCard")
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(10)
        header_layout = QHBoxLayout()
        icon_label = QLabel(icon)
        icon_label.setObjectName("ActionIcon")
        title_label = QLabel(title)
        title_label.setObjectName("ModernCardTitle")
        header_layout.addWidget(icon_label)
        header_layout.addWidget(title_label)
        header_layout.addStretch()
        layout.addLayout(header_layout)
        desc_label = QLabel(description)
        desc_label.setObjectName("MutedLabel")
        layout.addWidget(desc_label)
        layout.addStretch()
        btn_name = f"Modern{button_type.capitalize()}Button"
        btn = QPushButton(title)
        btn.setObjectName(btn_name)
        btn.setFixedHeight(35)
        btn.clicked.connect(callback)
        layout.addWidget(btn)
        return card


    def create_recent_transactions_card(self):
        # ... (Recent transactions card remains the same) ...
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(15)
        header = QLabel("Recent Transactions")
        header.setObjectName("ModernCardTitle")
        layout.addWidget(header)
        self.recent_table = QTableWidget()
        self.recent_table.setObjectName("RecentHistoryTable")
        self.recent_table.setColumnCount(3)
        self.recent_table.setHorizontalHeaderLabels(["Time", "Amount", "Source"])
        self.recent_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.recent_table.verticalHeader().setVisible(False)
        self.recent_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.recent_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.recent_table.setFixedHeight(150)
        self.recent_table.setFocusPolicy(Qt.NoFocus)
        layout.addWidget(self.recent_table)
        return card


    def update_modern_quick_actions(self):
        # ... (Update quick actions - adapted for no icon) ...
        for i in reversed(range(self.actions_grid.count())):
            widget = self.actions_grid.itemAt(i).widget()
            if widget:
                self.actions_grid.removeWidget(widget)
                widget.deleteLater()
        self.quick_action_buttons.clear()
        quick_actions = self.tracker.settings.get('quick_actions', [])
        for i, action in enumerate(quick_actions):
             # Ensure action is a dict and has the required keys
             if isinstance(action, dict) and all(k in action for k in ['text', 'value', 'is_positive']):
                 btn = ModernQuickActionButton( # No icon passed
                     action.get('text', 'Action'),
                     action.get('value', 0),
                     action.get('is_positive', True),
                     self.palette_colors
                 )
                 btn.clicked.connect(lambda ch, t=action.get('text'), v=action.get('value'), p=action.get('is_positive', True): self.quick_action(t, v, p))
                 self.actions_grid.addWidget(btn, i // 5, i % 5) # Adjust grid columns if needed
                 self.quick_action_buttons.append(btn)
             else:
                 print(f"Skipping invalid quick action item: {action}")


    def create_modern_analytics(self):
        # ... (Analytics page structure remains the same) ...
        page = QScrollArea()
        page.setWidgetResizable(True)
        page.setObjectName("Analytics")
        content = QWidget()
        page.setWidget(content)
        self.analytics_layout = QVBoxLayout(content)
        self.analytics_layout.setContentsMargins(40, 40, 40, 40)
        self.analytics_layout.setSpacing(30)
        title = QLabel("Analytics")
        title.setObjectName("ModernPageTitle")
        self.analytics_layout.addWidget(title)
        self.analytics_content_widget = QWidget()
        self.analytics_content_layout = QVBoxLayout(self.analytics_content_widget)
        self.analytics_layout.addWidget(self.analytics_content_widget)
        self.analytics_layout.addStretch()
        self.rebuild_analytics_page()
        return page


    def rebuild_analytics_page(self):
        # ... (Rebuild logic largely the same, ensures charts are created) ...
        while self.analytics_content_layout.count():
            item = self.analytics_content_layout.takeAt(0)
            widget = item.widget()
            if widget: widget.deleteLater()

        # Always try to clear chart views when rebuilding
        self.chart_views.clear()
        # Also clear stat label refs
        self.total_earnings_label = None
        self.total_spending_label = None
        self.net_balance_label = None


        if not self.tracker.transactions:
            empty_state = self.create_empty_analytics_state()
            self.analytics_content_layout.addWidget(empty_state)
            return

        # Build stats row
        stats_row = QHBoxLayout()
        stats_row.setSpacing(30)
        self.total_earnings_label = self.create_stat_card("Total Earnings", "+0", "success")
        self.total_spending_label = self.create_stat_card("Total Spending", "-0", "danger")
        self.net_balance_label = self.create_stat_card("Net Balance", "0", "primary")
        stats_row.addWidget(self.total_earnings_label.parentWidget())
        stats_row.addWidget(self.total_spending_label.parentWidget())
        stats_row.addWidget(self.net_balance_label.parentWidget())
        self.analytics_content_layout.addLayout(stats_row)

        # Build chart tabs
        charts_tabs = QTabWidget()

        if QTCHART_AVAILABLE:
            # Earnings Tab
            earnings_tab, donut_view = self.create_chart_tab("💰 Earnings", "Earnings Breakdown")
            if donut_view:
                 charts_tabs.addTab(earnings_tab, "💰 Earnings")
                 self.chart_views['donut'] = donut_view

            # Spending Tab
            spending_tab, bar_view = self.create_chart_tab("💸 Spending", "Spending Analysis")
            if bar_view:
                 charts_tabs.addTab(spending_tab, "💸 Spending")
                 self.chart_views['bar'] = bar_view

            # Timeline Tab
            timeline_tab, line_view = self.create_chart_tab("📈 Timeline", "Balance Timeline")
            if line_view:
                 charts_tabs.addTab(timeline_tab, "📈 Timeline")
                 self.chart_views['line'] = line_view

            if charts_tabs.count() > 0:
                 self.analytics_content_layout.addWidget(charts_tabs)
            else: # Safety check
                 no_chart_label = QLabel("Could not create chart views.")
                 self.analytics_content_layout.addWidget(no_chart_label)

        else: # Charts not available
            no_chart_label = QLabel("Charts require PyQtChart to be installed.")
            no_chart_label.setAlignment(Qt.AlignCenter)
            no_chart_label.setObjectName("MutedLabel") # Style as muted text
            self.analytics_content_layout.addWidget(no_chart_label)

        self.analytics_content_layout.addStretch()
        # Update charts and stats AFTER creating structure
        self.update_analytics_stats()
        self.update_all_charts()

    # Helper to create chart tabs consistently
    def create_chart_tab(self, tab_title, card_title):
         if not QTCHART_AVAILABLE: return None, None

         tab_widget = QWidget()
         layout = QVBoxLayout(tab_widget)
         layout.setContentsMargins(0, 0, 0, 0)
         
         card = ModernCard(self.palette_colors, has_border=False)
         card.setObjectName("ChartCard")
         self.themed_widgets.append(card)
         
         card_layout = QVBoxLayout(card)
         card_layout.addWidget(QLabel(card_title, objectName="ModernCardTitle"))

         chart = QChart()
         chart_view = QChartView(chart)
         chart_view.setRenderHint(QPainter.Antialiasing)
         chart_view.setMinimumHeight(400)
         
         # ✅ FIXED: Force the QChartView widget itself to be transparent
         chart_view.setStyleSheet("background-color: transparent;") 
         
         card_layout.addWidget(chart_view)
         layout.addWidget(card)
         return tab_widget, chart_view
     
    def create_empty_analytics_state(self):
        # ... (Styling improved for theme) ...
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setAlignment(Qt.AlignCenter)
        layout.setSpacing(20)

        icon = QLabel("📊")
        icon.setStyleSheet(f"font-size: 64px; background: transparent; color: {self.palette_colors['muted']};") # Use muted color
        icon.setAlignment(Qt.AlignCenter)

        title = QLabel("No Analytics Data Yet")
        title.setObjectName("ModernCardTitle") # Use card title style
        title.setAlignment(Qt.AlignCenter)
        title.setStyleSheet("background: transparent;")

        description = QLabel("Start adding transactions to see your earnings and spending analytics.")
        description.setObjectName("MutedLabel")
        description.setAlignment(Qt.AlignCenter)
        description.setWordWrap(True)
        description.setStyleSheet("background: transparent;")

        layout.addWidget(icon)
        layout.addWidget(title)
        layout.addWidget(description)
        return card


    def create_stat_card(self, title_text, initial_value, color_name):
        # ... (Stat card creation remains the same) ...
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(5)
        title_label = QLabel(title_text)
        title_label.setObjectName("MutedLabel")
        value_label = QLabel(initial_value)
        value_label.setObjectName(f"StatLabel{color_name.capitalize()}")
        layout.addWidget(title_label)
        layout.addWidget(value_label)
        return value_label


    def update_donut_chart(self):
        # ... (Chart update logic remains the same, relies on chart_views) ...
        if not QTCHART_AVAILABLE or 'donut' not in self.chart_views: return
        chart_view = self.chart_views['donut']
        chart = chart_view.chart()
        if not chart: return
        chart.removeAllSeries()
        chart.legend().setVisible(True)
        chart.legend().setAlignment(Qt.AlignBottom)
        breakdown = self.tracker.get_source_breakdown()
        if not breakdown:
            chart.setTitle("No earnings data available")
            self.apply_chart_theme(chart) # Apply theme even if empty
            return
        chart.setTitle("")
        series = QPieSeries()
        series.setHoleSize(0.4)
        colors = [self.palette_colors['primary'], self.palette_colors['success'], self.palette_colors['warning'], self.palette_colors['danger'], self.palette_colors['accent']]
        colors = [QColor(c) for c in colors]
        total = sum(breakdown.values())
        slices = []
        for i, (source, amount) in enumerate(breakdown.items()):
            percentage = (amount / total) * 100 if total > 0 else 0
            slice_label = f"{source} ({percentage:.0f}%)"
            pie_slice = QPieSlice(slice_label, amount)
            pie_slice.setColor(colors[i % len(colors)])
            pie_slice.setLabelVisible(True)
            pie_slice.setLabelBrush(QColor(self.palette_colors['text']))
            pie_slice.setLabelFont(QFont("Segoe UI", 9))
            slices.append(pie_slice)
        series.append(slices)
        chart.addSeries(series)
        self.apply_chart_theme(chart)

    def update_spending_bar_chart(self):
        # ... (Chart update logic remains the same, relies on chart_views) ...
        if not QTCHART_AVAILABLE or 'bar' not in self.chart_views: return
        chart_view = self.chart_views['bar']
        chart = chart_view.chart()
        if not chart: return
        chart.removeAllSeries()
        for axis in chart.axes(): chart.removeAxis(axis)
        chart.legend().setVisible(False)
        breakdown = self.tracker.get_spending_breakdown()
        if not breakdown:
            chart.setTitle("No spending data available")
            self.apply_chart_theme(chart)
            return
        chart.setTitle("")
        series = QBarSeries()
        bar_set = QBarSet("Spending")
        categories = []
        max_val = 0
        amounts = []
        for category, amount in breakdown.items():
            amounts.append(amount)
            categories.append(category)
            max_val = max(max_val, amount)
        bar_set.append(amounts)
        bar_set.setColor(QColor(self.palette_colors['danger']))
        series.append(bar_set)
        chart.addSeries(series)
        axis_x = QBarCategoryAxis()
        axis_x.append(categories)
        chart.addAxis(axis_x, Qt.AlignBottom)
        series.attachAxis(axis_x)
        axis_y = QValueAxis()
        axis_y.setRange(0, max(10, max_val * 1.1))
        axis_y.setLabelFormat("%d")
        chart.addAxis(axis_y, Qt.AlignLeft)
        series.attachAxis(axis_y)
        self.apply_chart_theme(chart)


    def update_balance_line_chart(self):
        # ... (Chart update logic remains the same, relies on chart_views) ...
        if not QTCHART_AVAILABLE or 'line' not in self.chart_views: return
        chart_view = self.chart_views['line']
        chart = chart_view.chart()
        if not chart: return
        chart.removeAllSeries()
        for axis in chart.axes(): chart.removeAxis(axis)
        chart.legend().setVisible(False)
        timeline = self.tracker.get_balance_timeline()
        if not timeline:
            chart.setTitle("No timeline data available")
            self.apply_chart_theme(chart)
            return
        chart.setTitle("")
        series = QLineSeries()
        series.setName("Balance")
        series.setPen(QPen(QColor(self.palette_colors['primary']), 2))
        min_balance, max_balance = 0, 100
        if timeline:
            balances = [p['balance'] for p in timeline]
            min_balance = min(balances) if balances else 0 # Handle empty list
            max_balance = max(balances) if balances else 100 # Handle empty list

            for point in timeline:
                dt = point['date']
                balance = point['balance']
                series.append(dt.timestamp() * 1000, balance)
        chart.addSeries(series)
        axis_x = QDateTimeAxis()
        axis_x.setTickCount(min(len(timeline), 7))
        axis_x.setFormat("MMM dd")
        chart.addAxis(axis_x, Qt.AlignBottom)
        series.attachAxis(axis_x)
        axis_y = QValueAxis()
        y_min = min_balance * 0.95 if min_balance > 0 else min_balance * 1.05
        y_max = max_balance * 1.05 if max_balance > 0 else max_balance * 0.95
        # Prevent zero range
        if abs(y_min - y_max) < 1: y_min -= 50; y_max += 50
        if y_min == 0 and y_max == 0: y_max = 100
        axis_y.setRange(y_min, y_max)
        axis_y.setLabelFormat("%d")
        chart.addAxis(axis_y, Qt.AlignLeft)
        series.attachAxis(axis_y)
        self.apply_chart_theme(chart)


    def create_modern_history(self):
        # ... (History page creation remains the same) ...
        page = QWidget()
        page.setObjectName("History")
        layout = QVBoxLayout(page)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(20)
        title_layout = QHBoxLayout()
        title = QLabel("Transaction History")
        title.setObjectName("ModernPageTitle")
        title_layout.addWidget(title)
        title_layout.addStretch()
        self.period_summary = QLabel("Total: 0 coins")
        self.period_summary.setObjectName("MutedLabel")
        title_layout.addWidget(self.period_summary)
        layout.addLayout(title_layout)
        filter_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(filter_card)
        filter_layout = QGridLayout(filter_card)
        filter_layout.setSpacing(15)
        filter_layout.addWidget(QLabel("From:"), 0, 0)
        self.date_from = QDateEdit()
        self.date_from.setDate(QDate.currentDate().addMonths(-1))
        self.date_from.setCalendarPopup(True)
        filter_layout.addWidget(self.date_from, 0, 1)
        filter_layout.addWidget(QLabel("To:"), 0, 2)
        self.date_to = QDateEdit()
        self.date_to.setDate(QDate.currentDate())
        self.date_to.setCalendarPopup(True)
        filter_layout.addWidget(self.date_to, 0, 3)
        filter_layout.addWidget(QLabel("Source/Category:"), 1, 0)
        self.history_source_filter = QComboBox()
        filter_layout.addWidget(self.history_source_filter, 1, 1)
        self.history_search = QLineEdit(placeholderText="Search description or amount...")
        filter_layout.addWidget(self.history_search, 1, 2, 1, 2)
        filter_layout.setColumnStretch(1, 1)
        filter_layout.setColumnStretch(3, 1)
        layout.addWidget(filter_card)
        table_card = ModernCard(self.palette_colors)
        self.themed_widgets.append(table_card)
        table_layout = QVBoxLayout(table_card)
        self.history_table = QTableWidget()
        self.history_table.setColumnCount(6)
        self.history_table.setHorizontalHeaderLabels(["Date", "Type", "Source/Category", "Amount", "Balance After", "ID"])
        self.history_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.history_table.setColumnHidden(5, True)
        self.history_table.verticalHeader().setVisible(False)
        self.history_table.setEditTriggers(QTableWidget.NoEditTriggers)
        self.history_table.setSelectionBehavior(QTableWidget.SelectRows)
        self.history_table.setSelectionMode(QTableWidget.SingleSelection)
        self.history_table.setAlternatingRowColors(True)
        self.history_table.setContextMenuPolicy(Qt.CustomContextMenu)
        self.history_table.customContextMenuRequested.connect(self.show_history_context_menu)
        table_layout.addWidget(self.history_table)
        layout.addWidget(table_card)
        self.date_from.dateChanged.connect(self.filter_history)
        self.date_to.dateChanged.connect(self.filter_history)
        self.history_source_filter.currentTextChanged.connect(self.filter_history)
        self.history_search.textChanged.connect(self.filter_history)
        return page


    def create_modern_settings(self):
        # ... (Settings page creation remains the same) ...
        page = QScrollArea()
        page.setWidgetResizable(True)
        page.setObjectName("Settings")
        content = QWidget()
        page.setWidget(content)
        layout = QVBoxLayout(content)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(30)
        title = QLabel("Settings")
        title.setObjectName("ModernPageTitle")
        layout.addWidget(title)
        goal_card = self.create_settings_card("🎯 Goal Setting", "Set your coin collection target")
        goal_layout = goal_card.layout()
        current_goal_layout = QHBoxLayout()
        current_goal_layout.addWidget(QLabel("Current Goal:"))
        self.current_goal_display = QLabel("13,500 coins")
        current_goal_layout.addWidget(self.current_goal_display)
        current_goal_layout.addStretch()
        goal_layout.addLayout(current_goal_layout)
        goal_input_layout = QHBoxLayout()
        self.goal_input = QLineEdit(placeholderText="Enter new goal amount")
        self.goal_input.setValidator(QIntValidator(0, 10000000))
        set_goal_btn = QPushButton("Update Goal")
        set_goal_btn.setObjectName("ModernPrimaryButton")
        set_goal_btn.clicked.connect(self.set_goal_clicked)
        goal_input_layout.addWidget(self.goal_input, 1)
        goal_input_layout.addWidget(set_goal_btn)
        goal_layout.addLayout(goal_input_layout)
        self.goal_progress_label = QLabel("")
        self.goal_progress_label.setObjectName("MutedLabel")
        goal_layout.addWidget(self.goal_progress_label)
        layout.addWidget(goal_card)

        # Quick Actions Card - Added back
        quick_actions_card = self.create_settings_card("⚡ Quick Actions", "Manage your quick action buttons")
        quick_layout = quick_actions_card.layout()
        quick_actions_desc = QLabel("Customize the quick action buttons shown on your dashboard.")
        quick_layout.addWidget(quick_actions_desc)
        manage_actions_btn = QPushButton("Manage Quick Actions")
        manage_actions_btn.clicked.connect(self.edit_quick_actions) # Connect button
        quick_layout.addWidget(manage_actions_btn)
        layout.addWidget(quick_actions_card)

        data_card = self.create_settings_card("💾 Data Management", "Export, import, or backup your data")
        data_layout = data_card.layout()
        data_buttons_layout = QHBoxLayout()
        export_btn = QPushButton("📤 Export Data")
        import_btn = QPushButton("📥 Import Data")
        backup_btn = QPushButton("🛟 Create Backup")
        export_btn.clicked.connect(self.export_data)
        import_btn.clicked.connect(self.import_data)
        backup_btn.clicked.connect(self.create_backup)
        data_buttons_layout.addWidget(export_btn)
        data_buttons_layout.addWidget(import_btn)
        data_buttons_layout.addWidget(backup_btn)
        data_layout.addLayout(data_buttons_layout)
        layout.addWidget(data_card)

        online_card = self.create_settings_card("🌐 Online Sync", "Firebase connection status")
        online_layout = online_card.layout()
        status_layout = QHBoxLayout()
        self.status_icon = QLabel("...")
        self.status_text = QLabel("Checking connection...")
        status_layout.addWidget(self.status_icon)
        status_layout.addWidget(self.status_text)
        status_layout.addStretch()
        online_layout.addLayout(status_layout)
        self.update_online_status_display()
        layout.addWidget(online_card)
        layout.addStretch()
        return page


    def create_settings_card(self, title_text, desc_text):
        # ... (Settings card creation remains the same) ...
        card = ModernCard(self.palette_colors)
        self.themed_widgets.append(card)
        layout = QVBoxLayout(card)
        layout.setSpacing(10)
        title = QLabel(title_text)
        title.setObjectName("ModernCardTitle")
        desc = QLabel(desc_text)
        desc.setObjectName("MutedLabel")
        layout.addWidget(title)
        layout.addWidget(desc)
        return card


    # --------------------------
    # NAVIGATION & DATA METHODS
    # --------------------------

    def show_page(self, index, name):
        """Switches to the specified page index and updates UI."""
        self.stacked_widget.setCurrentIndex(index)
        self.update_active_nav(name)
        # Only rebuild analytics if necessary and charts are available
        if name == "Analytics":
            # Check if analytics page needs rebuild (e.g., if data was previously empty)
            # Rebuild only if the content widget is currently holding the empty state card
            current_content = self.analytics_content_layout.itemAt(0).widget() if self.analytics_content_layout.count() > 0 else None
            is_empty_state = isinstance(current_content, ModernCard) and current_content.findChild(QLabel, "ModernPageTitle") # Basic check
            
            if is_empty_state and self.tracker.transactions:
                 print("Analytics was empty, rebuilding...")
                 self.rebuild_analytics_page() # Rebuild fully if data is now available
            elif self.tracker.transactions:
                 print("Updating existing analytics...")
                 self.update_analytics_stats() # Just update stats
                 self.update_all_charts() # Just update existing charts
            elif not self.tracker.transactions and not is_empty_state:
                 print("Analytics has no data, showing empty state...")
                 self.rebuild_analytics_page() # Rebuild to show empty state

        elif name == "History":
            self.filter_history() # Ensure history is up-to-date
        elif name == "Dashboard":
            self.update_balance_and_goal()
            self.update_quick_stats()
            self.update_recent_transactions()
        elif name == "Settings":
             self.update_balance_and_goal() # Update goal display


    def show_dashboard(self): self.show_page(0, "Dashboard")
    def show_analytics(self): self.show_page(1, "Analytics")
    def show_history(self): self.show_page(2, "History")
    def show_settings(self): self.show_page(3, "Settings")


    def update_all_data(self):
        print("Updating all data...")
        self.update_balance_and_goal()
        self.filter_history()
        # Only update if analytics widgets exist and analytics page is built
        if hasattr(self, 'total_earnings_label') and self.total_earnings_label:
            self.update_analytics_stats()
        if QTCHART_AVAILABLE and self.chart_views: # Check if chart_views dict is populated
             self.update_all_charts()
        if hasattr(self, 'recent_table'):
            self.update_recent_transactions()
        if hasattr(self, 'today_stat'):
            self.update_quick_stats()
        if hasattr(self, 'status_icon'):
            self.update_online_status_display()


    # ... (update_online_status_display, update_quick_stats, update_recent_transactions remain the same) ...
    def update_online_status_display(self):
         if hasattr(self, 'status_icon') and hasattr(self, 'status_text'):
             is_online = FIREBASE_AVAILABLE and self.tracker.db is not None
             self.status_icon.setText("✅" if is_online else "❌")
             self.status_text.setText("Connected to Firebase" if is_online else "Offline (using local storage)")

    def update_quick_stats(self):
        today = datetime.now().date()
        week_start = today - timedelta(days=today.weekday())
        month_start = today.replace(day=1)
        today_earn, week_earn, month_earn = 0, 0, 0
        for t in self.tracker.transactions:
            amount = t.get('amount', 0)
            if not isinstance(amount, (int, float)):
                try: amount = int(amount)
                except: amount = 0
            if amount > 0:
                try:
                    t_date = datetime.fromisoformat(t['date']).date()
                    if t_date == today: today_earn += amount
                    if t_date >= week_start: week_earn += amount
                    if t_date >= month_start: month_earn += amount
                except ValueError: pass # Skip invalid dates silently now
        if hasattr(self, 'today_stat'):
             value_label = self.today_stat.findChild(QLabel, "MiniStatValueSuccess")
             if value_label: value_label.setText(f"+{today_earn:,}")
        if hasattr(self, 'week_stat'):
             value_label = self.week_stat.findChild(QLabel, "MiniStatValuePrimary")
             if value_label: value_label.setText(f"+{week_earn:,}")
        if hasattr(self, 'month_stat'):
             value_label = self.month_stat.findChild(QLabel, "MiniStatValueAccent")
             if value_label: value_label.setText(f"+{month_earn:,}")

    def update_recent_transactions(self):
        if not hasattr(self, 'recent_table'): return
        transactions = self.tracker.get_transaction_history()[-5:]
        self.recent_table.setRowCount(len(transactions))
        for row, t in enumerate(reversed(transactions)):
            try: date_str = datetime.fromisoformat(t['date']).strftime("%I:%M %p")
            except ValueError: date_str = "Invalid Date"
            amount = t.get('amount', 0)
            source = t.get('source', 'N/A')
            self.recent_table.setItem(row, 0, QTableWidgetItem(date_str))
            amount_item = QTableWidgetItem(f"{'+' if amount >= 0 else ''}{amount:,}")
            amount_item.setForeground(QColor(self.palette_colors['success'] if amount >= 0 else self.palette_colors['danger']))
            amount_item.setTextAlignment(Qt.AlignRight | Qt.AlignVCenter)
            self.recent_table.setItem(row, 1, amount_item)
            self.recent_table.setItem(row, 2, QTableWidgetItem(source))

    def filter_history(self):
        # ... (filter_history remains the same) ...
        sources = sorted(list(set(t.get('source', 'N/A') for t in self.tracker.transactions)))
        current_filter = self.history_source_filter.currentText()
        self.history_source_filter.blockSignals(True)
        self.history_source_filter.clear()
        self.history_source_filter.addItems(["All Sources"] + sources)
        index = self.history_source_filter.findText(current_filter)
        self.history_source_filter.setCurrentIndex(index if index != -1 else 0)
        self.history_source_filter.blockSignals(False)
        search_text = self.history_search.text().lower()
        source_filter = self.history_source_filter.currentText()
        from_date = self.date_from.date().toPyDate()
        to_date = self.date_to.date().toPyDate()
        filtered_transactions = []
        period_earned = 0
        for t in self.tracker.get_transaction_history():
            try: transaction_date = datetime.fromisoformat(t['date']).date()
            except ValueError: continue
            date_match = from_date <= transaction_date <= to_date
            source_match = (source_filter == "All Sources" or t.get('source') == source_filter)
            search_match = (search_text in t.get('source', '').lower() or search_text in str(t.get('amount', '')))
            if date_match and source_match and search_match:
                filtered_transactions.append(t)
                if t.get('amount', 0) > 0: period_earned += t['amount']
        self.period_summary.setText(f"Earned in Period: {period_earned:,} coins")
        self.history_table.setRowCount(len(filtered_transactions))
        for row, t in enumerate(reversed(filtered_transactions)):
            try: dt_obj = datetime.fromisoformat(t['date']); date_str = dt_obj.strftime("%b %d, %Y, %I:%M %p")
            except ValueError: date_str = "Invalid Date"
            amount = t.get('amount', 0)
            source = t.get('source', 'N/A')
            balance_after = t.get('previous_balance', 0) + amount
            trans_id = t.get('id', '')
            date_item = QTableWidgetItem(date_str)
            amount_item = QTableWidgetItem(f"{'+' if amount >= 0 else ''}{amount:,}")
            type_icon = "💰" if amount >= 0 else "💸"
            type_item = QTableWidgetItem(f"{type_icon} {'Income' if amount >= 0 else 'Expense'}")
            source_item = QTableWidgetItem(source)
            balance_item = QTableWidgetItem(f"{balance_after:,}")
            id_item = QTableWidgetItem(trans_id)
            if amount >= 0: amount_item.setForeground(QColor(self.palette_colors['success']))
            else: amount_item.setForeground(QColor(self.palette_colors['danger']))
            self.history_table.setItem(row, 0, date_item)
            self.history_table.setItem(row, 1, type_item)
            self.history_table.setItem(row, 2, source_item)
            self.history_table.setItem(row, 3, amount_item)
            self.history_table.setItem(row, 4, balance_item)
            self.history_table.setItem(row, 5, id_item)

    def show_history_context_menu(self, position):
        # ... (Context menu logic remains the same) ...
        selected_indexes = self.history_table.selectedIndexes()
        if not selected_indexes: return
        row = selected_indexes[0].row()
        id_item = self.history_table.item(row, 5)
        if not id_item: return
        transaction_id = id_item.text()
        menu = QMenu(self)
        edit_action = menu.addAction("✎ Edit Transaction")
        delete_action = menu.addAction("🗑️ Delete Transaction")
        action = menu.exec_(self.history_table.mapToGlobal(position))
        if action == edit_action: self.edit_transaction(transaction_id)
        elif action == delete_action: self.delete_transaction(transaction_id)

    def apply_chart_theme(self, chart):
        """
        Apply consistent dark/light mode styling to all charts,
        removing white borders and ensuring text, grid, and backgrounds
        match the active theme.
        """
        p = self.palette_colors

        # --- Remove ALL backgrounds and borders ---
        chart.setBackgroundBrush(Qt.transparent)
        chart.setBackgroundPen(QPen(Qt.NoPen))
        chart.setPlotAreaBackgroundVisible(False) # ✅ FIXED: This is the key change.
        chart.setPlotAreaBackgroundPen(QPen(Qt.NoPen))

        # --- Legend styling ---
        legend = chart.legend()
        legend.setLabelColor(QColor(p['text']))
        legend.setBackgroundVisible(False)
        legend.setBorderColor(Qt.transparent)

        # --- Axis styling ---
        for axis in chart.axes():
            axis.setLinePenColor(QColor(p['border']))
            axis.setLabelsColor(QColor(p['text']))
            axis.setGridLineColor(QColor(p['border']))
            axis.setMinorGridLineColor(QColor(p['border']))

        # --- Title and general text styling ---
        chart.setTitleBrush(QBrush(QColor(p['text'])))
        chart.setTitleFont(QFont("Segoe UI", 10, QFont.Bold))

        chart.setAnimationOptions(QChart.SeriesAnimations)        
    
    def apply_modern_theme(self):
        """
        Apply the current palette to the window and all themed widgets.
        This final version removes the unwanted border from the chart container card.
        """
        # Determine palette from tracker state
        try:
            is_dark = bool(self.tracker.get_dark_mode())
        except Exception:
            is_dark = False
        self.palette_colors = DARK if is_dark else LIGHT
        p = self.palette_colors

        # Progress bar colors depend on mode
        progress_bg = p.get('progressBgDark') if is_dark else p.get('progressBgLight')
        progress_chunk = p.get('progressChunkDark') if is_dark else p.get('progressChunkLight')

        # Apply a global stylesheet for all common widgets and specific object names
        try:
            self.setStyleSheet(f"""
                /* General Window Style */
                QWidget#CentralWidget, QScrollArea {{
                    background-color: {p['bg']};
                    color: {p['text']};
                    font-family: "Segoe UI";
                    border: none;
                }}
                QScrollArea > QWidget > QWidget {{
                    background: transparent;
                }}

                /* --- QTabWidget Styling --- */
                QTabWidget::pane {{
                    border: none;
                }}
                QTabBar::tab {{
                    background: transparent;
                    color: {p['muted']};
                    padding: 10px 15px;
                    border: none;
                    font-weight: 500;
                    font-size: 14px;
                    font-family: "Segoe UI", "Segoe UI Emoji"; /* Use emoji-compatible font */
                }}
                QTabBar::tab:hover {{
                    color: {p['text']};
                }}
                QTabBar::tab:selected {{
                    color: {p['primary']};
                    border-bottom: 2px solid {p['primary']};
                }}

                /* --- Generic Widget Styles --- */
                QLabel {{ color: {p['text']}; background: transparent; }}
                QLineEdit, QComboBox, QDateEdit, QDateTimeEdit {{
                    padding: 8px 10px; border: 1px solid {p['border']}; border-radius: 8px;
                    background-color: {p['bg']}; color: {p['text']}; font-size: 13px;
                }}
                QLineEdit:focus, QComboBox:focus, QDateEdit:focus, QDateTimeEdit:focus {{ border-color: {p['primary']}; }}
                QComboBox QAbstractItemView {{
                    background-color: {p['card']}; color: {p['text']}; border: 1px solid {p['border']};
                    selection-background-color: {p['primary']}; selection-color: white; padding: 4px;
                }}
                QPushButton {{
                    padding: 8px 14px; border: 1px solid {p['border']}; border-radius: 8px;
                    font-weight: 500; background-color: {p['card']}; color: {p['text']};
                }}
                QPushButton:hover {{ background-color: {p['bg']}; border-color: {p['muted']}; }}

                /* --- Table Styling --- */
                QTableWidget {{
                    background-color: {p['card']}; color: {p['text']}; border: 1px solid {p['border']};
                    gridline-color: {p['border']}; alternate-background-color: {p['bg']}; border-radius: 8px;
                }}
                QTableWidget::item:selected {{ background-color: {p['primaryLight']}; color: {p['primary']}; }}
                QHeaderView::section {{
                    background-color: {p['tableHeader']}; color: {p['muted']}; padding: 10px;
                    border: none; font-weight: 600; text-transform: uppercase; font-size: 11px;
                }}

                /* --- ScrollBar Styling --- */
                QScrollBar:vertical {{
                    border: none; background: {p['bg']}; width: 10px; margin: 0px;
                }}
                QScrollBar::handle:vertical {{ background: {p['border']}; min-height: 20px; border-radius: 5px; }}
                QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0px; }}

                /* --- Specific Object Name Styles (Overrides) --- */
                QFrame#ModernSidebar {{ background-color: {p['sidebar']}; border-right: 1px solid {p['border']}; }}
                QFrame#ChartCard {{ border: none; }} /* Remove border from chart container card */
                QLabel#SidebarTitle {{ color: {p['primary']}; font-size: 18px; font-weight: 700; }}
                QLabel#ModernPageTitle {{ color: {p['text']}; font-size: 20px; font-weight: 700; }}
                QLabel#MutedLabel, QLabel#MiniStatLabel {{ color: {p['muted']}; font-size: 12px; }}
                QLabel#ModernCardTitle {{ color: {p['text']}; font-size: 15px; font-weight: 600; }}
                QLabel#GoalLabel, QLabel#ProgressPercent {{ color: {p['balanceCardText']}; opacity: 0.8; }}
                QLabel#BalanceTitle {{ color: {p['balanceCardText']}; font-size: 14px; opacity: 0.9; }}
                QLabel#BalanceLabel {{ color: {p['balanceCardText']}; font-size: 28px; font-weight: 700; }}

                /* Stat Labels */
                QLabel[objectName^="MiniStatValue"] {{ font-size: 18px; font-weight: 600; }}
                QLabel#MiniStatValueSuccess {{ color: {p['success']}; }}
                QLabel#MiniStatValuePrimary {{ color: {p['primary']}; }}
                QLabel#MiniStatValueAccent {{ color: {p['accent']}; }}
                QLabel[objectName^="StatLabel"] {{ font-size: 24px; font-weight: 700; }}
                QLabel#StatLabelSuccess {{ color: {p['success']}; }}
                QLabel#StatLabelDanger {{ color: {p['danger']}; }}
                QLabel#StatLabelPrimary {{ color: {p['primary']}; }}

                /* Buttons */
                QPushButton#ModernPrimaryButton {{ background-color: {p['primary']}; color: white; border: none; }}
                QPushButton#ModernPrimaryButton:hover {{ background-color: {p['primaryDark']}; }}
                QPushButton#ModernSecondaryButton {{ background-color: transparent; color: {p['text']}; border: 1px solid {p['border']}; }}
                QPushButton#ModernSecondaryButton:hover {{ background-color: {p['primaryLight']}; border-color: {p['primary']}; color: {p['primary']}; }}
                QPushButton#ModernDangerButton {{ background-color: {p['danger']}; color: white; border: none; }}
                QPushButton#ModernDangerButton:hover {{ background-color: {p['dangerDark']}; }}
                QPushButton#ModernThemeToggle {{ background: transparent; border: none; color: {p['muted']}; }}

                /* Progress Bar */
                QProgressBar {{
                    background-color: {progress_bg}; border-radius: 6px; height: 12px;
                    border: 1px solid {p['border']}; text-align: center;
                }}
                QProgressBar::chunk {{ background-color: {progress_chunk}; border-radius: 6px; }}

                /* Special card for balance with gradient */
                QFrame#BalanceCard {{
                    background-color: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 {p['gradientStart']}, stop:1 {p['gradientEnd']});
                    border: none; border-radius: 12px;
                }}
            """)
        except Exception as e:
            print(f"Failed to apply global stylesheet: {e}")

        # Update any custom widgets that implement setPalette
        for w in getattr(self, 'themed_widgets', []):
            try:
                if hasattr(w, 'setPalette'):
                    w.setPalette(p)
            except Exception: pass

        for btn in getattr(self, 'quick_action_buttons', []):
            try:
                if hasattr(btn, 'setPalette'):
                    btn.setPalette(p)
            except Exception: pass

        try:
            if QTCHART_AVAILABLE:
                self.update_all_charts()
        except Exception as e:
            print(f"Error updating charts after theme change: {e}")

        # Refresh all UI data to reflect potential color changes in data-driven items
        try:
            if hasattr(self, 'recent_table'): self.update_recent_transactions()
            if hasattr(self, 'today_stat'): self.update_quick_stats()
            if hasattr(self, 'balance_label'): self.update_balance_and_goal()
            if hasattr(self, 'history_table'): self.filter_history()
            try:
                current_index = self.stacked_widget.currentIndex() if hasattr(self, 'stacked_widget') else 0
                name = 'Dashboard' if current_index == 0 else ('Analytics' if current_index == 1 else ('History' if current_index == 2 else 'Settings'))
                self.update_active_nav(name)
            except Exception: pass
        except Exception as e:
            print(f"Error refreshing UI after theme applied: {e}")
                
    def update_active_nav(self, name):
        p = self.palette_colors
        for btn_name, button in self.nav_buttons.items():
            icon_label = button.findChild(QLabel, "NavIcon")
            text_label = button.findChild(QLabel, "NavText")
            if not icon_label or not text_label: continue
            button.setStyleSheet(f"""
                QPushButton#{btn_name} {{ background: transparent; border: none; border-radius: 10px; }}
                QPushButton#{btn_name}:hover {{ background: {p['primaryLight']}; }}
            """)
            icon_label.setStyleSheet(f"color: {p['muted']}; background: transparent; font-size: 16px;")
            text_label.setStyleSheet(f"color: {p['muted']}; background: transparent; font-size: 14px; font-weight: 500;")
            if btn_name == name:
                button.setStyleSheet(f"QPushButton#{btn_name} {{ background: {p['primaryLight']}; border-radius: 10px; }}")
                icon_label.setStyleSheet(f"color: {p['primary']}; background: transparent; font-size: 16px;")
                text_label.setStyleSheet(f"color: {p['primary']}; background: transparent; font-size: 14px; font-weight: 600;")


    def create_modern_icon(self):
        if getattr(sys, 'frozen', False):
            base_path = os.path.dirname(sys.executable)
        else:
            base_path = os.path.dirname(os.path.abspath(__file__))

        icon_path = os.path.join(base_path, "coin.ico") 

        if os.path.exists(icon_path):
            return QIcon(icon_path)
        else:
            print(f"Warning: Icon file not found at {icon_path}. Drawing default icon.")
            pixmap = QPixmap(64, 64)
            pixmap.fill(Qt.transparent)
            painter = QPainter(pixmap)
            painter.setRenderHint(QPainter.Antialiasing)
            painter.setBrush(QBrush(QColor("#f2aa2d")))
            painter.setPen(Qt.NoPen)
            painter.drawEllipse(4, 4, 56, 56)
            painter.setFont(QFont("Segoe UI", 30, QFont.Bold))
            painter.setPen(QPen(QColor("#ffffff")))
            painter.drawText(pixmap.rect(), Qt.AlignCenter, "C")
            painter.end()
            return QIcon(pixmap)
        
    def create_new_profile(self):
        name, ok = QInputDialog.getText(self, "New Profile", "Enter new profile name:")
        if ok and name and name.strip():
            clean_name = name.strip()
            if clean_name in self.get_profile_names():
                self.show_toast("Profile already exists!", "error")
                return
            success, message = self.tracker.create_profile(clean_name)
            if success:
                self.profiles = self.get_profile_names()
                self.profile_combo.blockSignals(True)
                self.profile_combo.clear()
                self.profile_combo.addItems(self.profiles)
                self.profile_combo.setCurrentText(clean_name)
                self.profile_combo.blockSignals(False)
                self.change_profile(clean_name)
            else:
                self.show_toast(message, "error")
        elif ok:
             self.show_toast("Profile name cannot be empty.", "error")

    def toggle_dark_mode(self):
        new_dark_mode = not self.tracker.get_dark_mode()
        self.tracker.set_dark_mode(new_dark_mode)
        self.palette_colors = DARK if new_dark_mode else LIGHT
        self.setWindowIcon(self.create_modern_icon())
        self.apply_modern_theme() # This now handles chart updates too

    def quick_action(self, action, amount, is_positive):
        final_amount = amount if is_positive else -amount
        if self.tracker.add_transaction(final_amount, action):
             self.show_toast(f"Quick action '{action}' recorded.", "success")
             self.update_all_data()
        else:
             self.show_toast(f"Failed to record quick action.", "error")


    def show_add_dialog(self):
        dialog = TransactionDialog(self.palette_colors, parent=self)
        dialog.toggle_type(0) # Set to Income
        if dialog.exec_() == QDialog.Accepted:
            data = dialog.get_transaction_data()
            if self.tracker.add_transaction(data['amount'], data['source'], data['date']):
                self.show_toast(f"Added {abs(data['amount']):,} coins", "success")
                self.update_all_data()
            else:
                self.show_toast("Failed to add transaction.", "error")


    def show_spend_dialog(self):
        dialog = TransactionDialog(self.palette_colors, parent=self)
        dialog.toggle_type(1) # Set to Expense
        if dialog.exec_() == QDialog.Accepted:
            data = dialog.get_transaction_data()
            if self.tracker.add_transaction(data['amount'], data['source'], data['date']):
                self.show_toast(f"Spent {abs(data['amount']):,} coins", "success")
                self.update_all_data()
            else:
                 self.show_toast("Failed to record spending.", "error")

    def edit_transaction(self, transaction_id):
        transaction = next((t for t in self.tracker.transactions if t.get('id') == transaction_id), None)
        if not transaction:
            self.show_toast("Could not find transaction to edit.", "error")
            return

        dialog = TransactionDialog(self.palette_colors, transaction=transaction, parent=self)
        if dialog.exec_() == QDialog.Accepted:
            new_data = dialog.get_transaction_data()
            if self.tracker.update_transaction(transaction_id, new_data):
                self.show_toast("Transaction updated successfully", "success")
                self.update_all_data()
            else:
                self.show_toast("Failed to update transaction", "error")

    def delete_transaction(self, transaction_id):
        reply = QMessageBox.question(self, "Delete Transaction",
                                     "Are you sure you want to permanently delete this transaction?",
                                     QMessageBox.Yes | QMessageBox.No, QMessageBox.No)
        if reply == QMessageBox.Yes:
            if self.tracker.delete_transaction(transaction_id):
                self.show_toast("Transaction deleted successfully", "success")
                self.update_all_data()
            else:
                self.show_toast("Failed to delete transaction", "error")

    def edit_quick_actions(self):
         current_actions = self.tracker.settings.get('quick_actions', [])
         if not isinstance(current_actions, list): current_actions = []

         dialog = QuickActionsDialog(current_actions, self.palette_colors, self)
         if dialog.exec_() == QDialog.Accepted:
              updated_actions = dialog.get_updated_actions()
              self.tracker.settings['quick_actions'] = updated_actions
              self.tracker.save_data(recalculate=False)
              self.update_modern_quick_actions()
              self.show_toast("Quick actions updated successfully", "success")


    def update_balance_and_goal(self):
        balance = self.tracker.get_balance()
        goal = self.tracker.get_goal()
        self.balance_label.setText(f"{balance:,} coins")
        self.goal_label.setText(f"Goal: {goal:,} coins")
        pct = 0
        if goal > 0: pct = max(0, min(100, int((balance / goal) * 100)))
        self.goal_progress.setValue(pct)
        if hasattr(self, 'progress_percent'):
             self.progress_percent.setText(f"{pct}%")
        if hasattr(self, 'current_goal_display'): self.current_goal_display.setText(f"{goal:,} coins")
        if hasattr(self, 'goal_progress_label'): self.goal_progress_label.setText(f"You are {pct}% of the way towards your current goal.")
        if hasattr(self, 'goal_input') and not self.goal_input.hasFocus(): self.goal_input.setText(str(goal))


    def update_analytics_stats(self):
        if not hasattr(self, 'total_earnings_label') or not self.total_earnings_label: return 
        if not self.tracker.transactions:
            self.total_earnings_label.setText("N/A")
            self.total_spending_label.setText("N/A")
            self.net_balance_label.setText("N/A")
            return
        earnings = sum(t['amount'] for t in self.tracker.transactions if t.get('amount',0) > 0)
        spending = abs(sum(t['amount'] for t in self.tracker.transactions if t.get('amount',0) < 0))
        net = earnings - spending
        self.total_earnings_label.setText(f"+{earnings:,}")
        self.total_spending_label.setText(f"-{spending:,}")
        self.net_balance_label.setText(f"{net:+,}")


    def update_all_charts(self):
        if not QTCHART_AVAILABLE: return
        print("Updating all charts...")
        if 'donut' in self.chart_views and self.chart_views['donut']: self.update_donut_chart()
        if 'bar' in self.chart_views and self.chart_views['bar']: self.update_spending_bar_chart()
        if 'line' in self.chart_views and self.chart_views['line']: self.update_balance_line_chart()


    def set_goal_clicked(self):
        goal_text = self.goal_input.text()
        if goal_text:
            try:
                goal = int(goal_text)
                if goal < 0: raise ValueError("Goal cannot be negative")
                self.tracker.set_goal(goal)
                self.update_balance_and_goal()
                self.show_toast(f"Goal updated to {goal:,} coins.", "success")
            except ValueError:
                self.show_toast("Please enter a valid non-negative number.", "error")
        else:
             self.show_toast("Please enter a goal amount.", "error")


    def export_data(self):
        suggested_name = f"{self.current_profile}_export_{date.today().strftime('%Y%m%d')}.json"
        downloads_path = os.path.join(os.path.expanduser('~'), 'Downloads')
        default_path = os.path.join(downloads_path, suggested_name)
        path, _ = QFileDialog.getSaveFileName(self, "Export Data", default_path, "JSON Files (*.json)")
        if path:
            if self.tracker.export_data(path): self.show_toast("Data exported successfully!", "success")
            else: self.show_toast("Failed to export data.", "error")


    def import_data(self):
        downloads_path = os.path.join(os.path.expanduser('~'), 'Downloads')
        path, _ = QFileDialog.getOpenFileName(self, "Import Data", downloads_path, "JSON Files (*.json)")
        if path:
            reply = QMessageBox.question(self, "Confirm Import", f"This will **overwrite** all existing data for the profile '{self.current_profile}'. Are you sure?", QMessageBox.Yes | QMessageBox.Cancel, QMessageBox.Cancel)
            if reply == QMessageBox.Yes:
                if self.tracker.import_data(path):
                    self.update_all_data()
                    self.show_toast("Data imported successfully!", "success")
                else:
                    self.show_toast("Failed to import data. Check file format.", "error")


    def create_backup(self):
        backup_dir = os.path.join(os.path.expanduser('~'), 'Documents', 'CoinTracker', 'Backups')
        os.makedirs(backup_dir, exist_ok=True)
        backup_file = os.path.join(backup_dir, f"{self.current_profile}_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json")
        if self.tracker.export_data(backup_file):
            self.show_toast(f"Backup created: {os.path.basename(backup_file)}", "success")
        else:
            self.show_toast("Backup failed!", "error")

    def show_toast(self, message, type="success"):
        """Shows a non-blocking toast notification."""
        if hasattr(self, 'toast_widget') and self.toast_widget:
            try:
                self.toast_widget.close() 
            except RuntimeError:
                 pass
        self.toast_widget = ToastNotification(self)
        self.toast_widget.show_toast(message, type, self.palette_colors)


# --------------------------
# APP BOOTSTRAP
# --------------------------

if __name__ == "__main__":
    QApplication.setAttribute(Qt.AA_EnableHighDpiScaling, True)
    QApplication.setAttribute(Qt.AA_UseHighDpiPixmaps, True)

    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    app.setFont(QFont("Segoe UI", 10))

    if FIREBASE_AVAILABLE:
        try:
            if getattr(sys, 'frozen', False): base_path = os.path.dirname(sys.executable)
            else: base_path = os.path.dirname(os.path.abspath(__file__))
            key_file = os.path.join(base_path, "firebase-key.json")
            if os.path.exists(key_file) and not firebase_admin._apps:
                cred = credentials.Certificate(key_file)
                firebase_admin.initialize_app(cred)
                print("✅ Firebase initialized successfully (pre-app)")
            elif not os.path.exists(key_file):
                print("Firebase key not found, proceeding offline.")
                FIREBASE_AVAILABLE = False
        except Exception as e:
            print(f"❌ Firebase init error (pre-app): {e}")
            FIREBASE_AVAILABLE = False

    try:
        window = MainWindow()
        window.show()
        sys.exit(app.exec_())
    except Exception as e:
         print(f"FATAL ERROR during MainWindow initialization or execution: {e}")
         try:
             error_msg = QMessageBox()
             error_msg.setIcon(QMessageBox.Critical)
             error_msg.setText("Application Error")
             error_msg.setInformativeText(f"An unexpected error occurred:\n{e}\n\nPlease check the console output for details.")
             error_msg.setWindowTitle("Fatal Error")
             error_msg.exec_()
         except Exception as e2:
              print(f"Could not show error message box: {e2}")
         sys.exit(1)
