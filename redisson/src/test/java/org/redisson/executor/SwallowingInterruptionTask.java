package org.redisson.executor;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class SwallowingInterruptionTask implements Runnable, Serializable {
    @RInject
    private RedissonClient redisson;
    private String objectName;
    private String cancelObjectName;

    public SwallowingInterruptionTask() {
        super();
    }

    public SwallowingInterruptionTask(String objectName, String cancelObjectName) {
        super();
        this.objectName = objectName;
        this.cancelObjectName = cancelObjectName;
    }

    @Override
    public void run() {
        Logger logger = LoggerFactory.getLogger(getClass());
        logger.info("start");
        redisson.getAtomicLong(objectName).incrementAndGet();
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            logger.info("interrupted");
            redisson.getAtomicLong(cancelObjectName).incrementAndGet();
            return;
        }
        logger.info("end");
    }
}
