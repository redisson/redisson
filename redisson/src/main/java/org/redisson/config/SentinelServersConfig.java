/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SentinelServersConfig extends BaseMasterSlaveServersConfig<SentinelServersConfig> {

    private List<String> sentinelAddresses = new ArrayList<>();
    
    private Map<String, String> natMap = Collections.emptyMap();

    private String masterName;

    /**
     * Database index used for Redis connection
     */
    private int database = 0;
    
    /**
     * Sentinel scan interval in milliseconds
     */
    private int scanInterval = 1000;

    public SentinelServersConfig() {
    }

    SentinelServersConfig(SentinelServersConfig config) {
        super(config);
        setSentinelAddresses(config.getSentinelAddresses());
        setMasterName(config.getMasterName());
        setDatabase(config.getDatabase());
        setScanInterval(config.getScanInterval());
        setNatMap(new HashMap<>(config.getNatMap()));
    }

    /**
     * Master server name used by Redis Sentinel servers and master change monitoring task.
     *
     * @param masterName of Redis
     * @return config
     */
    public SentinelServersConfig setMasterName(String masterName) {
        this.masterName = masterName;
        return this;
    }
    public String getMasterName() {
        return masterName;
    }

    /**
     * Add Redis Sentinel node address in host:port format. Multiple nodes at once could be added.
     *
     * @param addresses of Redis
     * @return config
     */
    public SentinelServersConfig addSentinelAddress(String... addresses) {
        sentinelAddresses.addAll(Arrays.asList(addresses));
        return this;
    }
    public List<String> getSentinelAddresses() {
        return sentinelAddresses;
    }
    void setSentinelAddresses(List<String> sentinelAddresses) {
        this.sentinelAddresses = sentinelAddresses;
    }

    /**
     * Database index used for Redis connection
     * Default is <code>0</code>
     *
     * @param database number
     * @return config
     */
    public SentinelServersConfig setDatabase(int database) {
        this.database = database;
        return this;
    }
    public int getDatabase() {
        return database;
    }

    public int getScanInterval() {
        return scanInterval;
    }
    /**
     * Sentinel scan interval in milliseconds
     *
     * @param scanInterval in milliseconds
     * @return config
     */
    public SentinelServersConfig setScanInterval(int scanInterval) {
        this.scanInterval = scanInterval;
        return this;
    }
    
    public Map<String, String> getNatMap() {
        return natMap;
    }
    
    /**
     * Defines NAT mapping. Address as a map key is replaced with mapped address as value.
     * 
     * @param natMap - nat mapping
     * @return config
     */
    public SentinelServersConfig setNatMap(Map<String, String> natMap) {
        this.natMap = natMap;
        return this;
    }
    
}
