"""OS: mutex, mutual exclusion and critical section."""
import threading
counter=0
mutex=threading.Lock()
def increment():
    global counter
    for _ in range(10000):
        with mutex:             # critical section protected by mutex
            counter += 1
if __name__ == "__main__":
    ts=[threading.Thread(target=increment) for _ in range(5)]
    [t.start() for t in ts]; [t.join() for t in ts]
    print("Expected counter=50000; actual counter=",counter)
