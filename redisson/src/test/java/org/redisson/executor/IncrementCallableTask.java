package org.redisson.executor;

import java.io.Serializable;
import java.util.concurrent.Callable;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class IncrementCallableTask implements Callable<String>, Serializable {

    private String counterName;
    
    @RInject
    private RedissonClient redisson;

    public IncrementCallableTask() {
    }
    
    public IncrementCallableTask(String counterName) {
        super();
        this.counterName = counterName;
    }

    @Override
    public String call() throws Exception {
        redisson.getAtomicLong(counterName).incrementAndGet();
        return "1234";
    }

}
