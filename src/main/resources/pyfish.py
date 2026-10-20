import java

def log_print(*args):
    """Logs a message to the console.
    This function uses standard print, which is redirected to the mod's logger.
    """
    print(' '.join(str(arg) for arg in args))


# Runtime stubs keep imports stable while richer autocomplete lives in the
# separate IDE support package.
class MessageHandle: ...


class GameProfileHandle: ...


class TextureHandle: ...


class PyFishModMetadata(dict): ...


class BlockPosHandle: ...


class ServerHandle: ...


class WorldHandle: ...


class EntityHandle: ...


class PlayerHandle(EntityHandle):
    def getArmorItemId(self, slot_name: str) -> str: ...
    def getItemInSlot(self, slot: int) -> str: ...


class ItemUseOnContextHandle: ...


class CallbackEvent: ...


class ServerEvent: ...


class LevelEvent: ...


class PlayerEvent(CallbackEvent): ...


class PlayerCloneEvent(PlayerEvent): ...


class BlockBreakEvent(PlayerEvent): ...


class BlockPlaceEvent(PlayerEvent): ...


class PlayerChatEvent(PlayerEvent): ...


class EntityEvent(CallbackEvent): ...


class EntitySpawnEvent(EntityEvent): ...


class EntityDimensionChangeEvent(EntityEvent): ...


class LivingEvent(EntityEvent): ...


class LivingDamageEvent(LivingEvent): ...


class LivingDeathEvent(LivingEvent): ...


class PlayerInteractEvent(PlayerEvent): ...


class PlayerInteractBlockEvent(PlayerInteractEvent): ...


class PlayerInteractEntityEvent(PlayerInteractEvent): ...


class PlayerSleepInBedEvent(PlayerEvent): ...


class PlayerWakeUpEvent(PlayerEvent): ...


class EntityTravelToDimensionEvent(EntityDimensionChangeEvent): ...


class LivingFallEvent(LivingEvent): ...


class LivingHealEvent(LivingEvent): ...


class AnimalTameEvent(LivingEvent): ...


class ItemTossEvent(PlayerEvent): ...


class ItemEntityPickupEvent(PlayerEvent): ...


class CommandEvent(ServerEvent): ...


class BlockEvent:
    class EntityPlaceEvent(PlayerInteractBlockEvent): ...

    class CropGrowEvent:
        class Post(CallbackEvent): ...

    class NeighborNotifyEvent(CallbackEvent): ...


class NoteBlockEvent:
    class Play(CallbackEvent): ...


class PlayerXpEvent:
    class XpChange(PlayerEvent): ...


class ExplosionEvent:
    class Detonate(CallbackEvent): ...


class _PlayerItemCraftedEvent(PlayerEvent): ...


class _PlayerItemSmeltedEvent(PlayerEvent): ...


class _LivingJumpEvent(LivingEvent): ...


PlayerEvent.ItemCraftedEvent = _PlayerItemCraftedEvent
PlayerEvent.ItemSmeltedEvent = _PlayerItemSmeltedEvent
LivingEvent.LivingJumpEvent = _LivingJumpEvent


class _ApiWrapper:
    def __init__(self, java_obj, mappings=None):
        self._obj = java_obj
        mappings = mappings or {}
        for java_name, py_name in mappings.items():
            func = getattr(java_obj, java_name)
            setattr(self, py_name, func)
            setattr(self, java_name, func)

    def __getattr__(self, name):
        return getattr(self._obj, name)


class _PlayerWrapper:
    """Pythonic wrapper for ServerPlayer with convenience methods."""
    def __init__(self, java_player):
        self._player = java_player

    def unwrapPlayer(self):
        return self._player

    @property
    def name(self):
        return self._player.getName().getString()

    @property
    def uuid(self):
        return str(self._player.getUUID())

    @property
    def health(self):
        return self._player.getHealth()

    @health.setter
    def health(self, value):
        mc.set_health(self._player, value)

    @property
    def food_level(self):
        return self._player.getFoodData().getFoodLevel()

    @food_level.setter
    def food_level(self, value):
        mc.set_food_level(self._player, value)

    @property
    def experience_level(self):
        return self._player.experienceLevel

    @experience_level.setter
    def experience_level(self, value):
        mc.set_experience(self._player, value)

    @property
    def position(self):
        pos = self._player.position()
        return (pos.x, pos.y, pos.z)

    def send_message(self, message):
        mc.send_message(self._player, message)

    def give_item(self, item_id, count=1):
        mc.give_item(self._player, item_id, count)

    def teleport(self, x, y, z):
        mc.teleport(self._player, x, y, z)

    def heal(self, amount=None):
        if amount is None:
            self.health = self._player.getMaxHealth()
        else:
            self.health = min(self.health + amount, self._player.getMaxHealth())

    def add_effect(self, effect_id, duration, amplifier=0):
        mc.add_effect(self._player, effect_id, duration, amplifier)

    def __repr__(self):
        return f"Player(name={self.name!r}, uuid={self.uuid!r})"


class _WorldWrapper:
    """Pythonic wrapper for ServerLevel with convenience methods."""
    def __init__(self, java_world):
        self._world = java_world

    def unwrapLevel(self):
        return self._world

    @property
    def time(self):
        return mc.get_time(self._world)

    @time.setter
    def time(self, value):
        mc.set_time(self._world, value)

    @property
    def players(self):
        return [_PlayerWrapper(p) for p in mc.get_players(self._world)]

    def set_block(self, x, y, z, block_id):
        mc.set_block(self._world, x, y, z, block_id)

    def get_block(self, x, y, z):
        return mc.get_block_id(self._world, x, y, z)

    def break_block(self, x, y, z):
        mc.break_block(self._world, x, y, z)

    def spawn_entity(self, entity_type_id, x, y, z):
        mc.spawn_entity(self._world, entity_type_id, x, y, z)

    def get_entities_in_range(self, x, y, z, range):
        return mc.get_entities_in_range(self._world, x, y, z, range)

    def create_explosion(self, x, y, z, radius, fire=False, mode="block"):
        mc.create_explosion(self._world, x, y, z, radius, fire, mode)

    def strike_lightning(self, x, y, z):
        mc.strike_lightning(self._world, x, y, z)

    def broadcast_message(self, message):
        mc.broadcast_message(self._world, message)

    def __repr__(self):
        return f"World(dimension={self._world.dimension().location().toString()!r})"


class _TextureWrapper(_ApiWrapper):
    def get_texture_bytes(self, texture_id):
        java_bytes = self._obj.getTextureBytes(texture_id)
        if java_bytes is None:
            return None
        return bytes([b & 0xff for b in java_bytes])

    getTexturePythonBytes = get_texture_bytes

    def register_texture(self, texture_id, py_bytes):
        if py_bytes is None:
            return
        signed = [(b - 256 if b > 127 else b) for b in py_bytes]
        try:
            java_data = java.to(signed, "byte[]")
        except Exception:
            java_data = signed
        self._obj.registerTextureBytes(texture_id, java_data)

    registerTexturePythonBytes = register_texture

    def load_texture(self, texture_id):
        return self._obj.loadTexture(texture_id)

    def register_image(self, texture_id, image):
        self._obj.registerImage(texture_id, image)


class _PyFishContentModule:
    def __init__(self):
        bridge = java.type("fr.oesphy.pyfish.content.PyFishContentBridge")

        self.recipes = _ApiWrapper(bridge.getRecipes(), {
            "shaped": "shaped",
            "shapeless": "shapeless",
            "remove": "remove"
        })
        self.items = _ApiWrapper(bridge.getItems(), {
            "registerItem": "register_item",
            "registerFood": "register_food",
            "registerTool": "register_tool"
        })
        self.blocks = _ApiWrapper(bridge.getBlocks(), {
            "registerBlock": "register_block"
        })
        self.tags = _ApiWrapper(bridge.getTags(), {
            "addItemTag": "add_item_tag",
            "addBlockTag": "add_block_tag",
            "getItemsByTag": "get_items_by_tag",
            "unify": "unify"
        })
        self.textures = _TextureWrapper(bridge.getTextures())
        self.tabs = _ApiWrapper(bridge.getTabs(), {
            "create": "create"
        })

class MinecraftAPI:
    """Python wrapper for the Minecraft API exposed from Java.
    Provides methods to interact with the Minecraft world, players, and events.
    """

    def _get_mc(self):
        """Gets the Minecraft API instance from globals.
        Returns:
            The Java MinecraftAPI instance.
        Raises:
            RuntimeError: If the Java MinecraftAPI is not exposed.
        """
        mc_internal = globals().get('_mc_internal')
        if mc_internal is None:
            raise RuntimeError("MinecraftAPI not exposed by Java! Make sure _mc_internal is set in globals.")
        return mc_internal

    def _unwrap_player(self, player):
        """Unwraps loader-specific player handles into the backing Java ServerPlayer."""
        if isinstance(player, _PlayerWrapper):
            return player.unwrapPlayer()
        unwrap = getattr(player, "unwrapPlayer", None)
        if unwrap is not None:
            return unwrap()
        return player

    def _unwrap_entity(self, entity):
        """Unwraps loader-specific entity handles into the backing Java Entity."""
        unwrap = getattr(entity, "unwrapEntity", None)
        if unwrap is not None:
            return unwrap()
        return entity

    def _unwrap_world(self, world):
        """Unwraps loader-specific world handles into the backing Java ServerLevel."""
        if isinstance(world, _WorldWrapper):
            return world.unwrapLevel()
        unwrap = getattr(world, "unwrapLevel", None)
        if unwrap is not None:
            return unwrap()
        return world

    def _unwrap_server(self, server):
        """Unwraps loader-specific server handles into the backing Java MinecraftServer."""
        unwrap = getattr(server, "unwrapServer", None)
        if unwrap is not None:
            return unwrap()
        return server

    def _unwrap_block_pos(self, pos):
        """Unwraps loader-specific block position handles into the backing Java BlockPos."""
        unwrap = getattr(pos, "unwrapBlockPos", None)
        if unwrap is not None:
            return unwrap()
        return pos

    def wrap_player(self, java_player):
        """Wraps a Java ServerPlayer in a Pythonic wrapper."""
        if java_player is None:
            return None
        return _PlayerWrapper(java_player)

    def wrap_world(self, java_world):
        """Wraps a Java ServerLevel in a Pythonic wrapper."""
        if java_world is None:
            return None
        return _WorldWrapper(java_world)

    def set_block(self, world, x, y, z, block_id):
        """Places a block in the world at the specified coordinates.
        Args:
            world: The Minecraft world (ServerLevel).
            x: X-coordinate.
            y: Y-coordinate.
            z: Z-coordinate.
            block_id: Block ID (e.g., "minecraft:stone").
        """
        self._get_mc().setBlock(self._unwrap_world(world), x, y, z, block_id)

    def get_block_id(self, world, x, y, z):
        """Gets the block ID at the specified coordinates.
        Args:
            world: The Minecraft world (ServerLevel).
            x: X-coordinate.
            y: Y-coordinate.
            z: Z-coordinate.
        Returns:
            str: Block ID (e.g., "minecraft:stone").
        """
        return self._get_mc().getBlockId(self._unwrap_world(world), x, y, z)

    def break_block(self, world, x, y, z):
        """Breaks a block at the specified coordinates.
        Args:
            world: The Minecraft world (ServerLevel).
            x: X-coordinate.
            y: Y-coordinate.
            z: Z-coordinate.
        """
        self._get_mc().breakBlock(self._unwrap_world(world), x, y, z)

    # --- Player Interaction ---
    def send_message(self, player, message):
        """Sends a message to a player.
        Args:
            player: The target player (ServerPlayer).
            message: The message to send.
        """
        self._get_mc().sendMessage(self._unwrap_player(player), message)

    def broadcast_message(self, world, message):
        """Broadcasts a message to all players.
        Args:
            world: The Minecraft world (ServerLevel).
            message: The message to broadcast.
        """
        self._get_mc().broadcastMessage(self._unwrap_world(world), message)

    def give_item(self, player, item_id, count):
        """Gives an item to a player.
        Args:
            player: The target player (ServerPlayer).
            item_id: Item ID (e.g., "minecraft:apple").
            count: Quantity of the item.
        """
        self._get_mc().giveItem(self._unwrap_player(player), item_id, count)

    # --- World & Server ---

    def get_time(self, world):
        """Gets the current world time.
        Args:
            world: The Minecraft world (ServerLevel).
        Returns:
            int: The current world time.
        """
        return self._get_mc().getTime(self._unwrap_world(world))

    def set_time(self, world, time):
        """Sets the current world time.
        Args:
            world: The Minecraft world (ServerLevel).
            time: The time to set.
        """
        self._get_mc().setTime(self._unwrap_world(world), time)

    def get_players(self, world):
        """Gets a list of all players in the world.
        Args:
            world: The Minecraft world (ServerLevel).
        Returns:
            list: A list of wrapped Player objects.
        """
        java_players = self._get_mc().getPlayers(self._unwrap_world(world))
        return [self.wrap_player(p) for p in java_players]

    # --- Entities & Movement ---

    def teleport(self, player, x, y, z):
        """Teleports a player to the specified coordinates.
        Args:
            player: The target player (ServerPlayer).
            x, y, z: Target coordinates.
        """
        self._get_mc().teleport(self._unwrap_player(player), x, y, z)

    def set_health(self, player, health):
        """Sets a player's health.
        Args:
            player: The target player (ServerPlayer).
            health: The health value to set.
        """
        self._get_mc().setHealth(self._unwrap_player(player), health)

    def set_food_level(self, player, food_level):
        """Sets a player's hunger level.
        Args:
            player: The target player (ServerPlayer).
            food_level: Hunger level from 0 to 20.
        """
        self._get_mc().setFoodLevel(self._unwrap_player(player), food_level)

    def set_experience(self, player, level):
        """Sets a player's experience level.
        Args:
            player: The target player (ServerPlayer).
            level: Experience level to apply.
        """
        self._get_mc().setExperience(self._unwrap_player(player), level)

    def add_effect(self, player, effect_id, duration, amplifier=0):
        """Adds a potion effect to a player.
        Args:
            player: The target player (ServerPlayer).
            effect_id: The ID of the effect (e.g., "minecraft:speed").
            duration: Duration in ticks.
            amplifier: Effect level (starts at 0).
        """
        self._get_mc().addEffect(self._unwrap_player(player), effect_id, duration, amplifier)

    def spawn_entity(self, world, entity_type_id, x, y, z):
        """Spawns an entity in the world.
        Args:
            world: The Minecraft world (ServerLevel).
            entity_type_id: The ID of the entity type (e.g., "minecraft:cow").
            x, y, z: Spawn coordinates.
        """
        self._get_mc().spawnEntity(self._unwrap_world(world), entity_type_id, x, y, z)

    def kill_entity(self, entity):
        """Removes an entity from the world.
        Args:
            entity: The target entity.
        """
        self._get_mc().killEntity(self._unwrap_entity(entity))

    def get_entities_in_range(self, world, x, y, z, range):
        """Returns all entities in a radius around a position.
        Args:
            world: The Minecraft world (ServerLevel).
            x, y, z: Center coordinates.
            range: Search radius.
        Returns:
            list: A list of Entity objects.
        """
        return self._get_mc().getEntitiesInRange(self._unwrap_world(world), x, y, z, range)

    def execute_command(self, server, command):
        """Executes a server command.
        Args:
            server: The Minecraft server.
            command: Command without a leading slash.
        """
        self._get_mc().executeCommand(self._unwrap_server(server), command)

    def create_explosion(self, world, x, y, z, radius, fire=False, mode="block"):
        """Creates an explosion at the target coordinates.
        Args:
            world: The Minecraft world (ServerLevel).
            x, y, z: Center coordinates.
            radius: Explosion radius.
            fire: Whether the explosion starts fires.
            mode: Explosion interaction mode: "block", "none", "mob", or "tnt".
        """
        self._get_mc().createExplosion(self._unwrap_world(world), x, y, z, radius, fire, mode)

    def strike_lightning(self, world, x, y, z):
        """Strikes lightning at the target coordinates.
        Args:
            world: The Minecraft world (ServerLevel).
            x, y, z: Target coordinates.
        """
        self._get_mc().strikeLightning(self._unwrap_world(world), x, y, z)

    def play_sound(self, world, x, y, z, sound_id, category="master", volume=1.0, pitch=1.0):
        """Plays a sound in the world.
        Args:
            world: The Minecraft world (ServerLevel).
            x, y, z: Sound origin.
            sound_id: Sound event ID.
            category: Sound category such as "master" or "players".
            volume: Playback volume.
            pitch: Playback pitch.
        """
        self._get_mc().playSound(
            self._unwrap_world(world),
            x,
            y,
            z,
            sound_id,
            category,
            volume,
            pitch,
        )

    def spawn_particle(self, world, particle_id, x, y, z, count=1, dx=0.0, dy=0.0, dz=0.0, speed=0.0):
        """Spawns particles in the world.
        Args:
            world: The Minecraft world (ServerLevel).
            particle_id: Particle type ID.
            x, y, z: Spawn coordinates.
            count: Number of particles.
            dx, dy, dz: Spawn spread on each axis.
            speed: Particle speed.
        """
        self._get_mc().spawnParticle(
            self._unwrap_world(world),
            particle_id,
            x,
            y,
            z,
            count,
            dx,
            dy,
            dz,
            speed,
        )

    # --- Event System ---
    def on(self, event, callback):
        """Registers a callback for an event.
        Args:
            event: A built-in PyFish alias, a registered custom native alias, or a
                   NeoForge event class name for backward-compatible dynamic registration.
            callback: Python function to call when the event triggers.
        """
        self._get_mc().on(event, callback)

    def on_native(self, alias, event_source, callback):
        """Binds a custom alias to one native event and registers its callback.

        Fabric sources use ``fully.qualified.Callback#EVENT``; NeoForge sources
        use a fully qualified event class name, optionally prefixed with
        ``mod:`` to select the PyFish mod event bus.
        """
        self._get_mc().onNative(alias, event_source, callback)

    def register_native_event(self, alias, event_source):
        """Binds an alias for later use with ``mc.on(alias, callback)``."""
        if not self._get_mc().registerNativeEventAlias(alias, event_source):
            raise ValueError(f"Native event alias {alias!r} is invalid or already bound")

    # --- Decorator-based event registration ---
    def event(self, event_name):
        """Decorator for registering event callbacks.
        Usage:
            @mc.event("player.join")
            def on_player_join(event):
                player = event.player
                mc.send_message(player, "Welcome!")
        """
        def decorator(callback):
            self.on(event_name, callback)
            return callback
        return decorator

    def command(self, command_name):
        """Decorator for registering command handlers (future implementation).
        Usage:
            @mc.command("heal")
            def heal_command(source):
                player = source.player
                if player is not None:
                    mc.set_health(player, 20)
        """
        def decorator(callback):
            # TODO: Implement command registration system
            return callback
        return decorator

    # --- Task Scheduling ---
    def schedule_delayed_task(self, server, delay_ticks, callback):
        """Schedules a task to run after a delay in ticks.
        Args:
            server: The Minecraft server
            delay_ticks: Delay in ticks (20 ticks = 1 second)
            callback: Python function to execute
        Returns:
            Task ID for cancellation
        """
        return self._get_mc().scheduleDelayedTask(self._unwrap_server(server), delay_ticks, callback)

    def schedule_repeating_task(self, server, interval_ticks, callback):
        """Schedules a repeating task.
        Args:
            server: The Minecraft server
            interval_ticks: Interval in ticks
            callback: Python function to execute
        Returns:
            Task ID for cancellation
        """
        return self._get_mc().scheduleRepeatingTask(self._unwrap_server(server), interval_ticks, callback)

    def cancel_task(self, task_id):
        """Cancels a scheduled task.
        Args:
            task_id: The task ID to cancel
        Returns:
            True if the task was cancelled
        """
        return self._get_mc().cancelTask(task_id)
# --- Instance ---
mc = MinecraftAPI()
content = _PyFishContentModule()
recipes = content.recipes
items = content.items
blocks = content.blocks
tags = content.tags
textures = content.textures
tabs = content.tabs

__all__ = [
    "log_print",
    "mc",
    "MinecraftAPI",
    "MessageHandle",
    "GameProfileHandle",
    "TextureHandle",
    "PyFishModMetadata",
    "BlockPosHandle",
    "ServerHandle",
    "WorldHandle",
    "EntityHandle",
    "PlayerHandle",
    "ItemUseOnContextHandle",
    "CallbackEvent",
    "ServerEvent",
    "LevelEvent",
    "PlayerEvent",
    "PlayerCloneEvent",
    "BlockBreakEvent",
    "BlockPlaceEvent",
    "PlayerChatEvent",
    "EntityEvent",
    "EntitySpawnEvent",
    "EntityDimensionChangeEvent",
    "EntityTravelToDimensionEvent",
    "LivingEvent",
    "LivingDamageEvent",
    "LivingDeathEvent",
    "LivingFallEvent",
    "LivingHealEvent",
    "AnimalTameEvent",
    "PlayerInteractEvent",
    "PlayerInteractBlockEvent",
    "PlayerInteractEntityEvent",
    "PlayerSleepInBedEvent",
    "PlayerWakeUpEvent",
    "ItemTossEvent",
    "ItemEntityPickupEvent",
    "CommandEvent",
    "BlockEvent",
    "NoteBlockEvent",
    "PlayerXpEvent",
    "ExplosionEvent",
    "content",
    "recipes",
    "items",
    "blocks",
    "tags",
    "textures",
    "tabs",
    "_PlayerWrapper",
    "_WorldWrapper",
]
