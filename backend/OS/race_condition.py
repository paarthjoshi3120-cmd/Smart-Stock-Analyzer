"""OS: race condition demonstration and synchronized correction."""
import threading
value=0
def unsafe_increment():
    global value
    for _ in range(10000):
        old=value
        value=old+1
def safe_increment(lock):
    global value
    for _ in range(10000):
        with lock: value += 1
if __name__ == "__main__":
    lock=threading.Lock()
    threads=[threading.Thread(target=safe_increment,args=(lock,)) for _ in range(5)]
    [t.start() for t in threads]; [t.join() for t in threads]
    print("Synchronized result (expected 50000):",value)
