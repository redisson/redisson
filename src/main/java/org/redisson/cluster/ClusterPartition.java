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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.redisson.misc.URIBuilder;

public class ClusterPartition {

    private boolean masterFail;
    private URI masterAddress;
    private List<URI> slaveAddresses = new ArrayList<URI>();
    private final Set<ClusterSlotRange> slotRanges = new HashSet<ClusterSlotRange>();

    public void setMasterFail(boolean masterFail) {
        this.masterFail = masterFail;
    }
    public boolean isMasterFail() {
        return masterFail;
    }

    public void addSlotRanges(List<ClusterSlotRange> ranges) {
        slotRanges.addAll(ranges);
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

    public void addSlaveAddress(URI address) {
        slaveAddresses.add(address);
    }
    public List<URI> getSlaveAddresses() {
        return slaveAddresses;
    }

}
