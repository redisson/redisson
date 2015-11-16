package org.redisson.client;

public class RedisLoadingException extends RedisException {

    private static final long serialVersionUID = -2565335188503354660L;

    public RedisLoadingException(String message) {
        super(message);
    }

}
