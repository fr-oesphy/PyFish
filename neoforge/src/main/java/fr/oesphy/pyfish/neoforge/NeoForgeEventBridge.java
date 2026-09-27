package fr.oesphy.pyfish.neoforge;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.platform.PlatformEventBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class NeoForgeEventBridge implements PlatformEventBridge {
    private static final Map<String, String> EVENT_ALIASES = new HashMap<>();

    static {
        EVENT_ALIASES.put("on_block_break", "net.neoforged.neoforge.event.level.BlockEvent$BreakEvent");
        EVENT_ALIASES.put("on_block_place", "net.neoforged.neoforge.event.level.BlockEvent$EntityPlaceEvent");
        EVENT_ALIASES.put("on_crop_grow", "net.neoforged.neoforge.event.level.BlockEvent$CropGrowEvent$Post");
        EVENT_ALIASES.put("on_block_neighbor_change",
                "net.neoforged.neoforge.event.level.BlockEvent$NeighborNotifyEvent");
        EVENT_ALIASES.put("on_note_block_play", "net.neoforged.neoforge.event.level.NoteBlockEvent$Play");

        EVENT_ALIASES.put("on_player_join",
                "net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent");
        EVENT_ALIASES.put("on_player_leave",
                "net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent");
        EVENT_ALIASES.put("on_player_chat", "net.neoforged.neoforge.event.ServerChatEvent");
        EVENT_ALIASES.put("on_player_interact_entity",
                "net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract");
        EVENT_ALIASES.put("on_player_interact_block",
                "net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$RightClickBlock");
        EVENT_ALIASES.put("on_player_tick", "net.neoforged.neoforge.event.tick.PlayerTickEvent$Post");
        EVENT_ALIASES.put("on_player_respawn", "net.neoforged.neoforge.event.entity.player.PlayerRespawnEvent");
        EVENT_ALIASES.put("on_player_clone", "net.neoforged.neoforge.event.entity.player.PlayerEvent$Clone");
        EVENT_ALIASES.put("on_player_xp_change", "net.neoforged.neoforge.event.entity.player.PlayerXpEvent$XpChange");
        EVENT_ALIASES.put("on_player_sleep", "net.neoforged.neoforge.event.entity.player.PlayerSleepInBedEvent");
        EVENT_ALIASES.put("on_player_wake_up", "net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent");
        EVENT_ALIASES.put("on_item_crafted", "net.neoforged.neoforge.event.entity.player.PlayerEvent$ItemCraftedEvent");
        EVENT_ALIASES.put("on_item_smelted", "net.neoforged.neoforge.event.entity.player.PlayerEvent$ItemSmeltedEvent");

        EVENT_ALIASES.put("on_entity_death", "net.neoforged.neoforge.event.entity.living.LivingDeathEvent");
        EVENT_ALIASES.put("on_entity_hurt", "net.neoforged.neoforge.event.entity.living.LivingHurtEvent");
        EVENT_ALIASES.put("on_entity_damage", "net.neoforged.neoforge.event.entity.living.LivingDamageEvent");
        EVENT_ALIASES.put("on_entity_spawn", "net.neoforged.neoforge.event.entity.EntityJoinLevelEvent");
        EVENT_ALIASES.put("on_entity_dimension_change",
                "net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent");
        EVENT_ALIASES.put("on_living_jump", "net.neoforged.neoforge.event.entity.living.LivingEvent$LivingJumpEvent");
        EVENT_ALIASES.put("on_living_fall", "net.neoforged.neoforge.event.entity.living.LivingFallEvent");
        EVENT_ALIASES.put("on_living_heal", "net.neoforged.neoforge.event.entity.living.LivingHealEvent");
        EVENT_ALIASES.put("on_animal_tame", "net.neoforged.neoforge.event.entity.living.AnimalTameEvent");

        EVENT_ALIASES.put("on_item_dropped", "net.neoforged.neoforge.event.entity.item.ItemTossEvent");
        EVENT_ALIASES.put("on_item_pickup", "net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent");
        EVENT_ALIASES.put("on_explosion", "net.neoforged.neoforge.event.level.ExplosionEvent$Detonate");
        EVENT_ALIASES.put("on_command_executed", "net.neoforged.neoforge.event.CommandEvent");
        EVENT_ALIASES.put("on_level_load", "net.neoforged.neoforge.event.level.LevelEvent$Load");
        EVENT_ALIASES.put("on_level_save", "net.neoforged.neoforge.event.level.LevelEvent$Save");
        EVENT_ALIASES.put("on_level_unload", "net.neoforged.neoforge.event.level.LevelEvent$Unload");

        EVENT_ALIASES.put("on_server_starting", "net.neoforged.neoforge.event.server.ServerStartingEvent");
        EVENT_ALIASES.put("on_server_started", "net.neoforged.neoforge.event.server.ServerStartedEvent");
        EVENT_ALIASES.put("on_server_stopping", "net.neoforged.neoforge.event.server.ServerStoppingEvent");
        EVENT_ALIASES.put("on_server_stopped", "net.neoforged.neoforge.event.server.ServerStoppedEvent");
        EVENT_ALIASES.put("on_server_tick", "net.neoforged.neoforge.event.tick.ServerTickEvent$Post");
        EVENT_ALIASES.put("on_level_tick", "net.neoforged.neoforge.event.tick.LevelTickEvent$Post");
    }

    private final Map<String, List<Consumer<Object[]>>> dispatchers = new HashMap<>();
    private final Set<String> wiredEvents = new HashSet<>();
    private final IEventBus modEventBus;

    public NeoForgeEventBridge(IEventBus modEventBus) {
        this.modEventBus = modEventBus;
    }

    @Override
    public synchronized boolean registerEvent(String eventNameOrClass, Consumer<Object[]> dispatcher) {
        String resolvedEventName = resolveEventName(eventNameOrClass);
        if (resolvedEventName == null) {
            return false;
        }

        Consumer<Object[]> adaptedDispatcher = wrapDispatcherIfNeeded(eventNameOrClass, dispatcher);
        dispatchers.computeIfAbsent(resolvedEventName, key -> new ArrayList<>()).add(adaptedDispatcher);
        if (!wiredEvents.add(resolvedEventName)) {
            return true;
        }

        Class<?> eventClass = resolveEventClass(resolvedEventName);
        if (eventClass == null || !Event.class.isAssignableFrom(eventClass)) {
            unwindRegistration(resolvedEventName, adaptedDispatcher);
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> typedEventClass = (Class<? extends Event>) eventClass;
        NeoForge.EVENT_BUS.addListener(typedEventClass,
                event -> dispatch(resolvedEventName, new Object[] { event }));
        return true;
    }

    @Override
    public synchronized boolean registerNativeEvent(
            String alias,
            String eventSource,
            Function<Object[], Object> dispatcher) {
        boolean modBusEvent = eventSource.startsWith("mod:");
        String className = modBusEvent ? eventSource.substring("mod:".length()) : eventSource;
        Class<?> eventClass;
        try {
            eventClass = resolveEventClass(className);
        } catch (LinkageError | SecurityException exception) {
            PyFish.LOGGER.warn("[PyFish] Could not load NeoForge event '{}': {}", className,
                    exception.getMessage());
            return false;
        }
        if (eventClass == null || !Event.class.isAssignableFrom(eventClass)
                || Modifier.isAbstract(eventClass.getModifiers())) {
            PyFish.LOGGER.warn("[PyFish] '{}' is not a concrete, loadable NeoForge event class", className);
            return false;
        }
        IEventBus targetBus = modBusEvent ? modEventBus : NeoForge.EVENT_BUS;
        if (targetBus == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends Event> typedEventClass = (Class<? extends Event>) eventClass;
        try {
            targetBus.addListener(typedEventClass,
                    event -> dispatcher.apply(new Object[] { event }));
            return true;
        } catch (RuntimeException exception) {
            PyFish.LOGGER.warn("[PyFish] Could not subscribe to NeoForge event '{}' as '{}': {}",
                    className, alias, exception.getMessage());
            return false;
        }
    }

    private String resolveEventName(String eventNameOrClass) {
        if (EVENT_ALIASES.containsKey(eventNameOrClass)) {
            return EVENT_ALIASES.get(eventNameOrClass);
        }
        if (eventNameOrClass.contains(".")) {
            return eventNameOrClass;
        }
        return null;
    }

    private Class<?> resolveEventClass(String className) {
        try {
            return Class.forName(className, true, PyFish.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                String innerClassName = className.substring(0, lastDot) + "$" + className.substring(lastDot + 1);
                try {
                    return Class.forName(innerClassName, true, PyFish.class.getClassLoader());
                } catch (ClassNotFoundException ignored) {
                    PyFish.LOGGER.debug("[PyFish] Failed to resolve NeoForge event class '{}'", className);
                }
            }
            return null;
        }
    }

    private void unwindRegistration(String resolvedEventName, Consumer<Object[]> dispatcher) {
        List<Consumer<Object[]>> dispatcherList = dispatchers.get(resolvedEventName);
        if (dispatcherList != null) {
            dispatcherList.remove(dispatcher);
            if (dispatcherList.isEmpty()) {
                dispatchers.remove(resolvedEventName);
            }
        }
        wiredEvents.remove(resolvedEventName);
    }

    private void dispatch(String resolvedEventName, Object[] args) {
        List<Consumer<Object[]>> consumers = dispatchers.get(resolvedEventName);
        if (consumers == null || consumers.isEmpty()) {
            return;
        }

        for (Consumer<Object[]> consumer : new ArrayList<>(consumers)) {
            consumer.accept(args);
        }
    }

    private Consumer<Object[]> wrapDispatcherIfNeeded(String eventNameOrClass, Consumer<Object[]> dispatcher) {
        if (!EVENT_ALIASES.containsKey(eventNameOrClass)) {
            return dispatcher;
        }

        return args -> dispatcher.accept(adaptAliasArguments(eventNameOrClass, args));
    }

    private Object[] adaptAliasArguments(String alias, Object[] args) {
        if (args == null || args.length != 1 || args[0] == null) {
            return args;
        }

        Object wrapped = wrapAliasedEvent(alias, args[0]);
        if (wrapped == null) {
            return args;
        }

        return new Object[] { wrapped };
    }

    private Object wrapAliasedEvent(String alias, Object event) {
        return switch (alias) {
            case "on_block_break" -> wrapBlockBreakEvent(event);
            case "on_player_join", "on_player_leave", "on_player_respawn", "on_player_tick" -> wrapPlayerEvent(event);
            case "on_player_clone" -> wrapPlayerCloneEvent(event);
            case "on_player_chat" -> wrapPlayerChatEvent(event);
            case "on_player_interact_entity" -> wrapPlayerInteractEntityEvent(event);
            case "on_player_interact_block" -> wrapPlayerInteractBlockEvent(event);
            case "on_server_starting", "on_server_started", "on_server_stopping", "on_server_stopped",
                    "on_server_tick" -> wrapServerEvent(event);
            case "on_level_load", "on_level_save", "on_level_unload", "on_level_tick" -> wrapLevelEvent(event);
            case "on_entity_spawn" -> wrapEntitySpawnEvent(event);
            case "on_entity_hurt", "on_entity_damage" -> wrapLivingDamageEvent(event);
            case "on_entity_death" -> wrapLivingDeathEvent(event);
            default -> null;
        };
    }

    private PyFishEventObjects.PlayerEvent wrapPlayerEvent(Object event) {
        ServerPlayer player = extractServerPlayer(event, "getEntity", "getPlayer");
        return player != null ? new PyFishEventObjects.PlayerEvent(player) : null;
    }

    private PyFishEventObjects.PlayerCloneEvent wrapPlayerCloneEvent(Object event) {
        ServerPlayer newPlayer = extractServerPlayer(event, "getEntity", "getPlayer");
        ServerPlayer oldPlayer = extractServerPlayer(event, "getOriginal", "getOldPlayer");
        if (newPlayer == null || oldPlayer == null) {
            return null;
        }

        boolean alive = extractBoolean(event, false, "isAlive");
        Object wasDeath = invokeNoArg(event, "isWasDeath");
        if (wasDeath instanceof Boolean wasDeathBool) {
            alive = !wasDeathBool;
        }

        return new PyFishEventObjects.PlayerCloneEvent(oldPlayer, newPlayer, alive);
    }

    private PyFishEventObjects.PlayerChatEvent wrapPlayerChatEvent(Object event) {
        ServerPlayer player = extractServerPlayer(event, "getPlayer", "getEntity", "getSender");
        if (player == null) {
            return null;
        }

        return new PyFishEventObjects.PlayerChatEvent(
                MinecraftCompat.getServerLevel(player),
                player,
                extractMessageComponent(event));
    }

    private PyFishEventObjects.PlayerInteractEntityEvent wrapPlayerInteractEntityEvent(Object event) {
        ServerPlayer player = extractServerPlayer(event, "getEntity", "getPlayer");
        Entity target = extractEntity(event, "getTarget", "getTargetEntity");
        InteractionHand hand = extractHand(event, "getHand");
        if (player == null || target == null || hand == null) {
            return null;
        }

        return new PyFishEventObjects.PlayerInteractEntityEvent(
                MinecraftCompat.getServerLevel(player),
                player,
                target,
                hand);
    }

    private PyFishEventObjects.PlayerInteractBlockEvent wrapPlayerInteractBlockEvent(Object event) {
        ServerPlayer player = extractServerPlayer(event, "getEntity", "getPlayer");
        BlockPos pos = extractBlockPos(event, "getPos");
        InteractionHand hand = extractHand(event, "getHand");
        if (player == null || pos == null || hand == null) {
            return null;
        }

        return new PyFishEventObjects.PlayerInteractBlockEvent(
                MinecraftCompat.getServerLevel(player),
                player,
                pos,
                hand);
    }

    private PyFishEventObjects.BlockBreakEvent wrapBlockBreakEvent(Object event) {
        ServerPlayer player = extractServerPlayer(event, "getPlayer", "getEntity");
        ServerLevel level = extractServerLevel(event, "getLevel");
        BlockPos pos = extractBlockPos(event, "getPos");
        if (player == null) {
            return null;
        }
        if (level == null) {
            level = MinecraftCompat.getServerLevel(player);
        }
        if (level == null || pos == null) {
            return null;
        }

        return new PyFishEventObjects.BlockBreakEvent(level, player, pos);
    }

    private PyFishEventObjects.ServerEvent wrapServerEvent(Object event) {
        MinecraftServer server = extractServer(event, "getServer");
        return server != null ? new PyFishEventObjects.ServerEvent(server) : null;
    }

    private PyFishEventObjects.LevelEvent wrapLevelEvent(Object event) {
        ServerLevel level = extractServerLevel(event, "getLevel");
        return level != null ? new PyFishEventObjects.LevelEvent(level) : null;
    }

    private PyFishEventObjects.EntitySpawnEvent wrapEntitySpawnEvent(Object event) {
        Entity entity = extractEntity(event, "getEntity");
        ServerLevel level = extractServerLevel(event, "getLevel");
        if (entity == null || level == null) {
            return null;
        }
        return new PyFishEventObjects.EntitySpawnEvent(level, entity);
    }

    private PyFishEventObjects.LivingDamageEvent wrapLivingDamageEvent(Object event) {
        LivingEntity entity = extractLivingEntity(event, "getEntity");
        ServerLevel level = entity != null && entity.level() instanceof ServerLevel serverLevel
                ? serverLevel
                : extractServerLevel(event, "getLevel");
        DamageSource source = extractDamageSource(event, "getSource");
        float amount = extractFloat(event, 0.0F, "getAmount", "getNewDamage");
        if (entity == null || level == null || source == null) {
            return null;
        }

        Object damageTakenValue = invokeNoArg(event, "getNewDamage");
        Object blockedValue = invokeNoArg(event, "isBlocked");
        if (damageTakenValue instanceof Number damageTaken && blockedValue instanceof Boolean blocked) {
            return new PyFishEventObjects.LivingDamageEvent(level, entity, source, amount, damageTaken.floatValue(),
                    blocked);
        }

        return new PyFishEventObjects.LivingDamageEvent(level, entity, source, amount);
    }

    private PyFishEventObjects.LivingDeathEvent wrapLivingDeathEvent(Object event) {
        LivingEntity entity = extractLivingEntity(event, "getEntity");
        ServerLevel level = entity != null && entity.level() instanceof ServerLevel serverLevel
                ? serverLevel
                : extractServerLevel(event, "getLevel");
        DamageSource source = extractDamageSource(event, "getSource");
        if (entity == null || level == null || source == null) {
            return null;
        }

        return new PyFishEventObjects.LivingDeathEvent(level, entity, source);
    }

    private ServerPlayer extractServerPlayer(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private ServerLevel extractServerLevel(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        if (value instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        if (value instanceof ServerPlayer serverPlayer) {
            return MinecraftCompat.getServerLevel(serverPlayer);
        }
        if (value instanceof Entity entity && entity.level() instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        return null;
    }

    private Entity extractEntity(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof Entity entity ? entity : null;
    }

    private LivingEntity extractLivingEntity(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    private BlockPos extractBlockPos(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof BlockPos blockPos ? blockPos : null;
    }

    private InteractionHand extractHand(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof InteractionHand hand ? hand : null;
    }

    private MinecraftServer extractServer(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof MinecraftServer server ? server : null;
    }

    private DamageSource extractDamageSource(Object event, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof DamageSource damageSource ? damageSource : null;
    }

    private Object extractObject(Object event, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invokeNoArg(event, methodName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Component extractMessageComponent(Object event) {
        Object message = extractObject(event, "getRawText", "getMessage", "getOriginalMessage");
        if (message == null) {
            return Component.literal("");
        }
        if (message instanceof Component component) {
            return component;
        }

        Object signedContent = invokeNoArg(message, "signedContent");
        if (signedContent instanceof String stringSignedContent) {
            return Component.literal(stringSignedContent);
        }

        Object stringValue = invokeNoArg(message, "getString");
        if (stringValue instanceof String stringifiedMessage) {
            return Component.literal(stringifiedMessage);
        }

        return Component.literal(message.toString());
    }

    private float extractFloat(Object event, float fallback, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private boolean extractBoolean(Object event, boolean fallback, String... methodNames) {
        Object value = extractObject(event, methodNames);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
