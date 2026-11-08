import ctypes
import json
from pathlib import Path
import socket
import sys
import time
import traceback


def run(factory, installer, cache, pip, runtime):
    root = Path(cache) / 'original-libraries'
    results = {'socket_options': [], 'clipboard_calls': []}
    for package, requirement in (('websocket', 'websocket-client'), ('pyperclip', 'pyperclip')):
        plan = factory(str(root), json.dumps({package: requirement}), runtime, pip, 'original-libraries-v1')
        assert plan.cached, 'Run original-library tests first to populate the diagnostic cache'
        plan.activate()

    import websocket
    from websocket._socket import DEFAULT_SOCKET_OPTION
    for option in DEFAULT_SOCKET_OPTION:
        with socket.socket() as probe:
            try:
                probe.setsockopt(*option)
                results['socket_options'].append({'option': option, 'status': 'passed'})
                print('SOCKET_OPTION', option, 'OK', flush=True)
            except Exception as error:
                results['socket_options'].append({'option': option, 'status': 'failed', 'error': str(error)})
                print('SOCKET_OPTION', option, str(error), flush=True)

    if sys.platform == 'win32':
        from ctypes import wintypes
        print('CTYPES_SIZES', {name: ctypes.sizeof(value) for name, value in
                             [('BOOL', wintypes.BOOL), ('int', ctypes.c_int), ('long', ctypes.c_long),
                              ('void_p', ctypes.c_void_p)]}, flush=True)
        for result_type in (wintypes.BOOL, ctypes.c_int, ctypes.c_uint, ctypes.c_long):
            library = ctypes.WinDLL('user32', use_last_error=True)
            opening = library.OpenClipboard
            opening.argtypes = [ctypes.c_void_p]
            opening.restype = result_type
            closing = library.CloseClipboard
            closing.argtypes = []
            closing.restype = ctypes.c_int
            ctypes.set_last_error(0)
            result = opening(None)
            last_error = ctypes.get_last_error()
            results['clipboard_calls'].append({'type': result_type.__name__, 'result': int(result), 'error': last_error})
            print('OPEN_CLIPBOARD', result_type.__name__, repr(result), type(result).__name__,
                  'last_error', last_error, flush=True)
            if result:
                print('CLOSE_CLIPBOARD', repr(closing()), flush=True)
            time.sleep(0.05)
        import pyperclip
        try:
            # Only report success, never the clipboard text.
            previous = pyperclip.paste()
            results['pyperclip'] = {'status': 'read_passed', 'copy_tested': False}
            print('PYPERCLIP_PASTE_OK', isinstance(previous, str), flush=True)
        except Exception as error:
            denied = all(call['result'] == 0 and call['error'] == 5 for call in results['clipboard_calls'])
            results['pyperclip'] = {'status': 'blocked' if denied else 'failed', 'error': str(error)}
            print('PYPERCLIP_RESULT', json.dumps(results['pyperclip']), flush=True)
    # This is an official websocket-client argument, not a replacement transport
    # or a mutation of the installed package's defaults.
    try:
        websocket_check(root, prepared_socket=True)
        results['websocket_prepared'] = {'status': 'passed'}
        print('WEBSOCKET_PREPARED_SOCKET_PASSED', flush=True)
    except Exception as error:
        results['websocket_prepared'] = {'status': 'failed', 'error': str(error)}
        traceback.print_exc()
        raise
    finally:
        (root / 'interop-results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')


run
