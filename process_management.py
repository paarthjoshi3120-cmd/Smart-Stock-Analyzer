"""OS: process creation, PID and parent PID."""
import multiprocessing as mp, os
def worker():
    print("Child process PID:", os.getpid())
    print("Parent process PID:", os.getppid())
if __name__ == "__main__":
    print("Main process PID:", os.getpid())
    p=mp.Process(target=worker, name="ssa-child")
    p.start(); p.join()
    print("Exit code:", p.exitcode)
