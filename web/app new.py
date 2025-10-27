import os
import uuid
from flask import Flask, render_template, request, jsonify, session
from datetime import datetime, date, timedelta
from collections import defaultdict

# --- Firebase Initialization ---
try:
    import firebase_admin
    from firebase_admin import credentials, firestore
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False

app = Flask(__name__,
    template_folder='templates',
    static_folder='static'
)
app.secret_key = os.environ.get('SECRET_KEY', 'a-very-secret-key-for-dev')

db = None
if FIREBASE_AVAILABLE:
    try:
        # Assumes 'firebase-key.json' is in the same directory for local development
        if os.path.exists('firebase-key.json'):
            cred = credentials.Certificate('firebase-key.json')
            if not firebase_admin._apps:
                firebase_admin.initialize_app(cred)
            db = firestore.client()
            print("✅ Firebase initialized successfully")
        else:
            print("⚠️ Firebase key file not found. Running in offline mode.")
            FIREBASE_AVAILABLE = False
    except Exception as e:
        print(f"❌ Firebase init error: {e}")
        db = None
else:
    print("⚠️ Firebase library not found. Running in offline mode.")

# --- Helper Functions ---
def dt_now_iso():
    return datetime.now().isoformat()

# --- Core Data Handler Class ---
class WebCoinTracker:
    def __init__(self, profile_name="Default", user_id="default_user"):
        self.profile_name = profile_name
        self.user_id = user_id
        self.db = db

    def get_default_settings(self):
        """Returns the default structure for settings."""
        return {
            'goal': 13500,
            'dark_mode': False, # Defaulting to light mode to match screenshots
            'quick_actions': [
                {"text": "Event Rewards", "value": 50, "is_positive": True},
                {"text": "Watch Ads", "value": 10, "is_positive": True},
                {"text": "Daily Games", "value": 100, "is_positive": True},
                {"text": "Box Draw", "value": 100, "is_positive": False},
                {"text": "Login Bonus", "value": 50, "is_positive": True},
                {"text": "Achievement", "value": 25, "is_positive": True}
            ]
        }

    def get_data(self):
        """Fetches and validates data from Firebase or session."""
        transactions, settings = [], self.get_default_settings()
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                doc = doc_ref.get()
                if doc.exists:
                    data = doc.to_dict()
                    profiles_data = data.get('profiles', {})
                    profile_data = profiles_data.get(self.profile_name, {})
                    transactions = profile_data.get('transactions', [])
                    loaded_settings = profile_data.get('settings', {})
                    settings.update(loaded_settings)
            except Exception as e:
                print(f"Firebase load error: {e}")
        else: # Fallback to session
            session_profiles = session.get('profiles', {})
            profile_data = session_profiles.get(self.profile_name, {})
            transactions = profile_data.get('transactions', [])
            loaded_settings = profile_data.get('settings', {})
            settings.update(loaded_settings)
        return self.validate_data(transactions, settings)

    def validate_data(self, transactions, settings):
        """Ensures transactions have IDs and settings have the correct structure."""
        valid_transactions = []
        for t in transactions:
            if isinstance(t, dict) and all(k in t for k in ['date', 'amount', 'source']):
                if 'id' not in t or not t['id']:
                    t['id'] = str(uuid.uuid4())
                valid_transactions.append(t)
        if 'quick_actions' not in settings or not isinstance(settings['quick_actions'], list):
            settings['quick_actions'] = self.get_default_settings()['quick_actions']
        return valid_transactions, settings

    def save_data(self, transactions, settings):
        """Recalculates balances and saves data to Firebase or session."""
        transactions = self.recalculate_balances(transactions)
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                profile_data = {'transactions': transactions, 'settings': settings, 'last_updated': dt_now_iso()}
                doc_ref.set({'profiles': {self.profile_name: profile_data}}, merge=True)
                return True
            except Exception as e:
                print(f"Firebase save error: {e}")
                return False
        else: # Fallback to session
            session_profiles = session.get('profiles', {})
            session_profiles[self.profile_name] = {'transactions': transactions, 'settings': settings, 'last_updated': dt_now_iso()}
            session['profiles'] = session_profiles
            session.modified = True
            return True

    def recalculate_balances(self, transactions):
        """Sorts transactions and accurately calculates running balance."""
        sorted_transactions = sorted(transactions, key=lambda x: x.get('date', ''))
        balance = 0
        for t in sorted_transactions:
            t['previous_balance'] = balance
            balance += t.get('amount', 0)
        return sorted_transactions

    def add_transaction(self, amount, source, date):
        transactions, settings = self.get_data()
        transaction = {"id": str(uuid.uuid4()), "date": date or dt_now_iso(), "amount": int(amount), "source": source}
        transactions.append(transaction)
        return self.save_data(transactions, settings)

    def update_transaction(self, transaction_id, new_data):
        transactions, settings = self.get_data()
        for t in transactions:
            if t.get('id') == transaction_id:
                t.update({'amount': int(new_data['amount']), 'source': new_data['source'], 'date': new_data['date']})
                return self.save_data(transactions, settings)
        return False

    def delete_transaction(self, transaction_id):
        transactions, settings = self.get_data()
        initial_len = len(transactions)
        transactions = [t for t in transactions if t.get('id') != transaction_id]
        if len(transactions) < initial_len:
            return self.save_data(transactions, settings)
        return False

    def get_profiles(self):
        profiles = ['Default']
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc = self.db.collection('users').document(self.user_id).get()
                if doc.exists: profiles.extend([p for p in doc.to_dict().get('profiles', {}).keys() if p != 'Default'])
            except Exception as e:
                print(f"Firebase profiles error: {e}")
        profiles.extend([p for p in session.get('profiles', {}).keys() if p not in profiles])
        return sorted(list(set(profiles)))

# --- API Routes ---
@app.route('/api/data')
def get_all_data():
    profile_name = session.get('current_profile', 'Default')
    tracker = WebCoinTracker(profile_name)
    transactions, settings = tracker.get_data()
    balance = sum(t.get('amount', 0) for t in transactions)
    goal = settings.get('goal', 13500)

    # Dashboard Quick Stats
    today = datetime.now().date()
    week_start = today - timedelta(days=today.weekday())
    month_start = today.replace(day=1)
    today_earn, week_earn, month_earn = 0, 0, 0
    for t in transactions:
        amount = t.get('amount', 0)
        if amount > 0:
            try:
                t_date = datetime.fromisoformat(t['date']).date()
                if t_date == today: today_earn += amount
                if t_date >= week_start: week_earn += amount
                if t_date >= month_start: month_earn += amount
            except (ValueError, TypeError): pass

    # Analytics Data
    total_earnings = sum(t['amount'] for t in transactions if t['amount'] > 0)
    total_spending = abs(sum(t['amount'] for t in transactions if t['amount'] < 0))
    earnings_breakdown = defaultdict(int)
    for t in transactions:
        if t['amount'] > 0: earnings_breakdown[t['source']] += t['amount']
    spending_breakdown = defaultdict(int)
    for t in transactions:
        if t['amount'] < 0: spending_breakdown[t['source']] += abs(t['amount'])
    timeline = [{'date': t['date'], 'balance': t['previous_balance'] + t['amount']} for t in sorted(transactions, key=lambda x: x.get('date', ''))]

    return jsonify({
        'profile': profile_name,
        'transactions': transactions,
        'settings': settings,
        'balance': balance,
        'goal': goal,
        'progress': min(100, int((balance / goal) * 100)) if goal > 0 else 0,
        'dashboard_stats': {
            'today': today_earn,
            'week': week_earn,
            'month': month_earn,
        },
        'analytics': {
            'total_earnings': total_earnings,
            'total_spending': total_spending,
            'net_balance': total_earnings - total_spending,
            'earnings_breakdown': dict(earnings_breakdown),
            'spending_breakdown': dict(spending_breakdown),
            'timeline': timeline,
        }
    })

@app.route('/api/add-transaction', methods=['POST'])
def handle_add_transaction():
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    data = request.json
    if tracker.add_transaction(data['amount'], data['source'], data['date']):
        return jsonify({'success': True, 'message': 'Transaction added successfully'})
    return jsonify({'success': False, 'error': 'Failed to save transaction'}), 500

@app.route('/api/update-transaction/<transaction_id>', methods=['POST'])
def handle_update_transaction(transaction_id):
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    if tracker.update_transaction(transaction_id, request.json):
        return jsonify({'success': True, 'message': 'Transaction updated successfully'})
    return jsonify({'success': False, 'error': 'Transaction not found or failed to update'}), 404

@app.route('/api/delete-transaction/<transaction_id>', methods=['POST'])
def handle_delete_transaction(transaction_id):
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    if tracker.delete_transaction(transaction_id):
        return jsonify({'success': True, 'message': 'Transaction deleted successfully'})
    return jsonify({'success': False, 'error': 'Transaction not found or failed to delete'}), 404

@app.route('/api/update-settings', methods=['POST'])
def update_settings():
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    transactions, settings = tracker.get_data()
    settings.update(request.json)
    if tracker.save_data(transactions, settings):
        return jsonify({'success': True, 'message': 'Settings updated'})
    return jsonify({'success': False, 'error': 'Failed to save settings'}), 500

@app.route('/api/profiles')
def get_profiles():
    tracker = WebCoinTracker()
    return jsonify({'profiles': tracker.get_profiles(), 'current_profile': session.get('current_profile', 'Default')})

@app.route('/api/switch-profile', methods=['POST'])
def switch_profile():
    session['current_profile'] = request.json.get('profile_name')
    return jsonify({'success': True})

@app.route('/api/create-profile', methods=['POST'])
def create_profile():
    profile_name = request.json.get('profile_name')
    tracker = WebCoinTracker(profile_name)
    if profile_name in tracker.get_profiles():
        return jsonify({'success': False, 'error': 'Profile already exists'}), 409
    if tracker.save_data([], tracker.get_default_settings()):
        session['current_profile'] = profile_name
        return jsonify({'success': True})
    return jsonify({'success': False, 'error': 'Failed to create profile'}), 500

@app.route('/')
def index():
    return render_template('index.html')

if __name__ == '__main__':
    app.run(debug=True)