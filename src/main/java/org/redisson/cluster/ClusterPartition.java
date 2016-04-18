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
package org.redisson.cluster;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.redisson.misc.URIBuilder;

public class ClusterPartition {

    private final String nodeId;
    private boolean masterFail;
    private URI masterAddress;
    private final Set<URI> slaveAddresses = new HashSet<URI>();
    private final Set<URI> failedSlaves = new HashSet<URI>();
    private final Set<ClusterSlotRange> slotRanges = new HashSet<ClusterSlotRange>();

    public ClusterPartition(String nodeId) {
        super();
        this.nodeId = nodeId;
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

    public void addSlotRanges(Set<ClusterSlotRange> ranges) {
        slotRanges.addAll(ranges);
    }
    public void removeSlotRanges(Set<ClusterSlotRange> ranges) {
        slotRanges.removeAll(ranges);
    }
    public Set<ClusterSlotRange> getSlotRanges() {
        return slotRanges;
    }

    public InetSocketAddress getMasterAddr() {
        return new InetSocketAddress(masterAddress.getHost(), masterAddress.getPort());
    }

    public URI getMasterAddress() {
        return masterAddress;
    }
    public void setMasterAddress(String masterAddress) {
        setMasterAddress(URIBuilder.create(masterAddress));
    }
    public void setMasterAddress(URI masterAddress) {
        this.masterAddress = masterAddress;
    }

    public void addFailedSlaveAddress(URI address) {
        failedSlaves.add(address);
    }
    public Set<URI> getFailedSlaveAddresses() {
        return Collections.unmodifiableSet(failedSlaves);
    }
    public void removeFailedSlaveAddress(URI uri) {
        failedSlaves.remove(uri);
    }

    public void addSlaveAddress(URI address) {
        slaveAddresses.add(address);
    }
    public Set<URI> getSlaveAddresses() {
        return Collections.unmodifiableSet(slaveAddresses);
    }
    public void removeSlaveAddress(URI uri) {
        slaveAddresses.remove(uri);
        failedSlaves.remove(uri);
    }

    @Override
    public String toString() {
        return "ClusterPartition [nodeId=" + nodeId + ", masterFail=" + masterFail + ", masterAddress=" + masterAddress
                + ", slaveAddresses=" + slaveAddresses + ", failedSlaves=" + failedSlaves + ", slotRanges=" + slotRanges
                + "]";
    }
    
}
