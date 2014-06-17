/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.redisson.connection.LoadBalancer;
import org.redisson.connection.RoundRobinLoadBalancer;

public class MasterSlaveServersConfig extends BaseConfig<MasterSlaveServersConfig> {

    /**
     * Сonnection load balancer for multiple Redis slave servers
     */
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    /**
     * Redis slave servers addresses
     */
    private List<URI> slaveAddresses = new ArrayList<URI>();

    /**
     * Redis master server address
     */
    private URI masterAddress;

    /**
     * Redis 'slave' servers subscription (pub/sub) connection pool size for <b>each</b> slave node
     */
    private int slaveSubscriptionConnectionPoolSize = 25;

    /**
     * Redis 'slave' servers connection pool size for <b>each</b> slave node
     */
    private int slaveConnectionPoolSize = 100;

    /**
     * Redis 'master' server connection pool size
     */
    private int masterConnectionPoolSize = 100;

    public MasterSlaveServersConfig() {
    }

    MasterSlaveServersConfig(MasterSlaveServersConfig config) {
        super(config);
        setLoadBalancer(config.getLoadBalancer());
        setMasterAddress(config.getMasterAddress());
        setMasterConnectionPoolSize(config.getMasterConnectionPoolSize());
        setSlaveAddresses(config.getSlaveAddresses());
        setSlaveConnectionPoolSize(config.getSlaveConnectionPoolSize());
        setSlaveSubscriptionConnectionPoolSize(config.getSlaveSubscriptionConnectionPoolSize());
    }

    /**
     * Set Redis master server address. Use follow format -- host:port
     *
     * @param masterAddress
     */
    public MasterSlaveServersConfig setMasterAddress(String masterAddress) {
        try {
            this.masterAddress = new URI("//" + masterAddress);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Can't parse " + masterAddress);
        }
        return this;
    }
    public URI getMasterAddress() {
        return masterAddress;
    }
    void setMasterAddress(URI masterAddress) {
        this.masterAddress = masterAddress;
    }

    /**
     * Add Redis slave server address. Use follow format -- host:port
     *
     * @param addresses
     * @return
     */
    public MasterSlaveServersConfig addSlaveAddress(String ... sAddresses) {
        for (String address : sAddresses) {
            try {
                slaveAddresses.add(new URI("//" + address));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Can't parse " + address);
            }
        }
        return this;
    }

    public List<URI> getSlaveAddresses() {
        return slaveAddresses;
    }
    void setSlaveAddresses(List<URI> readAddresses) {
        this.slaveAddresses = readAddresses;
    }

    /**
     * Redis 'slave' servers connection pool size for <b>each</b> slave node
     * Default is 100
     *
     * @param slaveConnectionPoolSize
     * @return
     */
    public MasterSlaveServersConfig setSlaveConnectionPoolSize(int slaveConnectionPoolSize) {
        this.slaveConnectionPoolSize = slaveConnectionPoolSize;
        return this;
    }
    public int getSlaveConnectionPoolSize() {
        return slaveConnectionPoolSize;
    }

    /**
     * Redis 'master' server connection pool size
     * Default is 100
     *
     * @param masterConnectionPoolSize
     * @return
     */
    public MasterSlaveServersConfig setMasterConnectionPoolSize(int masterConnectionPoolSize) {
        this.masterConnectionPoolSize = masterConnectionPoolSize;
        return this;
    }
    public int getMasterConnectionPoolSize() {
        return masterConnectionPoolSize;
    }

    /**
     * Сonnection load balancer to multiple Redis slave servers.
     * Uses Round-robin algorithm by default
     *
     * @param loadBalancer
     * @return
     *
     * @see org.redisson.connection.RoundRobinLoadBalancer
     * @see org.redisson.connection.BaseLoadBalancer
     */
    public MasterSlaveServersConfig setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return this;
    }
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * Redis 'slave' servers subscription connection pool size for <b>each</b> slave node
     * Default is 25
     *
     * @param slaveSubscriptionConnectionPoolSize
     * @return
     */
    public MasterSlaveServersConfig setSlaveSubscriptionConnectionPoolSize(int slaveSubscriptionConnectionPoolSize) {
        this.slaveSubscriptionConnectionPoolSize = slaveSubscriptionConnectionPoolSize;
        return this;
    }
    public int getSlaveSubscriptionConnectionPoolSize() {
        return slaveSubscriptionConnectionPoolSize;
    }

}
