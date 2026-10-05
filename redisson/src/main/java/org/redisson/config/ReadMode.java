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
package org.redisson.config;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public enum ReadMode {

    /**
     * Read from slave nodes. Uses MASTER if no SLAVES are available.
     * Node is selected using specified <code>loadBalancer</code> in Redisson configuration.
     */
    SLAVE,

    /**
     * Read from master node
     */
    MASTER,

    /**
     * Read from master and slave nodes.
     * Node is selected using specified <code>loadBalancer</code> in Redisson configuration.
     */
    MASTER_SLAVE,

    /**
     * Read from slave nodes in the client's availability zone. Falls back to slave nodes
     * in any zone, then to the master node if no slave is available.
     * Within each of these steps the node is selected using specified <code>loadBalancer</code>
     * in Redisson configuration.
     * <p>
     * Requires <b>Valkey 8.0 or higher.</b>
     * <p>
     * Requires <code>clientAvailabilityZone</code> in Redisson configuration.
     *
     * @see BaseMasterSlaveServersConfig#setClientAvailabilityZone(String)
     */
    AZ_AFFINITY,

    /**
     * Read from slave nodes in the client's availability zone, then from the master node
     * if it is in that zone. Falls back to slave nodes in any zone, then to the master node.
     * Within each of these steps the node is selected using specified <code>loadBalancer</code>
     * in Redisson configuration.
     * <p>
     * As with <code>MASTER_SLAVE</code>, subscriptions under <code>subscriptionMode</code> <code>SLAVE</code>
     * are spread over the master node too.
     * <p>
     * Requires <b>Valkey 8.0 or higher.</b>
     * <p>
     * Requires definition of <code>clientAvailabilityZone</code> in Redisson configuration.
     *
     * @see BaseMasterSlaveServersConfig#setClientAvailabilityZone(String)
     */
    AZ_AFFINITY_SLAVES_AND_MASTER,

    /**
     * Read from master and slave nodes in the client's availability zone, without preferring
     * either type. Falls back to master and slave nodes in any zone.
     * Within each of these steps the node is selected using specified <code>loadBalancer</code>
     * in Redisson configuration.
     * <p>
     * As with <code>MASTER_SLAVE</code>, subscriptions under <code>subscriptionMode</code> <code>SLAVE</code>
     * are spread over the master node too.
     * <p>
     * Requires <b>Valkey 8.0 or higher.</b>
     * <p>
     * Requires definition of <code>clientAvailabilityZone</code> in Redisson configuration.
     *
     * @see BaseMasterSlaveServersConfig#setClientAvailabilityZone(String)
     */
    AZ_AFFINITY_MASTER_SLAVE;

    public boolean isMasterInSlavePool() {
        return this == MASTER_SLAVE
                || this == AZ_AFFINITY_SLAVES_AND_MASTER
                || this == AZ_AFFINITY_MASTER_SLAVE;
    }

    public boolean isAvailabilityZoneAware() {
        return this == AZ_AFFINITY
                || this == AZ_AFFINITY_SLAVES_AND_MASTER
                || this == AZ_AFFINITY_MASTER_SLAVE;
    }

}
