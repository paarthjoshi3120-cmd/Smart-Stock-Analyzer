"""OS: CPU core count and sequential-vs-process-pool timing."""
from multiprocessing import Pool, cpu_count
from time import perf_counter
def work(n):
    total=0
    for i in range(1,n): total += (i*i) % 97
    return total
if __name__ == "__main__":
    jobs=[250000]*max(2,cpu_count())
    t=perf_counter(); [work(n) for n in jobs]; seq=perf_counter()-t
    t=perf_counter()
    with Pool() as p: p.map(work,jobs)
    parallel=perf_counter()-t
    print("Logical CPU cores:",cpu_count())
    print(f"Sequential: {seq:.3f}s | Process pool: {parallel:.3f}s")
