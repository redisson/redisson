package org.redisson.client;

public class RedisException extends RuntimeException {

    private static final long serialVersionUID = 3389820652701696154L;

    public RedisException() {
    }

    public RedisException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisException(String message) {
        super(message);
    }

}
