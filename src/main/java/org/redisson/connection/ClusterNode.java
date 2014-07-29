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
package org.redisson.connection;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ClusterNode {

    public enum Flag {SLAVE, MASTER, MYSELF};
    
    private String nodeId;
    private URI address;
    private List<Flag> flags = new ArrayList<Flag>();
    private String slaveOf;
    
    public String getNodeId() {
        return nodeId;
    }
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public URI getAddress() {
        return address;
    }
    public void setAddress(URI address) {
        this.address = address;
    }
    
    public List<Flag> getFlags() {
        return flags;
    }
    public void addFlag(Flag flag) {
        this.flags.add(flag);
    }

    public String getSlaveOf() {
        return slaveOf;
    }
    public void setSlaveOf(String slaveOf) {
        this.slaveOf = slaveOf;
    }
    
}
