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

public class MasterSlaveServersConfig extends BaseMasterSlaveServersConfig<MasterSlaveServersConfig> {

    /**
     * Redis slave servers addresses
     */
    private List<URI> slaveAddresses = new ArrayList<URI>();

    /**
     * Redis master server address
     */
    private URI masterAddress;

    public MasterSlaveServersConfig() {
    }

    MasterSlaveServersConfig(MasterSlaveServersConfig config) {
        super(config);
        setLoadBalancer(config.getLoadBalancer());
        setMasterAddress(config.getMasterAddress());
        setSlaveAddresses(config.getSlaveAddresses());
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
            slaveAddresses.add(URI.create("//" + address));
        }
        return this;
    }

    public List<URI> getSlaveAddresses() {
        return slaveAddresses;
    }
    void setSlaveAddresses(List<URI> readAddresses) {
        this.slaveAddresses = readAddresses;
    }

}
