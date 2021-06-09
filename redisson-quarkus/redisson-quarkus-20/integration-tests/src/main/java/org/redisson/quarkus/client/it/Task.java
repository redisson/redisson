package org.redisson.quarkus.client.it;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * @author Nikita Koksharov
 */
public class Task implements Callable<String>, Serializable {

    @Override
    public String call() throws Exception {
        return "hello";
    }
}
