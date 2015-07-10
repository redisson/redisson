package org.redisson.client;

public class RedisConnectionException extends RedisException {

    private static final long serialVersionUID = -4756928186967834601L;

    public RedisConnectionException(String msg) {
        super(msg);
    }

    public RedisConnectionException(String msg, Throwable e) {
        super(msg, e);
    }

}
