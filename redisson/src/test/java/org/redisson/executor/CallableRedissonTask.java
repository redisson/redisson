package org.redisson.executor;

import java.io.Serializable;
import java.util.concurrent.Callable;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class CallableRedissonTask implements Callable<Long>, Serializable {

    private static final long serialVersionUID = 8875732248655428049L;

    private Long incrementValue;
    
    @RInject
    private RedissonClient redissonClient;
    
    public CallableRedissonTask() {
    }
    
    public CallableRedissonTask(Long incrementValue) {
        this.incrementValue = incrementValue;
    }

    @Override
    public Long call() throws Exception {
        return redissonClient.getAtomicLong("counter").addAndGet(incrementValue);
    }

}
