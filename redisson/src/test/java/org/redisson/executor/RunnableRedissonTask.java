package org.redisson.executor;

import java.io.Serializable;

import org.redisson.RedissonClient;
import org.redisson.api.annotation.RInject;

public class RunnableRedissonTask implements Runnable, Serializable {

    private static final long serialVersionUID = 4165626916136893351L;

    @RInject
    private RedissonClient redissonClient;
    
    @Override
    public void run() {
        redissonClient.getAtomicLong("runnableCounter").addAndGet(100);
    }

}
