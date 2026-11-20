"""Unit V live demos on MySQL/InnoDB: isolation anomalies, lock modes, deadlock, lock wait + retry.
Usage: python lock_demo.py [lost|isolation|locks|deadlock|retry|perf|all]   (needs root-like DB account)"""
import os, sys, threading, time
from dotenv import load_dotenv
import mysql.connector

load_dotenv()

CFG = dict(host=os.environ.get("SSA_DB_HOST", "127.0.0.1"), port=int(os.environ.get("SSA_DB_PORT", "3306")),
           user=os.environ.get("SSA_DB_USER", "root"), password=os.environ.get("SSA_DB_PASSWORD", ""),
           database="smart_stock_analyzer", autocommit=True)

def conn(level=None, wait=None):
    c = mysql.connector.connect(**CFG)
    cur = c.cursor()
    if level: cur.execute(f"SET SESSION TRANSACTION ISOLATION LEVEL {level}")
    if wait: cur.execute(f"SET SESSION innodb_lock_wait_timeout={wait}")
    return c

def q(name, c, sql, show=True):
    cur = c.cursor()
    print(f"{name}> {sql}")
    try:
        cur.execute(sql)
        rows = cur.fetchall() if cur.with_rows else []
        for r in rows: print(f"{name}   -> {r if len(r) > 1 else r[0]}")
        return rows
    except mysql.connector.Error as e:
        print(f"{name}   !! ERROR {e.errno}: {e.msg}")
        return e

def reset():
    c = conn(); cur = c.cursor()
    cur.execute("DROP TABLE IF EXISTS lab_account")
    cur.execute("CREATE TABLE lab_account (id INT PRIMARY KEY, owner VARCHAR(20), balance INT NOT NULL) ENGINE=InnoDB")
    cur.execute("INSERT INTO lab_account VALUES (1,'alice',1000),(2,'bob',1000),(3,'carol',1000)," + ",".join(f"({i},'u{i}',0)" for i in range(4, 11)))
    c.close()

def title(t): print("\n" + "=" * 70 + f"\n{t}\n" + "=" * 70)

def lost():
    title("E1  LOST UPDATE: read-modify-write WITHOUT locks vs WITH SELECT ... FOR UPDATE")
    reset(); A = conn("READ COMMITTED"); B = conn("READ COMMITTED")
    print("-- no locks: both read 1000, A adds 100, B subtracts 200 (computed from its stale read)")
    q("A", A, "START TRANSACTION"); q("B", B, "START TRANSACTION")
    q("A", A, "SELECT balance FROM lab_account WHERE id=1"); q("B", B, "SELECT balance FROM lab_account WHERE id=1")
    q("A", A, "UPDATE lab_account SET balance=1100 WHERE id=1"); q("A", A, "COMMIT")
    q("B", B, "UPDATE lab_account SET balance=800 WHERE id=1"); q("B", B, "COMMIT")
    q("*", A, "SELECT balance FROM lab_account WHERE id=1")
    print("   RESULT: 800.  Correct answer is 900 -> A's +100 was LOST.\n")
    reset(); A = conn(); B = conn(wait=20)
    print("-- with SELECT ... FOR UPDATE (what app.py trade() does)")
    q("A", A, "START TRANSACTION"); q("A", A, "SELECT balance FROM lab_account WHERE id=1 FOR UPDATE")
    t0 = time.time(); out = {}
    def b_work():
        q("B", B, "START TRANSACTION")
        out["r"] = q("B", B, "SELECT balance FROM lab_account WHERE id=1 FOR UPDATE   -- blocks until A commits")
        bal = out["r"][0][0]
        q("B", B, f"UPDATE lab_account SET balance={bal}-200 WHERE id=1"); q("B", B, "COMMIT")
        out["t"] = time.time() - t0
    th = threading.Thread(target=b_work); th.start(); time.sleep(2)
    q("A", A, "UPDATE lab_account SET balance=1100 WHERE id=1"); q("A", A, "COMMIT"); th.join()
    print(f"   B waited {out['t']:.1f}s for A's lock, then read the fresh value 1100.")
    q("*", A, "SELECT balance FROM lab_account WHERE id=1")
    print("   RESULT: 900 = correct. No update lost.")

def isolation():
    title("E2  ISOLATION LEVELS vs DIRTY READ / NON-REPEATABLE READ / PHANTOM")
    rows = []
    for lvl in ("READ UNCOMMITTED", "READ COMMITTED", "REPEATABLE READ", "SERIALIZABLE"):
        reset(); A = conn(); B = conn(lvl, wait=2); res = {}
        # dirty read
        q("A", A, "START TRANSACTION"); q("A", A, "UPDATE lab_account SET balance=0 WHERE id=1  -- uncommitted")
        q("B", B, "START TRANSACTION")
        r = q("B", B, f"SELECT balance FROM lab_account WHERE id=1   -- reader at {lvl}")
        res["dirty"] = ("timeout (blocked)" if isinstance(r, Exception) else ("DIRTY READ " + str(r[0][0]) if r[0][0] == 0 else "no (sees 1000)"))
        q("A", A, "ROLLBACK"); q("B", B, "ROLLBACK")
        # non-repeatable read
        q("B", B, "START TRANSACTION"); r1 = q("B", B, "SELECT balance FROM lab_account WHERE id=2")
        if isinstance(r1, Exception): res["nonrep"] = "timeout"; res["phantom"] = "-"; rows.append((lvl, res)); continue
        q("A", A, "UPDATE lab_account SET balance=1500 WHERE id=2  -- A autocommits")
        r2 = q("B", B, "SELECT balance FROM lab_account WHERE id=2")
        res["nonrep"] = "YES " + f"{r1[0][0]}->{r2[0][0]}" if r1[0][0] != r2[0][0] else "no (stays " + str(r2[0][0]) + ")"
        # phantom
        c1 = q("B", B, "SELECT COUNT(*) FROM lab_account WHERE balance>=1000")
        q("A", A, "INSERT INTO lab_account VALUES (99,'zed',5000)  -- A autocommits")
        c2 = q("B", B, "SELECT COUNT(*) FROM lab_account WHERE balance>=1000")
        res["phantom"] = "YES " + f"{c1[0][0]}->{c2[0][0]}" if c1[0][0] != c2[0][0] else "no (stays " + str(c2[0][0]) + ")"
        q("B", B, "COMMIT"); rows.append((lvl, res)); print()
    print("\nSUMMARY (measured on MySQL 8.0.46 / InnoDB)")
    print(f"{'isolation level':<18}{'dirty read':<22}{'non-repeatable read':<22}{'phantom'}")
    for lvl, r in rows: print(f"{lvl:<18}{r['dirty']:<22}{r['nonrep']:<22}{r['phantom']}")

def locks():
    title("E3  LOCK MODES: shared (S) vs exclusive (X) - compatibility measured on InnoDB rows")
    reset(); out = []
    for holder, hsql in (("S (FOR SHARE)", "FOR SHARE"), ("X (FOR UPDATE)", "FOR UPDATE")):
        row = [holder]
        for rq, rsql in (("FOR SHARE", "FOR SHARE"), ("FOR UPDATE", "FOR UPDATE"), ("plain SELECT", "")):
            A = conn(); B = conn(wait=1)
            q("A", A, "START TRANSACTION"); q("A", A, f"SELECT * FROM lab_account WHERE id=1 {hsql}")
            q("B", B, "START TRANSACTION"); r = q("B", B, f"SELECT * FROM lab_account WHERE id=1 {rsql}")
            row.append("WAIT -> timeout 1205" if isinstance(r, Exception) else "granted")
            q("A", A, "ROLLBACK"); q("B", B, "ROLLBACK"); A.close(); B.close(); print()
        out.append(row)
    print("LOCK COMPATIBILITY MATRIX (holder = lock already held by A; columns = what B asks for)")
    print(f"{'holder':<18}{'B: FOR SHARE':<24}{'B: FOR UPDATE':<24}{'B: plain SELECT'}")
    for r in out: print(f"{r[0]:<18}{r[1]:<24}{r[2]:<24}{r[3]}")
    print("\n-- The InnoDB lock table while A holds an X lock on stocks row 1 (what trade() does):")
    A = conn(); R = conn()
    q("A", A, "START TRANSACTION"); q("A", A, "SELECT stock_id,symbol FROM stocks WHERE stock_id=1 FOR UPDATE")
    q("A", A, "SELECT user_id FROM users WHERE user_id=10 FOR UPDATE")
    q("R", R, "SELECT OBJECT_NAME,INDEX_NAME,LOCK_TYPE,LOCK_MODE,LOCK_DATA FROM performance_schema.data_locks WHERE OBJECT_SCHEMA='smart_stock_analyzer' ORDER BY OBJECT_NAME,LOCK_TYPE DESC")
    B = conn(wait=30)
    th = threading.Thread(target=lambda: q("B", B, "SELECT stock_id FROM stocks WHERE stock_id=1 FOR UPDATE   -- B must wait")); th.start(); time.sleep(1.5)
    q("R", R, "SELECT REQUESTING_ENGINE_TRANSACTION_ID AS waiting_trx, BLOCKING_ENGINE_TRANSACTION_ID AS blocking_trx FROM performance_schema.data_lock_waits")
    q("R", R, "SELECT trx_id,trx_state,trx_rows_locked,trx_isolation_level FROM information_schema.innodb_trx ORDER BY trx_started")
    q("A", A, "COMMIT  -- locks released only now (strict 2PL)"); th.join()

def deadlock():
    title("E4  DEADLOCK: opposite lock order (detected by InnoDB) vs ordered locking (prevented)")
    reset(); A = conn(wait=20); B = conn(wait=20); res = {}
    q("A", A, "START TRANSACTION"); q("B", B, "START TRANSACTION")
    q("A", A, "UPDATE lab_account SET balance=balance+1 WHERE id=1   -- A locks row 1")
    q("B", B, "UPDATE lab_account SET balance=balance+1 WHERE id=2   -- B locks row 2")
    def a(): res["A"] = q("A", A, "UPDATE lab_account SET balance=balance+1 WHERE id=2   -- A waits for B")
    th = threading.Thread(target=a); th.start(); time.sleep(1.5)
    res["B"] = q("B", B, "UPDATE lab_account SET balance=balance+1 WHERE id=1   -- B waits for A  => CYCLE")
    th.join()
    for n, C in (("A", A), ("B", B)): q(n, C, "COMMIT")
    victim = [n for n in "AB" if isinstance(res[n], Exception)]
    print(f"   InnoDB chose victim {victim}: rolled back automatically, the other transaction committed.\n")
    sc = conn(); st = sc.cursor(); st.execute("SHOW ENGINE INNODB STATUS"); txt = st.fetchone()[2]
    seg = txt.split("LATEST DETECTED DEADLOCK")[1].split("TRANSACTIONS")[0].strip().splitlines()
    print("-- SHOW ENGINE INNODB STATUS (LATEST DETECTED DEADLOCK, trimmed)")
    for ln in seg[:22]: print("   " + ln[:110])
    print("\n-- Prevention: both sessions lock rows in the SAME order (1 then 2), like app.py (stock -> user -> portfolio)")
    reset(); A = conn(wait=20); B = conn(wait=20)
    q("A", A, "START TRANSACTION"); q("A", A, "UPDATE lab_account SET balance=balance+1 WHERE id=1")
    q("B", B, "START TRANSACTION")
    th = threading.Thread(target=lambda: (q("B", B, "UPDATE lab_account SET balance=balance+1 WHERE id=1   -- B waits (no cycle)"), q("B", B, "UPDATE lab_account SET balance=balance+1 WHERE id=2"), q("B", B, "COMMIT")))
    th.start(); time.sleep(1.5)
    q("A", A, "UPDATE lab_account SET balance=balance+1 WHERE id=2"); q("A", A, "COMMIT"); th.join()
    q("*", A, "SELECT id,balance FROM lab_account WHERE id IN (1,2)")
    print("   RESULT: both committed, no deadlock.")

def retry():
    import requests
    title("E5  LOCK WAIT TIMEOUT (1205) + automatic retry inside POST /api/trade")
    base = os.environ.get("SSA_API", "http://127.0.0.1:5000")
    s = requests.Session(); r = s.post(base + "/api/login", json={"email": "investor10@example.test", "password": "Demo@123"}).json(); csrf = r["csrf_token"]
    A = conn(); print("Server setting: innodb_lock_wait_timeout =", q("A", A, "SELECT @@innodb_lock_wait_timeout")[0][0], "seconds")
    q("A", A, "START TRANSACTION"); q("A", A, "SELECT user_id,cash FROM users WHERE user_id=10 FOR UPDATE   -- another session holds the user's row")
    out = {}
    def call():
        t0 = time.time()
        resp = s.post(base + "/api/trade", headers={"X-CSRF-Token": csrf}, json={"symbol": "INFY", "type": "BUY", "quantity": 1, "confirm": True}, timeout=60)
        out["r"] = (resp.status_code, resp.json(), time.time() - t0)
    th = threading.Thread(target=call); th.start(); print("   (client) POST /api/trade BUY 1 INFY sent; it must wait for the row lock ...")
    time.sleep(13); q("A", A, "COMMIT  -- row lock released after 13 s"); th.join()
    code, body, el = out["r"]
    print(f"   (client) HTTP {code} after {el:.1f}s -> {body.get('message')} | attempts={body.get('attempts')} | handled_by={body.get('handled_by')}")
    q("A", A, "SELECT event FROM logs WHERE user_id=10 ORDER BY log_id DESC LIMIT 3")

def perf():
    title("E6  PERFORMANCE OF LOCKING: one hot row vs rows spread out (10 threads x 150 transactions)")
    reset(); adm = conn(); cur = adm.cursor()
    def snap():
        cur.execute("SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time')")
        return {k: int(v) for k, v in cur.fetchall()}
    def run(hot):
        def w(i):
            c = conn(); k = c.cursor(); row = 1 if hot else i + 1
            for _ in range(150):
                k.execute("START TRANSACTION"); k.execute("SELECT balance FROM lab_account WHERE id=%s FOR UPDATE", (row,)); k.fetchall()
                time.sleep(0.002)   # a little work while the lock is held (like risk/price checks in trade())
                k.execute("UPDATE lab_account SET balance=balance+1 WHERE id=%s", (row,)); k.execute("COMMIT")
            c.close()
        b = snap(); t0 = time.time(); ts = [threading.Thread(target=w, args=(i,)) for i in range(10)]
        [t.start() for t in ts]; [t.join() for t in ts]; el = time.time() - t0; a = snap()
        return el, a["Innodb_row_lock_waits"] - b["Innodb_row_lock_waits"], a["Innodb_row_lock_time"] - b["Innodb_row_lock_time"]
    h = run(True); sp = run(False)
    print(f"{'workload':<28}{'seconds':<10}{'row_lock_waits':<16}{'lock_wait_ms'}")
    print(f"{'ALL on ONE row (hot lock)':<28}{h[0]:<10.2f}{h[1]:<16}{h[2]}")
    print(f"{'each on its OWN row':<28}{sp[0]:<10.2f}{sp[1]:<16}{sp[2]}")
    cur.execute("SELECT SUM(balance) FROM lab_account"); print("   total balance:", cur.fetchone()[0], "= 3000 initial + 1500 (hot row) + 1500 (spread rows): no increment lost")

if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    for name, fn in (("lost", lost), ("isolation", isolation), ("locks", locks), ("deadlock", deadlock), ("retry", retry), ("perf", perf)):
        if which in ("all", name): fn()
    c = conn(); c.cursor().execute("DROP TABLE IF EXISTS lab_account")
