package org.example.generator;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

public class Generator {
    private static final int MAX_DEPTH = 4;
    private final Random random = new Random();

    public Object generateValueOfType(Class<?> clazz)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        if (clazz == null || clazz == void.class || clazz == Void.class) {
            throw new IllegalArgumentException("Cannot generate a value for " + clazz);
        }
        return generate(clazz, clazz, new HashSet<>(), 0);
    }

    private Object generate(Type declaredType, Class<?> rawType, Set<Class<?>> path, int depth)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Object scalar = generateScalar(rawType);
        if (scalar != UNSUPPORTED) return scalar;
        if (rawType.isArray()) return generateArray(rawType, path, depth);
        if (List.class.isAssignableFrom(rawType)) return generateList(declaredType, path, depth);
        if (Map.class.isAssignableFrom(rawType)) return generateMap(declaredType, path, depth);
        if (depth >= MAX_DEPTH || path.contains(rawType)) return null;

        return generateObject(rawType, path, depth);
    }

    private static final Object UNSUPPORTED = new Object();

    private Object generateScalar(Class<?> type) {
        if (type == String.class) return randomString();
        if (type == boolean.class || type == Boolean.class) return random.nextBoolean();
        if (type == char.class || type == Character.class) return (char) (32 + random.nextInt(95));
        if (type == byte.class || type == Byte.class) return (byte) random.nextInt(128);
        if (type == short.class || type == Short.class) return (short) random.nextInt(Short.MAX_VALUE + 1);
        if (type == int.class || type == Integer.class) return random.nextInt(201) - 100;
        if (type == long.class || type == Long.class) return random.nextLong(-1000, 1001);
        if (type == float.class || type == Float.class) return random.nextFloat() * 100;
        if (type == double.class || type == Double.class) return random.nextDouble() * 100;
        if (type.isEnum()) {
            Object[] values = type.getEnumConstants();
            return values[random.nextInt(values.length)];
        }
        return UNSUPPORTED;
    }

    private Object generateArray(Class<?> arrayType, Set<Class<?>> path, int depth)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        int length = randomSize();
        Class<?> component = arrayType.getComponentType();
        Object result = Array.newInstance(component, length);
        for (int i = 0; i < length; i++) {
            Array.set(result, i, generate(component, component, path, depth + 1));
        }
        return result;
    }

    private List<Object> generateList(Type declaredType, Set<Class<?>> path, int depth)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> elementType = genericArgument(declaredType, 0, String.class);
        List<Object> result = new ArrayList<>();
        for (int i = 0, count = randomSize(); i < count; i++) {
            result.add(generate(elementType, elementType, path, depth + 1));
        }
        return result;
    }

    private Map<Object, Object> generateMap(Type declaredType, Set<Class<?>> path, int depth)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> keyType = genericArgument(declaredType, 0, String.class);
        Class<?> valueType = genericArgument(declaredType, 1, String.class);
        Map<Object, Object> result = new HashMap<>();
        for (int i = 0, count = randomSize(); i < count; i++) {
            result.put(generate(keyType, keyType, path, depth + 1),
                    generate(valueType, valueType, path, depth + 1));
        }
        return result;
    }

    private int randomSize() {
        return 1 + random.nextInt(3);
    }

    private Object generateObject(Class<?> rawType, Set<Class<?>> path, int depth)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {

        Class<?> concreteType = rawType;
        if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
            concreteType = findImplementation(rawType);
            if (concreteType == null) throw new InstantiationException("No concrete implementation found for " + rawType.getName());
        }
        Set<Class<?>> nextPath = new HashSet<>(path);
        nextPath.add(rawType);
        Constructor<?>[] constructors = concreteType.getDeclaredConstructors();
        if (constructors.length == 0) {
            throw new InstantiationException("No constructor available for " + concreteType.getName());
        }
        Constructor<?> constructor = selectConstructor(concreteType, constructors);
        if (!constructor.canAccess(null)) constructor.setAccessible(true);
        Type[] genericTypes = constructor.getGenericParameterTypes();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == concreteType && constructor.getAnnotation(Generatable.class) != null) {
                arguments[i] = null;
            } else {
                arguments[i] = generate(genericTypes[i], parameterTypes[i], nextPath, depth + 1);
            }
        }
        return constructor.newInstance(arguments);
    }

    private Constructor<?> selectConstructor(Class<?> clazz, Constructor<?>[] constructors) {
        Constructor<?> selected = null;
        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(Generatable.class)) {
                if (selected != null) throw new IllegalStateException("More than one @Generatable constructor in " + clazz.getName());
                selected = constructor;
            }
        }
        if (selected != null) return selected;
        for (Constructor<?> constructor : constructors) {
            boolean hasRecursiveParameter = false;
            for (Class<?> parameter : constructor.getParameterTypes()) if (parameter == clazz) hasRecursiveParameter = true;
            if (!hasRecursiveParameter) return constructor;
        }
        return constructors[0];
    }

    private Class<?> genericArgument(Type type, int index, Class<?> fallback) {
        if (type instanceof ParameterizedType parameterized) {
            Type argument = parameterized.getActualTypeArguments()[index];
            if (argument instanceof Class<?> clazz) return clazz;
            if (argument instanceof ParameterizedType nested && nested.getRawType() instanceof Class<?> clazz) return clazz;
        }
        return fallback;
    }

    private Class<?> findImplementation(Class<?> parent) {
        String packageName = parent.getPackageName();
        String path = packageName.replace('.', '/');
        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (resource.getProtocol().equals("file")) {
                    File directory = new File(resource.toURI());
                    File[] files = directory.listFiles((dir, name) -> name.endsWith(".class") && !name.contains("$"));
                    if (files == null) continue;
                    for (File file : files) {
                        String name = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        Class<?> candidate = Class.forName(name, false, parent.getClassLoader());
                        if (isConcreteImplementation(parent, candidate)) return candidate;
                    }
                } else if (resource.getProtocol().equals("jar")) {
                    JarURLConnection connection = (JarURLConnection) resource.openConnection();
                    try (var jar = connection.getJarFile()) {
                        var entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            if (name.startsWith(path + "/") && name.endsWith(".class") && !name.contains("$")) {
                                Class<?> candidate = Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, parent.getClassLoader());
                                if (isConcreteImplementation(parent, candidate)) return candidate;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect implementations of " + parent.getName(), e);
        }
        return null;
    }

    private boolean isConcreteImplementation(Class<?> parent, Class<?> candidate) {
        return candidate != parent && parent.isAssignableFrom(candidate)
                && !Modifier.isAbstract(candidate.getModifiers()) && !candidate.isInterface();
    }

    private String randomString() {
        int length = 3 + random.nextInt(8);
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return value.toString();
    }
}
