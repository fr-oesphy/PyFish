import sys

DEBUG = 10
INFO = 20
WARNING = 30
ERROR = 40
CRITICAL = 50

# The runtime injects the SLF4J logger before executing this module, keeping Python output in
# the same file and rotation policy as Minecraft's logs.
_logger = globals().get("log")

def getLogger(name=None):
    return _Logger()

class _Logger:
    def debug(self, msg, *args, **kwargs):
        _log(DEBUG, msg, args)
    def info(self, msg, *args, **kwargs):
        _log(INFO, msg, args)
    def warning(self, msg, *args, **kwargs):
        _log(WARNING, msg, args)
    def error(self, msg, *args, **kwargs):
        _log(ERROR, msg, args)
    def critical(self, msg, *args, **kwargs):
        _log(CRITICAL, msg, args)
    def exception(self, msg, *args, **kwargs):
        _log(ERROR, msg, args)

def debug(msg, *args, **kwargs):
    _log(DEBUG, msg, args)

def info(msg, *args, **kwargs):
    _log(INFO, msg, args)

def warning(msg, *args, **kwargs):
    _log(WARNING, msg, args)

def error(msg, *args, **kwargs):
    _log(ERROR, msg, args)

def critical(msg, *args, **kwargs):
    _log(CRITICAL, msg, args)

def exception(msg, *args, **kwargs):
    error(msg, *args, **kwargs)

def _log(level, msg, args):
    if args:
        msg = msg % args
    
    if level == DEBUG:
        _logger.debug(str(msg))
    elif level == INFO:
        _logger.info(str(msg))
    elif level == WARNING:
        _logger.warn(str(msg))
    elif level == ERROR or level == CRITICAL:
        _logger.error(str(msg))

def basicConfig(**kwargs):
    # Minecraft owns logger configuration; changing it from one PYZ mod would affect all mods.
    pass
