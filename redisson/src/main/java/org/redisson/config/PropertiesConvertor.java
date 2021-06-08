/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import java.util.*;
import java.util.function.Function;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PropertiesConvertor {

    public static String toYaml(String suffix, Iterable<String> propertyNames, Function<String, String> resolver) {
        Map<String, Object> map = new HashMap<>();
        for (String propertyName : propertyNames) {
            if (!propertyName.startsWith(suffix)) {
                continue;
            }

            List<String> pps = Arrays.asList(propertyName.replace(suffix, "").split("\\."));
            String value = resolver.apply(propertyName);
            if (pps.size() == 2) {
                Map<String, Object> m = (Map<String, Object>) map.computeIfAbsent(pps.get(0), k -> new HashMap<String, Object>());
                m.put(pps.get(1), value);
            } else {
                map.put(pps.get(0), value);
            }
        }

        StringBuilder yaml = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                yaml.append(convertKey(entry.getKey())).append(":").append("\n");

                Map<String, Object> m = (Map) entry.getValue();
                for (Map.Entry<String, Object> subEntry : m.entrySet()) {
                    yaml.append("  ").append(convertKey(subEntry.getKey())).append(": ");
                    addValue(yaml, subEntry);
                    yaml.append("\n");
                }
            } else {
                yaml.append(convertKey(entry.getKey())).append(": ");
                addValue(yaml, entry);
                yaml.append("\n");
            }
        }
        return yaml.toString();
    }

    private static String convertKey(String key) {
        if (!key.contains("-")) {
            return key;
        }

        String[] parts = key.split("-");
        StringBuilder builder = new StringBuilder();
        builder.append(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            builder.append(parts[i].substring(0, 1).toUpperCase())
                    .append(parts[i].substring(1));
        }
        return builder.toString();
    }

    private static void addValue(StringBuilder yaml, Map.Entry<String, Object> subEntry) {
        String value = (String) subEntry.getValue();
        if (value.contains(",")) {
            for (String part : value.split(",")) {
                yaml.append("\n  ").append("- \"").append(part.trim()).append("\"");
            }
            return;
        }

        if ("codec".equals(subEntry.getKey())
                || "loadBalancer".equals(subEntry.getKey())) {
            value = "!<" + value + "> {}";
        } else {
            try {
                Long.parseLong(value);
            } catch (NumberFormatException e) {
                if (!Boolean.parseBoolean(value)
                        && !"null".equals(value)) {
                    value = "\"" + value + "\"";
                }
            }
        }

        yaml.append(value);
    }

}
