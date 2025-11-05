/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.config;

import io.netty.channel.EventLoopGroup;
import org.redisson.api.NameMapper;
import org.redisson.api.NatMapper;
import org.redisson.api.RedissonNodeInitializer;
import org.redisson.client.FailedNodeDetector;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.balancer.LoadBalancer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class ConfigSupport {

    private final Yaml yaml;
    private final boolean useCaseInsensitive;

    public ConfigSupport() {
        this(false);
    }

    public ConfigSupport(boolean useCaseInsensitive) {
        this(null, useCaseInsensitive);
    }

    public ConfigSupport(ClassLoader classLoader, boolean useCaseInsensitive) {
        this.useCaseInsensitive = useCaseInsensitive;

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        dumperOptions.setIndent(2);

        DelayConstructor constructor = new DelayConstructor(classLoader, loaderOptions, useCaseInsensitive);
        CustomRepresenter representer = new CustomRepresenter(dumperOptions, useCaseInsensitive);

        this.yaml = new Yaml(constructor, representer, dumperOptions, loaderOptions);
    }

    private static class CustomPropertyUtils extends PropertyUtils {
        private final Set<String> ignoredProperties = new HashSet<>(
                Arrays.asList("slaveNotUsed", "clusterConfig", "sentinelConfig", "singleConfig", "retryInterval")
        );

        private final Set<Class<?>> ignoredClasses = new HashSet<>(Arrays.asList(
                KeyManagerFactory.class,
                TrustManagerFactory.class
        ));

        private final boolean useCaseInsensitive;

        CustomPropertyUtils(boolean useCaseInsensitive) {
            this.useCaseInsensitive = useCaseInsensitive;
            setSkipMissingProperties(!useCaseInsensitive);
            setAllowReadOnlyProperties(true);
        }
        @Override
        protected Map<String, Property> getPropertiesMap(Class<?> type, BeanAccess bAccess) {
            try {
                return super.getPropertiesMap(type, bAccess);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("No JavaBean properties found")) {
                    return new LinkedHashMap<>();
                }
                throw e;
            }
        }

        @Override
        public Property getProperty(Class<?> type, String name) {
            if (isIgnoredProperty(name)) {
                return null;
            }

            Property property = findProperty(type, name, false);
            if (property == null && useCaseInsensitive) {
                property = findProperty(type, name, true);
            }

            return property;
        }

        @Override
        public Property getProperty(Class<?> type, String name, BeanAccess bAccess) {
            return getProperty(type, name);
        }

        private boolean isIgnoredProperty(String name) {
            if (ignoredProperties.contains(name)) {
                return true;
            }

            if (useCaseInsensitive) {
                for (String ignored : ignoredProperties) {
                    if (ignored.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Property findProperty(Class<?> type, String name, boolean caseInsensitive) {

            Set<Property> protectedProps = getProtectedProperties(type);
            List<Property> props = new ArrayList<>(protectedProps);
            Set<Property> standardProps = getStandardProperties(type);
            props.addAll(standardProps);

            for (Property property : props) {
                boolean matches;
                if (caseInsensitive) {
                    matches = property.getName().equalsIgnoreCase(name);
                } else {
                    matches = property.getName().equals(name);
                }

                if (matches) {
                    if (!ignoredClasses.contains(property.getType())) {
                        return property;
                    }
                }
            }

            return null;
        }

        private final Map<Class<?>, Set<Property>> propertiesCache = new HashMap<>();
        private final Map<Class<?>, Set<Property>> protectedPropertiesCache = new HashMap<>();

        private Set<Property> getStandardProperties(Class<?> type) {
            return propertiesCache.computeIfAbsent(type, t -> super.createPropertySet(type, BeanAccess.DEFAULT));
        }

        private Set<Property> getProtectedProperties(Class<?> type) {
            return protectedPropertiesCache.computeIfAbsent(type, t -> discoverProtectedProperties(t));
        }

        @Override
        protected Set<Property> createPropertySet(Class<?> type, BeanAccess bAccess) {
            Map<String, Property> propertyMap = new LinkedHashMap<>();

            try {
                Set<Property> properties = super.createPropertySet(type, BeanAccess.DEFAULT);
                for (Property prop : properties) {
                    propertyMap.put(prop.getName(), prop);
                }
            } catch (Exception e) {
                // If standard property discovery fails, that's OK - we'll try protected methods
            }

            for (Property prop : getProtectedProperties(type)) {
                propertyMap.putIfAbsent(prop.getName(), prop);
            }

            Set<Property> filtered = new LinkedHashSet<>();
            for (Property property : propertyMap.values()) {
                String name = property.getName();
                Class<?> propType = property.getType();

                if (ignoredProperties.contains(name)) {
                    continue;
                }
                if (ignoredClasses.contains(propType)) {
                    continue;
                }

                if (property.isReadable() || property.isWritable()) {
                    filtered.add(property);
                }
            }

            return filtered;
        }

        private Set<Property> discoverProtectedProperties(Class<?> type) {
            Set<Property> properties = new LinkedHashSet<>();

            Class<?> currentClass = type;
            Map<String, Method> getters = new HashMap<>();
            Map<String, Method> setters = new HashMap<>();

            while (currentClass != null && currentClass != Object.class) {
                Method[] methods = currentClass.getDeclaredMethods();

                for (Method method : methods) {
                    int modifiers = method.getModifiers();

                    boolean isAccessible = Modifier.isPublic(modifiers)
                            || Modifier.isProtected(modifiers)
                            || !Modifier.isPrivate(modifiers);

                    String name = method.getName();
                    if (name.startsWith("get") && name.length() > 3
                            && method.getParameterCount() == 0
                                && !method.getReturnType().equals(void.class)
                                    && isAccessible) {
                        String propertyName = decapitalize(name.substring(3));
                        getters.putIfAbsent(propertyName, method);
                    } else if (name.startsWith("is") && name.length() > 2
                                    && method.getParameterCount() == 0
                                        && (method.getReturnType().equals(boolean.class)
                                                || method.getReturnType().equals(Boolean.class))
                                                    && isAccessible) {
                        String propertyName = decapitalize(name.substring(2));
                        getters.putIfAbsent(propertyName, method);
                    } else if (name.startsWith("set") && name.length() > 3
                                        && method.getParameterCount() == 1) {
                        // For setters, include all visibility levels including private
                        String propertyName = decapitalize(name.substring(3));
                        setters.putIfAbsent(propertyName, method);
                    }
                }

                currentClass = currentClass.getSuperclass();
            }

            Set<String> allPropertyNames = new HashSet<>();
            allPropertyNames.addAll(getters.keySet());
            allPropertyNames.addAll(setters.keySet());

            for (String propertyName : allPropertyNames) {
                Method getter = getters.get(propertyName);
                Method setter = setters.get(propertyName);

                if (getter != null || setter != null) {
                    properties.add(new MethodProperty(propertyName, getter, setter));
                }
            }

            return properties;
        }

        private String decapitalize(String string) {
            if (string == null || string.isEmpty()) {
                return string;
            }
            char[] chars = string.toCharArray();
            chars[0] = Character.toLowerCase(chars[0]);
            return new String(chars);
        }
    }

    private static class MethodProperty extends Property {
        private final Method getter;
        private final Method setter;

        MethodProperty(String name, Method getter, Method setter) {
            super(name, getType(getter, setter));
            this.getter = getter;
            this.setter = setter;

            if (getter != null) {
                getter.setAccessible(true);
            }
            if (setter != null) {
                setter.setAccessible(true);
            }
        }

        private static Class<?> getType(Method getter, Method setter) {
            if (getter != null) {
                return getter.getReturnType();
            }
            if (setter != null) {
                return setter.getParameterTypes()[0];
            }
            return Object.class;
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            if (getter != null) {
                Type returnType = getter.getGenericReturnType();
                if (returnType instanceof ParameterizedType) {
                    Type[] types = ((ParameterizedType) returnType).getActualTypeArguments();
                    Class<?>[] classes = new Class<?>[types.length];
                    for (int i = 0; i < types.length; i++) {
                        if (types[i] instanceof Class) {
                            classes[i] = (Class<?>) types[i];
                        } else {
                            classes[i] = Object.class;
                        }
                    }
                    return classes;
                }
            }
            return new Class<?>[0];
        }

        @Override
        public void set(Object object, Object value) throws Exception {
            if (setter != null) {
                setter.invoke(object, value);
            }
        }

        @Override
        public Object get(Object object) {
            if (getter != null) {
                try {
                    return getter.invoke(object);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to get property: " + getName() + " from " + object.getClass(), e);
                }
            }
            return null;
        }

        @Override
        public List<java.lang.annotation.Annotation> getAnnotations() {
            List<java.lang.annotation.Annotation> annotations = new ArrayList<>();
            if (getter != null) {
                annotations.addAll(Arrays.asList(getter.getAnnotations()));
            }
            if (setter != null) {
                annotations.addAll(Arrays.asList(setter.getAnnotations()));
            }
            return annotations;
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationType) {
            if (getter != null) {
                A annotation = getter.getAnnotation(annotationType);
                if (annotation != null) {
                    return annotation;
                }
            }
            if (setter != null) {
                return setter.getAnnotation(annotationType);
            }
            return null;
        }

        @Override
        public boolean isWritable() {
            return setter != null;
        }

        @Override
        public boolean isReadable() {
            return getter != null;
        }
    }

    private static class DelayConstructor extends Constructor {
        private final ClassLoader classLoader;

        DelayConstructor(ClassLoader classLoader, LoaderOptions loaderOptions, boolean useCaseInsensitive) {
            super(loaderOptions);
            this.classLoader = classLoader;
            this.setPropertyUtils(new CustomPropertyUtils(useCaseInsensitive));

            ConstructDelayStrategy delayConstructor = new ConstructDelayStrategy();
            this.yamlConstructors.put(new Tag("tag:yaml.org,2002:org.redisson.config.EqualJitterDelay"), delayConstructor);
            this.yamlConstructors.put(new Tag("tag:yaml.org,2002:org.redisson.config.FullJitterDelay"), delayConstructor);
            this.yamlConstructors.put(new Tag("tag:yaml.org,2002:org.redisson.config.DecorrelatedJitterDelay"), delayConstructor);
            this.yamlConstructors.put(new Tag("tag:yaml.org,2002:org.redisson.config.ConstantDelay"), delayConstructor);
        }

        @Override
        protected Class<?> getClassForName(String name) throws ClassNotFoundException {
            if (classLoader != null) {
                return Class.forName(name, true, classLoader);
            }
            return super.getClassForName(name);
        }

        private final class ConstructDelayStrategy extends ConstructMapping {
            @Override
            public Object construct(Node node) {
                MappingNode mappingNode = (MappingNode) node;

                Class<?> clazz;
                try {
                    String className = node.getTag().getValue().replace("tag:yaml.org,2002:", "");
                    clazz = getClassForName(className);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }

                Duration baseDelay = null;
                Duration maxDelay = null;
                Duration delay = null;

                List<NodeTuple> tuples = mappingNode.getValue();

                if (tuples.isEmpty()) {
                    flattenMapping(mappingNode);
                    tuples = mappingNode.getValue();
                }

                // If still empty after flatten, might be empty object {}
                if (tuples.isEmpty()) {
                    // Return default values based on class type
                    try {
                        if (clazz.getName().contains("ConstantDelay")) {
                            java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(Duration.class);
                            return constructor.newInstance(Duration.ofMillis(100));
                        } else {
                            java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(Duration.class, Duration.class);
                            return constructor.newInstance(Duration.ofMillis(100), Duration.ofSeconds(1));
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot construct " + clazz.getName() + " with empty mapping", e);
                    }
                }

                // Iterate through the key-value tuples
                for (NodeTuple tuple : tuples) {
                    Node keyNode = tuple.getKeyNode();
                    String key = null;

                    if (keyNode instanceof org.yaml.snakeyaml.nodes.ScalarNode) {
                        key = ((org.yaml.snakeyaml.nodes.ScalarNode) keyNode).getValue();
                    }

                    if (key == null) continue;

                    // Construct the value
                    Object value = constructObject(tuple.getValueNode());

                    if ("delay".equals(key)) {
                        if (value instanceof String) {
                            delay = Duration.parse((String) value);
                        } else if (value instanceof Duration) {
                            delay = (Duration) value;
                        }
                    } else if ("baseDelay".equals(key)) {
                        if (value instanceof String) {
                            baseDelay = Duration.parse((String) value);
                        } else if (value instanceof Duration) {
                            baseDelay = (Duration) value;
                        }
                    } else if ("maxDelay".equals(key)) {
                        if (value instanceof String) {
                            maxDelay = Duration.parse((String) value);
                        } else if (value instanceof Duration) {
                            maxDelay = (Duration) value;
                        }
                    }
                }

                try {
                    if (clazz.getName().contains("ConstantDelay")) {
                        if (delay == null) {
                            throw new IllegalStateException("Missing delay for " + clazz.getName());
                        }
                        java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(Duration.class);
                        return constructor.newInstance(delay);
                    } else {
                        if (baseDelay == null || maxDelay == null) {
                            throw new IllegalStateException("Missing baseDelay or maxDelay for " + clazz.getName() +
                                    ". Got: baseDelay=" + baseDelay + ", maxDelay=" + maxDelay);
                        }
                        java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(Duration.class, Duration.class);
                        return constructor.newInstance(baseDelay, maxDelay);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to construct " + clazz.getName(), e);
                }
            }
        }
    }

    private static class JSONCustomConstructor extends Constructor {

        private final ClassLoader classLoader;
        private final ConstructMapWithClass mapWithClassConstructor;

        JSONCustomConstructor(ClassLoader classLoader, LoaderOptions loaderOptions, boolean useCaseInsensitive) {
            super(loaderOptions);
            this.classLoader = classLoader;
            this.setPropertyUtils(new CustomPropertyUtils(useCaseInsensitive));

            this.mapWithClassConstructor = new ConstructMapWithClass();
            this.yamlConstructors.put(Tag.MAP, mapWithClassConstructor);
        }

        @Override
        protected Class<?> getClassForName(String name) throws ClassNotFoundException {
            if (classLoader != null) {
                return Class.forName(name, true, classLoader);
            }
            return super.getClassForName(name);
        }

        @Override
        protected org.yaml.snakeyaml.constructor.Construct getConstructor(Node node) {
            if (node.getTag() == Tag.MAP) {
                return new org.yaml.snakeyaml.constructor.Construct() {
                    @Override
                    public Object construct(Node node) {
                        return mapWithClassConstructor.construct(node);
                    }

                    @Override
                    public void construct2ndStep(Node node, Object object) {
                        mapWithClassConstructor.construct2ndStep(node, object);
                    }
                };
            }
            return super.getConstructor(node);
        }

        private final class ConstructMapWithClass extends ConstructMapping {

            @Override
            public Object construct(Node node) {
                try {
                    if (!(node instanceof MappingNode)) {
                        return super.construct(node);
                    }

                    MappingNode mappingNode = (MappingNode) node;

                    String className = findClassField(mappingNode);

                    if (className == null) {
                        return constructRegularMap(mappingNode);
                    }

                    return constructTypedObject(className, mappingNode);

                } catch (Exception e) {
                    throw new IllegalStateException("Failed in ConstructMapWithClass", e);
                }
            }


            private String findClassField(MappingNode mappingNode) {
                List<NodeTuple> tuples = mappingNode.getValue();

                if (tuples.isEmpty()) {
                    flattenMapping(mappingNode);
                    tuples = mappingNode.getValue();
                }

                for (NodeTuple tuple : tuples) {
                    Node keyNode = tuple.getKeyNode();
                    if (keyNode instanceof org.yaml.snakeyaml.nodes.ScalarNode) {
                        String key = ((org.yaml.snakeyaml.nodes.ScalarNode) keyNode).getValue();
                        if ("class".equals(key)) {
                            Node valueNode = tuple.getValueNode();
                            if (valueNode instanceof org.yaml.snakeyaml.nodes.ScalarNode) {
                                return ((org.yaml.snakeyaml.nodes.ScalarNode) valueNode).getValue();
                            }
                        }
                    }
                }
                return null;
            }

            private Object constructRegularMap(MappingNode mappingNode) {
                if (mappingNode.getType() != null && mappingNode.getType() != Object.class) {
                    try {
                        Class<?> expectedType = mappingNode.getType();
                        if (!Map.class.equals(expectedType) && !expectedType.isInterface()) {
                            return constructTypedObject(expectedType.getName(), mappingNode);
                        }
                    } catch (Exception e) {
                        // Fall back to regular map
                    }
                }

                Map<Object, Object> map = new LinkedHashMap<>();
                List<NodeTuple> tuples = mappingNode.getValue();

                for (NodeTuple tuple : tuples) {
                    Object key = constructObject(tuple.getKeyNode());
                    Object value = constructObject(tuple.getValueNode());
                    map.put(key, value);
                }
                return map;
            }

            private Object constructTypedObject(String className, MappingNode mappingNode) {
                try {
                    Class<?> clazz = getClassForName(className);

                    List<NodeTuple> filteredTuples = getFilteredTuples(mappingNode);

                    if (DelayStrategy.class.isAssignableFrom(clazz)) {
                        return constructDelayStrategy(clazz, filteredTuples);
                    }

                    Object instance = instantiateClass(clazz, className);

                    populateProperties(instance, clazz, filteredTuples);

                    return instance;

                } catch (Exception e) {
                    throw new IllegalStateException("Failed to construct object from JSON for class: " + className + ". Error: " + e.getMessage(), e);
                }
            }

            private List<NodeTuple> getFilteredTuples(MappingNode mappingNode) {
                List<NodeTuple> tuples = mappingNode.getValue();
                List<NodeTuple> filtered = new ArrayList<>();

                for (NodeTuple tuple : tuples) {
                    Node keyNode = tuple.getKeyNode();
                    if (keyNode instanceof org.yaml.snakeyaml.nodes.ScalarNode) {
                        String key = ((org.yaml.snakeyaml.nodes.ScalarNode) keyNode).getValue();
                        if (!"class".equals(key)) {
                            filtered.add(tuple);
                        }
                    }
                }
                return filtered;
            }

            private Object instantiateClass(Class<?> clazz, String className) throws Exception {
                if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                    throw new IllegalStateException("Cannot instantiate interface or abstract class: " + className);
                }

                try {
                    // Try to find a no-arg constructor (public, protected, or private)
                    java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    return constructor.newInstance();
                } catch (NoSuchMethodException e) {
                    // Try public no-arg constructor
                    try {
                        return clazz.newInstance();
                    } catch (Exception ex) {
                        throw new IllegalStateException("Cannot instantiate " + className + ": no accessible no-arg constructor. " +
                                "Make sure the class has a no-argument constructor. Error: " + ex.getMessage(), e);
                    }
                }
            }

            private void populateProperties(Object instance, Class<?> clazz, List<NodeTuple> tuples) {
                for (NodeTuple tuple : tuples) {
                    Node keyNode = tuple.getKeyNode();
                    if (keyNode instanceof org.yaml.snakeyaml.nodes.ScalarNode) {
                        String propertyName = ((org.yaml.snakeyaml.nodes.ScalarNode) keyNode).getValue();
                        Object value = constructObject(tuple.getValueNode());

                        try {
                            Property property =
                                    getPropertyUtils().getProperty(clazz, propertyName);
                            if (property != null) {
                                // For collections, try the read-only approach first
                                if (value instanceof Collection && property.isReadable()) {
                                    try {
                                        Object existingCollection = property.get(instance);
                                        if (existingCollection instanceof Collection) {
                                            Collection<Object> existingCol = (Collection<Object>) existingCollection;
                                            Collection<?> newCol = (Collection<?>) value;
                                            existingCol.clear();
                                            existingCol.addAll(newCol);
                                            continue; // Successfully populated, skip to next property
                                        }
                                    } catch (Exception e) {
                                        // If we can't populate via getter, try setter below
                                    }
                                }

                                // Try setter for non-collections or if collection population failed
                                if (property.isWritable()) {
                                    Object convertedValue = convertValueToPropertyType(value, property.getType());
                                    property.set(instance, convertedValue);
                                }
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException("ERROR: Could not set property " + propertyName + " on " + clazz.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            private Object convertValueToPropertyType(Object value, Class<?> targetType) {
                if (value == null) {
                    return null;
                }

                if (targetType.isInstance(value)) {
                    return value;
                }

                if (targetType.isEnum() && value instanceof String) {
                    return Enum.valueOf((Class<Enum>) targetType, (String) value);
                }

                if (targetType == Duration.class && value instanceof String) {
                    return Duration.parse((String) value);
                }

                return value;
            }

            private Object constructDelayStrategy(Class<?> clazz, List<NodeTuple> tuples) {
                Duration baseDelay = null;
                Duration maxDelay = null;
                Duration delay = null;

                for (NodeTuple tuple : tuples) {
                    Node keyNode = tuple.getKeyNode();
                    if (keyNode instanceof org.yaml.snakeyaml.nodes.ScalarNode) {
                        String key = ((org.yaml.snakeyaml.nodes.ScalarNode) keyNode).getValue();
                        Object value = constructObject(tuple.getValueNode());

                        if ("delay".equals(key)) {
                            delay = parseDuration(value);
                        } else if ("baseDelay".equals(key)) {
                            baseDelay = parseDuration(value);
                        } else if ("maxDelay".equals(key)) {
                            maxDelay = parseDuration(value);
                        }
                    }
                }

                try {
                    if (clazz.getName().contains("ConstantDelay")) {
                        if (delay == null) {
                            delay = Duration.ofMillis(100);
                        }
                        java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(Duration.class);
                        return constructor.newInstance(delay);
                    } else {
                        if (baseDelay == null) {
                            baseDelay = Duration.ofMillis(100);
                        }
                        if (maxDelay == null) {
                            maxDelay = Duration.ofSeconds(1);
                        }
                        java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(Duration.class, Duration.class);
                        return constructor.newInstance(baseDelay, maxDelay);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to construct " + clazz.getName(), e);
                }
            }

            private Duration parseDuration(Object value) {
                if (value instanceof String) {
                    return Duration.parse((String) value);
                } else if (value instanceof Duration) {
                    return (Duration) value;
                }
                return null;
            }
        }
    }

    private static class JsonRepresenter extends CustomRepresenter {

        JsonRepresenter(DumperOptions dumperOptions, boolean useCaseInsensitive) {
            super(dumperOptions, useCaseInsensitive);

            this.addClassTag(Config.class, Tag.MAP);
            // Represent Duration as quoted string
            this.representers.put(Duration.class, data -> {
                Duration duration = (Duration) data;
                return representScalar(Tag.STR, duration.toString(), DumperOptions.ScalarStyle.DOUBLE_QUOTED);
            });

            // Represent String as quoted string (always)
            this.representers.put(String.class, data -> {
                return representScalar(Tag.STR, (String) data, DumperOptions.ScalarStyle.DOUBLE_QUOTED);
            });

            // Represent enums as quoted strings
            this.multiRepresenters.put(Enum.class, data -> {
                return representScalar(Tag.STR, ((Enum<?>) data).name(), DumperOptions.ScalarStyle.DOUBLE_QUOTED);
            });
        }

        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            List<NodeTuple> tuples = new ArrayList<>();

            // Add "class" field first for typed objects
            if (shouldIncludeClassName(javaBean.getClass()) && !(javaBean instanceof Config)) {
                // Quote the "class" key
                Node keyNode = representScalar(Tag.STR, "class", DumperOptions.ScalarStyle.DOUBLE_QUOTED);
                Node valueNode = representScalar(Tag.STR, javaBean.getClass().getName(),
                        DumperOptions.ScalarStyle.DOUBLE_QUOTED);
                tuples.add(new NodeTuple(keyNode, valueNode));
            }

            // Add all other properties
            for (Property property : properties) {
                Object value = null;
                try {
                    value = property.get(javaBean);
                } catch (Exception e) {
                    continue;
                }

                if (value == null) {
                    continue;
                }

                boolean isDelayStrategy = javaBean instanceof DelayStrategy;

                if (!property.isWritable() && !isDelayStrategy) {
                    continue;
                }

                // Quote property name
                Node keyNode = representScalar(Tag.STR, property.getName(),
                        DumperOptions.ScalarStyle.DOUBLE_QUOTED);
                Node valueNode = representData(value);
                tuples.add(new NodeTuple(keyNode, valueNode));
            }

            return new MappingNode(
                    Tag.MAP, tuples, DumperOptions.FlowStyle.AUTO);
        }

        @Override
        protected boolean shouldIncludeClassName(Class<?> clazz) {
            // First check parent's logic (for Codec, LoadBalancer, etc.)
            if (super.shouldIncludeClassName(clazz)) {
                return true;
            }

            // Additionally include config classes for JSON
            return BaseConfig.class.isAssignableFrom(clazz) && !(clazz.equals(Config.class));
        }
    }

    private static class CustomRepresenter extends Representer {
        private final Set<Class<?>> classTypedClasses = new HashSet<>(Arrays.asList(
                ReferenceCodecProvider.class,
                AddressResolverGroupFactory.class,
                Codec.class,
                RedissonNodeInitializer.class,
                LoadBalancer.class,
                NatMapper.class,
                NameMapper.class,
                NettyHook.class,
                CredentialsResolver.class,
                EventLoopGroup.class,
                ConnectionListener.class,
                ExecutorService.class,
                CommandMapper.class,
                FailedNodeDetector.class,
                DelayStrategy.class,
                EqualJitterDelay.class,
                FullJitterDelay.class,
                DecorrelatedJitterDelay.class,
                ConstantDelay.class
        ));

        CustomRepresenter(DumperOptions dumperOptions, boolean useCaseInsensitive) {
            super(dumperOptions);

            CustomPropertyUtils propUtils = new CustomPropertyUtils(useCaseInsensitive);
            this.setPropertyUtils(propUtils);

            // Skip null values (equivalent to Jackson's NON_NULL)
            this.setDefaultFlowStyle(dumperOptions.getDefaultFlowStyle());

            // Remove default Set representer
            this.multiRepresenters.remove(Set.class);

            // Add custom representers for Duration
            this.addClassTag(Duration.class, Tag.STR);
            this.representers.put(Duration.class, data -> {
                Duration duration = (Duration) data;
                return representScalar(Tag.STR, duration.toString());
            });

            // Represent Sets as sequences (arrays) - must be before Enum
            this.multiRepresenters.put(Set.class, data -> {
                Set<?> set = (Set<?>) data;
                List<?> list = new ArrayList<>(set);
                return representSequence(Tag.SEQ, list, DumperOptions.FlowStyle.AUTO);
            });

            // Represent enums as quoted strings
            this.multiRepresenters.put(Enum.class, data -> {
                return representScalar(Tag.STR, ((Enum<?>) data).name(), DumperOptions.ScalarStyle.DOUBLE_QUOTED);
            });

            // Don't use global tags for root Config object
            this.addClassTag(Config.class, Tag.MAP);
        }

        @Override
        public Node represent(Object data) {
            if (data != null && !data.getClass().isPrimitive()
                    && !data.getClass().isArray()
                    && !(data instanceof String)
                    && !(data instanceof Number)
                    && !(data instanceof Boolean)
                    && !(data instanceof Duration)
                    && !(data instanceof Enum)
                    && !(data instanceof Collection)
                    && !(data instanceof Map)) {

                Set<Property> properties = getProperties(data.getClass());
                return representJavaBean(properties, data);
            }
            return super.represent(data);
        }

        @Override
        protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
            MappingNode node = super.representJavaBean(properties, javaBean);

            // Override the tag for classes that need explicit class names
            if (shouldIncludeClassName(javaBean.getClass()) && !(javaBean instanceof Config)) {
                // Set tag with just "!classname" - post-processing will convert to !<classname>
                node.setTag(new Tag("!" + javaBean.getClass().getName()));
            }

            // Use flow style for small delay objects
            if (javaBean instanceof EqualJitterDelay
                    || javaBean instanceof FullJitterDelay
                        || javaBean instanceof DecorrelatedJitterDelay
                            || javaBean instanceof ConstantDelay) {
                node.setFlowStyle(DumperOptions.FlowStyle.FLOW);
            } else if (shouldIncludeClassName(javaBean.getClass())
                            && !(javaBean instanceof Config)) {
                // For other tagged objects (except Config), check if they're small enough for flow style
                if (properties.size() <= 2 && hasOnlySimpleProperties(javaBean, properties)) {
                    node.setFlowStyle(DumperOptions.FlowStyle.FLOW);
                }
            }

            return node;
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean,
                                                                               Property property, Object propertyValue, Tag customTag) {
            if (propertyValue == null) {
                return null;
            }

            // For delay strategy classes, include read-only properties (they're constructed via constructor)
            boolean isDelayStrategy = javaBean instanceof EqualJitterDelay
                                                    || javaBean instanceof FullJitterDelay
                                                    || javaBean instanceof DecorrelatedJitterDelay
                                                    || javaBean instanceof ConstantDelay;

            // Skip read-only properties (no setter) EXCEPT for delay strategies
            if (!property.isWritable() && !isDelayStrategy) {
                return null;
            }

            // Force double quotes for string values (but not keys)
            if (propertyValue instanceof String) {
                Node valueNode = representScalar(Tag.STR, (String) propertyValue, DumperOptions.ScalarStyle.DOUBLE_QUOTED);
                Node keyNode = representData(property.getName());
                return new NodeTuple(keyNode, valueNode);
            }

            return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        }

        private boolean hasOnlySimpleProperties(Object javaBean, Set<Property> properties) {
            try {
                for (Property property : properties) {
                    Object value = property.get(javaBean);
                    if (value != null && !isSimpleType(value.getClass())) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean isSimpleType(Class<?> clazz) {
            return clazz.isPrimitive()
                    || clazz == String.class
                    || clazz == Integer.class
                    || clazz == Long.class
                    || clazz == Boolean.class
                    || clazz == Double.class
                    || clazz == Float.class
                    || Duration.class.isAssignableFrom(clazz);
        }

        boolean shouldIncludeClassName(Class<?> clazz) {
            for (Class<?> classTyped : classTypedClasses) {
                if (classTyped.isAssignableFrom(clazz)) {
                    return true;
                }
            }
            return false;
        }
    }

    private String resolveEnvParams(Readable in) {
        try (Scanner s = new Scanner(in).useDelimiter("\\A")) {
            if (s.hasNext()) {
                return resolveEnvParams(s.next());
            }
            return "";
        }
    }

    private static final Pattern ENV_PARAM_PATTERN =
            Pattern.compile("\\$\\{([\\w\\.]+(:-.+?)?)\\}");

    private String resolveEnvParams(String content) {
        Matcher m = ENV_PARAM_PATTERN.matcher(content);
        while (m.find()) {
            String[] parts = m.group(1).split(":-");
            String v = System.getenv(parts[0]);
            v = System.getProperty(parts[0], v);
            if (v != null) {
                content = content.replace(m.group(), v);
            } else if (parts.length == 2) {
                content = content.replace(m.group(), parts[1]);
            }
        }
        return content;
    }

    @Deprecated
    public <T> T fromJSON(File file, Class<T> configType) throws IOException {
        return fromJSON(file, configType, null);
    }

    @Deprecated
    public <T> T fromJSON(String content, Class<T> configType) throws IOException {
        content = resolveEnvParams(content);

        return fromJSON(configType, content, null);
    }

    @Deprecated
    public <T> T fromJSON(File file, Class<T> configType, ClassLoader classLoader) throws IOException {
        String content = resolveEnvParams(new FileReader(file));

        return fromJSON(configType, content, classLoader);
    }

    @Deprecated
    public <T> T fromJSON(URL url, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(url.openStream()));

        return fromJSON(configType, content, null);
    }

    private <T> T fromJSON(Class<T> configType, String content, ClassLoader classLoader) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true);

        DumperOptions dumperOptions = new DumperOptions();
        Yaml jsonYaml = new Yaml(new JSONCustomConstructor(classLoader, loaderOptions, useCaseInsensitive),
                new CustomRepresenter(dumperOptions, useCaseInsensitive),
                dumperOptions,
                loaderOptions);

        return jsonYaml.loadAs(content, configType);
    }

    @Deprecated
    public <T> T fromJSON(Reader reader, Class<T> configType) throws IOException {
        String content = resolveEnvParams(reader);

        return fromJSON(configType, content, null);
    }

    @Deprecated
    public <T> T fromJSON(InputStream inputStream, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(inputStream));

        return fromJSON(configType, content, null);
    }

    @Deprecated
    public String toJSON(Config config) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true);

        DumperOptions jsonOptions = new DumperOptions();
        jsonOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        jsonOptions.setPrettyFlow(true);
        jsonOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        jsonOptions.setIndent(2);

        Yaml jsonYaml = new Yaml(new DelayConstructor(null, loaderOptions, useCaseInsensitive),
                new JsonRepresenter(jsonOptions, useCaseInsensitive),
                jsonOptions,
                loaderOptions);
        String json = jsonYaml.dump(config);
        return convertYamlToJson(json);
    }

    private String convertYamlToJson(String yaml) {
        String[] lines = yaml.split("\n");
        StringBuilder json = new StringBuilder();

        List<Integer> indentStack = new ArrayList<Integer>();
        indentStack.add(-1);

        json.append("{");

        boolean needsComma = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) continue;

            int currentIndent = line.length() - line.replaceAll("^\\s+", "").length();

            // Close braces for decreased indentation
            while (indentStack.size() > 1 && currentIndent <= indentStack.get(indentStack.size() - 1)) {
                indentStack.remove(indentStack.size() - 1);
                json.append("\n").append(repeatSpaces(indentStack.size())).append("}");
                needsComma = true;
            }

            // Determine if we need comma before this line
            // Don't add comma if line already ends with comma (from YAML flow style)
            if (needsComma && !"]".equals(trimmed) && !"}".equals(trimmed) && !trimmed.endsWith(",")) {
                json.append(",");
            }

            boolean hasArrayStart = line.contains(": [") || "[".equals(trimmed);
            boolean isArrayElement = trimmed.startsWith("- ") || "-".equals(trimmed);

            boolean startsNestedObject = false;
            if (line.contains(":") && !hasArrayStart && !isArrayElement) {
                if (trimmed.endsWith(":")) {
                    startsNestedObject = true;
                } else if (i < lines.length - 1) {
                    String nextLine = lines[i + 1];
                    String nextTrimmed = nextLine.trim();
                    if (!nextTrimmed.isEmpty() && !"]".equals(nextTrimmed) && !"}".equals(nextTrimmed)) {
                        int nextIndent = nextLine.length() - nextLine.replaceAll("^\\s+", "").length();
                        if (nextIndent > currentIndent && nextTrimmed.startsWith("\"")) {
                            startsNestedObject = true;
                        }
                    }
                }
            }

            json.append("\n").append(repeatSpaces(indentStack.size()));

            if (startsNestedObject) {
                if (trimmed.endsWith(":")) {
                    json.append(line, 0, line.lastIndexOf(":")).append(": {");
                } else {
                    json.append(line).append(" {");
                }
                indentStack.add(currentIndent);
                needsComma = false;
            } else {
                json.append(line);

                // Check if this line already has a comma or what comes next
                if ("]".equals(trimmed)) {
                    // After closing bracket, check if next line is a property
                    if (i < lines.length - 1) {
                        String nextLine = lines[i + 1];
                        String nextTrimmed = nextLine.trim();
                        int nextIndent;
                        if (nextLine.isEmpty()) {
                            nextIndent = 0;
                        } else {
                            nextIndent = nextLine.length() - nextLine.replaceAll("^\\s+", "").length();
                        }

                        // Add comma if next line is a sibling property
                        if (!nextTrimmed.isEmpty() && nextIndent <= currentIndent
                                && nextTrimmed.startsWith("\"") && nextTrimmed.contains(":")) {
                            json.append(",");
                            needsComma = false;
                        } else {
                            needsComma = true;
                        }
                    } else {
                        needsComma = true;
                    }
                } else {
                    // Don't set needsComma if line already ends with comma or ends with opening bracket
                    needsComma = !trimmed.endsWith(",") && !trimmed.endsWith("[") && !"}".equals(trimmed);
                }
            }
        }

        // Close remaining braces
        while (indentStack.size() > 1) {
            indentStack.remove(indentStack.size() - 1);
            json.append("\n").append(repeatSpaces(indentStack.size())).append("}");
        }

        json.append("\n}");

        return json.toString();
    }

    private String repeatSpaces(int count) {
        if (count <= 0) {
            return "";
        }
        char[] chars = new char[count * 2];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }

    public <T> T fromYAML(String content, Class<T> configType) throws IOException {
        content = resolveEnvParams(content);
        content = unfixTagFormat(content);
        return yaml.loadAs(content, configType);
    }

    public <T> T fromYAML(File file, Class<T> configType) throws IOException {
        return fromYAML(file, configType, null);
    }

    public <T> T fromYAML(File file, Class<T> configType, ClassLoader classLoader) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true); // Allow all tags

        DumperOptions dumperOptions = new DumperOptions();
        Yaml yamlParser = new Yaml(new DelayConstructor(classLoader, loaderOptions, useCaseInsensitive),
                new CustomRepresenter(dumperOptions, useCaseInsensitive),
                dumperOptions,
                loaderOptions);
        String content = resolveEnvParams(new FileReader(file));
        content = unfixTagFormat(content);
        return yamlParser.loadAs(content, configType);
    }

    public <T> T fromYAML(URL url, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(url.openStream()));
        content = unfixTagFormat(content);
        return yaml.loadAs(content, configType);
    }

    public <T> T fromYAML(Reader reader, Class<T> configType) throws IOException {
        String content = resolveEnvParams(reader);
        content = unfixTagFormat(content);
        return yaml.loadAs(content, configType);
    }

    public <T> T fromYAML(InputStream inputStream, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(inputStream));
        content = unfixTagFormat(content);
        return yaml.loadAs(content, configType);
    }

    public String toYAML(Config config) throws IOException {
        String yaml = this.yaml.dump(config);
        return fixTagFormat(yaml);
    }

    private static final Pattern TAG_FIX_PATTERN = Pattern.compile("!([a-zA-Z0-9_.]+)");

    private String fixTagFormat(String yaml) {
        // Pattern to match tags like !className (with dots indicating a package)
        // and convert them to !<className> format for verbatim tags
        Matcher matcher = TAG_FIX_PATTERN.matcher(yaml);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String className = matcher.group(1);
            matcher.appendReplacement(result, "!<" + className + ">");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private static final Pattern TAG_UNFIX_PATTERN = Pattern.compile("!<([a-zA-Z0-9_.]+)>");

    private String unfixTagFormat(String yaml) {
        // Pattern to match tags like !<className>
        // and convert them back to !!className format for parsing
        Matcher matcher = TAG_UNFIX_PATTERN.matcher(yaml);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String className = matcher.group(1);
            matcher.appendReplacement(result, "!!" + className);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public static BaseConfig<?> getConfig(Config configCopy) {
        if (configCopy.getMasterSlaveServersConfig() != null) {
            validate(configCopy.getMasterSlaveServersConfig());
            return configCopy.getMasterSlaveServersConfig();
        } else if (configCopy.getSingleServerConfig() != null) {
            validate(configCopy.getSingleServerConfig());
            return configCopy.getSingleServerConfig();
        } else if (configCopy.getSentinelServersConfig() != null) {
            validate(configCopy.getSentinelServersConfig());
            return configCopy.getSentinelServersConfig();
        } else if (configCopy.getClusterServersConfig() != null) {
            validate(configCopy.getClusterServersConfig());
            return configCopy.getClusterServersConfig();
        } else if (configCopy.getReplicatedServersConfig() != null) {
            validate(configCopy.getReplicatedServersConfig());
            return configCopy.getReplicatedServersConfig();
        }

        throw new IllegalArgumentException("server(s) address(es) not defined!");
    }

    private static void validate(SingleServerConfig config) {
        if (config.getConnectionPoolSize() < config.getConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("connectionPoolSize can't be lower than connectionMinimumIdleSize");
        }
    }

    private static void validate(BaseMasterSlaveServersConfig<?> config) {
        if (config.getSlaveConnectionPoolSize() < config.getSlaveConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("slaveConnectionPoolSize can't be lower than slaveConnectionMinimumIdleSize");
        }
        if (config.getMasterConnectionPoolSize() < config.getMasterConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("masterConnectionPoolSize can't be lower than masterConnectionMinimumIdleSize");
        }
        if (config.getSubscriptionConnectionPoolSize() < config.getSubscriptionConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("slaveSubscriptionConnectionMinimumIdleSize can't be lower than slaveSubscriptionConnectionPoolSize");
        }
    }

}