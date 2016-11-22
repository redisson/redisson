package org.redisson.rule;

import org.junit.rules.ExternalResource;
import org.redisson.RedisRunner;
import org.redisson.RedissonRuntimeEnvironment;

/**
 * @author Philipp Marx
 */
public class RedisServerRule extends ExternalResource {

    private static boolean running = false;

    private boolean startedByMe;
    
    @Override
    protected void after() {
        try {
            shutdownServerIfNecessary();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void before() throws Throwable {
        startServerIfNecessary();
    }

    private void shutdownServerIfNecessary() throws Exception {
        if (!RedissonRuntimeEnvironment.isTravis && startedByMe) {
            RedisRunner.shutDownDefaultRedisServerInstance();
            running = false;
        }
    }

    private void startServerIfNecessary() throws Exception {
        if (!RedissonRuntimeEnvironment.isTravis && !running) {
            RedisRunner.startDefaultRedisServerInstance();
            running = true;
            startedByMe = true;
        }
    }
}
