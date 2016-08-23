package org.redisson.executor;

import java.util.concurrent.Callable;

public class ParameterizedTask implements Callable<String> {

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
