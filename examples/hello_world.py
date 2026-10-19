"""
Hello World Example for PyFish

This is a minimal example demonstrating basic PyFish functionality.
Place this file in:
- pyfish/scripts/hello_world.py (global script)
- pyfish/my_modpack/scripts/hello_world.py (namespace script)

The script will:
1. Log a message when loaded
2. Send a message to players when they join
3. Register a simple command handler
"""

from pyfish import mc, log_print


def on_player_join(event):
    """Called when a player joins the server."""
    player = event.getPlayer() or event.getEntity()
    if player is not None:
        mc.send_message(player, "Hello from PyFish! Python scripting is working!")
        log_print(f"Player {player.getName().getString()} joined the server")


# Register the event callback
mc.on("on_player_join", on_player_join)

# Log that the script loaded successfully
log_print("Hello World script loaded successfully!")
