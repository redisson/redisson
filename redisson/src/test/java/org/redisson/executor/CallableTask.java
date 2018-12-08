package org.redisson.executor;

import java.util.concurrent.Callable;

public class CallableTask implements Callable<String> {

    public static final String RESULT = "callable";
    
    @Override
    public String call() throws Exception {
        return RESULT;
    }
    

}
