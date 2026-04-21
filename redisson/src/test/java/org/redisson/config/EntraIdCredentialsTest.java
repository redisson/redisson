package org.redisson.config;

import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.handler.BaseConnectionHandler;
import org.testcontainers.containers.GenericContainer;
import redis.clients.authentication.core.SimpleToken;
import redis.clients.authentication.core.TokenListener;
import redis.clients.authentication.core.TokenManager;
import redis.clients.authentication.core.TokenManagerConfig;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class EntraIdCredentialsTest extends RedisDockerTest {

    @Test
    public void test() throws InterruptedException, ExecutionException {
        TokenManagerConfig cfg = new TokenManagerConfig(0,
                0, 0, new TokenManagerConfig.RetryPolicy(0, 0));
        AtomicReference<TokenListener> ref = new AtomicReference<>();
        TokenManager tm = new TokenManager(null, cfg) {
            @Override
            public void start(TokenListener listener, boolean blockForInitialToken) {
                ref.set(listener);
            }
        };

        EntraIdCredentialsResolver resolver = new EntraIdCredentialsResolver(tm);

        SimpleToken st = new SimpleToken("user", "password", System.currentTimeMillis() + 1000, 0, new HashMap<>());
        ref.get().onTokenRenewed(st);

        Credentials v = resolver.resolve(null).toCompletableFuture().join();
        Assertions.assertEquals(v.getPassword(), st.getValue());
        Assertions.assertEquals(v.getUsername(), st.getUser());

        try {
            resolver.nextRenewal().toCompletableFuture().get(1, TimeUnit.SECONDS);
            Assertions.fail();
        } catch (TimeoutException e) {
            // skip
        }

        SimpleToken st2 = new SimpleToken("user1", "password2", System.currentTimeMillis() + 1000, 0, new HashMap<>());
        ref.get().onTokenRenewed(st2);

        Credentials v2 = resolver.resolve(null).toCompletableFuture().join();
        Assertions.assertEquals(v2.getPassword(), st2.getValue());
        Assertions.assertEquals(v2.getUsername(), st2.getUser());
    }

    @Test
    public void testReauthOnSubscribedConnection() throws Exception {
        GenericContainer<?> redis = createRedis("--requirepass", "1234");
        redis.start();

        TokenManagerConfig cfg = new TokenManagerConfig(0, 0, 0,
                new TokenManagerConfig.RetryPolicy(0, 0));
        AtomicReference<TokenListener> listenerRef = new AtomicReference<>();
        TokenManager tm = new TokenManager(null, cfg) {
            @Override
            public void start(TokenListener listener, boolean blockForInitialToken) {
                listenerRef.set(listener);
            }
        };

        EntraIdCredentialsResolver resolver = new EntraIdCredentialsResolver(tm);
        listenerRef.get().onTokenRenewed(new SimpleToken("default", "1234",
                System.currentTimeMillis() + 60_000, 0, new HashMap<>()));

        Config config = new Config();
        config.setProtocol(Protocol.RESP2);
        config.setCredentialsResolver(resolver);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort())
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(1);

        LogCaptor logCaptor = LogCaptor.forClass(BaseConnectionHandler.class);
        RedissonClient redisson = Redisson.create(config);
        try {
            RTopic topic = redisson.getTopic("entra-id-reauth-repro");
            CountDownLatch beforeRenewal = new CountDownLatch(1);
            CountDownLatch afterRenewal = new CountDownLatch(1);

            topic.addListener(String.class, (ch, m) -> {
                if ("before".equals(m)) {
                    beforeRenewal.countDown();
                }
                if ("after".equals(m)) {
                    afterRenewal.countDown();
                }
            });
            topic.publish("before");

            assertThat(beforeRenewal.await(5, TimeUnit.SECONDS))
                    .isTrue();

            listenerRef.get().onTokenRenewed(new SimpleToken("default", "1234",
                    System.currentTimeMillis() + 60_000, 0, new HashMap<>()));

            Thread.sleep(3000);

            assertThat(logCaptor.getErrorLogs())
                    .noneMatch(msg -> msg.contains("Unable to send AUTH command"));

            topic.publish("after");
            assertThat(afterRenewal.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            logCaptor.close();
            redisson.shutdown();
            redis.stop();
        }
    }

}