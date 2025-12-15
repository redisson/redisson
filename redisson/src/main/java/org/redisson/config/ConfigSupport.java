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

    private final ClassLoader classLoader;
    private final boolean useCaseInsensitive;

    public ConfigSupport() {
        this(false);
    }

    public ConfigSupport(boolean useCaseInsensitive) {
        this(null, useCaseInsensitive);
    }

    public ConfigSupport(ClassLoader classLoader, boolean useCaseInsensitive) {
        this.classLoader = classLoader;
        this.useCaseInsensitive = useCaseInsensitive;
    }

    private Yaml createYamlParser(ClassLoader classLoader, boolean useCaseInsensitive) {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        dumperOptions.setIndent(2);

        DelayConstructor constructor = new DelayConstructor(classLoader, loaderOptions, useCaseInsensitive);
        CustomRepresenter representer = new CustomRepresenter(dumperOptions, useCaseInsensitive);

        return new Yaml(constructor, representer, dumperOptions, loaderOptions);
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

    public <T> T fromYAML(String content, Class<T> configType) {
        content = resolveEnvParams(content);
        content = unfixTagFormat(content);
        Yaml yaml = createYamlParser(classLoader, useCaseInsensitive);
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
        Yaml yaml = createYamlParser(classLoader, useCaseInsensitive);
        return yaml.loadAs(content, configType);
    }

    public <T> T fromYAML(Reader reader, Class<T> configType) throws IOException {
        String content = resolveEnvParams(reader);
        content = unfixTagFormat(content);
        Yaml yaml = createYamlParser(classLoader, useCaseInsensitive);
        return yaml.loadAs(content, configType);
    }

    public <T> T fromYAML(InputStream inputStream, Class<T> configType) {
        String content = resolveEnvParams(new InputStreamReader(inputStream));
        content = unfixTagFormat(content);
        Yaml yaml = createYamlParser(classLoader, useCaseInsensitive);
        return yaml.loadAs(content, configType);
    }

    public String toYAML(Config config) {
        Yaml yaml = createYamlParser(classLoader, useCaseInsensitive);
        String yamlStr = yaml.dump(config);
        return fixTagFormat(yamlStr);
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