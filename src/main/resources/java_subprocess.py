import java.lang.ProcessBuilder as ProcessBuilder
import java.lang.Process as Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

PIPE = -1
STDOUT = -2
DEVNULL = -3

class CompletedProcess:
    def __init__(self, args, returncode, stdout=None, stderr=None):
        self.args = args
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        
    def check_returncode(self):
        if self.returncode != 0:
            raise CalledProcessError(self.returncode, self.args, self.stdout, self.stderr)

class CalledProcessError(Exception):
    def __init__(self, returncode, cmd, output=None, stderr=None):
        self.returncode = returncode
        self.cmd = cmd
        self.output = output
        self.stderr = stderr
        super().__init__(f"Command '{cmd}' returned non-zero exit status {returncode}.")

def _coerce_python_bytes(value):
    if value is None:
        return b""
    if isinstance(value, (bytes, bytearray)):
        return bytes(value)

    try:
        return bytes(value)
    except TypeError:
        pass

    try:
        return bytes((int(part) & 0xFF for part in value))
    except Exception:
        raise TypeError(f"Unable to convert subprocess output to bytes: {type(value)}")

def run(args, capture_output=False, text=False, input=None, check=False):
    """
    Run the command described by args. Wait for command to complete, then return a CompletedProcess instance.
    """
    if isinstance(args, str):
        # This is not shell parsing and cannot preserve quoted arguments. Passing a list is
        # the portable form, just as it is with shell=False in CPython.
        args_list = args.split()
    else:
        args_list = list(args)

    pb = ProcessBuilder(args_list)

    # stdin piping is intentionally absent from this subset; accepting input and silently
    # dropping it would be worse than making the limitation visible.
    
    try:
        process = pb.start()
        
        stdout_data = None
        stderr_data = None
        
        if capture_output:
            # Streams are drained serially. This is fine for short helper commands, but a
            # future Popen implementation must pump both concurrently to avoid full-buffer
            # deadlocks with chatty children.
            stdout_bytes = process.getInputStream().readAllBytes()
            stderr_bytes = process.getErrorStream().readAllBytes()
            stdout_buffer = _coerce_python_bytes(stdout_bytes)
            stderr_buffer = _coerce_python_bytes(stderr_bytes)
            
            if text:
                stdout_data = stdout_buffer.decode("utf-8", errors="replace")
                stderr_data = stderr_buffer.decode("utf-8", errors="replace")
            else:
                stdout_data = stdout_buffer
                stderr_data = stderr_buffer
        
        exit_code = process.waitFor()
        
        completed = CompletedProcess(args, exit_code, stdout_data, stderr_data)
        
        if check:
            completed.check_returncode()
            
        return completed
        
    except Exception as e:
        raise e

def check_output(args, **kwargs):
    kwargs['capture_output'] = True
    return run(args, **kwargs).stdout
