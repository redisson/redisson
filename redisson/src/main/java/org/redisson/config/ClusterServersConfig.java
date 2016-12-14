/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.redisson.misc.URLBuilder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterServersConfig extends BaseMasterSlaveServersConfig<ClusterServersConfig> {

    /**
     * Redis cluster node urls list
     */
    private List<URL> nodeAddresses = new ArrayList<URL>();

    /**
     * Redis cluster scan interval in milliseconds
     */
    private int scanInterval = 1000;

    public ClusterServersConfig() {
    }

    ClusterServersConfig(ClusterServersConfig config) {
        super(config);
        setNodeAddresses(config.getNodeAddresses());
        setScanInterval(config.getScanInterval());
    }

    /**
     * Add Redis cluster node address. Use follow format -- <code>host:port</code>
     *
     * @param addresses in <code>host:port</code> format
     * @return config
     */
    public ClusterServersConfig addNodeAddress(String ... addresses) {
        for (String address : addresses) {
            nodeAddresses.add(URLBuilder.create(address));
        }
        return this;
    }
    public List<URL> getNodeAddresses() {
        return nodeAddresses;
    }
    void setNodeAddresses(List<URL> nodeAddresses) {
        this.nodeAddresses = nodeAddresses;
    }

    public int getScanInterval() {
        return scanInterval;
    }
    /**
     * Redis cluster scan interval in milliseconds
     *
     * @param scanInterval in milliseconds
     * @return config
     */
    public ClusterServersConfig setScanInterval(int scanInterval) {
        this.scanInterval = scanInterval;
        return this;
    }

}
