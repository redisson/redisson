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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveServersConfig extends BaseMasterSlaveServersConfig<MasterSlaveServersConfig> {

    /**
     * Redis slave servers addresses
     */
    private Set<String> slaveAddresses = new HashSet<String>();

    /**
     * Redis master server address
     */
    private String masterAddress;

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
        this.masterAddress = masterAddress;
        return this;
    }
    public String getMasterAddress() {
        return masterAddress;
    }

    /**
     * Add Redis slave server address. Use follow format -- host:port
     *
     * @param addresses of Redis
     * @return config
     */
    public MasterSlaveServersConfig addSlaveAddress(String... addresses) {
        slaveAddresses.addAll(Arrays.asList(addresses));
        return this;
    }
    public MasterSlaveServersConfig addSlaveAddress(String slaveAddress) {
        slaveAddresses.add(slaveAddress);
        return this;
    }
    public Set<String> getSlaveAddresses() {
        return slaveAddresses;
    }
    public void setSlaveAddresses(Set<String> readAddresses) {
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
