package org.redisson.executor;

import java.io.Serializable;
import java.util.concurrent.Callable;

public class CallableTask implements Callable<String>, Serializable {

    public static final String RESULT = "callable";
    
    @Override
    public String call() throws Exception {
        return RESULT;
    }
    

}
