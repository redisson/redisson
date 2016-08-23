package org.redisson.executor;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class RunnableRedissonTask implements Runnable {

    @RInject
    private RedissonClient redissonClient;
    
    private String counterName;
    
    public RunnableRedissonTask() {
    }
    
    public RunnableRedissonTask(String counterName) {
        this.counterName = counterName;
    }

    @Override
    public void run() {
        redissonClient.getAtomicLong(counterName).addAndGet(100);
    }

}
