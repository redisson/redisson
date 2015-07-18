package org.redisson.client.handler;

import java.util.List;

public class DecoderState {

    private int index;

    private long size;
    private List<Object> respParts;

    public DecoderState() {
        super();
    }

    public void setSize(long size) {
        this.size = size;
    }
    public long getSize() {
        return size;
    }

    public void setRespParts(List<Object> respParts) {
        this.respParts = respParts;
    }
    public List<Object> getRespParts() {
        return respParts;
    }

    public void setIndex(int index) {
        this.index = index;
    }
    public int getIndex() {
        return index;
    }


}
