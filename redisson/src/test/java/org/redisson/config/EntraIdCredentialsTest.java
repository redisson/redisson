package org.redisson.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import redis.clients.authentication.core.SimpleToken;
import redis.clients.authentication.core.TokenListener;
import redis.clients.authentication.core.TokenManager;
import redis.clients.authentication.core.TokenManagerConfig;

import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class EntraIdCredentialsTest {

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

}
