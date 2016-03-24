package org.redisson;

public class RedissonShutdownException extends RuntimeException {

    private static final long serialVersionUID = -2694051226420789395L;

    public RedissonShutdownException(String message) {
        super(message);
    }
    
}
