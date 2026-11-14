package fr.oesphy.pyfish.fabric;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import fr.oesphy.pyfish.fabric.compat.FabricVersionCompat;
import fr.oesphy.pyfish.platform.PlatformEventBridge;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class FabricEventBridge implements PlatformEventBridge {
    private final Map<String, List<Consumer<Object[]>>> dispatchers = new HashMap<>();
    private final Set<String> wiredEvents = new HashSet<>();
    private final Set<String> warnedNativeReturns = new HashSet<>();

    @Override
    public synchronized boolean registerEvent(String eventNameOrClass, Consumer<Object[]> dispatcher) {
        dispatchers.computeIfAbsent(eventNameOrClass, key -> new ArrayList<>()).add(dispatcher);
        if (!wiredEvents.add(eventNameOrClass)) {
            return true;
        }

        boolean supported = switch (eventNameOrClass) {
            case "on_player_join" -> {
                ServerPlayConnectionEvents.JOIN.register(
                        (handler, sender, server) -> {
                            ServerPlayer player = handler.getPlayer();
                            ServerLevel level = MinecraftCompat.getServerLevel(player);
                            dispatch("on_player_join",
                                    new Object[] { new PyFishEventObjects.PlayerEvent(player, level, server) });
                        });
                yield true;
            }
            case "on_player_leave" -> {
                ServerPlayConnectionEvents.DISCONNECT.register(
                        (handler, server) -> {
                            ServerPlayer player = handler.getPlayer();
                            ServerLevel level = MinecraftCompat.getServerLevel(player);
                            dispatch("on_player_leave",
                                    new Object[] { new PyFishEventObjects.PlayerEvent(player, level, server) });
                        });
                yield true;
            }
            case "on_player_respawn" -> {
                ServerPlayerEvents.AFTER_RESPAWN.register(
                        (oldPlayer, newPlayer, alive) -> {
                            ServerLevel level = MinecraftCompat.getServerLevel(newPlayer);
                            dispatch("on_player_respawn",
                                    new Object[] {
                                            new PyFishEventObjects.PlayerEvent(newPlayer, level, level.getServer())
                                    });
                        });
                yield true;
            }
            case "on_player_clone" -> {
                ServerPlayerEvents.COPY_FROM.register(
                        (oldPlayer, newPlayer, alive) -> dispatch("on_player_clone",
                                new Object[] { new PyFishEventObjects.PlayerCloneEvent(oldPlayer, newPlayer, alive) }));
                yield true;
            }
            case "on_player_chat" -> {
                ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> dispatch("on_player_chat",
                        new Object[] {
                                new PyFishEventObjects.PlayerChatEvent(MinecraftCompat.getServerLevel(sender), sender,
                                        Component.literal(message.signedContent()))
                        }));
                yield true;
            }
            case "on_player_interact_entity" -> {
                UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
                    if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                        dispatch("on_player_interact_entity",
                                new Object[] {
                                        new PyFishEventObjects.PlayerInteractEntityEvent(serverLevel, serverPlayer,
                                                entity, hand)
                                });
                    }
                    return InteractionResult.PASS;
                });
                yield true;
            }
            case "on_player_interact_block" -> {
                UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
                    if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                        dispatch("on_player_interact_block",
                                new Object[] {
                                        new PyFishEventObjects.PlayerInteractBlockEvent(serverLevel, serverPlayer,
                                                hitResult.getBlockPos(), hand)
                                });
                    }
                    return InteractionResult.PASS;
                });
                yield true;
            }
            case "on_player_tick" -> {
                ServerTickEvents.END_SERVER_TICK.register(server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        ServerLevel level = MinecraftCompat.getServerLevel(player);
                        dispatch("on_player_tick",
                                new Object[] { new PyFishEventObjects.PlayerEvent(player, level, server) });
                    }
                });
                yield true;
            }
            case "on_server_starting" -> {
                ServerLifecycleEvents.SERVER_STARTING.register(
                        server -> dispatch("on_server_starting", new Object[] { new PyFishEventObjects.ServerEvent(server) }));
                yield true;
            }
            case "on_server_started" -> {
                ServerLifecycleEvents.SERVER_STARTED.register(
                        server -> dispatch("on_server_started", new Object[] { new PyFishEventObjects.ServerEvent(server) }));
                yield true;
            }
            case "on_server_stopping" -> {
                ServerLifecycleEvents.SERVER_STOPPING.register(
                        server -> dispatch("on_server_stopping", new Object[] { new PyFishEventObjects.ServerEvent(server) }));
                yield true;
            }
            case "on_server_stopped" -> {
                ServerLifecycleEvents.SERVER_STOPPED.register(
                        server -> dispatch("on_server_stopped", new Object[] { new PyFishEventObjects.ServerEvent(server) }));
                yield true;
            }
            case "on_server_tick" -> {
                ServerTickEvents.END_SERVER_TICK.register(
                        server -> dispatch("on_server_tick", new Object[] { new PyFishEventObjects.ServerEvent(server) }));
                yield true;
            }
            case "on_level_load" -> {
                FabricVersionCompat.onLevelLoad(
                        (server, world) -> dispatch("on_level_load", new Object[] { new PyFishEventObjects.LevelEvent(world) }));
                yield true;
            }
            case "on_level_save" -> {
                ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
                    for (ServerLevel level : server.getAllLevels()) {
                        dispatch("on_level_save", new Object[] { new PyFishEventObjects.LevelEvent(level) });
                    }
                });
                yield true;
            }
            case "on_level_unload" -> {
                FabricVersionCompat.onLevelUnload(
                        (server, world) -> dispatch("on_level_unload", new Object[] { new PyFishEventObjects.LevelEvent(world) }));
                yield true;
            }
            case "on_level_tick" -> {
                FabricVersionCompat.onLevelTick(
                        world -> dispatch("on_level_tick", new Object[] { new PyFishEventObjects.LevelEvent(world) }));
                yield true;
            }
            case "on_entity_spawn" -> {
                ServerEntityEvents.ENTITY_LOAD.register(
                        (entity, world) -> dispatch("on_entity_spawn",
                                new Object[] { new PyFishEventObjects.EntitySpawnEvent(world, entity) }));
                yield true;
            }
            case "on_entity_dimension_change" -> {
                FabricVersionCompat.onDimensionChange(
                        (entity, origin, destination) -> dispatch("on_entity_dimension_change",
                                new Object[] {
                                        new PyFishEventObjects.EntityDimensionChangeEvent(origin, destination, entity)
                                }));
                yield true;
            }
            case "on_entity_hurt" -> {
                ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
                    dispatch("on_entity_hurt",
                            new Object[] {
                                    new PyFishEventObjects.LivingDamageEvent((ServerLevel) entity.level(), entity,
                                            source, amount)
                            });
                    return true;
                });
                yield true;
            }
            case "on_entity_damage" -> {
                ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
                        dispatch("on_entity_damage",
                                new Object[] {
                                        new PyFishEventObjects.LivingDamageEvent((ServerLevel) entity.level(), entity,
                                                source, baseDamageTaken, damageTaken, blocked)
                                }));
                yield true;
            }
            case "on_entity_death" -> {
                ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> dispatch("on_entity_death",
                        new Object[] {
                                new PyFishEventObjects.LivingDeathEvent((ServerLevel) entity.level(), entity, source)
                        }));
                yield true;
            }
            case "on_block_break" -> {
                PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
                    if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                        dispatch("on_block_break",
                                new Object[] { new PyFishEventObjects.BlockBreakEvent(serverLevel, serverPlayer, pos) });
                    }
                });
                yield true;
            }
            case "on_block_place" -> {
                UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
                    if (!(world instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResult.PASS;
                    }
                    if (!(serverPlayer.getItemInHand(hand).getItem() instanceof BlockItem)) {
                        return InteractionResult.PASS;
                    }

                    BlockPos placementPos = hitResult.getBlockPos();
                    if (!world.getBlockState(placementPos).canBeReplaced()) {
                        placementPos = placementPos.relative(hitResult.getDirection());
                    }

                    dispatch("on_block_place",
                            new Object[] {
                                    new PyFishEventObjects.PlayerInteractBlockEvent(serverLevel, serverPlayer,
                                            placementPos, hand)
                            });
                    return InteractionResult.PASS;
                });
                yield true;
            }
            default -> false;
        };

        if (!supported) {
            unwindRegistration(eventNameOrClass, dispatcher);
        }
        return supported;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public synchronized boolean registerNativeEvent(
            String alias,
            String eventSource,
            Function<Object[], Object> dispatcher) {
        int separator = eventSource.lastIndexOf('#');
        if (separator <= 0 || separator == eventSource.length() - 1) {
            PyFish.LOGGER.warn(
                    "[PyFish] Fabric native event source must use 'fully.qualified.Callback#FIELD': {}",
                    eventSource);
            return false;
        }

        String ownerName = eventSource.substring(0, separator);
        String fieldName = eventSource.substring(separator + 1);
        try {
            Class<?> owner = Class.forName(ownerName, true, FabricEventBridge.class.getClassLoader());
            Field eventField = owner.getField(fieldName);
            if (!Modifier.isStatic(eventField.getModifiers())) {
                return false;
            }

            Object fieldValue = eventField.get(null);
            if (!(fieldValue instanceof Event event)) {
                PyFish.LOGGER.warn("[PyFish] Fabric field '{}' is not an Event", eventSource);
                return false;
            }

            Class<?> callbackType = findCallbackType(eventField.getGenericType());
            if (callbackType == null) {
                callbackType = findListenerInterface(event.invoker().getClass());
            }
            if (callbackType == null || !callbackType.isInterface()) {
                PyFish.LOGGER.warn("[PyFish] Could not determine callback interface for Fabric event '{}'", eventSource);
                return false;
            }

            InvocationHandler handler = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return handleProxyObjectMethod(proxy, method, args, alias);
                }
                Object result = dispatcher.apply(args == null ? new Object[0] : args);
                return adaptCallbackResult(alias, eventSource, method, result);
            };
            Object listener = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[] { callbackType },
                    handler);
            event.register(listener);
            return true;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            PyFish.LOGGER.warn(
                    "[PyFish] Could not resolve Fabric event '{}': {}",
                    eventSource,
                    exception.getMessage());
            return false;
        } catch (RuntimeException exception) {
            PyFish.LOGGER.warn(
                    "[PyFish] Could not subscribe to Fabric event '{}': {}",
                    eventSource,
                    exception.getMessage());
            return false;
        }
    }

    private Class<?> findCallbackType(Type fieldType) {
        if (!(fieldType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        return findClass(parameterizedType.getActualTypeArguments()[0]);
    }

    private Class<?> findClass(Type type) {
        if (type instanceof Class<?> typeClass) {
            return typeClass;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return findClass(parameterizedType.getRawType());
        }
        if (type instanceof java.lang.reflect.WildcardType wildcardType
                && wildcardType.getUpperBounds().length > 0) {
            return findClass(wildcardType.getUpperBounds()[0]);
        }
        return null;
    }

    private Class<?> findListenerInterface(Class<?> type) {
        for (Class<?> candidate : type.getInterfaces()) {
            if (candidate != Event.class) {
                return candidate;
            }
        }
        Class<?> parent = type.getSuperclass();
        return parent == null ? null : findListenerInterface(parent);
    }

    private Object handleProxyObjectMethod(Object proxy, Method method, Object[] args, String alias) {
        return switch (method.getName()) {
            case "toString" -> "PyFish native event listener: " + alias;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> null;
        };
    }

    private Object adaptCallbackResult(String alias, String eventSource, Method method, Object result) {
        Class<?> expected = method.getReturnType();
        if (expected == void.class) {
            return null;
        }
        if (result != null && expected.isInstance(result)) {
            return result;
        }
        if (result instanceof Number number && expected.isPrimitive()) {
            if (expected == int.class) return number.intValue();
            if (expected == long.class) return number.longValue();
            if (expected == float.class) return number.floatValue();
            if (expected == double.class) return number.doubleValue();
            if (expected == short.class) return number.shortValue();
            if (expected == byte.class) return number.byteValue();
        }
        if (expected == boolean.class && result instanceof Boolean) {
            return result;
        }
        if (expected == char.class && result instanceof String string && string.length() == 1) {
            return string.charAt(0);
        }
        if (result instanceof String name && expected.isEnum()) {
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                Object enumValue = Enum.valueOf((Class<? extends Enum>) expected, name);
                return enumValue;
            } catch (IllegalArgumentException ignored) {
                // Fall through to a safe callback default and report the mismatch once.
            }
        }

        warnNativeReturnOnce(alias, eventSource, method, result);
        if (result == null || !expected.isInstance(result)) {
            return defaultCallbackResult(expected);
        }
        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object defaultCallbackResult(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type.isEnum()) {
            for (String preferred : new String[] { "PASS", "DEFAULT" }) {
                try {
                    return Enum.valueOf((Class<? extends Enum>) type, preferred);
                } catch (IllegalArgumentException ignored) {
                    // Not every Fabric callback uses the same neutral result enum.
                }
            }
        }
        return null;
    }

    private synchronized void warnNativeReturnOnce(String alias, String eventSource, Method method, Object result) {
        String warningKey = eventSource + "#" + method.toGenericString();
        if (warnedNativeReturns.add(warningKey)) {
            PyFish.LOGGER.warn(
                    "[PyFish] Native callback '{}' must return {} for {} (got {}). Using a neutral fallback; "
                            + "return the loader's expected value explicitly for predictable behavior.",
                    alias,
                    method.getReturnType().getTypeName(),
                    eventSource,
                    result == null ? "None" : result.getClass().getTypeName());
        }
    }

    private void unwindRegistration(String eventNameOrClass, Consumer<Object[]> dispatcher) {
        List<Consumer<Object[]>> dispatcherList = dispatchers.get(eventNameOrClass);
        if (dispatcherList != null) {
            dispatcherList.remove(dispatcher);
            if (dispatcherList.isEmpty()) {
                dispatchers.remove(eventNameOrClass);
            }
        }
        wiredEvents.remove(eventNameOrClass);
    }

    private void dispatch(String eventNameOrClass, Object[] args) {
        List<Consumer<Object[]>> consumers = dispatchers.get(eventNameOrClass);
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        for (Consumer<Object[]> consumer : new ArrayList<>(consumers)) {
            try {
                consumer.accept(args);
            } catch (Throwable throwable) {
                fr.oesphy.pyfish.PyFish.LOGGER.error(
                        "[PyFish] Fabric event dispatch failed for {}: {}",
                        eventNameOrClass,
                        throwable.getMessage(),
                        throwable);
            }
        }
    }
}
