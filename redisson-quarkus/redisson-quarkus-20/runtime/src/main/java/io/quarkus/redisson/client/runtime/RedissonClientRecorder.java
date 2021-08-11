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
package io.quarkus.redisson.client.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.quarkus.runtime.annotations.Recorder;
import org.apache.commons.text.CaseUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author ineednousername https://github.com/ineednousername
 */
@Recorder
public class RedissonClientRecorder {

    public Supplier<RedissonClient> configureRedisson(String yaml) {
        return () -> {
            Config redissonConfig = yamlToRedissonConfig(yaml);
            return createClient(redissonConfig);
        };
    }

    public Supplier<RedissonClient> configureRedisson(RedissonRuntimeConfig config) {
        return () -> {
            String yaml = convertToYaml(config);
            Config redissonConfig = yamlToRedissonConfig(yaml);
            resolveCodec(config, redissonConfig);
            return createClient(redissonConfig);
        };

    }

    private RedissonClient createClient(Config redissonConfig) {
        try {
            return Redisson.create(redissonConfig);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create client. ", e);
        }
    }

    private void resolveCodec(RedissonRuntimeConfig config, Config redissonConfig) {
        try{
            final Optional<Codec> resolvedCodec = config.codec.map(c -> {
                try {
                    return (Codec) Class.forName(c).getConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Cannot load codec " + c+" \n"+e.getMessage(), e);
                }
            });
            resolvedCodec.ifPresent(redissonConfig::setCodec);
        }catch (Exception e){
            throw new RuntimeException("Cannot resolve Redisson codec. "+ config.codec, e);
        }
    }

    private Config yamlToRedissonConfig(String yaml) {
        Config redissonConfig;
        try{
            redissonConfig = Config.fromYAML(yaml);

        }catch (Exception e){
            throw new RuntimeException("Cannot resolve Redisson config from yaml. "+ yaml, e);
        }
        return redissonConfig;
    }

    private String convertToYaml(RedissonRuntimeConfig config) {
        String yaml;
        try {
            final Map.Entry<String, RedissonRuntimeConfig.NamedRuntimeConfig> serverConfig = config.serversConfig.entrySet().stream().findFirst().get();
            final Map<String, String> configs = serverConfig.getValue().configs.entrySet().stream().collect(Collectors.toMap(
                    e -> camelCase(e.getKey()),
                    Map.Entry::getValue
            ));

            Map<String, Object> configurationModel = new HashMap<>();
            configurationModel.put(camelCase(serverConfig.getKey()), configs);

            put(configurationModel, "threads", config.threads);
            put(configurationModel, "nettyThreads", config.nettyThreads);
            put(configurationModel, "transportMode", config.transportMode);

            yaml = yamlString(configurationModel);
        }catch (Exception e){
            throw new RuntimeException("Cannot convert quarkus config to yaml. ", e);
        }
        return yaml;
    }

    private <T> void put(Map<String, Object> configurationModel, String name, Optional<T> configItem) {
        configItem.ifPresent(i -> configurationModel.put(name, i));
    }

    private String yamlString(Map<String, Object> configuration) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final StringWriter w = new StringWriter();
        mapper.writeValue(w, configuration);
        return w.toString();
    }

    private String camelCase(String key) {
        return CaseUtils.toCamelCase(key, false, new char[]{'-'});
    }

}
