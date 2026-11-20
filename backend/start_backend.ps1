$ErrorActionPreference = 'Stop'
if (!(Test-Path '.venv')) { python -m venv .venv }
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
if (!(Test-Path '.env')) { Copy-Item '.env.example' '.env'; Write-Host 'Edit .env with your MySQL password and SSA_SECRET, then run this script again.'; exit }
python run_backend.py
