from pyfish import items
import numpy as np
import numpy._core._multiarray_umath as native

assert native.__file__.lower().endswith((".pyd", ".so")), native.__file__
assert np.array_equal(np.array([[1, 2], [3, 4]]) @ np.array([[5, 6], [7, 8]]), [[19, 22], [43, 50]])
assert abs(float(np.linalg.det([[1., 2.], [3., 5.]])) + 1) < 1e-10
assert np.allclose(np.fft.fft([1., 0., 0., 0.]), [1., 1., 1., 1.])

from pathlib import Path
checks = Path('pyfish/pyfish_ore_tests/websocket_checks.py')
if checks.exists():
    namespace = {'__name__': 'pyfish_websocket_game_checks'}
    exec(compile(checks.read_text(encoding='utf-8'), str(checks), 'exec'), namespace)
    namespace['websocket_check'](checks.parent)
    print('[PyFish/NativeTest] websocket-client default create_connection and WebSocketApp passed')
    if (checks.parent / 'websocket-test.crt').exists():
        namespace['websocket_check'](checks.parent, tls=True)
        print('[PyFish/NativeTest] websocket-client wss TLS certificate verification and callbacks passed')

items.register_item("native_probe", {"texture": "minecraft:item/emerald"})
print("[PyFish/NativeTest] NumPy", np.__version__, "loaded from", native.__file__)
