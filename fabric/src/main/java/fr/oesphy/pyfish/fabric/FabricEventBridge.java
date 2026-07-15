package fr.oesphy.pyfish.fabric;

import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import fr.oesphy.pyfish.platform.PlatformEventBridge;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FabricEventBridge implements PlatformEventBridge {
    private final Map<String, List<Consumer<Object[]>>> dispatchers = new HashMap<>();
    private final Set<String> wiredEvents = new HashSet<>();

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
                ServerWorldEvents.LOAD.register(
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
                ServerWorldEvents.UNLOAD.register(
                        (server, world) -> dispatch("on_level_unload", new Object[] { new PyFishEventObjects.LevelEvent(world) }));
                yield true;
            }
            case "on_level_tick" -> {
                ServerTickEvents.END_WORLD_TICK.register(
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
                ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register(
                        (originalEntity, newEntity, origin, destination) -> dispatch("on_entity_dimension_change",
                                new Object[] {
                                        new PyFishEventObjects.EntityDimensionChangeEvent(origin, destination, newEntity)
                                }));
                ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                        (player, origin, destination) -> dispatch("on_entity_dimension_change",
                                new Object[] {
                                        new PyFishEventObjects.EntityDimensionChangeEvent(origin, destination, player)
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