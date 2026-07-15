import java

ClipboardCompat = java.type("fr.oesphy.pyfish.compat.PyFishClipboardCompat")

def copy(text):
    """
    Copies the text to the system clipboard.
    """
    try:
        ClipboardCompat.copy("" if text is None else str(text))
    except BaseException as e:
        print(f"[PyFish] Clipboard copy failed: {e}")

def paste():
    """
    Returns the text from the system clipboard.
    """
    try:
        value = ClipboardCompat.paste()
        return "" if value is None else str(value)
    except BaseException as e:
        print(f"[PyFish] Clipboard paste failed: {e}")
    return ""
