"""OS: multiprocessing Pool and parallel task execution."""
from multiprocessing import Pool, cpu_count
def square(n): return n*n
if __name__ == "__main__":
    values=list(range(1,9))
    with Pool(processes=cpu_count()) as pool:
        print("CPU cores:",cpu_count())
        print("Input:",values)
        print("Parallel squares:",pool.map(square,values))
