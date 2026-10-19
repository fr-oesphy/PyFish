package fr.oesphy.pyfish.api;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import fr.oesphy.pyfish.content.PyFishScriptContext;
import fr.oesphy.pyfish.platform.PlatformEventBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MinecraftAPI {
    private final Map<String, List<EventCallback>> eventCallbacks = new HashMap<>();
    private final Map<String, String> nativeEventAliases = new HashMap<>();
    private final Set<String> wiredEvents = new HashSet<>();
    private final PlatformEventBridge eventBridge;

    public MinecraftAPI(PlatformEventBridge eventBridge) {
        this.eventBridge = eventBridge;
    }

    public void setBlock(ServerLevel world, int x, int y, int z, String blockId) {
        if (world == null) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] World is null in setBlock!");
            return;
        }
        try {
            String resolvedBlockId = resolveIdentifier(blockId, "minecraft:air");
            Block block = MinecraftCompat.registryGet(BuiltInRegistries.BLOCK, resolvedBlockId);
            if (block != null && block != Blocks.AIR) {
                world.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 3);
                PyFish.LOGGER.info("[PyFish/MCAPI] Block {} placed at {}, {}, {}", resolvedBlockId, x, y, z);
            } else {
                PyFish.LOGGER.warn("[PyFish/MCAPI] Invalid block ID: {}", resolvedBlockId);
            }
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error in setBlock: {}", e.getMessage());
        }
    }

    public String getBlockId(ServerLevel world, int x, int y, int z) {
        if (world == null) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] World is null in getBlockId!");
            return "minecraft:air";
        }
        Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public void breakBlock(ServerLevel world, int x, int y, int z) {
        if (world == null) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] World is null in breakBlock!");
            return;
        }
        world.destroyBlock(new BlockPos(x, y, z), true);
        PyFish.LOGGER.info("[PyFish/MCAPI] Block broken at {}, {}, {}", x, y, z);
    }

    public void sendMessage(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message != null ? message : ""));
            PyFish.LOGGER.info("[PyFish/MCAPI] Message sent to {}: {}", player.getName().getString(), message);
        } else {
            PyFish.LOGGER.warn("[PyFish/MCAPI] Player is null in sendMessage!");
        }
    }

    public void broadcastMessage(ServerLevel world, String message) {
        if (world != null) {
            world.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message != null ? message : ""),
                    false);
            PyFish.LOGGER.info("[PyFish/MCAPI] Broadcast message: {}", message);
        } else {
            PyFish.LOGGER.warn("[PyFish/MCAPI] World is null in broadcastMessage!");
        }
    }

    public void giveItem(ServerPlayer player, String itemId, int count) {
        if (player == null) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] Player is null in giveItem!");
            return;
        }
        try {
            String resolvedItemId = resolveIdentifier(itemId, "minecraft:air");
            Item item = MinecraftCompat.registryGet(BuiltInRegistries.ITEM, resolvedItemId);
            if (item != null && item != Items.AIR) {
                player.getInventory().add(new ItemStack(item, count));
                PyFish.LOGGER.info("[PyFish/MCAPI] Gave {}x{} to {}", count, resolvedItemId,
                        player.getName().getString());
            } else {
                PyFish.LOGGER.warn("[PyFish/MCAPI] Invalid item ID: {}", resolvedItemId);
            }
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error in giveItem: {}", e.getMessage());
        }
    }

    public long getTime(ServerLevel world) {
        return world != null ? world.getDayTime() : 0;
    }

    public void setTime(ServerLevel world, long time) {
        if (world != null) {
            world.setDayTime(time);
        }
    }

    public List<ServerPlayer> getPlayers(ServerLevel world) {
        return world != null ? world.players() : new ArrayList<>();
    }

    public void teleport(ServerPlayer player, double x, double y, double z) {
        if (player != null) {
            player.teleportTo(x, y, z);
        }
    }

    public void setHealth(ServerPlayer player, float health) {
        if (player != null) {
            player.setHealth(health);
        }
    }

    public void addEffect(ServerPlayer player, String effectId, int duration, int amplifier) {
        if (player == null) {
            return;
        }
        try {
            String resolvedEffectId = resolveIdentifier(effectId, "minecraft:speed");
            net.minecraft.world.effect.MobEffect effect = MinecraftCompat.registryGet(BuiltInRegistries.MOB_EFFECT,
                    resolvedEffectId);
            if (effect != null) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier));
            } else {
                PyFish.LOGGER.warn("[PyFish/MCAPI] Invalid effect ID: {}", resolvedEffectId);
            }
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error adding effect: {}", e.getMessage());
        }
    }

    public void spawnEntity(ServerLevel world, String entityTypeId, double x, double y, double z) {
        if (world == null) {
            return;
        }
        String resolvedEntityTypeId = resolveIdentifier(entityTypeId, "minecraft:pig");
        try {
            EntityType<?> type = MinecraftCompat.registryGet(BuiltInRegistries.ENTITY_TYPE, resolvedEntityTypeId);
            if (type == null) {
                PyFish.LOGGER.warn("[PyFish/MCAPI] Invalid entity type ID: {}", resolvedEntityTypeId);
                return;
            }
            Entity entity;
            try {
                entity = MinecraftCompat.createEntity(type, world);
            } catch (Exception directCreationError) {
                PyFish.LOGGER.warn(
                        "[PyFish/MCAPI] Direct entity creation failed for {}: {}. Falling back to summon command.",
                        resolvedEntityTypeId,
                        directCreationError.getMessage());
                executeCommand(
                        world.getServer(),
                        String.format(
                                Locale.ROOT,
                                "summon %s %.3f %.3f %.3f",
                                resolvedEntityTypeId,
                                x,
                                y,
                                z));
                return;
            }
            if (entity != null) {
                MinecraftCompat.setEntityPosition(entity, x, y, z);
                world.addFreshEntity(entity);
            } else {
                PyFish.LOGGER.warn(
                        "[PyFish/MCAPI] Entity factory returned null for {}. Falling back to summon command.",
                        resolvedEntityTypeId);
                executeCommand(
                        world.getServer(),
                        String.format(
                                Locale.ROOT,
                                "summon %s %.3f %.3f %.3f",
                                resolvedEntityTypeId,
                                x,
                                y,
                        z));
            }
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error spawning entity: {}", e.getMessage());
            try {
                executeCommand(
                        world.getServer(),
                        String.format(
                                Locale.ROOT,
                                "summon %s %.3f %.3f %.3f",
                                resolvedEntityTypeId,
                                x,
                                y,
                                z));
            } catch (Exception fallbackError) {
                PyFish.LOGGER.error(
                        "[PyFish/MCAPI] Fallback summon command also failed for {}: {}",
                        resolvedEntityTypeId,
                        fallbackError.getMessage());
            }
        }
    }

    public void killEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            if (entity instanceof ServerPlayer player) {
                executeCommand(MinecraftCompat.getServer(player), "kill " + player.getName().getString());
                return;
            }
            MinecraftCompat.killEntity(entity);
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error killing entity: {}", e.getMessage());
        }
    }

    public void executeCommand(net.minecraft.server.MinecraftServer server, String command) {
        if (server != null && command != null) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }
    }

    public void createExplosion(ServerLevel world, double x, double y, double z, float radius, boolean fire,
            String mode) {
        if (world == null) {
            return;
        }

        Level.ExplosionInteraction interaction = Level.ExplosionInteraction.BLOCK;
        if ("none".equalsIgnoreCase(mode)) {
            interaction = Level.ExplosionInteraction.NONE;
        } else if ("mob".equalsIgnoreCase(mode)) {
            interaction = Level.ExplosionInteraction.MOB;
        } else if ("tnt".equalsIgnoreCase(mode)) {
            interaction = Level.ExplosionInteraction.TNT;
        }

        world.explode(null, x, y, z, radius, fire, interaction);
    }

    public void strikeLightning(ServerLevel world, double x, double y, double z) {
        if (world == null) {
            return;
        }
        try {
            net.minecraft.world.entity.LightningBolt bolt = MinecraftCompat.createEntity(EntityType.LIGHTNING_BOLT,
                    world);
            if (bolt != null) {
                MinecraftCompat.setEntityPosition(bolt, x, y, z);
                world.addFreshEntity(bolt);
                return;
            }
        } catch (Exception directCreationError) {
            PyFish.LOGGER.warn(
                    "[PyFish/MCAPI] Direct lightning creation failed: {}. Falling back to summon command.",
                    directCreationError.getMessage());
        }

        try {
            executeCommand(
                    world.getServer(),
                    String.format(
                            Locale.ROOT,
                            "summon minecraft:lightning_bolt %.3f %.3f %.3f",
                            x,
                            y,
                            z));
        } catch (Exception fallbackError) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error striking lightning: {}", fallbackError.getMessage());
        }
    }

    public void playSound(ServerLevel world, double x, double y, double z, String soundId, String category,
            float volume, float pitch) {
        if (world == null) {
            return;
        }
        try {
            String resolvedSoundId = resolveIdentifier(soundId, "minecraft:entity.experience_orb.pickup");
            SoundEvent sound = MinecraftCompat.registryGet(BuiltInRegistries.SOUND_EVENT, resolvedSoundId);
            if (sound == null) {
                PyFish.LOGGER.warn("[PyFish/MCAPI] Invalid sound ID: {}", resolvedSoundId);
                return;
            }
            SoundSource soundCategory = SoundSource.valueOf(category.toUpperCase());
            world.playSound(null, x, y, z, sound, soundCategory, volume, pitch);
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error playing sound {}: {}", soundId, e.getMessage());
        }
    }

    public void spawnParticle(ServerLevel world, String particleId, double x, double y, double z, int count, double dx,
            double dy, double dz, double speed) {
        if (world == null) {
            return;
        }
        try {
            String resolvedParticleId = resolveIdentifier(particleId, "minecraft:happy_villager");
            net.minecraft.core.particles.ParticleOptions type = MinecraftCompat.registryGet(
                    BuiltInRegistries.PARTICLE_TYPE, resolvedParticleId);
            if (type == null) {
                PyFish.LOGGER.warn("[PyFish/MCAPI] Invalid particle ID: {}", resolvedParticleId);
                return;
            }
            world.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error spawning particles {}: {}", particleId, e.getMessage());
        }
    }

    public void setExperience(ServerPlayer player, int level) {
        if (player != null) {
            player.setExperienceLevels(level);
        }
    }

    public void setFoodLevel(ServerPlayer player, int foodLevel) {
        if (player != null) {
            player.getFoodData().setFoodLevel(foodLevel);
        }
    }

    public List<Entity> getEntitiesInRange(ServerLevel world, double x, double y, double z, double range) {
        if (world == null) {
            return new ArrayList<>();
        }
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(x - range, y - range, z - range,
                x + range, y + range, z + range);
        return world.getEntities(null, area);
    }

    public void on(String eventNameOrClass, Value callback) {
        if (callback == null || !callback.canExecute()) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] Callback for event {} is not executable", eventNameOrClass);
            return;
        }

        String nativeEventSource = nativeEventAliases.get(eventNameOrClass);
        if (nativeEventSource != null) {
            EventCallback binding = new EventCallback(callback, PyFishScriptContext.currentNamespace());
            if (!eventBridge.registerNativeEvent(
                    eventNameOrClass,
                    nativeEventSource,
                    args -> invokeNativeCallback(eventNameOrClass, binding, args))) {
                PyFish.LOGGER.warn(
                        "[PyFish/MCAPI] Could not register native event alias '{}' from '{}'",
                        eventNameOrClass,
                        nativeEventSource);
                return;
            }
            PyFish.LOGGER.info("[PyFish/MCAPI] Registered callback for native event alias: {}", eventNameOrClass);
            return;
        }

        eventCallbacks.computeIfAbsent(eventNameOrClass, key -> new ArrayList<>())
                .add(new EventCallback(callback, PyFishScriptContext.currentNamespace()));

        if (!wiredEvents.contains(eventNameOrClass)) {
            if (!eventBridge.registerEvent(eventNameOrClass, args -> this.trigger(eventNameOrClass, args))) {
                removeLastCallback(eventNameOrClass);
                PyFish.LOGGER.warn("[PyFish/MCAPI] Unsupported or unavailable event on this loader: {}",
                        eventNameOrClass);
                return;
            }

            wiredEvents.add(eventNameOrClass);
        }

        PyFish.LOGGER.info("[PyFish/MCAPI] Registered callback for event: {}", eventNameOrClass);
    }

    public void trigger(String event, Object... args) {
        if (eventCallbacks.containsKey(event)) {
            for (EventCallback callbackBinding : eventCallbacks.get(event)) {
                try {
                    try (PyFishScriptContext.Scope ignored = PyFishScriptContext.push(callbackBinding.namespace)) {
                        callbackBinding.callback.execute(args);
                    }
                } catch (Exception e) {
                    PyFish.LOGGER.error("[PyFish/MCAPI] Error in event callback for {}: {}", event, e.getMessage());
                }
            }
        }
    }

    public synchronized boolean registerNativeEventAlias(String alias, String eventSource) {
        if (alias == null || alias.isBlank() || eventSource == null || eventSource.isBlank()) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] Native event alias and source must not be blank");
            return false;
        }
        String existingSource = nativeEventAliases.putIfAbsent(alias, eventSource);
        if (existingSource != null && !existingSource.equals(eventSource)) {
            PyFish.LOGGER.warn(
                    "[PyFish/MCAPI] Native event alias '{}' is already bound to '{}'",
                    alias,
                    existingSource);
            return false;
        }
        return true;
    }

    public void onNative(String alias, String eventSource, Value callback) {
        if (callback == null || !callback.canExecute()) {
            PyFish.LOGGER.warn("[PyFish/MCAPI] Callback for native event {} is not executable", alias);
            return;
        }
        if (!registerNativeEventAlias(alias, eventSource)) {
            return;
        }
        on(alias, callback);
    }

    private Object invokeNativeCallback(String alias, EventCallback binding, Object[] args) {
        try (PyFishScriptContext.Scope ignored = PyFishScriptContext.push(binding.namespace)) {
            return toNativeReturnValue(binding.callback.execute(args));
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/MCAPI] Error in native event callback for {}: {}", alias, e.getMessage());
            return null;
        }
    }

    private Object toNativeReturnValue(Value result) {
        if (result == null || result.isNull()) {
            return null;
        }
        if (result.isHostObject()) {
            return result.asHostObject();
        }
        if (result.isBoolean()) {
            return result.asBoolean();
        }
        if (result.isString()) {
            return result.asString();
        }
        if (result.fitsInInt()) {
            return result.asInt();
        }
        if (result.fitsInLong()) {
            return result.asLong();
        }
        if (result.fitsInDouble()) {
            return result.asDouble();
        }
        return result.as(Object.class);
    }

    private String resolveIdentifier(String rawId, String fallbackId) {
        String id = rawId != null && !rawId.isBlank() ? rawId : fallbackId;
        return PyFishResourceGenerator.resolveNamespacedString(id, PyFishScriptContext.currentNamespace());
    }

    private void removeLastCallback(String eventNameOrClass) {
        List<EventCallback> callbacks = eventCallbacks.get(eventNameOrClass);
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }

        callbacks.remove(callbacks.size() - 1);
        if (callbacks.isEmpty()) {
            eventCallbacks.remove(eventNameOrClass);
        }
    }

    private static final class EventCallback {
        private final Value callback;
        private final String namespace;

        private EventCallback(Value callback, String namespace) {
            this.callback = callback;
            this.namespace = namespace;
        }
    }
}
