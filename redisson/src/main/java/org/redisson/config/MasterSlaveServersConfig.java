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

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.redisson.misc.URIBuilder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveServersConfig extends BaseMasterSlaveServersConfig<MasterSlaveServersConfig> {

    /**
     * Redis slave servers addresses
     */
    private Set<URI> slaveAddresses = new HashSet<URI>();

    /**
     * Redis master server address
     */
    private URI masterAddress;

    /**
     * Database index used for Redis connection
     */
    private int database = 0;

    public MasterSlaveServersConfig() {
    }

    MasterSlaveServersConfig(MasterSlaveServersConfig config) {
        super(config);
        setLoadBalancer(config.getLoadBalancer());
        setMasterAddress(config.getMasterAddress());
        setSlaveAddresses(config.getSlaveAddresses());
        setDatabase(config.getDatabase());
    }

    /**
     * Set Redis master server address. Use follow format -- host:port
     *
     * @param masterAddress of Redis
     * @return config
     */
    public MasterSlaveServersConfig setMasterAddress(String masterAddress) {
        if (masterAddress != null) {
            this.masterAddress = URIBuilder.create(masterAddress);
        }
        return this;
    }
    public URI getMasterAddress() {
        if (masterAddress != null) {
            return masterAddress;
        }
        return null;
    }
    public MasterSlaveServersConfig setMasterAddress(URI masterAddress) {
        if (masterAddress != null) {
            this.masterAddress = masterAddress;
        }
        return this;
    }

    /**
     * Add Redis slave server address. Use follow format -- host:port
     *
     * @param addresses of Redis
     * @return config
     */
    public MasterSlaveServersConfig addSlaveAddress(String ... addresses) {
        for (String address : addresses) {
            slaveAddresses.add(URIBuilder.create(address));
        }
        return this;
    }
    public MasterSlaveServersConfig addSlaveAddress(URI slaveAddress) {
        slaveAddresses.add(slaveAddress);
        return this;
    }
    public Set<URI> getSlaveAddresses() {
        return slaveAddresses;
    }
    public void setSlaveAddresses(Set<URI> readAddresses) {
        this.slaveAddresses = readAddresses;
    }

    /**
     * Database index used for Redis connection
     * Default is <code>0</code>
     *
     * @param database number
     * @return config
     */
    public MasterSlaveServersConfig setDatabase(int database) {
        this.database = database;
        return this;
    }
    public int getDatabase() {
        return database;
    }

}
