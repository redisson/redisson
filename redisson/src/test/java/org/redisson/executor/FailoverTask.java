package org.redisson.executor;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

public class FailoverTask implements Runnable {

    @RInject
    private RedissonClient redisson;
    private String objectName;
    
    public FailoverTask() {
    }
    
    public FailoverTask(String objectName) {
        super();
        this.objectName = objectName;
    }

    @Override
    public void run() {
        for (long i = 0; i < 20_000_000_000L; i++) {
        }
        redisson.getBucket(objectName).set(true);
    }
    
}
