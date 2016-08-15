package org.redisson.executor;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class RunnableRedissonTask implements Runnable {

    @RInject
    private RedissonClient redissonClient;
    
    @Override
    public void run() {
        redissonClient.getAtomicLong("runnableCounter").addAndGet(100);
    }

}
