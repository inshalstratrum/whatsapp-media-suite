@echo off
REM Build WhatsApp Media Suite .exe locally.
REM Requires: Python 3.9+ on PATH, then:  pip install -r requirements.txt pyinstaller
pyinstaller --noconfirm --onefile --windowed --name WhatsAppMediaSuite wmsuite_launch.py
echo.
echo Done. The exe is in dist\WhatsAppMediaSuite.exe
pause
