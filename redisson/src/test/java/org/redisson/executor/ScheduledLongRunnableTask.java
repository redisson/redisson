package org.redisson.executor;

import java.io.Serializable;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class ScheduledLongRunnableTask implements Runnable, Serializable {

    @RInject
    private RedissonClient redisson;
    private String objectName;
    
    public ScheduledLongRunnableTask() {
    }
    
    public ScheduledLongRunnableTask(String objectName) {
        super();
        this.objectName = objectName;
    }

    @Override
    public void run() {
        for (long i = 0; i < Long.MAX_VALUE; i++) {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("interrupted " + i);
                redisson.getBucket(objectName).set(i);
                return;
            }
        }
    }
    
}
