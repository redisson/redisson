package com.lambdaworks.redis;

public class RedisConnectionException extends RedisException {

    private static final long serialVersionUID = 4007817232147176510L;

    public RedisConnectionException(String msg, Throwable e) {
        super(msg, e);
    }

}
