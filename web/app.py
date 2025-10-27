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
        # --- THIS IS THE CORRECTED LOGIC BLOCK ---
        # Define required environment variable keys
        required_env_vars = ['FIREBASE_PROJECT_ID', 'FIREBASE_PRIVATE_KEY', 'FIREBASE_CLIENT_EMAIL']
        
        # Check if all required environment variables are present and not empty
        if all(os.getenv(key) for key in required_env_vars):
            print("Attempting to initialize Firebase with environment variables...")
            # Sanitize the private key
            private_key = os.getenv('FIREBASE_PRIVATE_KEY').replace('\\n', '\n')
            
            firebase_config = {
                "type": "service_account",
                "project_id": os.getenv('FIREBASE_PROJECT_ID'),
                "private_key_id": os.getenv('FIREBASE_PRIVATE_KEY_ID', ''), # Optional
                "private_key": private_key,
                "client_email": os.getenv('FIREBASE_CLIENT_EMAIL'),
                "client_id": os.getenv('FIREBASE_CLIENT_ID', ''), # Optional
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            }
            cred = credentials.Certificate(firebase_config)
            print("Firebase credentials loaded from environment.")
        # Fallback to local file if environment variables are not set
        elif os.path.exists('firebase-key.json'):
            print("Attempting to initialize Firebase with firebase-key.json...")
            cred = credentials.Certificate('firebase-key.json')
            print("Firebase credentials loaded from file.")
        # If neither method works, raise an exception
        else:
            raise Exception("No Firebase configuration found. Set environment variables or provide firebase-key.json.")
        
        if not firebase_admin._apps:
            firebase_admin.initialize_app(cred)
        
        db = firestore.client()
        print("✅ Firebase initialized successfully")
        # --- END OF CORRECTED LOGIC BLOCK ---

    except Exception as e:
        print(f"❌ Firebase init error: {e}")
        db = None
        FIREBASE_AVAILABLE = False # Explicitly set to false on failure
else:
    print("⚠️ Firebase library not found. Running in offline mode.")

# (The rest of the file remains the same)
def dt_now_iso():
    return datetime.now().isoformat()

class WebCoinTracker:
    def __init__(self, profile_name="Default", user_id="default_user"):
        self.profile_name = profile_name
        self.user_id = user_id
        self.db = db

    def get_default_settings(self):
        return {
            'goal': 13500, 'dark_mode': False,
            'quick_actions': [
                {"text": "Event Rewards", "value": 50, "is_positive": True}, {"text": "Watch Ads", "value": 10, "is_positive": True},
                {"text": "Daily Games", "value": 100, "is_positive": True}, {"text": "Box Draw", "value": 100, "is_positive": False},
                {"text": "Login Bonus", "value": 50, "is_positive": True}, {"text": "Achievement", "value": 25, "is_positive": True}
            ]
        }

    def get_data(self):
        transactions, settings = [], self.get_default_settings()
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc = self.db.collection('users').document(self.user_id).get()
                if doc.exists:
                    data = doc.to_dict()
                    profile_data = data.get('profiles', {}).get(self.profile_name, {})
                    transactions = profile_data.get('transactions', [])
                    settings.update(profile_data.get('settings', {}))
            except Exception as e: print(f"Firebase load error: {e}")
        else:
            profile_data = session.get('profiles', {}).get(self.profile_name, {})
            transactions = profile_data.get('transactions', [])
            settings.update(profile_data.get('settings', {}))
        return self.validate_data(transactions, settings)

    def validate_data(self, transactions, settings):
        for t in transactions:
            if 'id' not in t or not t['id']: t['id'] = str(uuid.uuid4())
        if 'quick_actions' not in settings: settings['quick_actions'] = self.get_default_settings()['quick_actions']
        return transactions, settings

    def save_data(self, transactions, settings):
        transactions = self.recalculate_balances(transactions)
        if self.db and FIREBASE_AVAILABLE:
            try:
                doc_ref = self.db.collection('users').document(self.user_id)
                doc = doc_ref.get()
                profiles_data = doc.to_dict().get('profiles', {}) if doc.exists else {}
                profiles_data[self.profile_name] = {'transactions': transactions, 'settings': settings, 'last_updated': dt_now_iso()}
                doc_ref.set({'profiles': profiles_data}, merge=True)
                return True
            except Exception as e:
                print(f"Firebase save error: {e}")
                return False
        else:
            profiles = session.get('profiles', {})
            profiles[self.profile_name] = {'transactions': transactions, 'settings': settings, 'last_updated': dt_now_iso()}
            session['profiles'] = profiles
            session.modified = True
            return True

    def recalculate_balances(self, transactions):
        sorted_transactions = sorted(transactions, key=lambda x: x.get('date', ''))
        balance = 0
        for t in sorted_transactions:
            t['previous_balance'] = balance
            balance += t.get('amount', 0)
        return sorted_transactions

    def add_transaction(self, amount, source, date):
        transactions, settings = self.get_data()
        transactions.append({"id": str(uuid.uuid4()), "date": date or dt_now_iso(), "amount": int(amount), "source": source})
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
            except Exception as e: print(f"Firebase profiles error: {e}")
        profiles.extend([p for p in session.get('profiles', {}).keys() if p not in profiles])
        return sorted(list(set(profiles)))

@app.route('/api/data')
def get_all_data():
    profile_name = session.get('current_profile', 'Default')
    tracker = WebCoinTracker(profile_name)
    transactions, settings = tracker.get_data()
    balance = sum(t.get('amount', 0) for t in transactions)
    goal = settings.get('goal', 13500)
    today, week_start, month_start = datetime.now().date(), datetime.now().date() - timedelta(days=datetime.now().weekday()), datetime.now().date().replace(day=1)
    today_earn, week_earn, month_earn = 0, 0, 0
    for t in transactions:
        if t.get('amount', 0) > 0:
            try:
                t_date = datetime.fromisoformat(t['date']).date()
                if t_date == today: today_earn += t['amount']
                if t_date >= week_start: week_earn += t['amount']
                if t_date >= month_start: month_earn += t['amount']
            except (ValueError, TypeError): pass
    total_earnings = sum(t['amount'] for t in transactions if t['amount'] > 0)
    total_spending = abs(sum(t['amount'] for t in transactions if t['amount'] < 0))
    earnings_breakdown = defaultdict(int)
    for t in transactions:
        if t['amount'] > 0: earnings_breakdown[t['source']] += t['amount']
    spending_breakdown = defaultdict(int)
    for t in transactions:
        if t['amount'] < 0: spending_breakdown[t['source']] += abs(t['amount'])
    timeline = [{'date': t['date'], 'balance': t.get('previous_balance', 0) + t.get('amount', 0)} for t in sorted(transactions, key=lambda x: x.get('date', ''))]

    # Add firebase availability to settings
    settings['firebase_available'] = FIREBASE_AVAILABLE and db is not None

    return jsonify({
        'profile': profile_name, 'transactions': transactions, 'settings': settings, 'balance': balance, 'goal': goal,
        'progress': min(100, int((balance / goal) * 100)) if goal > 0 else 0,
        'dashboard_stats': {'today': today_earn, 'week': week_earn, 'month': month_earn},
        'analytics': {
            'total_earnings': total_earnings, 'total_spending': total_spending, 'net_balance': total_earnings - total_spending,
            'earnings_breakdown': dict(earnings_breakdown), 'spending_breakdown': dict(spending_breakdown), 'timeline': timeline,
        }
    })

@app.route('/api/add-transaction', methods=['POST'])
def handle_add_transaction():
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    data = request.json
    if tracker.add_transaction(data['amount'], data['source'], data['date']):
        return jsonify({'success': True, 'message': 'Transaction added'})
    return jsonify({'success': False, 'error': 'Failed to save transaction'}), 500

@app.route('/api/update-transaction/<transaction_id>', methods=['POST'])
def handle_update_transaction(transaction_id):
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    if tracker.update_transaction(transaction_id, request.json):
        return jsonify({'success': True, 'message': 'Transaction updated'})
    return jsonify({'success': False, 'error': 'Failed to update'}), 404

@app.route('/api/delete-transaction/<transaction_id>', methods=['POST'])
def handle_delete_transaction(transaction_id):
    tracker = WebCoinTracker(session.get('current_profile', 'Default'))
    if tracker.delete_transaction(transaction_id):
        return jsonify({'success': True, 'message': 'Transaction deleted'})
    return jsonify({'success': False, 'error': 'Failed to delete'}), 404

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
    profile_name = request.json.get('profile_name')
    session['current_profile'] = profile_name
    if db and FIREBASE_AVAILABLE:
        try:
            db.collection('users').document('default_user').set({'last_active_profile': profile_name}, merge=True)
        except Exception as e: print(f"Error saving last active profile: {e}")
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
    if 'current_profile' not in session:
        primary_profile = 'Default'
        if db and FIREBASE_AVAILABLE:
            try:
                doc = db.collection('users').document('default_user').get()
                if doc.exists:
                    primary_profile = doc.to_dict().get('last_active_profile', 'Default')
            except Exception as e: print(f"Error fetching primary profile: {e}")
        session['current_profile'] = primary_profile
    return render_template('index.html')

if __name__ == '__main__':
    app.run(debug=True)
