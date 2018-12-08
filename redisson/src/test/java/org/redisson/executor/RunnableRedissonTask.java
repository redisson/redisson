package org.redisson.executor;

import java.io.Serializable;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class RunnableRedissonTask implements Runnable, Serializable {

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
