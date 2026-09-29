"""PyInstaller entry point for WhatsApp Media Suite.

Frozen with:
    pyinstaller --noconfirm --onefile --windowed --name WhatsAppMediaSuite wmsuite_launch.py
"""

from wmsuite.gui import run

if __name__ == "__main__":
    raise SystemExit(run())
