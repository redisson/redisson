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

import org.redisson.connection.balancer.LoadBalancer;
import org.redisson.connection.balancer.RoundRobinLoadBalancer;

public class BaseMasterSlaveServersConfig<T extends BaseMasterSlaveServersConfig<T>> extends BaseConfig<T> {

    /**
     * Сonnection load balancer for multiple Redis slave servers
     */
    private LoadBalancer loadBalancer = new RoundRobinLoadBalancer();

    /**
     * Redis 'slave' node minimum idle subscription (pub/sub) connection amount for <b>each</b> slave node
     */
    private int slaveSubscriptionConnectionMinimumIdleSize = 1;

    /**
     * Redis 'slave' node maximum subscription (pub/sub) connection pool size for <b>each</b> slave node
     */
    private int slaveSubscriptionConnectionPoolSize = 25;

    /**
     * Redis 'slave' node minimum idle connection amount for <b>each</b> slave node
     */
    private int slaveConnectionMinimumIdleSize = 5;

    /**
     * Redis 'slave' node maximum connection pool size for <b>each</b> slave node
     */
    private int slaveConnectionPoolSize = 100;

    /**
     * Redis 'master' node minimum idle connection amount for <b>each</b> slave node
     */
    private int masterConnectionMinimumIdleSize = 5;

    /**
     * Redis 'master' node maximum connection pool size
     */
    private int masterConnectionPoolSize = 100;

    public BaseMasterSlaveServersConfig() {
    }

    BaseMasterSlaveServersConfig(T config) {
        super(config);
        setLoadBalancer(config.getLoadBalancer());
        setMasterConnectionPoolSize(config.getMasterConnectionPoolSize());
        setSlaveConnectionPoolSize(config.getSlaveConnectionPoolSize());
        setSlaveSubscriptionConnectionPoolSize(config.getSlaveSubscriptionConnectionPoolSize());
        setMasterConnectionMinimumIdleSize(config.getMasterConnectionMinimumIdleSize());
        setSlaveConnectionMinimumIdleSize(config.getSlaveConnectionMinimumIdleSize());
        setSlaveSubscriptionConnectionMinimumIdleSize(config.getSlaveSubscriptionConnectionMinimumIdleSize());
    }

    /**
     * Redis 'slave' servers connection pool size for <b>each</b> slave node.
     *
     * Default is 100
     *
     * @param slaveConnectionPoolSize
     * @return
     */
    public T setSlaveConnectionPoolSize(int slaveConnectionPoolSize) {
        this.slaveConnectionPoolSize = slaveConnectionPoolSize;
        return (T)this;
    }
    public int getSlaveConnectionPoolSize() {
        return slaveConnectionPoolSize;
    }

    /**
     * Redis 'master' server connection pool size.
     *
     * Default is 100
     *
     */
    public T setMasterConnectionPoolSize(int masterConnectionPoolSize) {
        this.masterConnectionPoolSize = masterConnectionPoolSize;
        return (T)this;
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
     * @see org.redisson.connection.balancer.RoundRobinLoadBalancer
     * @see org.redisson.connection.BaseLoadBalancer
     */
    public T setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        return (T)this;
    }
    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    /**
     * Redis 'slave' node maximum subscription (pub/sub) connection pool size for <b>each</b> slave node
     *
     * Default is 25
     *
     */
    public T setSlaveSubscriptionConnectionPoolSize(int slaveSubscriptionConnectionPoolSize) {
        this.slaveSubscriptionConnectionPoolSize = slaveSubscriptionConnectionPoolSize;
        return (T)this;
    }
    public int getSlaveSubscriptionConnectionPoolSize() {
        return slaveSubscriptionConnectionPoolSize;
    }

    /**
     * Redis 'slave' node minimum idle connection amount for <b>each</b> slave node
     *
     * Default is 5
     *
     */
    public T setSlaveConnectionMinimumIdleSize(int slaveConnectionMinimumIdleSize) {
        this.slaveConnectionMinimumIdleSize = slaveConnectionMinimumIdleSize;
        return (T) this;
    }
    public int getSlaveConnectionMinimumIdleSize() {
        return slaveConnectionMinimumIdleSize;
    }

    /**
     * Redis 'master' node minimum idle connection amount for <b>each</b> slave node
     *
     * Default is 5
     *
     */
    public T setMasterConnectionMinimumIdleSize(int masterConnectionMinimumIdleSize) {
        this.masterConnectionMinimumIdleSize = masterConnectionMinimumIdleSize;
        return (T) this;
    }
    public int getMasterConnectionMinimumIdleSize() {
        return masterConnectionMinimumIdleSize;
    }

    /**
     * Redis 'slave' node minimum idle subscription (pub/sub) connection amount for <b>each</b> slave node
     * Default is 1
     *
     */
    public T setSlaveSubscriptionConnectionMinimumIdleSize(int slaveSubscriptionConnectionMinimumIdleSize) {
        this.slaveSubscriptionConnectionMinimumIdleSize = slaveSubscriptionConnectionMinimumIdleSize;
        return (T) this;
    }
    public int getSlaveSubscriptionConnectionMinimumIdleSize() {
        return slaveSubscriptionConnectionMinimumIdleSize;
    }

}
