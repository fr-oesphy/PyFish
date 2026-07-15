import time
import threading
from java.util.concurrent import Executors, TimeUnit, CompletableFuture
from java.util.function import Supplier
from java.lang import Thread

print(f"[java_asyncio] CompletableFuture type: {CompletableFuture}")

_executor = Executors.newCachedThreadPool()

class Future:
    def __init__(self):
        self._jfuture = CompletableFuture()
        self._callbacks = []

    def set_result(self, result):
        self._jfuture.complete(result)
        for cb in self._callbacks:
             cb(self)

    def set_exception(self, exception):
        # CompletableFuture requires a Throwable; compatibility callers sometimes pass a
        # message or another Python value instead.
        self._jfuture.completeExceptionally(exception if isinstance(exception, Exception) else Exception(str(exception)))
        for cb in self._callbacks:
             cb(self)

    def done(self):
        return self._jfuture.isDone()

    def result(self):
        try:
            return self._jfuture.get()
        except java.util.concurrent.ExecutionException as e:
            raise e.getCause()
        except Exception as e:
            import traceback
            traceback.print_exc()
            raise e

    def add_done_callback(self, fn):
        if self.done():
            fn(self)
        else:
            self._callbacks.append(fn)

    def __await__(self):
        if not self.done():
            yield self
        return self.result()

class Task(Future):
    def __init__(self, coro, loop=None):
        super().__init__()
        self._coro = coro
        self._loop = loop or get_event_loop()
        self._loop.call_soon(self._step)

    def _step(self, value=None, exc=None):
        try:
            if exc:
                result = self._coro.throw(exc)
            else:
                result = self._coro.send(value)
            
            if isinstance(result, Future):
                result.add_done_callback(self._wakeup)
            elif result is None:
                 # A bare yield gives other callbacks a chance to run on the loop thread.
                 self._loop.call_soon(self._step)
            else:
                 # Foreign awaitables are outside this shim's protocol. Leaving the task
                 # pending is safer than resuming it with a value it did not request.
                 pass

        except StopIteration as e:
            self.set_result(e.value)
        except Exception as e:
            # Complete the backing future after logging; otherwise run_until_complete would
            # wait forever on a failed coroutine.
            print(f"[java_asyncio] Error in Task step: {e}")
            import traceback
            traceback.print_exc()
            self.set_exception(e)

    def _wakeup(self, future):
        try:
            res = future.result()
            self._loop.call_soon(self._step, res)
        except Exception as e:
            self._loop.call_soon(self._step, None, e)

class AbstractEventLoop:
    def __init__(self):
        self._scheduler = Executors.newSingleThreadScheduledExecutor()
        self._running = False

    def run_until_complete(self, future):
        self._running = True
        
        if hasattr(future, '__await__'):
            future = Task(future, self)

        # Blocking is intentional here: Minecraft scripts use run() as a synchronous bridge
        # into the Java-owned scheduler.
        try:
            return future.result()
        finally:
            self._running = False
    
    def _create_runnable(self, callback, args):
        def safe_run():
            try:
                 callback(*args)
            except Exception as e:
                 print(f"[java_asyncio] Error in callback {callback}: {e}")
                 import traceback
                 traceback.print_exc()
        
        # The explicit Runnable wrapper avoids GraalPy choosing ExecutorService's Callable
        # overload for a Python callable.
        return Thread(safe_run)

    def call_soon(self, callback, *args):
        self._scheduler.execute(self._create_runnable(callback, args))

    def call_later(self, delay, callback, *args):
        self._scheduler.schedule(self._create_runnable(callback, args), int(delay * 1000), TimeUnit.MILLISECONDS)

    def create_task(self, coro):
        return Task(coro, self)

    def time(self):
        return time.time()
    
    def close(self):
        self._scheduler.shutdown()

_loop = AbstractEventLoop()

def get_event_loop():
    return _loop

def new_event_loop():
    global _loop
    _loop = AbstractEventLoop()
    return _loop

def set_event_loop(loop):
    global _loop
    _loop = loop

def sleep(delay):
    f = Future()
    get_event_loop().call_later(delay, f.set_result, None)
    return f

def create_task(coro):
    return get_event_loop().create_task(coro)

def run(coro):
    return get_event_loop().run_until_complete(coro)

def gather(*coros_or_futures):
    # Preserve input order even though callbacks complete on the scheduler thread. This is a
    # small compatibility subset; cancellation and return_exceptions are not implemented.
    results = [None] * len(coros_or_futures)
    count = len(coros_or_futures)
    if count == 0:
        f = Future()
        f.set_result([])
        return f
    
    outer_future = Future()
    
    def _done_callback(idx, res):
        nonlocal count
        results[idx] = res
        count -= 1
        if count == 0:
            outer_future.set_result(results)
    
    def _err_callback(idx, exc):
        if not outer_future.done():
            outer_future.set_exception(exc)

    for i, item in enumerate(coros_or_futures):
        if hasattr(item, '__await__'):
            task = create_task(item)
        else:
            task = item
            
        def mk_cb(index):
            return lambda fut: _done_callback(index, fut.result())
        
        task.add_done_callback(mk_cb(i))

    return outer_future
