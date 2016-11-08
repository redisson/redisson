package org.redisson.rule;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.redisson.RedisRunner;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;

import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * @author Philipp Marx
 */
public class RedissonRule extends ExternalResource {

    private static ExecutorService executor = new ThreadPoolExecutor(2, 20, 10L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(20), new DefaultThreadFactory("redisson-test-shutdown"),
            new CallerRunsPolicy());

    private List<RedissonClient> clients;
    private List<RedissonReactiveClient> clientsReactive;
    private RedissonClient sharedClient;
    private RedissonReactiveClient sharedReactiveClient;
    private String testName;

    @Override
    public Statement apply(Statement base, Description description) {
        setTestName(description.getMethodName());
        return super.apply(base, description);
    }

    public void cleanup() {
        flushDB();

        List<Future<?>> futures = new LinkedList<>();

        for (RedissonClient client : clients) {
            RedissonClient save = client;
            futures.add(executor.submit(() -> shutDownQuietly(save)));
        }
        clients = new LinkedList<>();

        for (RedissonReactiveClient client : clientsReactive) {
            RedissonReactiveClient save = client;
            futures.add(executor.submit(() -> shutDownQuietly(save)));
        }
        clientsReactive = new LinkedList<>();

        futures.stream().forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });
    }

    public RedissonClient createClient() {
        return createClient(getSharedConfig());
    }

    public RedissonClient createClient(Config config) {
        String id = UUID.randomUUID().toString();
        config.setExecutorThreadPrefix(
                testName != null ? "redisson-test-executor-" + testName : "redisson-test-executor-" + id);
        config.setEventLoopGroupThreadPrefix(
                testName != null ? "redisson-test-eventloop-" + testName : "redisson-test-eventloop-" + id);
        RedissonClient client = Redisson.create(config);
        clients.add(client);

        return client;
    }

    public RedissonClient getSharedClient() {
        if (sharedClient == null || sharedClient.isShutdown() || sharedClient.isShuttingDown()) {
            sharedClient = Redisson.create(getSharedConfig());
        }

        return sharedClient;
    }

    public final Config getSharedConfig() {
        Config config = new Config();
        config.setExecutorThreadPrefix("redisson-test-executor-default");
        config.setEventLoopGroupThreadPrefix("redisson-test-eventloop-default");
        config.useSingleServer().setAddress(RedisRunner.getDefaultRedisServerBindAddressAndPort())
                .setConnectTimeout(1000000).setTimeout(1000000);

        return config;
    }

    public RedissonReactiveClient createReactiveClient() {
        return createReactiveClient(getSharedConfig());
    }

    public RedissonReactiveClient createReactiveClient(Config config) {
        String id = UUID.randomUUID().toString();
        config.setExecutorThreadPrefix(
                testName != null ? "redisson-test-executor-" + testName : "redisson-test-executor-" + id);
        config.setEventLoopGroupThreadPrefix(
                testName != null ? "redisson-test-eventloop-" + testName : "redisson-test-eventloop-" + id);
        RedissonReactiveClient client = Redisson.createReactive(getSharedConfig());
        clientsReactive.add(client);

        return client;
    }

    public RedissonReactiveClient getSharedReactiveClient() {
        if (sharedReactiveClient == null || sharedReactiveClient.isShutdown()
                || sharedReactiveClient.isShuttingDown()) {
            sharedReactiveClient = Redisson.createReactive(getSharedConfig());
        }

        return sharedReactiveClient;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    @Override
    protected void after() {
        try {
            shutDownClients();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void before() throws Throwable {
        clients = new LinkedList<>();
        clientsReactive = new LinkedList<>();
    }

    private void flushDB() {
        if (sharedClient != null && !sharedClient.isShutdown() && !sharedClient.isShuttingDown()) {
            sharedClient.getKeys().flushall();
        } else if (sharedReactiveClient != null && !sharedReactiveClient.isShutdown() && !sharedReactiveClient.isShuttingDown()) {
            sharedReactiveClient.getKeys().flushall();
        } else {
            getSharedClient().getKeys().flushall();
        }
    }

    private void shutDownClients() {
        List<Future<?>> futures = new LinkedList<>();

        if (sharedClient != null) {
            RedissonClient save = sharedClient;
            futures.add(executor.submit(() -> shutDownQuietly(save)));
            sharedClient = null;
        }

        if (sharedReactiveClient != null) {
            RedissonReactiveClient save = sharedReactiveClient;
            futures.add(executor.submit(() -> shutDownQuietly(save)));
            sharedReactiveClient = null;
        }

        for (RedissonClient client : clients) {
            RedissonClient save = client;
            futures.add(executor.submit(() -> shutDownQuietly(save)));
        }
        clients = null;

        for (RedissonReactiveClient client : clientsReactive) {
            RedissonReactiveClient save = client;
            futures.add(executor.submit(() -> shutDownQuietly(save)));
        }
        clientsReactive = null;

        futures.stream().forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });
    }

    private void shutDownQuietly(RedissonClient client) {
        try {
            if (!client.isShutdown() && !client.isShuttingDown()) {
                client.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void shutDownQuietly(RedissonReactiveClient client) {
        try {
            if (!client.isShutdown() && !client.isShuttingDown()) {
                client.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
