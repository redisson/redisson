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
package org.redisson.spring.cache;

import jodd.bean.BeanUtil;
import jodd.bean.BeanUtilBean;
import jodd.bean.BeanVisitor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CacheConfigSupport {

    private final Yaml yamlParser;
    private final Yaml jsonParser;

    public CacheConfigSupport() {
        LoaderOptions yamlLoaderOptions = new LoaderOptions();
        yamlLoaderOptions.setTagInspector(tag -> true); // Allow all tags

        DumperOptions yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        yamlDumperOptions.setPrettyFlow(true);
        yamlDumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        yamlDumperOptions.setIndent(2);

        CustomConstructor yamlConstructor = new CustomConstructor(yamlLoaderOptions);
        this.yamlParser = new Yaml(yamlConstructor, new Representer(yamlDumperOptions), yamlDumperOptions, yamlLoaderOptions);

        DumperOptions jsonDumperOptions = new DumperOptions();
        jsonDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        jsonDumperOptions.setPrettyFlow(false);
        jsonDumperOptions.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);

        Representer representer = new Representer(jsonDumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);

        this.jsonParser = new Yaml(representer, jsonDumperOptions);
    }

    private static class CustomConstructor extends Constructor {

        CustomConstructor(LoaderOptions loaderOptions) {
            super(loaderOptions);
        }

        @Override
        protected Class<?> getClassForNode(Node node) {
            String className = extractClassNameFromTag(node.getTag());

            if (className != null) {
                try {
                    return getClassForName(className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Class not found: " + className, e);
                }
            }

            return super.getClassForNode(node);
        }

        private String extractClassNameFromTag(Tag tag) {
            if (tag == null) {
                return null;
            }

            String tagValue = tag.getValue();

            if (tagValue.startsWith("tag:yaml.org,2002:")) {
                String className = tagValue.substring("tag:yaml.org,2002:".length());
                if (className.contains(".") || className.contains("$")) {
                    return className;
                }
            }

            return tagValue;
        }
    }

    public Map<String, CacheConfig> fromJSON(String content) throws IOException {
        Map<String, Map<String, Object>> m = jsonParser.loadAs(content, Map.class);
        return fromMap(m, CacheConfig.class);
    }

    public Map<String, CacheConfig> fromJSON(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            Map<String, Map<String, Object>> m = jsonParser.loadAs(reader, Map.class);
            return fromMap(m, CacheConfig.class);
        }
    }

    public Map<String, CacheConfig> fromJSON(URL url) throws IOException {
        try (InputStream is = url.openStream()) {
            Map<String, Map<String, Object>> m = jsonParser.loadAs(is, Map.class);
            return fromMap(m, CacheConfig.class);
        }
    }

    public Map<String, CacheConfig> fromJSON(Reader reader) throws IOException {
        Map<String, Map<String, Object>> m = jsonParser.loadAs(reader, Map.class);
        return fromMap(m, CacheConfig.class);
    }

    public Map<String, CacheConfig> fromJSON(InputStream inputStream) throws IOException {
        Map<String, Map<String, Object>> m = jsonParser.loadAs(inputStream, Map.class);
        return fromMap(m, CacheConfig.class);
    }

    public String toJSON(Map<String, ? extends CacheConfig> configs) throws IOException {
        Map<String, Map<String, String>> plainMap = toPlainMap(configs);
        String value = jsonParser.dump(plainMap);
        return formatJson(value);
    }

    public Map<String, CacheConfig> fromYAML(String content) throws IOException {
        Map<String, Map<String, Object>> m = yamlParser.loadAs(content, Map.class);
        return fromMap(m, CacheConfig.class);
    }

    public static <T> Map<String, T> fromMap(Map<String, Map<String, Object>> configMap, Class<T> clazz) {
        Map<String, T> result = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : configMap.entrySet()) {
            String configName = entry.getKey();
            Map<String, Object> properties = entry.getValue();

            try {
                T instance = clazz.getDeclaredConstructor().newInstance();

                BeanUtil beanUtil = new BeanUtilBean();

                new BeanVisitor(properties)
                        .visit((name, value) -> {
                            name = name.replace("ttl", "TTL");

                            if (!beanUtil.hasProperty(instance, name)) {
                                throw new IllegalStateException("Field can't be found: " + name);
                            }

                            if ("listeners".equals(name)) {
                                List<Object> listValue = (List<Object>) value;
                                List<Object> listeners = new ArrayList<>();
                                for (Object val : listValue) {
                                    if (val instanceof Map) {
                                        Object className = ((Map<?, ?>) val).get("class");
                                        value = createListener(className.toString());
                                        listeners.add(value);
                                    }
                                }
                                if (!listeners.isEmpty()) {
                                    value = listeners;
                                }
                            }

                            beanUtil.setProperty(instance, name, value);
                        });

                result.put(configName, instance);

            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Failed to create instance of " + clazz.getName() +
                        ": no-arg constructor required", e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create instance of " + clazz.getName() +
                        " for key: " + configName, e);
            }
        }

        return result;
    }

    public static Object createListener(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + className, e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No no-arg constructor found for class: " + className, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate class: " + className, e);
        }
    }

    public Map<String, CacheConfig> fromYAML(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            Map<String, Map<String, Object>> m = yamlParser.loadAs(reader, Map.class);
            return fromMap(m, CacheConfig.class);
        }
    }

    public Map<String, CacheConfig> fromYAML(URL url) throws IOException {
        try (InputStream is = url.openStream()) {
            Map<String, Map<String, Object>> m = yamlParser.loadAs(is, Map.class);
            return fromMap(m, CacheConfig.class);
        }
    }

    public Map<String, CacheConfig> fromYAML(Reader reader) throws IOException {
        Map<String, Map<String, Object>> m = yamlParser.loadAs(reader, Map.class);
        return fromMap(m, CacheConfig.class);
    }

    public Map<String, CacheConfig> fromYAML(InputStream inputStream) throws IOException {
        Map<String, Map<String, Object>> m = yamlParser.loadAs(inputStream, Map.class);
        return fromMap(m, CacheConfig.class);
    }

    public String toYAML(Map<String, ? extends CacheConfig> configs) throws IOException {
        Map<String, Map<String, String>> plainMap = toPlainMap(configs);
        return yamlParser.dump(plainMap);
    }

    private Map<String, Map<String, String>> toPlainMap(Map<String, ? extends CacheConfig> configs) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        for (Map.Entry<String, ? extends CacheConfig> entry : configs.entrySet()) {
            String key = entry.getKey();
            Object config = entry.getValue();

            Map<String, String> properties = new LinkedHashMap<>();

            BeanUtil beanUtil = new BeanUtilBean();

            new BeanVisitor(config)
                    .visit((name, value) -> {
                        if ("listeners".equals(name)) {
                            return;
                        }

                        name = name.replace("TTL", "ttl");
                        if (value instanceof Enum) {
                            value = value.toString();
                        }

                        beanUtil.setProperty(properties, name, value);
                    });

            result.put(key, properties);
        }

        return result;
    }

    private String formatJson(String json) {
        json = json.trim();

        json = json.replaceAll("!!int\\s+", "");
        json = json.replaceAll("!!long\\s+", "");
        json = json.replaceAll("!!float\\s+", "");
        json = json.replaceAll("!!double\\s+", "");
        json = json.replaceAll("!!bool\\s+", "");

        // Replace newlines and multiple spaces with single space
        json = json.replaceAll("\\s*\\n\\s*", " ");

        // Add space after colons if not present
        json = json.replaceAll(":\\s*([^\\s])", ": $1");

        // Add space after commas if not present
        json = json.replaceAll(",\\s*([^\\s])", ", $1");

        // Fix spaces around braces
        json = json.replaceAll("\\{\\s+", "{");
        json = json.replaceAll("\\s+\\}", "}");

        // Remove quotes around numbers
        json = json.replaceAll("\"(\\d+)\"", "$1");

        return json;
    }

}