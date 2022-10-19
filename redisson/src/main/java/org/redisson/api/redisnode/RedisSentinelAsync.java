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
package org.redisson.api.redisnode;

import org.redisson.api.RFuture;
import org.redisson.misc.RedisURI;

import java.util.List;
import java.util.Map;

/**
 * Redis Sentinel node API interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RedisSentinelAsync extends RedisNodeAsync {

    /**
     * Returns network address of defined Redis master.
     *
     * @param masterName - name of master
     * @return network address
     */
    RFuture<RedisURI> getMasterAddrAsync(String masterName);

    /**
     * Returns list of map containing info regarding Redis Sentinel server
     * monitoring defined master.
     *
     * @param masterName - name of master
     * @return list of Redis Sentinels
     */
    RFuture<List<Map<String, String>>> getSentinelsAsync(String masterName);

    /**
     * Returns list of map containing info regarding Redis Master server
     * monitored by current Redis Sentinel server.
     *
     * @return list of Redis Masters
     */
    RFuture<List<Map<String, String>>> getMastersAsync();

    /**
     * Returns list of map containing info regarding Redis Slave server
     * of defined master.
     *
     * @param masterName - name of master
     * @return list of Redis Slaves
     */
    RFuture<List<Map<String, String>>> getSlavesAsync(String masterName);

    /**
     * Returns map containing info regarding defined Redis master.
     *
     * @param masterName - name of master
     * @return map containing info
     */
    RFuture<Map<String, String>> getMasterAsync(String masterName);

    /**
     * Starts failover process for defined Redis master
     *
     * @param masterName - name of master
     */
    RFuture<Void> failoverAsync(String masterName);

}
