package org.redisson.connection;

import java.util.ArrayList;
import java.util.List;

public class ClusterPartition {

    private int startSlot;
    private boolean masterFail;
    private String masterAddress;
    private List<String> slaveAddresses = new ArrayList<String>();

    public void setMasterFail(boolean masterFail) {
        this.masterFail = masterFail;
    }
    public boolean isMasterFail() {
        return masterFail;
    }

    public void setStartSlot(int startSlot) {
        this.startSlot = startSlot;
    }
    public int getStartSlot() {
        return startSlot;
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
