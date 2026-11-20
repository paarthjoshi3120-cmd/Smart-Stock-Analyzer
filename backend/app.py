import base64
import hashlib
import hmac
import os
import random
import secrets
import statistics
import threading
import time
from datetime import datetime, timezone
from multiprocessing import Pool, cpu_count
from functools import wraps

import mysql.connector
from flask import Flask, jsonify, request, session, g

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
FEE_RATE = 0.001
PRICE_INTERVAL = float(os.environ.get("SSA_TICK", "5"))
HISTORY_POINTS = 250
# DBMS transaction settings (Unit V): explicit isolation level and retry policy.
TX_ISOLATION = os.environ.get("SSA_ISOLATION", "REPEATABLE READ")
TX_RETRIES = 3
RETRYABLE_ERRNOS = (1213, 1205)   # 1213 = deadlock victim, 1205 = lock wait timeout

app = Flask(__name__)
app.config["SECRET_KEY"] = os.environ.get("SSA_SECRET", "")
app.config["SESSION_COOKIE_HTTPONLY"] = True
app.config["SESSION_COOKIE_SAMESITE"] = "Lax"
if not app.config["SECRET_KEY"]:
    raise RuntimeError("SSA_SECRET must be set in the environment")

DB_CONFIG = {
    "host": os.environ.get("SSA_DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("SSA_DB_PORT", "3306")),
    "user": os.environ.get("SSA_DB_USER", "root"),
    "password": os.environ.get("SSA_DB_PASSWORD", ""),
    "database": os.environ.get("SSA_DB_NAME", "smart_stock_analyzer"),
    "autocommit": True,
}

STOCK_META = {
    "RELIANCE": ("Energy", "IN"), "TCS": ("IT", "IN"), "INFY": ("IT", "IN"),
    "HDFCBANK": ("Banking", "IN"), "WIPRO": ("IT", "IN"), "ICICIBANK": ("Banking", "IN"),
    "BHARTIARTL": ("Telecom", "IN"), "LT": ("Infra", "IN"), "SBIN": ("Banking", "IN"),
    "ASIANPAINT": ("FMCG", "IN"), "TATAMOTORS": ("Auto", "IN"), "MARUTI": ("Auto", "IN"),
    "AAPL": ("Technology", "US"), "MSFT": ("Technology", "US"), "GOOGL": ("Technology", "US"),
    "AMZN": ("E-Commerce", "US"), "TSLA": ("Automotive", "US"), "NVDA": ("Semiconductors", "US"),
    "HSBC": ("Banking", "UK"), "BP": ("Energy", "UK"), "AZN": ("Pharma", "UK"),
    "SONY": ("Electronics", "JP"), "TM": ("Automotive", "JP"), "SAP": ("Software", "DE"),
    "BMW": ("Automotive", "DE"), "BABA": ("E-Commerce", "CN"), "JD": ("E-Commerce", "CN"),
}

# ---------------- password helpers ----------------
def hash_password(password: str) -> str:
    iterations = 260000
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, iterations)
    return "pbkdf2_sha256${}${}${}".format(iterations, base64.b64encode(salt).decode(), base64.b64encode(digest).decode())


def verify_password(password: str, encoded: str) -> bool:
    try:
        scheme, iterations, salt_b64, digest_b64 = encoded.split("$", 3)
        if scheme != "pbkdf2_sha256":
            return False
        salt = base64.b64decode(salt_b64)
        expected = base64.b64decode(digest_b64)
        actual = hashlib.pbkdf2_hmac("sha256", password.encode(), salt, int(iterations))
        return hmac.compare_digest(actual, expected)
    except Exception:
        return False


def new_csrf() -> str:
    token = secrets.token_urlsafe(32)
    session["csrf"] = token
    return token


def require_csrf():
    expected = session.get("csrf")
    supplied = request.headers.get("X-CSRF-Token")
    return bool(expected and supplied and hmac.compare_digest(expected, supplied))

# ---------------- DB helpers ----------------
def connect():
    return mysql.connector.connect(**DB_CONFIG)


def get_db():
    if "db" not in g:
        g.db = connect()
    return g.db


@app.teardown_appcontext
def close_db(_exc):
    db = g.pop("db", None)
    if db:
        db.close()


def query_one(sql, params=()):
    db = get_db(); cur = db.cursor(dictionary=True)
    try:
        cur.execute(sql, params); return cur.fetchone()
    finally:
        cur.close()


def query_all(sql, params=()):
    db = get_db(); cur = db.cursor(dictionary=True)
    try:
        cur.execute(sql, params); return cur.fetchall()
    finally:
        cur.close()


def audit(db, user_id, event, score=None):
    cur = db.cursor()
    try:
        cur.execute("INSERT INTO logs(user_id,event,risk_score,ts) VALUES(%s,%s,%s,%s)",
                    (user_id, event[:255], score, datetime.now(timezone.utc).replace(tzinfo=None)))
    finally:
        cur.close()


def who():
    return f"pid={os.getpid()} thread={threading.current_thread().name}"

# ---------------- OS mutexes ----------------
_registry_guard = threading.Lock()
_stock_locks = {}
_user_locks = {}


def lock_for(registry, key):
    with _registry_guard:
        return registry.setdefault(key, threading.Lock())


class ordered_locks:
    def __init__(self, stock_ids, user_id=None):
        self.locks = [lock_for(_stock_locks, sid) for sid in sorted(set(stock_ids))]
        if user_id is not None:
            self.locks.append(lock_for(_user_locks, user_id))

    def __enter__(self):
        for lk in self.locks: lk.acquire()
        return self

    def __exit__(self, *_):
        for lk in reversed(self.locks): lk.release()

# ---------------- background thread ----------------
_stop = threading.Event()
TICK = {"count": 0, "last": None, "thread": None}


def price_updater():
    while not _stop.wait(PRICE_INTERVAL):
        db = None
        try:
            db = connect(); cur = db.cursor(dictionary=True)
            cur.execute("SELECT stock_id,price FROM stocks ORDER BY stock_id")
            rows = cur.fetchall(); cur.close()
            ids = [r["stock_id"] for r in rows]
            with ordered_locks(ids):
                cur = db.cursor(dictionary=True)
                db.start_transaction()
                try:
                    for row in rows:
                        cur.execute("SELECT price FROM stocks WHERE stock_id=%s FOR UPDATE", (row["stock_id"],))
                        locked = cur.fetchone()
                        new_price = max(0.01, round(float(locked["price"]) * (1 + random.uniform(-0.01, 0.01)), 4))
                        cur.execute("UPDATE stocks SET price=%s WHERE stock_id=%s", (new_price, row["stock_id"]))
                        cur.execute("INSERT INTO price_history(stock_id,price,ts) VALUES(%s,%s,%s)",
                                    (row["stock_id"], new_price, datetime.now(timezone.utc).replace(tzinfo=None)))
                    db.commit()
                except Exception:
                    db.rollback(); raise
                finally:
                    cur.close()
            TICK["count"] += 1; TICK["last"] = datetime.now().strftime("%H:%M:%S")
            print(f"[price_updater] tick #{TICK['count']} {TICK['last']} {who()}", flush=True)
        except Exception as exc:
            print("[price_updater] error:", exc, flush=True)
        finally:
            if db: db.close()


def start_background():
    t = threading.Thread(target=price_updater, name="price-updater", daemon=True)
    t.start(); TICK["thread"] = t

# ---------------- auth ----------------
def current_user():
    uid = session.get("user_id")
    return query_one("SELECT user_id,name,email,role,cash FROM users WHERE user_id=%s", (uid,)) if uid else None


def login_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not session.get("user_id"):
            return jsonify(status="error", message="Authentication required"), 401
        if not current_user():
            session.clear(); return jsonify(status="error", message="Session expired"), 401
        return fn(*args, **kwargs)
    return wrapper


def csrf_required(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not require_csrf():
            return jsonify(status="error", message="CSRF token missing or invalid"), 403
        return fn(*args, **kwargs)
    return wrapper


@app.get("/api/health")
def health():
    row = query_one("SELECT COUNT(*) AS n FROM stocks")
    return jsonify(status="success", database="mysql", stocks=row["n"])


@app.post("/api/register")
def register():
    data = request.get_json(silent=True) or {}
    email = str(data.get("email", "")).strip().lower()
    name = str(data.get("name", "")).strip()
    password = str(data.get("password", ""))
    if not email or not name or len(password) < 6:
        return jsonify(status="error", message="Name, email and a 6+ character password are required"), 400
    db = get_db(); cur = db.cursor()
    try:
        cur.execute("INSERT INTO users(name,email,password_hash,cash,role) VALUES(%s,%s,%s,%s,'USER')",
                    (name, email, hash_password(password), 100000.00))
        db.commit()
        return jsonify(status="success", message="Account created")
    except mysql.connector.IntegrityError:
        db.rollback(); return jsonify(status="error", message="Email already registered"), 409
    finally: cur.close()


@app.post("/api/login")
def login():
    data = request.get_json(silent=True) or {}
    email = str(data.get("email", "")).strip().lower()
    password = str(data.get("password", ""))
    user = query_one("SELECT user_id,name,email,password_hash,role,cash FROM users WHERE email=%s", (email,))
    if not user or not verify_password(password, user["password_hash"]):
        return jsonify(status="error", message="Invalid username or password"), 401
    session.clear(); session["user_id"] = user["user_id"]
    token = new_csrf()
    db = get_db(); #db.start_transaction()
    try:
        audit(db, user["user_id"], f"LOGIN {who()}")
        db.commit()
    except Exception:
        db.rollback(); raise
    return jsonify(status="success", csrf_token=token, user={k: user[k] for k in ("user_id","name","email","role","cash")})


@app.post("/api/logout")
@login_required
@csrf_required
def logout():
    uid = session.get("user_id"); db = get_db(); db.start_transaction()
    try:
        audit(db, uid, f"LOGOUT {who()}"); db.commit()
    except Exception:
        db.rollback(); raise
    session.clear(); return jsonify(status="success", message="Logged out")

# ---------------- market ----------------
def history_prices(db, stock_id, n=HISTORY_POINTS):
    cur = db.cursor(dictionary=True)
    try:
        cur.execute("SELECT price FROM price_history WHERE stock_id=%s ORDER BY id DESC LIMIT %s", (stock_id, n))
        return [float(r["price"]) for r in reversed(cur.fetchall())]
    finally: cur.close()


def risk_metrics(prices):
    if len(prices) < 3: return 0.0, 0.0
    rets = [prices[i] / prices[i-1] - 1 for i in range(1, len(prices))]
    vol = statistics.pstdev(rets)
    peak, mdd = prices[0], 0.0
    for p in prices:
        peak = max(peak, p); mdd = max(mdd, (peak-p)/peak)
    return vol, mdd


def risk_score(prices):
    vol, mdd = risk_metrics(prices)
    return round(min(1.0, 0.55 * min(1.0, vol * 50) + 0.45 * min(1.0, mdd)), 4)


def decision(score):
    if score < 0.40: return {"key":"allow","label":"Low Risk","message":"Trade allowed"}
    if score < 0.70: return {"key":"reminder","label":"Moderate Risk","message":"Review before trading"}
    if score < 0.85: return {"key":"strong","label":"High Risk","message":"Explicit confirmation required"}
    return {"key":"restrict","label":"Restricted","message":"Buying is disabled"}


def recommendation(prices):
    if len(prices) < 20: return "HOLD"
    sma10 = sum(prices[-10:]) / 10; sma20 = sum(prices[-20:]) / 20
    return "BUY" if sma10 > sma20 * 1.01 else ("SELL" if sma10 < sma20 * 0.99 else "HOLD")


@app.get("/api/stocks")
@login_required
def stocks():
    db = get_db(); rows = query_all("SELECT stock_id,symbol,company,price,available_qty FROM stocks ORDER BY stock_id")
    out=[]
    for r in rows:
        prices=history_prices(db,r["stock_id"],20); prev=prices[-2] if len(prices)>1 else float(r["price"])
        change=(float(r["price"])-prev)/prev*100 if prev else 0
        score=risk_score(history_prices(db,r["stock_id"]))
        sector,country=STOCK_META.get(r["symbol"],("Other","--"))
        out.append({"stock_id":r["stock_id"],"symbol":r["symbol"],"name":r["company"],"company":r["company"],
                    "sector":sector,"country":country,"price":float(r["price"]),"changePct":round(change,2),
                    "available_qty":r["available_qty"],"risk_score":score,"decision":decision(score),
                    "recommendation":recommendation(history_prices(db,r["stock_id"]))})
    return jsonify(status="success", data=out)


@app.get("/api/portfolio")
@login_required
def portfolio():
    uid=session["user_id"]; cash=float(query_one("SELECT cash FROM users WHERE user_id=%s",(uid,))["cash"])
    rows=query_all("""SELECT s.symbol,s.company,s.price,p.quantity,p.avg_price,p.fees_paid
                      FROM portfolio p JOIN stocks s ON s.stock_id=p.stock_id
                      WHERE p.user_id=%s AND p.quantity>0 ORDER BY s.symbol""",(uid,))
    holdings=[]; total=0.0
    for r in rows:
        price=float(r["price"]); qty=int(r["quantity"]); value=price*qty; total+=value
        fees=float(r["fees_paid"]); avg=float(r["avg_price"])
        holdings.append({"symbol":r["symbol"],"company":r["company"],"quantity":qty,"avg_price":avg,
                         "price":price,"value":round(value,2),"profit_loss":round((price-avg)*qty-fees,2)})
    return jsonify(status="success",cash=round(cash,2),holdings_value=round(total,2),holdings=holdings)


@app.get("/api/history")
@login_required
def history():
    rows=query_all("""SELECT t.txn_id,s.symbol,t.type,t.quantity,t.price,t.fee,t.ts
                     FROM transactions t JOIN stocks s ON s.stock_id=t.stock_id
                     WHERE t.user_id=%s ORDER BY t.txn_id DESC LIMIT 200""",(session["user_id"],))
    return jsonify(status="success",data=[{**r,"price":float(r["price"]),"fee":float(r["fee"]),"ts":r["ts"].isoformat(sep=' ')} for r in rows])

# ---------------- trade ----------------
class TradeError(Exception):
    def __init__(self,message,code=400,**extra): self.message=message; self.code=code; self.extra=extra


@app.post("/api/trade")
@login_required
@csrf_required
def trade():
    data=request.get_json(silent=True) or {}; symbol=str(data.get("symbol","")).upper(); side=str(data.get("type","")).upper()
    confirm=bool(data.get("confirm",False))
    try: qty=int(data.get("quantity"))
    except (TypeError,ValueError): qty=0
    if side not in ("BUY","SELL") or qty<=0: return jsonify(status="error",message="Invalid trade details"),400
    stock=query_one("SELECT stock_id FROM stocks WHERE symbol=%s",(symbol,))
    if not stock: return jsonify(status="error",message="Unknown stock"),404
    sid=stock["stock_id"]; uid=session["user_id"]
    for attempt in range(1,TX_RETRIES+1):
      with ordered_locks([sid],uid):
        db=get_db(); db.start_transaction(isolation_level=TX_ISOLATION); cur=db.cursor(dictionary=True)
        try:
            # Row-level DBMS locks are the final authority; Python mutexes only protect this process.
            cur.execute("SELECT price,available_qty FROM stocks WHERE stock_id=%s FOR UPDATE",(sid,)); s=cur.fetchone()
            cur.execute("SELECT cash FROM users WHERE user_id=%s FOR UPDATE",(uid,)); u=cur.fetchone()
            cur.execute("SELECT quantity,avg_price,fees_paid FROM portfolio WHERE user_id=%s AND stock_id=%s FOR UPDATE",(uid,sid)); pf=cur.fetchone()
            score=risk_score(history_prices(db,sid)); dec=decision(score); price=float(s["price"])
            if side=="BUY" and dec["key"]=="restrict": raise TradeError(f"{symbol} is restricted (risk {score}). Buying disabled.",403,decision=dec["key"])
            if side=="BUY" and dec["key"]=="strong" and not confirm: raise TradeError(f"Strong warning (risk {score}). Send confirmation to proceed.",409,needs_confirmation=True,decision=dec["key"])
            fee=round(price*qty*FEE_RATE,2)
            if side=="BUY":
                total=round(price*qty+fee,2)
                if float(u["cash"])<total: raise TradeError("Insufficient cash")
                if int(s["available_qty"])<qty: raise TradeError("Not enough shares available")
                cur.execute("UPDATE users SET cash=cash-%s WHERE user_id=%s",(total,uid))
                cur.execute("UPDATE stocks SET available_qty=available_qty-%s WHERE stock_id=%s",(qty,sid))
                if pf: 
                    new_qty=int(pf["quantity"])+qty; new_avg=(float(pf["avg_price"])*int(pf["quantity"])+price*qty)/new_qty
                    cur.execute("UPDATE portfolio SET quantity=%s,avg_price=%s,fees_paid=fees_paid+%s WHERE user_id=%s AND stock_id=%s",(new_qty,new_avg,fee,uid,sid))
                else: cur.execute("INSERT INTO portfolio(user_id,stock_id,quantity,avg_price,fees_paid) VALUES(%s,%s,%s,%s,%s)",(uid,sid,qty,price,fee))
            else:
                if not pf or int(pf["quantity"])<qty: raise TradeError("Not enough shares to sell")
                total=round(price*qty-fee,2); left=int(pf["quantity"])-qty
                cur.execute("UPDATE users SET cash=cash+%s WHERE user_id=%s",(total,uid)); cur.execute("UPDATE stocks SET available_qty=available_qty+%s WHERE stock_id=%s",(qty,sid))
                if left==0: cur.execute("DELETE FROM portfolio WHERE user_id=%s AND stock_id=%s",(uid,sid))
                else: cur.execute("UPDATE portfolio SET quantity=%s,fees_paid=%s WHERE user_id=%s AND stock_id=%s",(left,float(pf["fees_paid"])*left/int(pf["quantity"]),uid,sid))
            cur.execute("INSERT INTO transactions(user_id,stock_id,type,quantity,price,fee,ts) VALUES(%s,%s,%s,%s,%s,%s,%s)",(uid,sid,side,qty,price,fee,datetime.now(timezone.utc).replace(tzinfo=None)))
            audit(db,uid,f"{side} {qty} {symbol} [{dec['key']}] {who()}",score); db.commit()
            return jsonify(status="success",message=f"Executed {side} {qty} x {symbol}",price=price,fee=fee,total=total,risk_score=score,decision=dec["key"],handled_by=who(),**({"attempts":attempt} if attempt>1 else {}))
        except TradeError as e:
            db.rollback(); audit(db,uid,f"TRADE REJECTED {side} {qty} {symbol} {who()}",None); db.commit()
            return jsonify(status="error",message=e.message,**e.extra),e.code
        except mysql.connector.Error as e:
            # InnoDB picked this transaction as a deadlock victim (1213) or the row lock
            # wait timed out (1205): everything was rolled back, so it is safe to retry.
            db.rollback()
            if e.errno in RETRYABLE_ERRNOS and attempt<TX_RETRIES:
                audit(db,uid,f"TRADE RETRY errno={e.errno} attempt={attempt} {side} {qty} {symbol} {who()}",None); db.commit()
                time.sleep(0.05*attempt); continue
            raise
        except Exception:
            db.rollback(); raise
        finally: cur.close()
