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
package org.redisson.api.redisnode;

import org.redisson.misc.RedisURI;

import java.util.List;
import java.util.Map;

/**
 * Redis Sentinel node API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisSentinel extends RedisNode, RedisSentinelAsync {

    /**
     * Returns network address of defined Redis master.
     *
     * @param masterName - name of master
     * @return network address
     */
    RedisURI getMasterAddr(String masterName);

    /**
     * Returns list of map containing info regarding Redis Sentinel server
     * monitoring defined master.
     *
     * @param masterName - name of master
     * @return list of Redis Sentinels
     */
    List<Map<String, String>> getSentinels(String masterName);

    /**
     * Returns list of map containing info regarding Redis Master server
     * monitored by current Redis Sentinel server.
     *
     * @return list of Redis Masters
     */
    List<Map<String, String>> getMasters();

    /**
     * Returns list of map containing info regarding Redis Slave server
     * of defined master.
     *
     * @param masterName - name of master
     * @return list of Redis Slaves
     */
    List<Map<String, String>> getSlaves(String masterName);

    /**
     * Returns map containing info regarding defined Redis master.
     *
     * @param masterName - name of master
     * @return map containing info
     */
    Map<String, String> getMaster(String masterName);

    /**
     * Starts failover process for defined Redis master
     *
     * @param masterName - name of master
     */
    void failover(String masterName);

}
