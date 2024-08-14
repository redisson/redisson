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
package io.quarkus.redisson.client.runtime;

import com.fasterxml.jackson.databind.MapperFeature;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.shutdown.ShutdownConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.config.PropertiesConvertor;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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

    @Inject
    public ShutdownConfig shutdownConfig;

    @Produces
    @Singleton
    @DefaultBean
    public RedissonClient create() throws IOException {
        String config = null;
        Optional<String> configFile = ConfigProvider.getConfig().getOptionalValue("quarkus.redisson.file", String.class);
        String configFileName = configFile.orElse("redisson.yaml");
        try (InputStream configStream = Optional.ofNullable(getClass().getResourceAsStream(configFileName))
                .orElse(Thread.currentThread().getContextClassLoader().getResourceAsStream(configFileName))
        ) {
            if (configStream != null) {
                byte[] array = new byte[configStream.available()];
                if (configStream.read(array) != -1) {
                    config = new String(array, StandardCharsets.UTF_8);
                }
            }
        }
        if (config == null) {
            Stream<String> s = StreamSupport.stream(ConfigProvider.getConfig().getPropertyNames().spliterator(), false);
            config = PropertiesConvertor.toYaml("quarkus.redisson.", s.sorted().collect(Collectors.toList()), prop -> {
                return ConfigProvider.getConfig().getValue(prop, String.class);
            }, false);
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

    public void setConfig(org.eclipse.microprofile.config.Config config) {

    }

    @PreDestroy
    public void close() {
        if (redisson != null) {
            if (shutdownConfig.isShutdownTimeoutSet()){
                Duration grace = shutdownConfig.timeout.get();
                redisson.shutdown(grace.toMillis(),grace.toMillis()*2, TimeUnit.MILLISECONDS);
            }else{
                redisson.shutdown();
            }
        }
    }

}
