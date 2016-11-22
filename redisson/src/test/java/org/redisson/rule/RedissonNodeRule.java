package org.redisson.rule;

import java.util.Collections;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.redisson.RedisRunner;
import org.redisson.RedissonNode;
import org.redisson.RedissonRuntimeEnvironment;
import org.redisson.config.Config;
import org.redisson.config.RedissonNodeConfig;

/**
 * @author Philipp Marx
 */
public class RedissonNodeRule extends ExternalResource {

    private Description description;
    private RedissonNode node;

    @Override
    public Statement apply(Statement base, Description description) {
        this.description = description;
        return super.apply(base, description);
    }
    
    public final Config getSharedConfig() {
        Config config = new Config();
        config.setEventLoopGroupThreadPrefix(description.getMethodName());
        config.useSingleServer().setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort())
        .setConnectTimeout(1000000).setTimeout(1000000);
        
        return config;
    }

    @Override
    protected void after() {
        showDownNodeIfNecessary();
    }
    
    @Override
    protected void before() throws Throwable {
        startNodeIfNecessary();
    }
    
    private void showDownNodeIfNecessary() {
        if (!RedissonRuntimeEnvironment.isTravis) {
            node.shutdown();
        }        
    }

    private void startNodeIfNecessary() {
        if (!RedissonRuntimeEnvironment.isTravis) {
            RedissonNodeConfig nodeConfig = new RedissonNodeConfig(getSharedConfig());
            nodeConfig.setExecutorServiceWorkers(Collections.singletonMap("test", 1));
            node = RedissonNode.create(nodeConfig);
            node.start();
        }
    }
}
