/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package io.quarkus.redisson.client.runtime;

import com.fasterxml.jackson.databind.MapperFeature;
import io.quarkus.arc.DefaultBean;
import org.eclipse.microprofile.config.ConfigProvider;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.config.PropertiesConvertor;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ApplicationScoped
public class RedissonClientProducer {

    private RedissonClient redisson;

    @Produces
    @Singleton
    @DefaultBean
    public RedissonClient create() throws IOException {
        InputStream configStream;
        Optional<String> configFile = ConfigProvider.getConfig().getOptionalValue("quarkus.redisson.file", String.class);
        if (configFile.isPresent()) {
            configStream = getClass().getResourceAsStream(configFile.get());
        } else {
            configStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("redisson.yaml");
        }
        String config;
        if (configStream != null) {
            byte[] array = new byte[configStream.available()];
            configStream.read(array);
            config = new String(array, StandardCharsets.UTF_8);
        } else {
            Stream<String> s = StreamSupport.stream(ConfigProvider.getConfig().getPropertyNames().spliterator(), false);
            String yaml = PropertiesConvertor.toYaml("quarkus.redisson.", s.sorted().collect(Collectors.toList()), prop -> {
                return ConfigProvider.getConfig().getValue(prop, String.class);
            }, false);
            config = yaml;
        }

        ConfigSupport support = new ConfigSupport() {
            {
                yamlMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
            }
        };
        Config c = support.fromYAML(config, Config.class);
        redisson = Redisson.create(c);
        return redisson;
    }

    @PreDestroy
    public void close() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

}
