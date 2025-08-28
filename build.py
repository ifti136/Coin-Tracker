import PyInstaller.__main__
import os

# Configuration
app_name = "CoinTracker"
script = "coin_tracker.py"
icon = "coin.ico"  # Optional: replace with your own coin icon
dist_path = "./dist"
build_path = "./build"

# Ensure directories exist
os.makedirs(dist_path, exist_ok=True)
os.makedirs(build_path, exist_ok=True)

# PyInstaller build parameters
params = [
    script,
    f'--name={app_name}',
    '--windowed',            # no console window
    '--onefile',             # bundle into single exe
    f'--icon={icon}',        # app icon
    f'--distpath={dist_path}',
    f'--workpath={build_path}',
    '--noconfirm',
    '--clean'
]

# Run PyInstaller
PyInstaller.__main__.run(params)
