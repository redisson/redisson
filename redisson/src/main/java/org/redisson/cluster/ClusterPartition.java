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

import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.redisson.misc.RedisURI;

import static org.redisson.connection.MasterSlaveConnectionManager.MAX_SLOT;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterPartition {

    public enum Type {MASTER, SLAVE}
    
    private Type type = Type.MASTER;
    
    private final String nodeId;
    private boolean masterFail;
    private RedisURI masterAddress;
    private final Set<RedisURI> slaveAddresses = new HashSet<>();
    private final Set<RedisURI> failedSlaves = new HashSet<>();
    
    private final BitSet slots = new BitSet(MAX_SLOT);
    private final Set<ClusterSlotRange> slotRanges = new HashSet<ClusterSlotRange>();

    private ClusterPartition parent;
    
    public ClusterPartition(String nodeId) {
        super();
        this.nodeId = nodeId;
    }
    
    public ClusterPartition getParent() {
        return parent;
    }

    public void setParent(ClusterPartition parent) {
        this.parent = parent;
    }

    public void setType(Type type) {
        this.type = type;
    }
    
    public Type getType() {
        return type;
    }
    
    public String getNodeId() {
        return nodeId;
    }

    public void setMasterFail(boolean masterFail) {
        this.masterFail = masterFail;
    }
    public boolean isMasterFail() {
        return masterFail;
    }

    public void addSlots(BitSet slots) {
        this.slots.or(slots);
    }

    public void removeSlots(BitSet slots) {
        this.slots.andNot(slots);
    }

    public void addSlotRanges(Set<ClusterSlotRange> ranges) {
        for (ClusterSlotRange clusterSlotRange : ranges) {
            slots.set(clusterSlotRange.getStartSlot(), clusterSlotRange.getEndSlot() + 1);
        }
        slotRanges.addAll(ranges);
    }
    public void removeSlotRanges(Set<ClusterSlotRange> ranges) {
        for (ClusterSlotRange clusterSlotRange : ranges) {
            slots.clear(clusterSlotRange.getStartSlot(), clusterSlotRange.getEndSlot() + 1);
        }
        slotRanges.removeAll(ranges);
    }
    public Set<ClusterSlotRange> getSlotRanges() {
        return slotRanges;
    }

    public void clear() {
        slotRanges.clear();
        this.slots.clear();
    }

    public Iterable<Integer> getSlots() {
        return slots.stream()::iterator;
    }
    
    public BitSet slots() {
        return slots;
    }
    
    public BitSet copySlots() {
        return (BitSet) slots.clone();
    }
    
    public boolean hasSlot(int slot) {
        return slots.get(slot);
    }
    
    public int getSlotsAmount() {
        return slots.cardinality();
    }

    public RedisURI getMasterAddress() {
        return masterAddress;
    }
    public void setMasterAddress(RedisURI masterAddress) {
        this.masterAddress = masterAddress;
    }

    public void addFailedSlaveAddress(RedisURI address) {
        failedSlaves.add(address);
    }
    public Set<RedisURI> getFailedSlaveAddresses() {
        return Collections.unmodifiableSet(failedSlaves);
    }
    public void removeFailedSlaveAddress(RedisURI uri) {
        failedSlaves.remove(uri);
    }

    public void addSlaveAddress(RedisURI address) {
        slaveAddresses.add(address);
    }
    public Set<RedisURI> getSlaveAddresses() {
        return Collections.unmodifiableSet(slaveAddresses);
    }
    public void removeSlaveAddress(RedisURI uri) {
        slaveAddresses.remove(uri);
        failedSlaves.remove(uri);
    }
    
    @Override
    @SuppressWarnings("AvoidInlineConditionals")
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((nodeId == null) ? 0 : nodeId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClusterPartition other = (ClusterPartition) obj;
        if (nodeId == null) {
            if (other.nodeId != null)
                return false;
        } else if (!nodeId.equals(other.nodeId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ClusterPartition [nodeId=" + nodeId + ", masterFail=" + masterFail + ", masterAddress=" + masterAddress
                + ", slaveAddresses=" + slaveAddresses + ", failedSlaves=" + failedSlaves + ", slotRanges=" + slotRanges
                + "]";
    }
    
}
