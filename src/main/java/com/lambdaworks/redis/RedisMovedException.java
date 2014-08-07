package com.lambdaworks.redis;

public class RedisMovedException extends RedisException {

    private static final long serialVersionUID = -6969734163155547631L;

    private int slot;

    public RedisMovedException(int slot) {
        this.slot = slot;
    }

    public int getSlot() {
        return slot;
    }

}
