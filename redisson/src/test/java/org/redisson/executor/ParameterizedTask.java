package org.redisson.executor;

import java.io.Serializable;
import java.util.concurrent.Callable;

public class ParameterizedTask implements Callable<String>, Serializable {

    private String param;
    
    public ParameterizedTask() {
    }
    
    public ParameterizedTask(String param) {
        super();
        this.param = param;
    }

    @Override
    public String call() throws Exception {
        return param;
    }
    

}
