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
package org.redisson.config;

import org.redisson.api.HostNatMapper;
import org.redisson.api.HostPortNatMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterServersConfig extends BaseMasterSlaveServersConfig<ClusterServersConfig> {

    private NatMapper natMapper = NatMapper.direct();
    
    /**
     * Redis cluster node urls list
     */
    private List<String> nodeAddresses = new ArrayList<>();

    /**
     * Redis cluster scan interval in milliseconds
     */
    private int scanInterval = 5000;

    private boolean checkSlotsCoverage = true;

    private boolean checkMasterLinkStatus = false;

    private ShardedSubscriptionMode shardedSubscriptionMode = ShardedSubscriptionMode.AUTO;

    private int database = 0;

    public ClusterServersConfig() {
    }

    ClusterServersConfig(ClusterServersConfig config) {
        super(config);
        setNodeAddresses(config.getNodeAddresses());
        setScanInterval(config.getScanInterval());
        setNatMapper(config.getNatMapper());
        setCheckSlotsCoverage(config.isCheckSlotsCoverage());
        setCheckMasterLinkStatus(config.isCheckMasterLinkStatus());
        setShardedSubscriptionMode(config.getShardedSubscriptionMode());
        setDatabase(config.getDatabase());
    }

    /**
     * Add Redis cluster node address. Use follow format -- <code>host:port</code>
     *
     * @param addresses in <code>host:port</code> format
     * @return config
     */
    public ClusterServersConfig addNodeAddress(String... addresses) {
        nodeAddresses.addAll(Arrays.asList(addresses));
        return this;
    }
    public List<String> getNodeAddresses() {
        return nodeAddresses;
    }
    public void setNodeAddresses(List<String> nodeAddresses) {
        this.nodeAddresses = nodeAddresses;
    }

    public int getScanInterval() {
        return scanInterval;
    }
    /**
     * Redis cluster scan interval in milliseconds
     * <p>
     * Default is <code>5000</code>
     *
     * @param scanInterval in milliseconds
     * @return config
     */
    public ClusterServersConfig setScanInterval(int scanInterval) {
        this.scanInterval = scanInterval;
        return this;
    }

    public boolean isCheckSlotsCoverage() {
        return checkSlotsCoverage;
    }

    /**
     * Enables cluster slots check during Redisson startup.
     * <p>
     * Default is <code>true</code>
     *
     * @param checkSlotsCoverage boolean value
     * @return config
     */
    public ClusterServersConfig setCheckSlotsCoverage(boolean checkSlotsCoverage) {
        this.checkSlotsCoverage = checkSlotsCoverage;
        return this;
    }

    public boolean isCheckMasterLinkStatus() {
        return checkMasterLinkStatus;
    }

    /**
     * Enable checking the 'master-link-status' of a slave from the INFO REPLICATION command.
     * <p>
     * Default is <code>false</code>
     *
     * @param checkMasterLinkStatus boolean value
     * @return config
     */
    public ClusterServersConfig setCheckMasterLinkStatus(boolean checkMasterLinkStatus) {
        this.checkMasterLinkStatus = checkMasterLinkStatus;
        return this;
    }

    /*
     * Use {@link #setNatMapper(NatMapper)}
     */
    @Deprecated
    public ClusterServersConfig setNatMap(Map<String, String> natMap) {
        HostPortNatMapper mapper = new HostPortNatMapper();
        mapper.setHostsPortMap(natMap);
        this.natMapper = mapper;
        return this;
    }

    public NatMapper getNatMapper() {
        return natMapper;
    }

    /**
     * Defines NAT mapper which maps Redis URI object.
     * Applied to all Redis connections.
     *
     * @see HostNatMapper
     * @see HostPortNatMapper
     *
     * @param natMapper nat mapper object
     * @return config
     */
    public ClusterServersConfig setNatMapper(NatMapper natMapper) {
        this.natMapper = natMapper;
        return this;
    }

    public ShardedSubscriptionMode getShardedSubscriptionMode() {
        return shardedSubscriptionMode;
    }

    /**
     * Defines whether to use sharded subscription feature available in Redis 7.0+.
     * <p>
     * Used in RMapCache, RLocalCachedMap, RCountDownLatch, RLock, RPermitExpirableSemaphore,
     * RSemaphore, RLongAdder, RDoubleAdder, Micronaut Session, Apache Tomcat Manager objects.
     * <p>
     * Default is <code>AUTO</code>
     *
     * @param shardedSubscriptionMode param
     * @return config
     */
    public ClusterServersConfig setShardedSubscriptionMode(ShardedSubscriptionMode shardedSubscriptionMode) {
        this.shardedSubscriptionMode = shardedSubscriptionMode;
        return this;
    }

    /**
     * Database index used for Valkey connection.
     * <p>
     * Default is <code>0</code>
     * <p>
     * <b>Requires <b>Valkey 9.0.0 and higher.</b>
     *
     * @param database number
     * @return config
     */
    public ClusterServersConfig setDatabase(int database) {
        this.database = database;
        return this;
    }
    public int getDatabase() {
        return database;
    }

}
