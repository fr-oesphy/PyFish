package fr.oesphy.pyfish.content;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public final class PyFishPackMetadataFactory {
    private static final Component DESCRIPTION = Component.literal("PyFish Generated Resources");

    private PyFishPackMetadataFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(Object metadataType, PackType packType) {
        try {
            Class<?> packMetadataClass = PackMetadataSection.class;
            if (!isPackMetadataType(packMetadataClass, metadataType)) {
                return null;
            }

            Object currentPackVersion = resolveCurrentPackVersion(packType);
            for (boolean allowOptional : new boolean[] { false, true }) {
                for (Constructor<?> constructor : packMetadataClass.getConstructors()) {
                    Class<?>[] parameters = constructor.getParameterTypes();
                    if ((parameters.length != 2 && parameters.length != 3)
                            || !Component.class.isAssignableFrom(parameters[0])) {
                        continue;
                    }

                    Object[] arguments = new Object[parameters.length];
                    arguments[0] = DESCRIPTION;

                    boolean supported = true;
                    for (int i = 1; i < parameters.length; i++) {
                        Object argument = createPackMetadataArgument(
                                parameters[i],
                                constructor.getGenericParameterTypes()[i],
                                currentPackVersion,
                                allowOptional);
                        if (argument == UnsupportedArgument.INSTANCE) {
                            supported = false;
                            break;
                        }
                        arguments[i] = argument;
                    }

                    if (!supported) {
                        continue;
                    }

                    try {
                        return (T) constructor.newInstance(arguments);
                    } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                        // Try the next compatible constructor shape for this version.
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        return null;
    }

    public static byte[] createPackMcmetaBytes(PackType packType) {
        try {
            int packFormat = MinecraftPackVersionCompat.currentFormatNumber(packType);

            String json = """
                    {
                      "pack": {
                        "pack_format": %d,
                        "description": "PyFish Generated Resources"
                      }
                    }
                    """.formatted(packFormat);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isPackMetadataType(Class<?> packMetadataClass, Object metadataType)
            throws IllegalAccessException {
        if (metadataType == null) {
            return false;
        }

        for (Field field : packMetadataClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!field.getType().isInstance(metadataType)) {
                continue;
            }

            try {
                field.setAccessible(true);
            } catch (RuntimeException ignored) {
                // Some runtimes may refuse deep reflective access; fall back to public access only.
            }
            if (field.get(null) == metadataType) {
                return true;
            }
        }

        for (String methodName : new String[] { "getMetadataSectionName", "name", "id", "getName", "getId" }) {
            try {
                Method method = metadataType.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) {
                    continue;
                }

                Object value = method.invoke(metadataType);
                if (value != null && "pack".equals(value.toString())) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next shape that the current loader/runtime may expose.
            }
        }

        String metadataTypeName = metadataType.getClass().getName();
        return metadataTypeName.contains("PackMetadata")
                || metadataTypeName.endsWith("PackMetadataSectionSerializer")
                || metadataType.toString().equals("pack");
    }

    private static Object resolveCurrentPackVersion(PackType packType) throws ReflectiveOperationException {
        return MinecraftPackVersionCompat.current(packType);
    }

    private static Object createSupportedFormatsArgument(
            Class<?> parameterType,
            Type genericParameterType,
            Object currentPackVersion,
            boolean allowOptional) throws ReflectiveOperationException {
        if (Optional.class.isAssignableFrom(parameterType)) {
            if (!allowOptional) {
                return UnsupportedArgument.INSTANCE;
            }

            Object optionalPayload = createOptionalPayload(genericParameterType, currentPackVersion);
            if (optionalPayload == UnsupportedArgument.INSTANCE) {
                return UnsupportedArgument.INSTANCE;
            }

            return optionalPayload != null ? Optional.of(optionalPayload) : Optional.empty();
        }

        if (parameterType == int.class || parameterType == Integer.class) {
            if (currentPackVersion instanceof Number number) {
                return number.intValue();
            }

            return UnsupportedArgument.INSTANCE;
        }

        if (InclusiveRange.class.isAssignableFrom(parameterType)) {
            Object rangeValue = rangeValue(genericParameterType, currentPackVersion);
            if (rangeValue == null) {
                return UnsupportedArgument.INSTANCE;
            }

            return createInclusiveRange(parameterType, rangeValue);
        }

        return UnsupportedArgument.INSTANCE;
    }

    private static Object createPackMetadataArgument(
            Class<?> parameterType,
            Type genericParameterType,
            Object currentPackVersion,
            boolean allowOptional) throws ReflectiveOperationException {
        if (parameterType == int.class || parameterType == Integer.class) {
            Integer packFormat = extractPackFormatNumber(currentPackVersion);
            return packFormat != null ? packFormat : UnsupportedArgument.INSTANCE;
        }

        Object supportedFormatsArgument = createSupportedFormatsArgument(
                parameterType,
                genericParameterType,
                currentPackVersion,
                allowOptional);
        if (supportedFormatsArgument != UnsupportedArgument.INSTANCE) {
            return supportedFormatsArgument;
        }

        Object normalizedPackFormat = normalizePackFormat(currentPackVersion);
        if (normalizedPackFormat != null && parameterType.isInstance(normalizedPackFormat)) {
            return normalizedPackFormat;
        }

        return UnsupportedArgument.INSTANCE;
    }

    private static Object createOptionalPayload(Type genericParameterType, Object currentPackVersion)
            throws ReflectiveOperationException {
        if (!(genericParameterType instanceof ParameterizedType optionalType)
                || optionalType.getActualTypeArguments().length != 1) {
            return null;
        }

        Type payloadType = optionalType.getActualTypeArguments()[0];
        if (payloadType instanceof ParameterizedType rangeType
                && rangeType.getRawType() instanceof Class<?> rangeClass
                && InclusiveRange.class.isAssignableFrom(rangeClass)) {
            Object rangeValue = rangeValue(rangeType, currentPackVersion);
            if (rangeValue == null) {
                return UnsupportedArgument.INSTANCE;
            }

            return createInclusiveRange(rangeClass, rangeValue);
        }

        if (payloadType instanceof Class<?> payloadClass) {
            if ((payloadClass == Integer.class || payloadClass == int.class)
                    && currentPackVersion instanceof Number number) {
                return number.intValue();
            }
            if (payloadClass.isInstance(currentPackVersion)) {
                return currentPackVersion;
            }
        }

        return null;
    }

    private static Object createInclusiveRange(Class<?> parameterType, Object packFormat)
            throws ReflectiveOperationException {
        Constructor<?> singleValueConstructor = findConstructor(parameterType, 1);
        if (singleValueConstructor != null) {
            return singleValueConstructor.newInstance(packFormat);
        }

        Constructor<?> rangeConstructor = findConstructor(parameterType, 2);
        if (rangeConstructor != null) {
            return rangeConstructor.newInstance(packFormat, packFormat);
        }

        return UnsupportedArgument.INSTANCE;
    }

    private static Object normalizePackFormat(Object currentPackVersion) throws ReflectiveOperationException {
        if (currentPackVersion == null) {
            return null;
        }

        return currentPackVersion instanceof Number ? null : currentPackVersion;
    }

    private static Object rangeValue(Type genericRangeType, Object currentPackVersion) {
        if (genericRangeType instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length == 1
                && parameterizedType.getActualTypeArguments()[0] == Integer.class
                && currentPackVersion instanceof Number number) {
            return number.intValue();
        }
        return currentPackVersion instanceof Number ? null : currentPackVersion;
    }

    private static Integer extractPackFormatNumber(Object value) throws ReflectiveOperationException {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof CharSequence || value instanceof Boolean || value.getClass().isEnum()) {
            return null;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? extractPackFormatNumber(optional.get()) : null;
        }

        for (String methodName : new String[] { "id", "value", "version", "current", "packFormat" }) {
            try {
                Method method = value.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Integer resolved = extractPackFormatNumber(method.invoke(value));
                if (resolved != null) {
                    return resolved;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next candidate.
            }
        }

        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);
            } catch (RuntimeException ignored) {
                // Fall back to public access only.
            }

            try {
                Integer resolved = extractPackFormatNumber(field.get(value));
                if (resolved != null) {
                    return resolved;
                }
            } catch (IllegalAccessException ignored) {
                // Try the next candidate field.
            }
        }

        return null;
    }

    private static Constructor<?> findConstructor(Class<?> type, int parameterCount) {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == parameterCount) {
                return constructor;
            }
        }

        return null;
    }

    private enum UnsupportedArgument {
        INSTANCE
    }
}
