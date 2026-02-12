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
package org.redisson.cluster;

import org.redisson.misc.RedisURI;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Set<RedisURI> slaveAddresses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<RedisURI> failedSlaves = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private BitSet slots;
    private Set<ClusterSlotRange> slotRanges = Collections.emptySet();

    private ClusterPartition parent;

    private int references;

    private long time = System.currentTimeMillis();
    
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

    public void updateSlotRanges(Set<ClusterSlotRange> ranges, BitSet slots) {
        this.slotRanges = ranges;
        this.slots = slots;
    }

    public void setSlotRanges(Set<ClusterSlotRange> ranges) {
        slots = new BitSet(MAX_SLOT);
        for (ClusterSlotRange clusterSlotRange : ranges) {
            slots.set(clusterSlotRange.getStartSlot(), clusterSlotRange.getEndSlot() + 1);
        }
        slotRanges = ranges;
    }

    public Set<ClusterSlotRange> getSlotRanges() {
        return Collections.unmodifiableSet(slotRanges);
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

    public void incReference() {
        references++;
    }
    public int decReference() {
        return --references;
    }

    public long getTime() {
        return time;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterPartition that = (ClusterPartition) o;
        return Objects.equals(nodeId, that.nodeId);
    }

    @Override
    public String toString() {
        return "ClusterPartition [nodeId=" + nodeId + ", masterFail=" + masterFail + ", masterAddress=" + masterAddress
                + ", slaveAddresses=" + slaveAddresses + ", failedSlaves=" + failedSlaves + ", slotRanges=" + slotRanges
                + "]";
    }
    
}
