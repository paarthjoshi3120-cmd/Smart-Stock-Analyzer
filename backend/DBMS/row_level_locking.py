"""DBMS demonstration: Demonstrates InnoDB row locks: FOR UPDATE and FOR SHARE.
Run from this folder with: python row_level_locking.py
Requires the project's MySQL configuration in backend/.env.
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "common"))
import lock_demo_engine as demo

if __name__ == "__main__":
    demo.locks()
