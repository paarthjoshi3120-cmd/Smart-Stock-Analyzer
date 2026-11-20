"""OS: background daemon thread and Event-based shutdown."""
import threading, time
stop=threading.Event()
def price_updater():
    tick=0
    while not stop.wait(1):
        tick+=1
        print("Background price update",tick)
if __name__ == "__main__":
    t=threading.Thread(target=price_updater,name="price-updater",daemon=True)
    t.start()
    try: time.sleep(4)
    finally: stop.set(); t.join(timeout=2)
    print("Updater stopped.")
