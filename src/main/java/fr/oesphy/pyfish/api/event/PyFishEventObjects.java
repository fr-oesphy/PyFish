package fr.oesphy.pyfish.api.event;

import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class PyFishEventObjects {
    private PyFishEventObjects() {
    }

    public static final class PlayerHandle {
        private final ServerPlayer player;
        private final ServerLevel level;

        public PlayerHandle(ServerPlayer player) {
            this(player, null);
        }

        public PlayerHandle(ServerPlayer player, ServerLevel level) {
            this.player = player;
            this.level = level;
        }

        public ServerPlayer unwrapPlayer() {
            return player;
        }

        public ServerLevel serverLevel() {
            return level != null ? level : MinecraftCompat.getServerLevel(player);
        }

        public String getArmorItemId(String slotName) {
            if (slotName == null) {
                throw new IllegalArgumentException("Armor slot cannot be null");
            }

            EquipmentSlot slot = switch (slotName.toLowerCase(Locale.ROOT)) {
                case "head" -> EquipmentSlot.HEAD;
                case "chest" -> EquipmentSlot.CHEST;
                case "legs" -> EquipmentSlot.LEGS;
                case "feet" -> EquipmentSlot.FEET;
                default -> throw new IllegalArgumentException(
                        "Unknown armor slot '" + slotName + "'. Expected head, chest, legs, or feet.");
            };

            ItemStack stack = player.getItemBySlot(slot);
            return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        public String getItemInSlot(int slot) {
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                throw new IllegalArgumentException("Inventory slot out of range: " + slot);
            }

            ItemStack stack = player.getInventory().getItem(slot);
            return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        public int getBlockX() {
            return player.getBlockX();
        }

        public int getBlockY() {
            return player.getBlockY();
        }

        public int getBlockZ() {
            return player.getBlockZ();
        }

        public double getX() {
            return player.getX();
        }

        public double getY() {
            return player.getY();
        }

        public double getZ() {
            return player.getZ();
        }

        public Component getName() {
            return player.getName();
        }
    }

    public static final class EntityHandle {
        private final Entity entity;

        public EntityHandle(Entity entity) {
            this.entity = entity;
        }

        public Entity unwrapEntity() {
            return entity;
        }

        public ServerLevel serverLevel() {
            return (ServerLevel) entity.level();
        }

        public double getX() {
            return entity.getX();
        }

        public double getY() {
            return entity.getY();
        }

        public double getZ() {
            return entity.getZ();
        }

        public Component getName() {
            return entity.getName();
        }
    }

    public static final class BlockPosHandle {
        private final BlockPos pos;

        public BlockPosHandle(BlockPos pos) {
            this.pos = pos;
        }

        public BlockPos unwrapBlockPos() {
            return pos;
        }

        public int getX() {
            return pos.getX();
        }

        public int getY() {
            return pos.getY();
        }

        public int getZ() {
            return pos.getZ();
        }
    }

    public static Object wrapContentPlayer(Player player, Level level) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = level instanceof ServerLevel resolvedLevel
                    ? resolvedLevel
                    : MinecraftCompat.getServerLevel(serverPlayer);
            return new PlayerHandle(serverPlayer, serverLevel);
        }

        return player;
    }

    public static Object wrapContentEntity(LivingEntity entity, Level level) {
        if (entity instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = level instanceof ServerLevel resolvedLevel
                    ? resolvedLevel
                    : MinecraftCompat.getServerLevel(serverPlayer);
            return new PlayerHandle(serverPlayer, serverLevel);
        }

        if (entity instanceof Entity rawEntity) {
            return new EntityHandle(rawEntity);
        }

        return entity;
    }

    public static final class ItemUseOnContextHandle {
        private final UseOnContext context;
        private final ServerLevel level;

        public ItemUseOnContextHandle(UseOnContext context, ServerLevel level) {
            this.context = context;
            this.level = level;
        }

        public UseOnContext unwrapContext() {
            return context;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public PlayerHandle getPlayer() {
            Player player = context.getPlayer();
            return player instanceof ServerPlayer serverPlayer ? new PlayerHandle(serverPlayer, level) : null;
        }

        public BlockPosHandle getClickedPos() {
            return new BlockPosHandle(context.getClickedPos());
        }

        public String getHand() {
            return context.getHand().name();
        }

        public ItemStack getItemInHand() {
            return context.getItemInHand();
        }
    }

    public static final class ServerEvent {
        private final MinecraftServer server;

        public ServerEvent(MinecraftServer server) {
            this.server = server;
        }

        public MinecraftServer getServer() {
            return server;
        }

        public MinecraftServer unwrapServer() {
            return server;
        }
    }

    public static final class LevelEvent {
        private final ServerLevel level;

        public LevelEvent(ServerLevel level) {
            this.level = level;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public ServerLevel unwrapLevel() {
            return level;
        }

        public MinecraftServer getServer() {
            return level.getServer();
        }
    }

    public static class PlayerEvent {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final MinecraftServer server;

        public PlayerEvent(ServerPlayer player) {
            this(player, null, null);
        }

        public PlayerEvent(ServerPlayer player, ServerLevel level, MinecraftServer server) {
            this.player = player;
            this.level = level;
            this.server = server;
        }

        public PlayerHandle getPlayer() {
            return new PlayerHandle(player, getLevel());
        }

        public PlayerHandle getEntity() {
            return new PlayerHandle(player, getLevel());
        }

        public ServerLevel getLevel() {
            return level != null ? level : MinecraftCompat.getServerLevel(player);
        }

        public MinecraftServer getServer() {
            if (server != null) {
                return server;
            }

            ServerLevel resolvedLevel = getLevel();
            return resolvedLevel != null ? resolvedLevel.getServer() : MinecraftCompat.getServer(player);
        }
    }

    public static final class PlayerCloneEvent extends PlayerEvent {
        private final ServerPlayer oldPlayer;
        private final boolean alive;

        public PlayerCloneEvent(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
            super(newPlayer, MinecraftCompat.getServerLevel(newPlayer), MinecraftCompat.getServer(newPlayer));
            this.oldPlayer = oldPlayer;
            this.alive = alive;
        }

        public PlayerHandle getOldPlayer() {
            return new PlayerHandle(oldPlayer, MinecraftCompat.getServerLevel(oldPlayer));
        }

        public boolean isAlive() {
            return alive;
        }
    }

    public static final class BlockBreakEvent {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final BlockPos pos;

        public BlockBreakEvent(ServerLevel level, ServerPlayer player, BlockPos pos) {
            this.level = level;
            this.player = player;
            this.pos = pos;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public PlayerHandle getPlayer() {
            return new PlayerHandle(player, level);
        }

        public PlayerHandle getEntity() {
            return new PlayerHandle(player, level);
        }

        public BlockPosHandle getPos() {
            return new BlockPosHandle(pos);
        }

        public MinecraftServer getServer() {
            return level.getServer();
        }
    }

    public static final class PlayerChatEvent {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final Component message;

        public PlayerChatEvent(ServerLevel level, ServerPlayer player, Component message) {
            this.level = level;
            this.player = player;
            this.message = message != null ? message : Component.literal("");
        }

        public ServerLevel getLevel() {
            return level;
        }

        public PlayerHandle getPlayer() {
            return new PlayerHandle(player, level);
        }

        public PlayerHandle getEntity() {
            return new PlayerHandle(player, level);
        }

        public Component getMessage() {
            return message;
        }

        public String getMessageString() {
            return message.getString();
        }

        public MinecraftServer getServer() {
            return level.getServer();
        }
    }

    public static class EntityEvent {
        private final ServerLevel level;
        private final Entity entity;

        protected EntityEvent(ServerLevel level, Entity entity) {
            this.level = level;
            this.entity = entity;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public EntityHandle getEntity() {
            return new EntityHandle(entity);
        }

        public PlayerHandle getPlayer() {
            return entity instanceof ServerPlayer serverPlayer ? new PlayerHandle(serverPlayer, level) : null;
        }

        public MinecraftServer getServer() {
            return level.getServer();
        }
    }

    public static final class EntitySpawnEvent extends EntityEvent {
        public EntitySpawnEvent(ServerLevel level, Entity entity) {
            super(level, entity);
        }
    }

    public static final class EntityDimensionChangeEvent extends EntityEvent {
        private final ServerLevel originLevel;

        public EntityDimensionChangeEvent(ServerLevel originLevel, ServerLevel destinationLevel, Entity entity) {
            super(destinationLevel, entity);
            this.originLevel = originLevel;
        }

        public ServerLevel getOriginLevel() {
            return originLevel;
        }
    }

    public static class LivingEvent extends EntityEvent {
        private final LivingEntity entity;

        protected LivingEvent(ServerLevel level, LivingEntity entity) {
            super(level, entity);
            this.entity = entity;
        }

        public EntityHandle getLivingEntity() {
            return new EntityHandle(entity);
        }
    }

    public static final class LivingDamageEvent extends LivingEvent {
        private final DamageSource damageSource;
        private final float amount;
        private final Float damageTaken;
        private final boolean blocked;

        public LivingDamageEvent(ServerLevel level, LivingEntity entity, DamageSource damageSource, float amount) {
            this(level, entity, damageSource, amount, null, false);
        }

        public LivingDamageEvent(ServerLevel level, LivingEntity entity, DamageSource damageSource, float amount,
                Float damageTaken, boolean blocked) {
            super(level, entity);
            this.damageSource = damageSource;
            this.amount = amount;
            this.damageTaken = damageTaken;
            this.blocked = blocked;
        }

        public float getAmount() {
            return amount;
        }

        public float getDamageTaken() {
            return damageTaken != null ? damageTaken : amount;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public String getDamageSourceName() {
            return damageSource.type().msgId();
        }
    }

    public static final class LivingDeathEvent extends LivingEvent {
        private final DamageSource damageSource;

        public LivingDeathEvent(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
            super(level, entity);
            this.damageSource = damageSource;
        }

        public String getDamageSourceName() {
            return damageSource.type().msgId();
        }
    }

    public static class PlayerInteractEvent {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final InteractionHand hand;

        protected PlayerInteractEvent(ServerLevel level, ServerPlayer player, InteractionHand hand) {
            this.level = level;
            this.player = player;
            this.hand = hand;
        }

        public ServerLevel getLevel() {
            return level;
        }

        public PlayerHandle getPlayer() {
            return new PlayerHandle(player, level);
        }

        public PlayerHandle getEntity() {
            return new PlayerHandle(player, level);
        }

        public String getHand() {
            return hand.name();
        }

        public MinecraftServer getServer() {
            return level.getServer();
        }
    }

    public static final class PlayerInteractBlockEvent extends PlayerInteractEvent {
        private final BlockPos pos;

        public PlayerInteractBlockEvent(ServerLevel level, ServerPlayer player, BlockPos pos, InteractionHand hand) {
            super(level, player, hand);
            this.pos = pos;
        }

        public BlockPosHandle getPos() {
            return new BlockPosHandle(pos);
        }
    }

    public static final class PlayerInteractEntityEvent extends PlayerInteractEvent {
        private final Entity target;

        public PlayerInteractEntityEvent(ServerLevel level, ServerPlayer player, Entity target, InteractionHand hand) {
            super(level, player, hand);
            this.target = target;
        }

        public EntityHandle getTarget() {
            return new EntityHandle(target);
        }
    }
}
