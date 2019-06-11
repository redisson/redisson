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
public class ClusterServersConfig extends BaseMasterSlaveServersConfig<ClusterServersConfig> {

    private Map<String, String> natMap = Collections.emptyMap();
    
    /**
     * Redis cluster node urls list
     */
    private List<String> nodeAddresses = new ArrayList<>();

    /**
     * Redis cluster scan interval in milliseconds
     */
    private int scanInterval = 5000;

    public ClusterServersConfig() {
    }

    ClusterServersConfig(ClusterServersConfig config) {
        super(config);
        setNodeAddresses(config.getNodeAddresses());
        setScanInterval(config.getScanInterval());
        setNatMap(new HashMap<>(config.getNatMap()));
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
    void setNodeAddresses(List<String> nodeAddresses) {
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

    public Map<String, String> getNatMap() {
        return natMap;
    }
    /**
     * Defines NAT mapping. Address as a map key is replaced with mapped address as value.
     * 
     * @param natMap - nat mapping
     * @return config
     */
    public ClusterServersConfig setNatMap(Map<String, String> natMap) {
        this.natMap = natMap;
        return this;
    }
    

}
