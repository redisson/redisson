package org.redisson.executor;

import java.io.Serializable;
import java.util.concurrent.Callable;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class DelayedTask implements Callable<Object>, Serializable {

    @RInject
    private RedissonClient redisson;
    
    private long delay;
    private String counterName;

    public DelayedTask() {
        // TODO Auto-generated constructor stub
    }
    
    public DelayedTask(long delay, String counter) {
        super();
        this.delay = delay;
        this.counterName = counter;
    }

    @Override
    public Object call() throws Exception {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        RAtomicLong counter = redisson.getAtomicLong(counterName);
        counter.incrementAndGet();
        return null;
    }
    
}
