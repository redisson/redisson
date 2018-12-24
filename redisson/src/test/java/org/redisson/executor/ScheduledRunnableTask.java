package org.redisson.executor;

import java.io.Serializable;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class ScheduledRunnableTask implements Runnable, Serializable {

    @RInject
    private RedissonClient redisson;
    private String objectName;
    
    public ScheduledRunnableTask() {
    }
    
    public ScheduledRunnableTask(String objectName) {
        super();
        this.objectName = objectName;
    }

    @Override
    public void run() {
        redisson.getAtomicLong(objectName).incrementAndGet();
    }

}
