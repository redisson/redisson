package org.redisson.client.handler;

import java.util.List;

public class State {

    private int index;
    private Object decoderState;

    private long size;
    private List<Object> respParts;

    public State() {
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

    public <T> T getDecoderState() {
        return (T)decoderState;
    }
    public void setDecoderState(Object decoderState) {
        this.decoderState = decoderState;
    }

}
