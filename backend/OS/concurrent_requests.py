"""OS: concurrent request simulation using a thread pool."""
from concurrent.futures import ThreadPoolExecutor
import threading, time
def handle_request(number):
    print("Handling request",number,"on",threading.current_thread().name)
    time.sleep(0.2)
    return f"request-{number}-complete"
if __name__ == "__main__":
    with ThreadPoolExecutor(max_workers=5) as pool:
        print(list(pool.map(handle_request,range(1,11))))
