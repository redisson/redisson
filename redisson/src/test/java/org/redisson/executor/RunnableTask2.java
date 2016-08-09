package org.redisson.executor;

import java.io.Serializable;

public class RunnableTask2 implements Runnable, Serializable {

    private static final long serialVersionUID = 2105094575950438867L;
    
    private String s = "1234";
    
    @Override
    public void run() {
        System.out.println("ioio");
    }

}
