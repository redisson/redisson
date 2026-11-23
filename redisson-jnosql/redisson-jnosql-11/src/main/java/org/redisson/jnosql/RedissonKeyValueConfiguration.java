/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.jnosql;

import org.eclipse.jnosql.communication.CommunicationException;
import org.eclipse.jnosql.communication.Settings;
import org.eclipse.jnosql.communication.keyvalue.KeyValueConfiguration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.PropertiesConvertor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Eclipse JNoSQL key-value driver based on Redisson.
 * <p>
 * Redisson configuration is read from the YAML file defined by {@link RedissonConfigurations#FILE},
 * or from flat properties prefixed with {@code jnosql.redisson.}, for example
 * {@code jnosql.redisson.singleServerConfig.address=redis://127.0.0.1:6379}.
 * Without either, Redisson connects to {@code redis://127.0.0.1:6379}.
 *
 * @author Nikita Koksharov
 *
 */
public final class RedissonKeyValueConfiguration implements KeyValueConfiguration {

    private static final String PREFIX = "jnosql.redisson.";

    private static final String CLASSPATH = "classpath:";

    @Override
    public RedissonBucketManagerFactory apply(Settings settings) {
        Objects.requireNonNull(settings, "settings is required");
        Optional<String> file = settings.get(RedissonConfigurations.FILE, String.class);
        List<String> properties = new ArrayList<>();
        for (String key : settings.keySet()) {
            if (key.startsWith(PREFIX) && !key.equals(RedissonConfigurations.FILE.get())) {
                properties.add(key);
            }
        }

        if (file.isPresent()) {
            if (!properties.isEmpty()) {
                throw new CommunicationException("Redisson configuration is defined by both "
                        + RedissonConfigurations.FILE.get() + " and " + properties);
            }
            return apply(read(file.get()));
        }
        if (properties.isEmpty()) {
            return new RedissonBucketManagerFactory(Redisson.create(), true);
        }
        String yaml = PropertiesConvertor.toYaml(PREFIX, properties, key -> settings.get(key, String.class)
                .orElseThrow(() -> new CommunicationException("Redisson configuration property " + key + " is empty")),
                true);
        return apply(Config.fromYAML(yaml));
    }

    public RedissonBucketManagerFactory apply(Config config) {
        Objects.requireNonNull(config, "config is required");
        return new RedissonBucketManagerFactory(Redisson.create(config), true);
    }

    public RedissonBucketManagerFactory apply(RedissonClient client) {
        Objects.requireNonNull(client, "client is required");
        return new RedissonBucketManagerFactory(client, false);
    }

    private static Config read(String location) {
        try {
            if (!location.startsWith(CLASSPATH)) {
                return Config.fromYAML(new File(location));
            }
            ClassLoader classLoader = Objects.requireNonNullElse(Thread.currentThread().getContextClassLoader(),
                                                                    RedissonKeyValueConfiguration.class.getClassLoader());
            String resource = location.substring(CLASSPATH.length());
            if (resource.startsWith("/")) {
                resource = resource.substring(1);
            }
            URL url = classLoader.getResource(resource);
            if (url == null) {
                throw new CommunicationException("Redisson configuration not found: " + location);
            }
            return Config.fromYAML(url);
        } catch (IOException e) {
            throw new CommunicationException("Unable to read Redisson configuration: " + location, e);
        }
    }

}
