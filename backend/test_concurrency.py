"""REST concurrency test for the MySQL/InnoDB row-locking trade engine.

Run with the Flask server running:   python test_concurrency.py

Why this version differs from the first one: the old test could report PASS
while ZERO trades succeeded (the seeded users have very little cash, so every
BUY was rejected). This version sizes each order so that only a few of the 20
simultaneous orders can possibly be afforded, then checks the invariants that
only correct transaction isolation can guarantee:
  * cash never goes negative and exactly matches the sum of executed trades
  * the quantity gained equals (successful BUYs x order size)  -> no lost update
  * concurrent SELLs never sell more shares than are held       -> no oversell
  * at least one trade really succeeded (the test is not vacuous)
"""
import concurrent.futures
import math
import os
import threading
import requests

BASE = os.environ.get("SSA_API", "http://127.0.0.1:5000")
USER = os.environ.get("SSA_TEST_USER", "investor02@example.test")
PASSWORD = os.environ.get("SSA_TEST_PASSWORD", "Demo@123")
WORKERS = int(os.environ.get("SSA_TEST_WORKERS", "20"))


def login():
    s = requests.Session()
    r = s.post(BASE + "/api/login", json={"email": USER, "password": PASSWORD}, timeout=15)
    r.raise_for_status()
    data = r.json()
    assert data["status"] == "success", data
    return s, data["csrf_token"]


def holdings(s, symbol):
    p = s.get(BASE + "/api/portfolio", timeout=10).json()
    qty = next((h["quantity"] for h in p["holdings"] if h["symbol"] == symbol), 0)
    return float(p["cash"]), qty


def fire(sessions, symbol, side, qty):
    """Send one order per session at the same instant (barrier = maximum contention)."""
    barrier = threading.Barrier(len(sessions))

    def one(pair):
        s, csrf = pair
        barrier.wait()
        r = s.post(BASE + "/api/trade", headers={"X-CSRF-Token": csrf},
                   json={"symbol": symbol, "type": side, "quantity": qty, "confirm": True}, timeout=60)
        return r.status_code, r.json()

    with concurrent.futures.ThreadPoolExecutor(max_workers=len(sessions)) as ex:
        return list(ex.map(one, sessions))


def main():
    sessions = [login() for _ in range(WORKERS)]          # log in first, trade afterwards
    s0 = sessions[0][0]
    stocks = s0.get(BASE + "/api/stocks", timeout=10).json()["data"]
    cheap = min((x for x in stocks if x["decision"]["key"] != "restrict"), key=lambda x: x["price"])
    symbol, price = cheap["symbol"], cheap["price"]
    cash0, qty0 = holdings(s0, symbol)
    unit = price * 1.001                                   # price + 0.1% fee
    qty = max(1, int(cash0 / unit / 4))                    # at most about 4 orders are affordable
    print(f"stock={symbol} price={price} cash={cash0:.2f} order_size={qty} workers={WORKERS}")
    assert cash0 > unit, "test user has too little cash for even one share"

    # 1) concurrent BUY orders that together cost far more than the cash balance
    results = fire(sessions, symbol, "BUY", qty)
    ok = [b for c, b in results if c == 200 and b.get("status") == "success"]
    bad = [(c, b) for c, b in results if not (c == 200 and b.get("status") == "success")]
    cash1, qty1 = holdings(s0, symbol)
    print(f"BUY : {len(ok)} succeeded, {len(bad)} rejected | quantity {qty0}->{qty1} | cash {cash0:.2f}->{cash1:.2f}")
    assert ok, "no BUY succeeded: the test would be vacuous"
    assert all(c in (400, 403, 409) for c, _ in bad), f"unexpected failure codes: {bad[:3]}"
    assert len(ok) <= math.floor(cash0 / (unit * qty * 0.99)), "more BUYs succeeded than the cash can pay for (overdraft!)"
    assert qty1 - qty0 == len(ok) * qty, "lost update on portfolio quantity"
    assert cash1 >= 0, "negative cash"
    assert abs((cash0 - cash1) - sum(b["total"] for b in ok)) < 0.05, "cash does not match executed trades"

    # 2) concurrent SELL orders: each tries to sell the whole new position
    sell_qty = max(1, qty1 - qty0)
    results = fire(sessions, symbol, "SELL", sell_qty)
    sold = [b for c, b in results if c == 200 and b.get("status") == "success"]
    cash2, qty2 = holdings(s0, symbol)
    print(f"SELL: {len(sold)} succeeded of {WORKERS} | quantity {qty1}->{qty2} | cash {cash1:.2f}->{cash2:.2f}")
    assert qty2 == qty1 - len(sold) * sell_qty, "lost update on SELL"
    assert qty2 >= 0 and len(sold) * sell_qty <= qty1, "oversell: sold more shares than held"
    print("PASS: InnoDB row locks + transactions kept every invariant under", WORKERS, "concurrent orders.")


if __name__ == "__main__":
    main()
