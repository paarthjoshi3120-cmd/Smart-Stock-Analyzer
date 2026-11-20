"""OS: lock ordering to prevent circular-wait deadlocks."""
import threading
a=threading.Lock(); b=threading.Lock()
def task(name):
    # Every thread acquires locks in the same order: a then b.
    with a:
        with b:
            print(name,"entered critical section")
if __name__ == "__main__":
    ts=[threading.Thread(target=task,args=(f"T{i}",)) for i in range(2)]
    [t.start() for t in ts]; [t.join() for t in ts]
    print("Completed without deadlock.")
