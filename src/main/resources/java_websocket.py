from java.net.http import HttpClient, WebSocket
from java.net import URI
from java.util.concurrent import CountDownLatch, TimeUnit, LinkedBlockingQueue
import threading
import time

class WebSocketApp:
    def __init__(self, url, header=None, on_open=None, on_message=None, on_error=None, on_close=None, on_ping=None, on_pong=None, on_cont_message=None, keep_running=True, get_mask_key=None, cookie=None, subprotocols=None):
        self.url = url
        self.header = header
        self.on_open = on_open
        self.on_message = on_message
        self.on_error = on_error
        self.on_close = on_close
        self.keep_running = keep_running
        self._ws = None
        self._latch = CountDownLatch(1) 
        
    def run_forever(self, origin=None, host=None, sslopt=None, ping_interval=0, ping_timeout=None, http_proxy_host=None, http_proxy_port=None, http_no_proxy=None, http_proxy_auth=None, skip_utf8_validation=False, host_ip_address=None, suppress_origin=False):
        
        listener = _AppListener(self)
        client = HttpClient.newHttpClient()
        builder = client.newWebSocketBuilder()
        
        if self.header:
            for k, v in self.header.items():
                builder.header(k, v)
        
        # Java establishes the socket asynchronously, but websocket-client's run_forever API
        # owns the calling thread until close.
        future = builder.buildAsync(URI.create(self.url), listener)
        
        try:
            self._ws = future.join()
        except Exception as e:
            if self.on_error:
                self.on_error(self, e)
            return

        try:
            # CountDownLatch.await cannot be written with attribute syntax in Python.
            getattr(self._latch, 'await')()
        except InterruptedException:
            pass
        except KeyboardInterrupt:
            self.close()

    def send(self, data):
        if self._ws:
            self._ws.sendText(str(data), True)

    def close(self):
        if self._ws:
            self._ws.sendClose(WebSocket.NORMAL_CLOSURE, "Bye")
            self._latch.countDown()

class _AppListener(WebSocket.Listener):
    def __init__(self, app):
        self.app = app
        self._buffer = []

    def onOpen(self, webSocket):
        if self.app.on_open:
            try:
                self.app.on_open(self.app)
            except Exception as e:
                pass
        webSocket.request(1)

    def onText(self, webSocket, data, last):
        self._buffer.append(str(data))
        if last:
            full_msg = "".join(self._buffer)
            self._buffer = []
            if self.app.on_message:
                try:
                    self.app.on_message(self.app, full_msg)
                except Exception as e:
                    if self.app.on_error: self.app.on_error(self.app, e)
        
        webSocket.request(1)
        return None

    def onClose(self, webSocket, statusCode, reason):
        if self.app.on_close:
            try:
                self.app.on_close(self.app, statusCode, reason)
            except:
                pass
        self.app._latch.countDown()
        return None

    def onError(self, webSocket, error):
        if self.app.on_error:
            self.app.on_error(self.app, error)

# Queueing complete text frames gives the synchronous facade backpressure without polling.
def create_connection(url, timeout=None, **kwargs):
    return _SyncWebSocket(url, timeout)

class _SyncWebSocket:
    def __init__(self, url, timeout=None):
        self.url = url
        self.timeout = timeout
        self._queue = LinkedBlockingQueue()
        self._ws = None
        self._open_latch = CountDownLatch(1)
        
        client = HttpClient.newHttpClient()
        builder = client.newWebSocketBuilder()
        if timeout:
             builder.connectTimeout(java.time.Duration.ofSeconds(timeout))

        listener = _SyncListener(self._queue, self._open_latch)
        self._ws = builder.buildAsync(URI.create(url), listener).join()
        getattr(self._open_latch, 'await')()
    
    def send(self, data):
        self._ws.sendText(str(data), True)
    
    def recv(self):
        return self._queue.take()
    
    def close(self):
        self._ws.sendClose(WebSocket.NORMAL_CLOSURE, "")

class _SyncListener(WebSocket.Listener):
    def __init__(self, queue, open_latch):
        self.queue = queue
        self.open_latch = open_latch
        self._buffer = []

    def onOpen(self, webSocket):
        self.open_latch.countDown()
        webSocket.request(1)

    def onText(self, webSocket, data, last):
        self._buffer.append(str(data))
        if last:
            self.queue.put("".join(self._buffer))
            self._buffer = []
        webSocket.request(1)
        return None
