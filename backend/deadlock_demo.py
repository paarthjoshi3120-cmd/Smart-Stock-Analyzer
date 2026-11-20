"""DBMS demonstration: Demonstrates deadlock detection and prevention through consistent lock ordering.
Run from this folder with: python deadlock_demo.py
Requires the project's MySQL configuration in backend/.env.
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "common"))
import lock_demo_engine as demo

if __name__ == "__main__":
    demo.deadlock()
