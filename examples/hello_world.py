"""
Hello World Example for PyFish

This is a minimal example demonstrating basic PyFish functionality.
Place this file in:
|- pyfish/scripts/hello_world.py (global script)
|- pyfish/my_modpack/scripts/hello_world.py (namespace script)

The script will:
1. Log a message when loaded
2. Send a message to players when they join (using new decorator syntax)
3. Schedule a delayed task
"""

from pyfish import mc, log_print


# Using the new decorator syntax for event registration
@mc.event("on_player_join")
def on_player_join(event):
    """Called when a player joins the server."""
    player = event.player if hasattr(event, 'player') else (event.getPlayer() or event.getEntity())
    if player is not None:
        # Use the wrapped player API for a more Pythonic interface
        wrapped_player = mc.wrap_player(player)
        wrapped_player.send_message("Hello from PyFish! Python scripting is working!")
        log_print(f"Player {wrapped_player.name} joined the server")


# Example of task scheduling
@mc.event("on_server_starting")
def on_server_starting(event):
    """Called when the server is starting."""
    server = event.server
    log_print("Server starting, scheduling tasks...")

    # Schedule a delayed task to announce server readiness
    def announce_startup():
        mc.broadcast_message(server, "Server is fully ready! Welcome to PyFish!")
        log_print("Server startup announcement sent")

    mc.schedule_delayed_task(server, 40, announce_startup)  # 2 seconds (40 ticks)


# Log that the script loaded successfully
log_print("Hello World script loaded successfully with new API features!")
