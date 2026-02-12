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
package io.quarkus.redisson.client.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;

/**
 * Redisson config
 *
 * @author Nikita Koksharov
 *
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus")
public interface RedissonConfig {

    /**
     * Common params
     *
     * @return params
     */
    @WithName("redisson")
    Map<String, String> params();

    /**
     * Single server params
     *
     * @return params
     */
    @WithName("redisson.single-server-config")
    Map<String, String> singleServerConfig();

    /**
     * Cluster servers params
     *
     * @return params
     */
    @WithName("redisson.cluster-servers-config")
    Map<String, String> clusterServersConfig();

    /**
     * Sentinel servers params
     *
     * @return params
     */
    @WithName("redisson.sentinel-servers-config")
    Map<String, String> sentinelServersConfig();

    /**
     * Replicated servers params
     *
     * @return params
     */
    @WithName("redisson.replicated-servers-config")
    Map<String, String> replicatedServersConfig();

    /**
     * Master and slave servers params
     *
     * @return params
     */
    @WithName("redisson.master-slave-servers-config")
    Map<String, String> masterSlaveServersConfig();

}
