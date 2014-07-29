package com.lambdaworks.redis;

public class RedisConnectionClosedException extends RedisException {

    private static final long serialVersionUID = 1895201562761894967L;

    public RedisConnectionClosedException(String msg) {
        super(msg);
    }

}
