import PyInstaller.__main__
import os
import shutil
import sys

print("🚀 Building Coin Tracker...")

# Configuration
app_name = "CoinTracker"
script = "coin_tracker.py"
icon = "coin.ico" if os.path.exists("coin.ico") else None
dist_path = "./dist"
build_path = "./build"

# Ensure directories exist
os.makedirs(dist_path, exist_ok=True)
os.makedirs(build_path, exist_ok=True)

# Build parameters
params = [
    script,
    f'--name={app_name}',
    '--windowed',
    '--onefile',
    f'--distpath={dist_path}',
    f'--workpath={build_path}',
    '--noconfirm',
    '--clean',
    '--hidden-import=PyQt5.QtCore',
    '--hidden-import=PyQt5.QtGui',
    '--hidden-import=PyQt5.QtWidgets',
    '--hidden-import=PyQt5.QtChart',
    '--hidden-import=firebase_admin',
    '--hidden-import=firebase_admin.credentials',
    '--hidden-import=firebase_admin.firestore',
    '--hidden-import=google.auth',
    '--hidden-import=google.cloud',
    '--hidden-import=google.oauth2',
    '--hidden-import=requests',
    '--collect-all=PyQt5'
]

# Add icon if exists
if icon and os.path.exists(icon):
    params.append(f'--icon={icon}')

# Add Firebase key if exists
if os.path.exists("firebase-key.json"):
    params.append('--add-data=firebase-key.json;.')

# Run PyInstaller
PyInstaller.__main__.run(params)

# Copy Firebase key to dist folder if it exists
if os.path.exists("firebase-key.json"):
    key_dest = os.path.join(dist_path, "firebase-key.json")
    shutil.copy("firebase-key.json", key_dest)
    print("✅ Copied firebase-key.json to dist folder")

print(f"📦 Build complete! Executable: {dist_path}/{app_name}.exe")
print("💡 If the executable doesn't run, check the build log above for errors.")
