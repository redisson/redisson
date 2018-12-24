package org.redisson.executor;

import java.io.Serializable;
import java.util.concurrent.Callable;

public class ScheduledCallableTask implements Callable<Long>, Serializable {

    @Override
    public Long call() throws Exception {
        return 100L;
    }

}
