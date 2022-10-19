/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.cluster;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.redisson.misc.RedisURI;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterNodeInfo {

    public enum Flag {
        NOFLAGS("noflags"), SLAVE("slave"), MASTER("master"), MYSELF("myself"),
        FAIL("fail"), EVENTUAL_FAIL("fail?"), HANDSHAKE("handshake"), NOADDR("noaddr");

        private final String value;

        Flag(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    };

    private final String nodeInfo;
    
    private String nodeId;
    private RedisURI address;
    private final Set<Flag> flags = EnumSet.noneOf(Flag.class);
    private String slaveOf;

    private final Set<ClusterSlotRange> slotRanges = new HashSet<ClusterSlotRange>();

    public ClusterNodeInfo(String nodeInfo) {
        this.nodeInfo = nodeInfo;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public RedisURI getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = new RedisURI(address);
    }

    public void addSlotRange(ClusterSlotRange range) {
        slotRanges.add(range);
    }
    public Set<ClusterSlotRange> getSlotRanges() {
        return slotRanges;
    }

    public boolean containsFlag(Flag flag) {
        return flags.contains(flag);
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

    public String getNodeInfo() {
        return nodeInfo;
    }
    
    @Override
    public String toString() {
        return "ClusterNodeInfo [nodeId=" + nodeId + ", address=" + address + ", flags=" + flags
                + ", slaveOf=" + slaveOf + ", slotRanges=" + slotRanges + "]";
    }

}
