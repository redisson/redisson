package org.redisson.executor;

import java.util.concurrent.Callable;

public class ScheduledCallableTask implements Callable<Long> {

    @Override
    public Long call() throws Exception {
        return 100L;
    }

}
