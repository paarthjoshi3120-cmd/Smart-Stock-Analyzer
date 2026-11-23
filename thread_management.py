"""OS: thread creation, identification and joining."""
import threading
def task(number):
    print(f"Worker {number}: name={threading.current_thread().name}, ident={threading.get_ident()}")
if __name__ == "__main__":
    threads=[threading.Thread(target=task,args=(i,),name=f"worker-{i}") for i in range(3)]
    for t in threads: t.start()
    for t in threads: t.join()
    print("All threads completed.")
