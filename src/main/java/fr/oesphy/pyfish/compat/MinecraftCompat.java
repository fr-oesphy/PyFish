package fr.oesphy.pyfish.compat;

import fr.oesphy.pyfish.PyFish;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.function.Supplier;

public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T id(String namespace, String path) {
        return (T) MinecraftIdentifierCompat.id(namespace, path);
    }

    @SuppressWarnings("unchecked")
    public static <T> T parseId(String value) {
        return (T) MinecraftIdentifierCompat.parseId(value);
    }

    public static String idNamespace(Object id) {
        return MinecraftIdentifierCompat.namespace(id);
    }

    public static String idPath(Object id) {
        return MinecraftIdentifierCompat.path(id);
    }

    public static String idString(Object id) {
        return id != null ? id.toString() : "";
    }

    public static String resourceKeyIdString(Object resourceKey) {
        if (resourceKey == null) {
            return "";
        }

        String text = resourceKey.toString();
        int separatorIndex = text.indexOf(" / ");
        if (text.startsWith("ResourceKey[") && separatorIndex >= 0 && text.endsWith("]")) {
            return text.substring(separatorIndex + 3, text.length() - 1);
        }

        for (String methodName : new String[] { "location", "identifier" }) {
            try {
                Object id = resourceKey.getClass().getMethod(methodName).invoke(resourceKey);
                return idString(id);
            } catch (ReflectiveOperationException ignored) {
                // Try the next compatible accessor.
            }
        }

        return text;
    }

    @SuppressWarnings("unchecked")
    public static <T> T registryRegister(Object registry, String id, T value) {
        try {
            Object parsedId = parseId(id);
            Method registerMethod = findCompatibleStaticMethod(Registry.class, "register", registry, parsedId, value);
            return (T) registerMethod.invoke(null, registry, parsedId, value);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getCause()
                    : e.getCause();
            if (cause != null) {
                throw new IllegalStateException(
                        "Unable to register " + id + " into registry " + registry + ": " + cause.getMessage(),
                        cause);
            }
            throw new IllegalStateException("Unable to register " + id + " into registry " + registry, e);
        }
    }

    /**
     * Fabric 1.21.x can expose some built-in registries as frozen by the time PyFish
     * executes user scripts. For dynamic Python-driven content we temporarily unfreeze
     * the target registry when the implementation supports it, perform the write, then
     * freeze it again.
     */
    public static <T> T registryRegisterWithTemporaryUnfreeze(Object registry, String id, T value) {
        RegistryWritableSession writableSession = makeRegistryWritable(registry);

        try {
            return registryRegister(registry, id, value);
        } finally {
            writableSession.restore(id);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createResourceKey(Object registryKey, String id) {
        try {
            Object parsedId = parseId(id);
            Method createMethod = findCompatibleStaticMethod(ResourceKey.class, "create", registryKey, parsedId);
            return (T) createMethod.invoke(null, registryKey, parsedId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create resource key for " + id, e);
        }
    }

    public static void bindPropertyIdIfPresent(Object properties, Object registryKey, String id) {
        if (properties == null || registryKey == null || id == null || id.isBlank()) {
            return;
        }

        try {
            Object resourceKey = createResourceKey(registryKey, id);
            try {
                Method setIdMethod = findSingleArgMethod(properties.getClass(), "setId", resourceKey.getClass());
                setIdMethod.invoke(properties, resourceKey);
                return;
            } catch (NoSuchMethodException ignored) {
                if (bindUniqueFieldByType(properties, resourceKey)) {
                    return;
                }
                // Some runtimes, especially Fabric intermediary jars, remap method names
                // away from Mojang names while still keeping a unique ResourceKey field for
                // the builder identifier. When we cannot find either path we deliberately
                // leave the builder untouched rather than guessing at another ResourceKey
                // mutator such as jukeboxPlayable(...) or trimMaterial(...).
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to bind id " + id + " to property builder " + properties, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createTagKey(Object registryKey, String id) {
        try {
            Object parsedId = parseId(id);
            Method createMethod = findCompatibleStaticMethod(TagKey.class, "create", registryKey, parsedId);
            return (T) createMethod.invoke(null, registryKey, parsedId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create tag key for " + id, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T registryGet(Object registry, String id) {
        try {
            Object key = parseId(id);
            for (String methodName : new String[] { "get", "getValue", "getOptional", "getHolder" }) {
                Method namedMethod = findSingleArgMethodIfPresent(registry.getClass(), methodName, key.getClass());
                if (namedMethod == null) {
                    continue;
                }

                T value = unwrapRegistryValue(namedMethod.invoke(registry, key));
                if (value != null) {
                    return value;
                }
            }

            Method getMethod = findCompatibleRegistryLookupMethod(registry.getClass(), key.getClass());
            return unwrapRegistryValue(getMethod.invoke(registry, key));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to query registry for identifier " + id, e);
        }
    }

    public static ServerLevel getServerLevel(ServerPlayer player) {
        if (player == null) {
            throw new IllegalStateException("Unable to resolve server level for a null player");
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            return serverLevel;
        }

        try {
            Method resolver = findNoArgMethod(player.getClass(), "serverLevel", "level", "getLevel",
                    "getCommandSenderWorld");
            Object resolved = resolver.invoke(player);
            if (resolved instanceof ServerLevel serverLevel) {
                return serverLevel;
            }
            throw new IllegalStateException(
                    "Resolved level for player " + getPlayerName(player) + " is not a ServerLevel: " + resolved);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve server level for player " + getPlayerName(player), e);
        }
    }

    public static MinecraftServer getServer(ServerPlayer player) {
        ServerLevel level = getServerLevel(player);
        return level != null ? level.getServer() : null;
    }

    public static String getPlayerName(ServerPlayer player) {
        return player != null ? player.getName().getString() : "unknown";
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> T createEntity(EntityType<?> type, ServerLevel level) {
        Throwable lastFailure = null;

        for (Method method : findCompatibleEntityCreateMethods(type.getClass(), level.getClass())) {
            try {
                Object[] arguments = buildEntityCreateArguments(method.getParameterTypes(), level);
                Object created = method.invoke(type, arguments);
                if (created instanceof Entity entity) {
                    return (T) entity;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                lastFailure = e instanceof InvocationTargetException invocationTargetException
                        ? invocationTargetException.getCause()
                        : e;
            }
        }

        try {
            Method createLevelOnly = type.getClass().getMethod("create", Level.class);
            return (T) createLevelOnly.invoke(type, level);
        } catch (ReflectiveOperationException e) {
            lastFailure = e instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getCause()
                    : e;
        }

        try {
            Method createWithReason = findEntityCreateWithSpawnReasonMethod(type.getClass(), level.getClass());
            Object spawnReason = findEnumConstant(createWithReason.getParameterTypes()[1], "COMMAND");
            return (T) createWithReason.invoke(type, level, spawnReason);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e instanceof InvocationTargetException invocationTargetException
                    ? invocationTargetException.getCause()
                    : e;
            if (lastFailure == null) {
                lastFailure = cause;
            }
        }

        throw new IllegalStateException("Unable to create entity " + type, lastFailure);
    }

    public static void killEntity(Entity entity) {
        if (entity == null) {
            return;
        }

        try {
            Level level = entity.level();
            if (level != null) {
                Method killWithLevel = findSingleArgMethodIfPresent(entity.getClass(), "kill", level.getClass());
                if (killWithLevel != null) {
                    killWithLevel.invoke(entity, level);
                    return;
                }
            }

            Method killMethod = findNoArgMethod(entity.getClass(), "kill");
            killMethod.invoke(entity);
            return;
        } catch (NoSuchMethodException ignored) {
            entity.discard();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to kill entity " + entity, e);
        }
    }

    public static void setEntityPosition(Entity entity, double x, double y, double z) {
        try {
            entity.setPos(x, y, z);
            return;
        } catch (Throwable directSetPosFailure) {
            try {
                entity.getClass()
                        .getMethod("moveTo", double.class, double.class, double.class, float.class, float.class)
                        .invoke(entity, x, y, z, 0.0F, 0.0F);
                return;
            } catch (NoSuchMethodException ignored) {
                try {
                    entity.getClass()
                            .getMethod("moveTo", double.class, double.class, double.class)
                            .invoke(entity, x, y, z);
                    return;
                } catch (NoSuchMethodException ignoredAgain) {
                    try {
                        entity.getClass()
                                .getMethod("setPos", double.class, double.class, double.class)
                                .invoke(entity, x, y, z);
                        return;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Unable to position entity " + entity, e);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Unable to position entity " + entity, e);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to position entity " + entity, e);
            }
        }
    }

    public static ItemStack createItemStack(Object itemLike) {
        if (itemLike == null) {
            throw new IllegalArgumentException("ItemStack cannot be created from a null item");
        }

        for (Constructor<?> constructor : ItemStack.class.getConstructors()) {
            if (constructor.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = constructor.getParameterTypes()[0];
            if (!parameterType.isInstance(itemLike) && !parameterType.isAssignableFrom(itemLike.getClass())) {
                continue;
            }

            try {
                return (ItemStack) constructor.newInstance(itemLike);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to create ItemStack from " + itemLike, e);
            }
        }

        throw new IllegalStateException("No compatible ItemStack constructor found for " + itemLike.getClass().getName());
    }

    public static CreativeModeTab createCreativeModeTab(String displayName, Supplier<ItemStack> iconSupplier) {
        try {
            Method builderMethod = findCreativeTabBuilderMethod();
            Object builder = builderMethod.getParameterCount() == 0
                    ? builderMethod.invoke(null)
                    : builderMethod.invoke(null, creativeTabRowTop(), 0);

            builder = builder.getClass()
                    .getMethod("title", net.minecraft.network.chat.Component.class)
                    .invoke(builder, net.minecraft.network.chat.Component.literal(displayName));
            builder = builder.getClass()
                    .getMethod("icon", Supplier.class)
                    .invoke(builder, iconSupplier);

            return (CreativeModeTab) builder.getClass().getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create creative tab " + displayName, e);
        }
    }

    private static Method findNoArgMethod(Class<?> type, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the next candidate.
            }
        }

        throw new NoSuchMethodException("No compatible method found on " + type.getName());
    }

    private static Method findSingleArgMethod(Class<?> type, String name, Class<?> argumentType)
            throws NoSuchMethodException {
        try {
            return type.getMethod(name, argumentType);
        } catch (NoSuchMethodException ignored) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                    continue;
                }

                if (method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
                    return method;
                }
            }
        }

        throw new NoSuchMethodException("No compatible " + name + "(" + argumentType.getName() + ") found on "
                + type.getName());
    }

    private static Method findSingleArgMethodIfPresent(Class<?> type, String name, Class<?> argumentType) {
        try {
            return findSingleArgMethod(type, name, argumentType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean bindUniqueFieldByType(Object target, Object value) throws ReflectiveOperationException {
        Field candidate = findUniqueInstanceFieldByType(target.getClass(), value.getClass());
        if (candidate == null) {
            return false;
        }

        candidate.setAccessible(true);
        candidate.set(target, value);
        return true;
    }

    private static Field findUniqueInstanceFieldByType(Class<?> type, Class<?> fieldType) {
        Field candidate = null;
        Class<?> current = type;

        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!field.getType().isAssignableFrom(fieldType)) {
                    continue;
                }

                if (candidate != null) {
                    return null;
                }
                candidate = field;
            }
            current = current.getSuperclass();
        }

        return candidate;
    }

    private static Method findCompatibleSingleArgMethod(Class<?> type, Class<?> argumentType)
            throws NoSuchMethodException {
        Method bestMatch = null;

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(argumentType)) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            if (returnType != Void.TYPE && !type.isAssignableFrom(returnType)) {
                continue;
            }

            if (bestMatch == null || bestMatch.getReturnType() != Void.TYPE) {
                bestMatch = method;
                if (returnType == Void.TYPE) {
                    return method;
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch;
        }

        throw new NoSuchMethodException(
                "No compatible single-argument method found on " + type.getName() + " for " + argumentType.getName());
    }

    private static Method findCompatibleRegistryLookupMethod(Class<?> type, Class<?> argumentType)
            throws NoSuchMethodException {
        Method exactObjectMatch = null;
        Method optionalMatch = null;
        Method assignableObjectMatch = null;

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(argumentType)) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE || returnType == Boolean.TYPE || returnType == Boolean.class
                    || returnType.isPrimitive()) {
                continue;
            }

            if (isUnsupportedRegistryLookupReturnType(returnType, argumentType)) {
                continue;
            }

            boolean exactParameterMatch = parameterType.equals(argumentType);

            if (Optional.class.isAssignableFrom(returnType)) {
                if (optionalMatch == null || exactParameterMatch) {
                    optionalMatch = method;
                    if (exactParameterMatch) {
                        continue;
                    }
                }
                continue;
            }

            if (exactParameterMatch) {
                if (exactObjectMatch == null) {
                    exactObjectMatch = method;
                }
                continue;
            }

            if (assignableObjectMatch == null) {
                assignableObjectMatch = method;
            }
        }

        if (exactObjectMatch != null) {
            return exactObjectMatch;
        }

        if (optionalMatch != null) {
            return optionalMatch;
        }

        if (assignableObjectMatch != null) {
            return assignableObjectMatch;
        }

        throw new NoSuchMethodException(
                "No compatible registry lookup method found on " + type.getName() + " for " + argumentType.getName());
    }

    private static boolean isUnsupportedRegistryLookupReturnType(Class<?> returnType, Class<?> argumentType) {
        if (returnType.equals(argumentType) || CharSequence.class.isAssignableFrom(returnType)) {
            return true;
        }

        String returnTypeName = returnType.getName();
        return returnTypeName.contains("DataResult")
                || returnTypeName.contains("Codec")
                || returnTypeName.contains("Stream")
                || returnTypeName.contains("Set")
                || returnTypeName.contains("Map");
    }

    private static Method findCompatibleStaticMethod(Class<?> type, String name, Object... arguments)
            throws NoSuchMethodException {
        Method namedMatch = findCompatibleStaticMethod(type, arguments, true, name);
        if (namedMatch != null) {
            return namedMatch;
        }

        Method unnamedMatch = findCompatibleStaticMethod(type, arguments, false, name);
        if (unnamedMatch != null) {
            return unnamedMatch;
        }

        throw new NoSuchMethodException("No compatible static " + name + "(...) found on " + type.getName());
    }

    private static Method findCompatibleStaticMethod(Class<?> type, Object[] arguments, boolean requireName,
            String name) {
        for (Method method : type.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (requireName && !method.getName().equals(name)) {
                continue;
            }
            if (method.getParameterCount() != arguments.length) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < arguments.length; index++) {
                Object argument = arguments[index];
                if (argument == null) {
                    continue;
                }
                if (!parameterTypes[index].isInstance(argument)
                        && !parameterTypes[index].isAssignableFrom(argument.getClass())) {
                    compatible = false;
                    break;
                }
            }

            if (compatible) {
                return method;
            }
        }

        return null;
    }

    private static boolean invokeRegistryMethodIfPresent(Object registry, String methodName) {
        try {
            registry.getClass().getMethod(methodName).invoke(registry);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke " + methodName + "() on registry " + registry, e);
        }
    }

    private static RegistryWritableSession makeRegistryWritable(Object registry) {
        if (invokeRegistryMethodIfPresent(registry, "unfreeze")) {
            return new RegistryWritableSession(registry, null, null, true);
        }

        Field frozenField = findRegistryFrozenField(registry.getClass());
        if (frozenField == null) {
            return RegistryWritableSession.NO_OP;
        }

        try {
            frozenField.setAccessible(true);
            Boolean previousFrozen = (Boolean) frozenField.get(registry);
            frozenField.set(registry, false);
            return new RegistryWritableSession(registry, frozenField, previousFrozen, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to make registry writable " + registry, e);
        }
    }

    private static Field findRegistryFrozenField(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("frozen");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private record RegistryWritableSession(Object registry, Field frozenField, Boolean previousFrozen,
            boolean viaUnfreezeMethod) {
        private static final RegistryWritableSession NO_OP = new RegistryWritableSession(null, null, null, false);

        void restore(String id) {
            if (registry == null) {
                return;
            }

            if (viaUnfreezeMethod || frozenField != null) {
                try {
                    registry.getClass().getMethod("freeze").invoke(registry);
                } catch (ReflectiveOperationException e) {
                    PyFish.LOGGER.warn("[PyFish] Failed to re-freeze registry {} after registering {}", registry, id, e);
                }
            }

            if (frozenField != null && previousFrozen != null) {
                try {
                    frozenField.setAccessible(true);
                    frozenField.set(registry, previousFrozen);
                } catch (ReflectiveOperationException e) {
                    PyFish.LOGGER.warn("[PyFish] Failed to restore frozen state on registry {} after registering {}",
                            registry, id, e);
                }
            }
        }
    }

    private static Method findCreativeTabBuilderMethod() throws NoSuchMethodException {
        for (Method method : CreativeModeTab.class.getMethods()) {
            if (!method.getName().equals("builder")) {
                continue;
            }
            if (method.getParameterCount() == 0 || method.getParameterCount() == 2) {
                return method;
            }
        }
        throw new NoSuchMethodException("No compatible CreativeModeTab.builder(...) method found");
    }

    @SuppressWarnings("unchecked")
    private static Object creativeTabRowTop() {
        for (Class<?> innerClass : CreativeModeTab.class.getDeclaredClasses()) {
            if (!innerClass.isEnum() || !"Row".equals(innerClass.getSimpleName())) {
                continue;
            }
            return Enum.valueOf((Class<? extends Enum>) innerClass.asSubclass(Enum.class), "TOP");
        }
        throw new IllegalStateException("CreativeModeTab.Row enum is unavailable");
    }

    private static Method findEntityCreateWithSpawnReasonMethod(Class<?> entityTypeClass, Class<?> levelClass)
            throws NoSuchMethodException {
        for (Method method : entityTypeClass.getMethods()) {
            if (!isCompatibleEntityCreateMethod(method, levelClass, true) || method.getParameterCount() != 2) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            return method;
        }

        for (Method method : entityTypeClass.getMethods()) {
            if (!isCompatibleEntityCreateMethod(method, levelClass, false) || method.getParameterCount() != 2) {
                continue;
            }
            return method;
        }

        throw new NoSuchMethodException("No compatible create(Level, SpawnReason) method found on "
                + entityTypeClass.getName());
    }

    private static Method[] findCompatibleEntityCreateMethods(Class<?> entityTypeClass, Class<?> levelClass) {
        Method[] namedMatches = collectCompatibleEntityCreateMethods(entityTypeClass, levelClass, true);
        if (namedMatches.length > 0) {
            return namedMatches;
        }
        return collectCompatibleEntityCreateMethods(entityTypeClass, levelClass, false);
    }

    private static Method[] collectCompatibleEntityCreateMethods(Class<?> entityTypeClass, Class<?> levelClass,
            boolean requireNamedCreate) {
        return java.util.Arrays.stream(entityTypeClass.getMethods())
                .filter(method -> isCompatibleEntityCreateMethod(method, levelClass, requireNamedCreate))
                .sorted((left, right) -> Integer.compare(left.getParameterCount(), right.getParameterCount()))
                .toArray(Method[]::new);
    }

    private static boolean isCompatibleEntityCreateMethod(Method method, Class<?> levelClass,
            boolean requireNamedCreate) {
        if (Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (requireNamedCreate && !method.getName().equals("create")) {
            return false;
        }
        if (!Entity.class.isAssignableFrom(method.getReturnType())) {
            return false;
        }
        if (method.getParameterCount() < 1 || method.getParameterCount() > 2) {
            return false;
        }
        if (!method.getParameterTypes()[0].isAssignableFrom(levelClass)) {
            return false;
        }
        return method.getParameterCount() != 2 || method.getParameterTypes()[1].isEnum();
    }

    private static Object[] buildEntityCreateArguments(Class<?>[] parameterTypes, ServerLevel level) {
        Object[] arguments = new Object[parameterTypes.length];
        arguments[0] = level;

        for (int index = 1; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];
            if (parameterType.isEnum()) {
                arguments[index] = findEnumConstant(parameterType, "COMMAND");
                continue;
            }

            if (parameterType == boolean.class || parameterType == Boolean.class) {
                arguments[index] = false;
                continue;
            }

            if (parameterType == int.class || parameterType == Integer.class) {
                arguments[index] = 0;
                continue;
            }

            if (parameterType == long.class || parameterType == Long.class) {
                arguments[index] = 0L;
                continue;
            }

            if (parameterType == float.class || parameterType == Float.class) {
                arguments[index] = 0.0F;
                continue;
            }

            if (parameterType == double.class || parameterType == Double.class) {
                arguments[index] = 0.0D;
                continue;
            }

            arguments[index] = null;
        }

        return arguments;
    }

    @SuppressWarnings("unchecked")
    private static Object findEnumConstant(Class<?> enumType, String preferredName) {
        if (!enumType.isEnum()) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum");
        }

        try {
            return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), preferredName);
        } catch (IllegalArgumentException ignored) {
            Object[] constants = enumType.getEnumConstants();
            if (constants != null && constants.length > 0) {
                return constants[0];
            }
            throw new IllegalStateException("No enum constants available on " + enumType.getName());
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T unwrapRegistryValue(Object result) {
        if (result == null) {
            return null;
        }

        if (result instanceof Optional<?> optional) {
            return unwrapRegistryValue(optional.orElse(null));
        }

        String className = result.getClass().getName();
        if (className.contains("Holder")) {
            try {
                Method valueMethod = result.getClass().getMethod("value");
                Object unwrapped = valueMethod.invoke(result);
                if (unwrapped != result) {
                    return unwrapRegistryValue(unwrapped);
                }
            } catch (ReflectiveOperationException ignored) {
                // Some holder implementations may not expose a public value() method.
            }
        }

        return (T) result;
    }
}
