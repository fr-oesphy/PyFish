import java.lang.Thread as JavaThread
import java.util.concurrent.LinkedBlockingQueue as LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock as ReentrantLock
import java.util.concurrent.Executors as Executors
import java.util.concurrent.CompletableFuture as CompletableFuture
import java.util.concurrent.TimeUnit as TimeUnit
import java.lang.Runtime as Runtime
import queue

class Process:
    """Thread-backed Process subset. It provides concurrency, not process isolation."""
    def __init__(self, target=None, args=(), kwargs={}, name=None):
        self._target = target
        self._args = args
        self._kwargs = kwargs
        self._name = name or "Process-JavaThread"
        self._thread = None
        
        def run_wrapper():
            try:
                if self._target:
                    self._target(*self._args, **self._kwargs)
            except Exception as e:
                import traceback
                print(f"[java_multiprocessing] Exception in process '{self._name}':")
                traceback.print_exc()
                
        self._run_wrapper = run_wrapper

    def start(self):
        self._thread = JavaThread(self._run_wrapper)
        if self._name:
            self._thread.setName(self._name)
        self._thread.start()

    def join(self, timeout=None):
        if self._thread:
            if timeout is None:
                self._thread.join()
            else:
                self._thread.join(int(timeout * 1000))

    def is_alive(self):
        if self._thread:
            return self._thread.isAlive()
        return False
        
    @property
    def name(self):
        return self._thread.getName() if self._thread else self._name

class Queue:
    """Thread-safe queue backed by LinkedBlockingQueue."""
    def __init__(self, maxsize=0):
        if maxsize <= 0:
            self._q = LinkedBlockingQueue()
        else:
            self._q = LinkedBlockingQueue(maxsize)
            
    def put(self, item, block=True, timeout=None):
        if block:
            if timeout is None:
                self._q.put(item)
            else:
                success = self._q.offer(item, int(timeout * 1000), TimeUnit.MILLISECONDS)
                if not success:
                    raise queue.Full()
        else:
            success = self._q.offer(item)
            if not success:
                raise queue.Full()

    def get(self, block=True, timeout=None):
        if block:
            if timeout is None:
                return self._q.take()
            else:
                item = self._q.poll(int(timeout * 1000), TimeUnit.MILLISECONDS)
                if item is None:
                    raise queue.Empty()
                return item
        else:
            item = self._q.poll()
            if item is None:
                raise queue.Empty()
            return item

    def empty(self):
        return self._q.isEmpty()
        
    def qsize(self):
        return self._q.size()

class Lock:
    """Re-entrant lock backed by java.util.concurrent."""
    def __init__(self):
        self._lock = ReentrantLock()
        
    def acquire(self, block=True, timeout=None):
        if block:
            if timeout is None:
                self._lock.lock()
                return True
            else:
                return self._lock.tryLock(int(timeout * 1000), TimeUnit.MILLISECONDS)
        else:
            return self._lock.tryLock()

    def release(self):
        self._lock.unlock()

    def __enter__(self):
        self.acquire()
        return self

    def __exit__(self, type, value, traceback):
        self.release()

class Pool:
    """Fixed Java thread pool with the small API surface PyFish scripts use."""
    def __init__(self, processes=None):
        if processes is None:
            processes = Runtime.getRuntime().availableProcessors()
        self._processes = processes
        self._executor = Executors.newFixedThreadPool(processes)
        
    def map(self, func, iterable):
        """Map in parallel and preserve input order in the returned list."""
        futures = []
        for item in iterable:
            # Bind now; Java may execute the closure after the Python loop advances.
            def task_wrapper(arg=item):
                return func(arg)

            future = CompletableFuture.supplyAsync(task_wrapper, self._executor)
            futures.append(future)
            
        results = []
        for f in futures:
            results.append(f.get())
            
        return results

    def close(self):
        self._executor.shutdown()
        
    def join(self):
        self._executor.awaitTermination(365, TimeUnit.DAYS)
