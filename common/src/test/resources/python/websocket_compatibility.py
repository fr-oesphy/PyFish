from types import SimpleNamespace


def run_compatibility_checks():
    timers = [(6, 3, 30), (6, 17, 10), (6, 16, 3)]
    supported = [(6, 1, 1), (65535, 8, 1)]
    calls = []
    failures = {}
    class Probe:
        def __enter__(self):
            return self
        def __exit__(self, *args):
            pass
        def setsockopt(self, *option):
            calls.append(option)
            if option in failures:
                raise OSError(failures[option], 'fixture error')
    sockets = SimpleNamespace(IPPROTO_TCP=6, AF_INET=2, SOCK_STREAM=1, TCP_KEEPIDLE=3,
                              TCP_KEEPINTVL=17, TCP_KEEPCNT=16, socket=lambda *args: Probe())
    defaults = supported + timers
    identity = id(defaults)
    failures.update({option: errno.ENOPROTOOPT for option in timers})
    assert _filter_websocket_defaults(defaults, sockets) == ['TCP_KEEPIDLE', 'TCP_KEEPINTVL', 'TCP_KEEPCNT']
    assert defaults == supported and id(defaults) == identity
    assert calls == timers
    calls.clear()
    assert _filter_websocket_defaults(defaults, sockets) == [] and calls == []

    failures.clear()
    defaults = supported + timers
    assert _filter_websocket_defaults(defaults, sockets) == [] and defaults == supported + timers
    failures[timers[1]] = errno.ENOPROTOOPT
    assert _filter_websocket_defaults(defaults, sockets) == ['TCP_KEEPINTVL']
    assert defaults == supported + [timers[0], timers[2]]
    defaults = supported + timers
    failures[timers[1]] = errno.EACCES
    failures[timers[0]] = errno.ENOPROTOOPT
    try:
        _filter_websocket_defaults(defaults, sockets)
    except OSError as error:
        assert error.errno == errno.EACCES
    else:
        raise AssertionError('Unexpected socket errors were swallowed')
    assert defaults == supported + timers

    failures.clear()
    failures.update({option: errno.ENOPROTOOPT for option in timers})
    custom = [(6, 3, 60), (6, 999, 42)]
    calls.clear()
    defaults = supported + timers + custom
    _filter_websocket_defaults(defaults, sockets)
    assert defaults == supported + custom and calls == timers
    missing_constants = SimpleNamespace(IPPROTO_TCP=6)
    assert _filter_websocket_defaults(defaults, missing_constants) == []
    print('WebSocket adapter checks passed: selective, idempotent, atomic, strict errors and custom defaults')


run_compatibility_checks
