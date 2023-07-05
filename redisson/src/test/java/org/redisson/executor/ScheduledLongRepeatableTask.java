package org.redisson.executor;

import java.io.Serializable;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class ScheduledLongRepeatableTask implements Runnable, Serializable {

    @RInject
    private RedissonClient redisson;
    
    private String counterName;
    private String objectName;
    
    public ScheduledLongRepeatableTask() {
    }
    
    public ScheduledLongRepeatableTask(String counterName, String objectName) {
        super();
        this.counterName = counterName;
        this.objectName = objectName;
    }

    @Override
    public void run() {
        if (redisson.getAtomicLong(counterName).incrementAndGet() == 3) {
            for (long i = 0; i < Long.MAX_VALUE; i++) {
                // Clear interrupted flag in order to access database
                if (Thread.interrupted()) {
                    System.out.println("interrupted " + i);
                    redisson.getAtomicLong(objectName).set(i);

                    // Set interrupted flag back
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

}
