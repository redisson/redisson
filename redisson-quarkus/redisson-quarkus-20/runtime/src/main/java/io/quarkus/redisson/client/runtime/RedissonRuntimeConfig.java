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

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import org.redisson.config.TransportMode;

import java.util.Map;
import java.util.Optional;

/**
 *
 * @author ineednousername https://github.com/ineednousername
 *
 */
@ConfigRoot(name = "redisson", phase = ConfigPhase.RUN_TIME)
public class RedissonRuntimeConfig {

    /**
     * Redis uri
     */
    @ConfigItem
    public Optional<String> codec;

    /**
     * empty javadoc
     */
    @ConfigItem
    public Optional<Integer> threads;

    /**
     * empty javadoc
     */
    @ConfigItem(name = "netty-threads")
    public Optional<Integer> nettyThreads;


    /**
     * empty javadoc
     */
    @ConfigItem(name = "transport-mode")
    public Optional<TransportMode> transportMode;


    /**
     * Redis cluster config
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public Map<String, NamedRuntimeConfig> serversConfig;


    @ConfigGroup
    public static class NamedRuntimeConfig {

        /**
         *
         */
        @ConfigItem(name = ConfigItem.PARENT)
        public Map<String, String> configs;
    }
}
