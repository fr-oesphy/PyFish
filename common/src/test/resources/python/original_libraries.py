import asyncio
import base64
import hashlib
import io
import importlib.metadata
import json
import logging
import os
from pathlib import Path
import socket
import sqlite3
import subprocess
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def logging_check(root):
    output = io.StringIO()
    handler = logging.StreamHandler(output)
    logger = logging.getLogger('pyfish-original-test')
    logger.addHandler(handler)
    try:
        logger.warning('value=%s', 42)
        assert output.getvalue().strip() == 'value=42'
    finally:
        logger.removeHandler(handler)
        handler.close()


def pathlib_check(root):
    target = root / 'files with spaces'
    target.mkdir(exist_ok=True)
    source = target / 'source.txt'
    source.write_text('PyFish 42', encoding='utf-8')
    moved = source.replace(target / 'moved.txt')
    assert moved.read_text(encoding='utf-8') == 'PyFish 42'
    assert moved in list(target.glob('*.txt'))
    moved.unlink()


def uuid_check(root):
    generated = {uuid.uuid4() for _ in range(100)}
    assert len(generated) == 100 and all(value.version == 4 for value in generated)
    assert str(uuid.uuid5(uuid.NAMESPACE_DNS, 'python.org')) == '886313e1-3b8a-5372-9b90-0c9aee199e5d'
    assert uuid.uuid3(uuid.NAMESPACE_DNS, 'python.org') == uuid.UUID('6fa459ea-ee8a-3ca4-894e-db77e160355e')


def sqlite_check(root):
    with sqlite3.connect(root / 'original.sqlite') as database:
        database.execute('drop table if exists sample')
        database.execute('create table sample (value integer, payload blob)')
        database.execute('insert into sample values (?, ?)', (42, b'\x00\xff'))
        assert database.execute('select * from sample').fetchone() == (42, b'\x00\xff')
        database.commit()
    with sqlite3.connect(root / 'original.sqlite') as database:
        assert database.execute('select count(*) from sample').fetchone()[0] == 1
    print('SQLITE_BACKEND', sqlite3.sqlite_version)


def asyncio_check(root):
    async def exercise():
        async def work(value):
            await asyncio.sleep(0.01)
            return value * 2
        assert await asyncio.gather(work(20), work(21)) == [40, 42]
        server = await asyncio.start_server(lambda reader, writer: writer.close(), '127.0.0.1', 0)
        async with server:
            reader, writer = await asyncio.open_connection('127.0.0.1', server.sockets[0].getsockname()[1])
            assert await asyncio.wait_for(reader.read(), 5) == b''
            writer.close()
            await writer.wait_closed()
        task = asyncio.create_task(asyncio.sleep(60))
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
        else:
            raise AssertionError('Cancellation failed')
    asyncio.run(exercise())


def subprocess_check(root):
    command = ['cmd.exe', '/d', '/c', 'echo PyFish42'] if os.name == 'nt' else ['/bin/sh', '-c', 'printf PyFish42']
    result = subprocess.run(command, capture_output=True, text=True, check=True, timeout=10)
    assert result.stdout.strip() == 'PyFish42' and result.returncode == 0
    command = ['cmd.exe', '/d', '/c', 'exit 7'] if os.name == 'nt' else ['/bin/sh', '-c', 'exit 7']
    assert subprocess.run(command, timeout=10).returncode == 7


def multiprocessing_check(root):
    import multiprocessing
    # Embedded PyFish has no Python executable to spawn. Exercise an actual child,
    # not multiprocessing.dummy, which would merely disguise a thread as a process.
    context = multiprocessing.get_context('spawn')
    event = context.Event()
    process = context.Process(target=event.set)
    try:
        process.start()
        process.join(10)
        assert process.exitcode == 0 and event.is_set()
    finally:
        if process.is_alive():
            process.terminate()
            process.join(5)


def requests_check(root):
    import requests
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Set-Cookie', 'probe=42')
            self.end_headers()
            self.wfile.write(json.dumps({'path': self.path}).encode())

        def do_POST(self):
            body = self.rfile.read(int(self.headers['Content-Length']))
            self.send_response(201)
            self.end_headers()
            self.wfile.write(body)
    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        with requests.Session() as session:
            session.trust_env = False
            # With GraalPy's Java socket backend the ephemeral port is assigned at
            # listen(), after HTTPServer has cached its server_port attribute.
            url = 'http://127.0.0.1:' + str(server.socket.getsockname()[1])
            response = session.get(url, params={'value': 42}, timeout=5)
            response.raise_for_status()
            assert response.json()['path'] == '/?value=42'
            assert session.cookies.get('probe') == '42'
            response = session.post(url, json={'value': 42}, timeout=5)
            assert response.status_code == 201 and response.json() == {'value': 42}
            response = session.get('https://pypi.org/pypi/requests/json', timeout=15)
            response.raise_for_status()
            assert response.json()['info']['name'] == 'requests'
    finally:
        server.shutdown()
        server.server_close()
        thread.join(5)


def websocket_check(root, prepared_socket=False, tls=False):
    import websocket
    import ssl
    from websocket._socket import DEFAULT_SOCKET_OPTION
    # Fail before starting the echo thread if the runtime cannot support the
    # client's unmodified defaults. Do not patch the library to make it pass.
    if not prepared_socket:
        with socket.socket() as probe:
            for option in DEFAULT_SOCKET_OPTION:
                try:
                    probe.setsockopt(*option)
                except OSError as error:
                    raise RuntimeError(f'websocket-client default socket option {option}: {error}') from error
    failures = []
    server_context = None
    ssl_options = {}
    if tls:
        server_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_context.load_cert_chain(str(root / 'websocket-test.crt'), str(root / 'websocket-test.key'))
        ssl_options = {'ca_certs': str(root / 'websocket-test.crt'), 'check_hostname': True}
    listener = socket.socket()
    listener.bind(('127.0.0.1', 0))
    listener.listen(1)
    listener.settimeout(5)
    def serve():
        try:
            client = listener.accept()[0]
            client.settimeout(5)
            if server_context is not None:
                try:
                    client = server_context.wrap_socket(client, server_side=True)
                except BaseException:
                    client.close()
                    raise
            with client:
                client.settimeout(5)
                stream = client.makefile('rb')
                stream.readline()
                headers = {}
                while line := stream.readline().strip():
                    key, value = line.decode().split(':', 1)
                    headers[key.lower()] = value.strip()
                accept = base64.b64encode(hashlib.sha1((headers['sec-websocket-key']
                    + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
                client.sendall(b'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: '
                               + accept + b'\r\n\r\n')
                for _ in range(2):
                    first, second = stream.read(2)
                    size = second & 127
                    assert size < 126 and second & 128
                    mask = stream.read(4)
                    data = stream.read(size)
                    data = bytes(value ^ mask[index % 4] for index, value in enumerate(data))
                    client.sendall(bytes([first, len(data)]) + data)
                stream.close()
        except BaseException as error:
            failures.append(str(error))
    thread = threading.Thread(target=serve, daemon=True)
    thread.start()
    try:
        endpoint = listener.getsockname()
        url = ('wss' if tls else 'ws') + '://127.0.0.1:' + str(endpoint[1])
        assert not tls or not prepared_socket, 'TLS fixture exercises the normal library-managed transport'
        options = {'socket': socket.create_connection(endpoint, timeout=5)} if prepared_socket else {}
        connection = websocket.create_connection(url, timeout=5, sslopt=ssl_options,
                                                 http_no_proxy=['127.0.0.1'], **options)
        try:
            connection.send('PyFish42')
            assert connection.recv() == 'PyFish42'
            connection.send_binary(b'\x00\xff')
            assert connection.recv() == b'\x00\xff'
        finally:
            connection.close(timeout=1)
        thread.join(5)
        assert not thread.is_alive() and not failures, failures
        thread = threading.Thread(target=serve, daemon=True)
        thread.start()
        messages = []
        def on_open(app):
            app.send('PyFish42')
            app.send(b'\x00\xff', opcode=websocket.ABNF.OPCODE_BINARY)
        def on_message(app, value):
            messages.append(value)
            if len(messages) == 2:
                app.close()
        options = {'socket': socket.create_connection(endpoint, timeout=5)} if prepared_socket else {}
        app = websocket.WebSocketApp(url, on_open=on_open, on_message=on_message, **options)
        deadline = threading.Timer(8, app.close)
        deadline.daemon = True
        deadline.start()
        try:
            app.run_forever(http_no_proxy=['127.0.0.1'], sslopt=ssl_options)
            assert messages == ['PyFish42', b'\x00\xff'], messages
        finally:
            deadline.cancel()
        if not prepared_socket:
            # The adapter only changes library defaults. A script explicitly
            # asking for an unsupported timer must still receive the real error.
            for name, value in (('TCP_KEEPIDLE', 30), ('TCP_KEEPINTVL', 10), ('TCP_KEEPCNT', 3)):
                number = getattr(socket, name, None)
                if number is None:
                    continue
                option = (socket.IPPROTO_TCP, number, value)
                with socket.socket() as probe:
                    try:
                        probe.setsockopt(*option)
                    except OSError as unsupported:
                        expected_errno = unsupported.errno
                    else:
                        continue
                try:
                    unexpected = websocket.create_connection(
                        'ws://127.0.0.1:' + str(endpoint[1]), timeout=0.5,
                        http_no_proxy=['127.0.0.1'], sockopt=(option,))
                except OSError as error:
                    assert error.errno == expected_errno, (name, error)
                else:
                    unexpected.close()
                    raise AssertionError('Explicit socket option was silently discarded: ' + name)
    finally:
        thread.join(10)
        listener.close()
    assert not thread.is_alive() and not failures, failures


def pillow_check(root):
    from PIL import Image, ImageDraw, ImageFont, ImageFilter
    import PIL._imaging as native
    assert native.__file__.lower().endswith(('.pyd', '.so'))
    image = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    ImageDraw.Draw(image).rectangle((0, 0, 15, 15), fill=(255, 0, 0, 255))
    assert image.getpixel((4, 4)) == (255, 0, 0, 255)
    image.resize((16, 16)).filter(ImageFilter.BLUR)
    path = root / 'original.png'
    image.save(path)
    with Image.open(path) as loaded:
        assert loaded.convert('RGBA').getpixel((4, 4)) == (255, 0, 0, 255)
    assert ImageFont.load_default().getbbox('PyFish')[2] > 0


class ClipboardUnavailable(RuntimeError):
    pass


def pyperclip_check(root):
    import pyperclip
    if os.name == 'nt':
        import ctypes
        library = ctypes.WinDLL('user32', use_last_error=True)
        opening, closing = library.OpenClipboard, library.CloseClipboard
        opening.argtypes, opening.restype = [ctypes.c_void_p], ctypes.c_int
        closing.argtypes, closing.restype = [], ctypes.c_int
        ctypes.set_last_error(0)
        available = opening(None)
        error = ctypes.get_last_error()
        if available:
            closing()
        elif error == 5:
            raise ClipboardUnavailable('Windows denies clipboard access (OpenClipboard error 5); '
                                       'copy/paste needs an interactive session with clipboard permission')
    previous = pyperclip.paste()
    try:
        pyperclip.copy('PyFish clipboard probe 42')
        assert pyperclip.paste() == 'PyFish clipboard probe 42'
    finally:
        pyperclip.copy(previous)


def run_standard_checks(cache):
    root = Path(cache)
    root.mkdir(parents=True, exist_ok=True)
    for check in (asyncio_check, logging_check, pathlib_check, uuid_check, sqlite_check, subprocess_check):
        check(root)
    print('Original standard-library checks passed', flush=True)


def run_checks(factory, pip_runner, cache, pip, runtime):
    root = Path(cache) / 'original-libraries'
    root.mkdir(parents=True, exist_ok=True)
    results = {}
    packages = {'requests': 'requests', 'websocket': 'websocket-client', 'pillow': 'Pillow', 'pyperclip': 'pyperclip'}
    checks = {'asyncio': asyncio_check, 'logging': logging_check, 'pathlib': pathlib_check, 'uuid': uuid_check,
              'sqlite3': sqlite_check, 'subprocess': subprocess_check, 'multiprocessing': multiprocessing_check,
              'requests': requests_check, 'websocket': websocket_check, 'pillow': pillow_check, 'pyperclip': pyperclip_check}
    for name, check in checks.items():
        stage = 'functionality'
        installation = None
        try:
            if name in packages:
                stage = 'installation'
                installation = factory(str(root), json.dumps({name: packages[name]}), runtime, pip, 'original-libraries-v1')
                if not installation.cached:
                    if not pip_runner(installation.arguments()):
                        raise RuntimeError('No compatible binary wheel set could be installed; see pip output')
                    installation.commit()
                installation.activate()
                stage = 'functionality'
            check(root)
            results[name] = {'status': 'passed'}
            if name in packages:
                results[name]['version'] = importlib.metadata.version(packages[name])
        except Exception as error:
            results[name] = {'status': 'blocked' if isinstance(error, ClipboardUnavailable) else 'failed',
                             'stage': 'environment' if isinstance(error, ClipboardUnavailable) else stage,
                             'error': str(error)}
            if installation is not None:
                installation.abort()
        print('ORIGINAL_LIBRARY', name, json.dumps(results[name]), flush=True)
    (root / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    required = {'asyncio', 'logging', 'pathlib', 'uuid', 'sqlite3', 'subprocess', 'requests', 'websocket'}
    regressions = sorted(name for name in required if results[name]['status'] != 'passed')
    if regressions:
        raise AssertionError('Original library regressions: ' + ', '.join(regressions))
    return json.dumps(results)


run_checks
