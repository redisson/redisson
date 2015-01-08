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

import java.util.ArrayList;
import java.util.List;

public class ClusterPartition {

    private int startSlot;
    private int endSlot;
    private boolean masterFail;
    private String masterAddress;
    private List<String> slaveAddresses = new ArrayList<String>();

    public void setMasterFail(boolean masterFail) {
        this.masterFail = masterFail;
    }
    public boolean isMasterFail() {
        return masterFail;
    }

    public int getStartSlot() {
        return startSlot;
    }
    public void setStartSlot(int startSlot) {
        this.startSlot = startSlot;
    }

    public int getEndSlot() {
        return endSlot;
    }
    public void setEndSlot(int endSlot) {
        this.endSlot = endSlot;
    }

    public String getMasterAddress() {
        return masterAddress;
    }
    public void setMasterAddress(String masterAddress) {
        this.masterAddress = masterAddress;
    }

    public void addSlaveAddress(String address) {
        slaveAddresses.add(address);
    }
    public List<String> getSlaveAddresses() {
        return slaveAddresses;
    }

}
