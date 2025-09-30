import os
from flask import Flask, render_template, request, jsonify, session
from datetime import datetime
import json
from collections import defaultdict

# Firebase
try:
    import firebase_admin
    from firebase_admin import credentials, firestore
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False

# Initialize Flask with correct paths
app = Flask(__name__, 
    template_folder='templates',
    static_folder='static'
)
app.secret_key = os.environ.get('SECRET_KEY', 'dev-secret-key')

# Firebase initialization
if FIREBASE_AVAILABLE:
    try:
        # Try to get Firebase config from environment variables (for production)
        if all(key in os.environ for key in ['FIREBASE_PROJECT_ID', 'FIREBASE_PRIVATE_KEY']):
            # Use environment variables
            firebase_config = {
                "type": "service_account",
                "project_id": os.environ['FIREBASE_PROJECT_ID'],
                "private_key_id": os.environ.get('FIREBASE_PRIVATE_KEY_ID', ''),
                "private_key": os.environ['FIREBASE_PRIVATE_KEY'].replace('\\n', '\n'),
                "client_email": os.environ['FIREBASE_CLIENT_EMAIL'],
                "client_id": os.environ.get('FIREBASE_CLIENT_ID', ''),
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
            }
            cred = credentials.Certificate(firebase_config)
        elif os.path.exists('firebase-key.json'):
            # Use local key file (for development)
            cred = credentials.Certificate('firebase-key.json')
        else:
            raise Exception("No Firebase configuration found")
            
        firebase_admin.initialize_app(cred)
        db = firestore.client()
        print("✅ Firebase initialized successfully")
    except Exception as e:
        print(f"❌ Firebase init error: {e}")
        db = None
else:
    db = None
    print("⚠️  Firebase not available - using session storage")

def dt_now_iso():
    return datetime.now().isoformat()

class WebCoinTracker:
    def __init__(self):
        self.db = db
        
    def get_data(self):
        if self.db and FIREBASE_AVAILABLE:
            try:
                # Access the exact structure: users -> default_user -> NoBodyNub
                doc_ref = self.db.collection('users').document('default_user')
                doc = doc_ref.get()
                
                if doc.exists:
                    data = doc.to_dict()
                    nobody_nub_data = data.get('NoBodyNub', {})
                    
                    # Extract transactions and settings from NoBodyNub map
                    transactions = nobody_nub_data.get('transactions', [])
                    settings = nobody_nub_data.get('settings', {'goal': 13500, 'dark_mode': False})
                    
                    # Ensure transactions is a list and handle any format issues
                    if isinstance(transactions, dict):
                        # Convert from Firebase map format to list
                        transactions = list(transactions.values())
                    elif not isinstance(transactions, list):
                        transactions = []
                    
                    # Ensure each transaction has the required fields
                    for transaction in transactions:
                        if 'previous_balance' not in transaction:
                            transaction['previous_balance'] = 0
                    
                    return transactions, settings
                else:
                    # Return empty data with default settings if no document exists
                    return [], {'goal': 13500, 'dark_mode': False}
            except Exception as e:
                # Log the error and return empty data to prevent app crash
                print(f"Firebase load error: {e}")
                return [], {'goal': 13500, 'dark_mode': False}
        else:
            # Fallback to session storage
            return session.get('transactions', []), session.get('settings', {'goal': 13500, 'dark_mode': False})

    def save_data(self, transactions, settings):
        if self.db and FIREBASE_AVAILABLE:
            try:
                # Save to the exact structure: users -> default_user -> NoBodyNub
                doc_ref = self.db.collection('users').document('default_user')
                
                # Create the NoBodyNub map with the exact structure
                nobody_nub_data = {
                    'last_updated': dt_now_iso(),
                    'settings': settings,
                    'transactions': transactions
                }
                
                # Update just the NoBodyNub field within the user document
                update_data = {
                    'NoBodyNub': nobody_nub_data
                }
                
                doc_ref.set(update_data, merge=True)
                return True
            except Exception as e:
                print(f"Firebase save error: {e}")
                return False
        else:
            # Save to session
            session['transactions'] = transactions
            session['settings'] = settings
            return True

    def add_transaction(self, amount, source):
        transactions, settings = self.get_data()
        
        # Calculate previous balance
        previous_balance = sum(t['amount'] for t in transactions)
        
        transaction = {
            "date": dt_now_iso(),
            "amount": amount,
            "source": source,
            "previous_balance": previous_balance
        }
        
        transactions.append(transaction)
        success = self.save_data(transactions, settings)
        return success

    def get_balance(self, transactions=None):
        if transactions is None:
            transactions, _ = self.get_data()
        return sum(t['amount'] for t in transactions)

    def get_source_breakdown(self):
        transactions, _ = self.get_data()
        breakdown = defaultdict(int)
        for t in transactions:
            if t['amount'] > 0:
                breakdown[t['source']] += t['amount']
        return dict(breakdown)

    def set_goal(self, goal):
        transactions, settings = self.get_data()
        settings['goal'] = goal
        return self.save_data(transactions, settings)

    def set_dark_mode(self, dark_mode):
        transactions, settings = self.get_data()
        settings['dark_mode'] = dark_mode
        return self.save_data(transactions, settings)

# Routes
@app.route('/')
def index():
    tracker = WebCoinTracker()
    _, settings = tracker.get_data()
    return render_template('index.html', 
                         FIREBASE_AVAILABLE=FIREBASE_AVAILABLE,
                         db=db,
                         dark_mode=settings.get('dark_mode', False))

@app.route('/api/balance')
def get_balance():
    tracker = WebCoinTracker()
    transactions, settings = tracker.get_data()
    balance = tracker.get_balance(transactions)
    goal = settings.get('goal', 13500)
    
    return jsonify({
        'balance': balance,
        'goal': goal,
        'progress': min(100, int((balance / goal) * 100)) if goal > 0 else 0
    })

@app.route('/api/transactions')
def get_transactions():
    tracker = WebCoinTracker()
    transactions, _ = tracker.get_data()
    # Sort by date descending (newest first)
    transactions.sort(key=lambda x: x['date'], reverse=True)
    return jsonify(transactions)

@app.route('/api/analytics')
def get_analytics():
    tracker = WebCoinTracker()
    transactions, _ = tracker.get_data()
    
    total_earnings = sum(t['amount'] for t in transactions if t['amount'] > 0)
    total_spending = abs(sum(t['amount'] for t in transactions if t['amount'] < 0))
    net_balance = total_earnings - total_spending
    breakdown = tracker.get_source_breakdown()
    
    return jsonify({
        'total_earnings': total_earnings,
        'total_spending': total_spending,
        'net_balance': net_balance,
        'breakdown': breakdown
    })

@app.route('/api/add-transaction', methods=['POST'])
def add_transaction():
    data = request.json
    amount = data.get('amount')
    source = data.get('source')
    
    if not amount or not source:
        return jsonify({'success': False, 'error': 'Missing amount or source'})
    
    try:
        amount = int(amount)
    except ValueError:
        return jsonify({'success': False, 'error': 'Invalid amount'})
    
    tracker = WebCoinTracker()
    success = tracker.add_transaction(amount, source)
    
    if success:
        return jsonify({'success': True, 'message': f'Added {amount} coins from {source}'})
    else:
        return jsonify({'success': False, 'error': 'Failed to save transaction'})

@app.route('/api/spend-coins', methods=['POST'])
def spend_coins():
    data = request.json
    amount = data.get('amount')
    category = data.get('category')
    
    if not amount or not category:
        return jsonify({'success': False, 'error': 'Missing amount or category'})
    
    try:
        amount = int(amount)
        if amount <= 0:
            return jsonify({'success': False, 'error': 'Amount must be positive'})
    except ValueError:
        return jsonify({'success': False, 'error': 'Invalid amount'})
    
    tracker = WebCoinTracker()
    success = tracker.add_transaction(-amount, category)
    
    if success:
        return jsonify({'success': True, 'message': f'Spent {amount} coins on {category}'})
    else:
        return jsonify({'success': False, 'error': 'Failed to save transaction'})

@app.route('/api/set-goal', methods=['POST'])
def set_goal():
    data = request.json
    goal = data.get('goal')
    
    if not goal:
        return jsonify({'success': False, 'error': 'Missing goal'})
    
    try:
        goal = int(goal)
        if goal < 0:
            return jsonify({'success': False, 'error': 'Goal must be positive'})
    except ValueError:
        return jsonify({'success': False, 'error': 'Invalid goal'})
    
    tracker = WebCoinTracker()
    success = tracker.set_goal(goal)
    
    if success:
        return jsonify({'success': True, 'message': f'Goal set to {goal} coins'})
    else:
        return jsonify({'success': False, 'error': 'Failed to save goal'})

@app.route('/api/toggle-theme', methods=['POST'])
def toggle_theme():
    data = request.json
    dark_mode = data.get('dark_mode')
    
    if dark_mode is None:
        return jsonify({'success': False, 'error': 'Missing dark_mode parameter'})
    
    tracker = WebCoinTracker()
    success = tracker.set_dark_mode(bool(dark_mode))
    
    if success:
        theme = "dark" if dark_mode else "light"
        return jsonify({'success': True, 'message': f'Theme set to {theme} mode'})
    else:
        return jsonify({'success': False, 'error': 'Failed to save theme preference'})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
