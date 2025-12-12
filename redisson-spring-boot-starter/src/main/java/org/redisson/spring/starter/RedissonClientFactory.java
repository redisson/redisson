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
package org.redisson.spring.starter;

import org.jspecify.annotations.Nullable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author livk
 */
abstract class RedissonClientFactory {

    private static final String REDIS_PROTOCOL_PREFIX = "redis://";

    private static final String REDISS_PROTOCOL_PREFIX = "rediss://";

    public static RedissonClient create(RedissonPropertiesV4 properties,
                                        ObjectProvider<RedissonAutoConfigurationCustomizer> configCustomizers) {
        Config config = properties.getConfig();
        Assert.notNull(config, "Redisson config must not be null");
        configCustomizers.orderedStream().forEach(customizer -> customizer.customize(config));
        return Redisson.create(config);
    }

    public static RedissonClient create(DataRedisProperties redisProperties,
                                        ObjectProvider<RedissonAutoConfigurationCustomizer> configCustomizers) {
        Config config = createConfig(redisProperties);
        configCustomizers.orderedStream().forEach(customizer -> customizer.customize(config));
        return Redisson.create(config);
    }

    private static Config createConfig(DataRedisProperties redisProperties) {
        Config config;
        Duration duration = redisProperties.getTimeout();
        int timeout = duration == null ? 10000 : (int) duration.toMillis();
        if (redisProperties.getSentinel() != null) {
            List<String> nodeList = redisProperties.getSentinel().getNodes();
            String[] nodes = convert(nodeList);
            config = new Config();
            config.setPassword(redisProperties.getPassword())
                    .useSentinelServers()
                    .setMasterName(redisProperties.getSentinel().getMaster())
                    .addSentinelAddress(nodes)
                    .setDatabase(redisProperties.getDatabase())
                    .setConnectTimeout(timeout);
        } else if (redisProperties.getCluster() != null) {
            List<String> nodeList = redisProperties.getCluster().getNodes();
            String[] nodes = convert(nodeList);
            config = new Config();
            config.setPassword(redisProperties.getPassword())
                    .useClusterServers()
                    .addNodeAddress(nodes)
                    .setConnectTimeout(timeout);
        } else {
            config = new Config();
            String prefix = REDIS_PROTOCOL_PREFIX;
            if (redisProperties.getSsl().isEnabled()) {
                prefix = REDISS_PROTOCOL_PREFIX;
            }
            config.setPassword(redisProperties.getPassword())
                    .useSingleServer()
                    .setAddress(prefix + redisProperties.getHost() + ":" + redisProperties.getPort())
                    .setConnectTimeout(timeout)
                    .setDatabase(redisProperties.getDatabase());
        }
        return config;
    }

    private static String[] convert(@Nullable List<String> nodesObject) {
        if (CollectionUtils.isEmpty(nodesObject)) {
            return new String[0];
        }
        List<String> nodes = new ArrayList<>(nodesObject.size());
        for (String node : nodesObject) {
            if (!node.startsWith(REDIS_PROTOCOL_PREFIX) && !node.startsWith(REDISS_PROTOCOL_PREFIX)) {
                nodes.add(REDIS_PROTOCOL_PREFIX + node);
            } else {
                nodes.add(node);
            }
        }
        return nodes.toArray(new String[0]);
    }

}
