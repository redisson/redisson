package org.redisson.client;

import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that server-side rejection and bootstrap errors
 * surface as the correct typed exception classes end-to-end.
 * <p>
 * Each test spins up a real Redis container so the full pipeline is exercised:
 * server sends a RESP error frame → CommandDecoder routes it → caller receives
 * the typed exception.
 */
public class RedisErrorBubblingIntegrationTest extends RedisDockerTest {

    /**
     * When Redis is configured with requirepass and the client supplies a wrong password,
     * AUTH fails during the bootstrap pipeline. The exception must:
     * <ul>
     *   <li>Be a {@link RedisConnectionBootstrapException}</li>
     *   <li>Have {@code phase == AUTH}</li>
     *   <li>Be caused by {@link RedisWrongPasswordException}</li>
     * </ul>
     */
    @Test
    public void wrongPassword_bootstrapFails_withAuthPhase() {
        GenericContainer<?> redis = createContainer("--requirepass", "correctpass");
        redis.start();

        try {
            Config config = createConfig(redis);
            config.useSingleServer().setPassword("wrongpassword");

            RedisConnectionException thrown = assertThrows(RedisConnectionException.class,
                    () -> Redisson.create(config));

            Throwable bootstrapEx = findCause(thrown, RedisConnectionBootstrapException.class);
            assertNotNull(bootstrapEx,
                    "Expected RedisConnectionBootstrapException in chain, got: " + thrown);

            RedisConnectionBootstrapException bex = (RedisConnectionBootstrapException) bootstrapEx;
            assertEquals(RedisConnectionBootstrapException.Phase.AUTH, bex.getPhase(),
                    "Wrong password must fail at AUTH phase");

            Throwable cause = bex.getCause();
            assertTrue(cause instanceof RedisWrongPasswordException || cause instanceof RedisAuthRequiredException,
                    "Cause should be RedisWrongPasswordException or RedisAuthRequiredException, was: "
                            + (cause != null ? cause.getClass().getSimpleName() : "null"));
        } finally {
            redis.stop();
        }
    }

    /**
     * When Redis hits its maxclients limit, a new connection receives
     * {@code "ERR max number of clients reached"} as an unsolicited pre-command
     * server message before AUTH or any other bootstrap command can complete.
     * <p>
     * The error is stored on the channel via {@link CommandDecoder#UNSOLICITED_ERROR_KEY}
     * and surfaced as a {@link RedisConnectionBootstrapException} whose cause is a
     * {@link RedisServerRejectionException} with {@code reason == MAX_CLIENTS}.
     * <p>
     * Strategy: start Redis with a low {@code --maxclients}. Open connections one by one
     * until one is rejected. Redis reserves a small number of extra slots for admin
     * connections, so we open up to {@code maxclients + 5} before failing the test.
     */
    @Test
    public void maxClientsReached_bootstrapExceptionContainsServerRejection() throws Exception {
        final int maxClients = 4;
        GenericContainer<?> redis = createContainer("--maxclients", String.valueOf(maxClients));
        redis.start();

        int port = redis.getFirstMappedPort();
        java.util.List<RedisClient> openedClients = new java.util.ArrayList<>();
        java.util.List<RedisConnection> openedConns = new java.util.ArrayList<>();

        try {
            Exception rejectionEx = null;
            // Keep opening connections until we hit the limit (at most maxclients + 5 attempts)
            for (int attempt = 0; attempt < maxClients + 5; attempt++) {
                RedisClientConfig cfg = new RedisClientConfig();
                cfg.setAddress("redis://127.0.0.1:" + port);
                RedisClient client = RedisClient.create(cfg);
                openedClients.add(client);
                try {
                    RedisConnection conn = client.connectAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
                    conn.sync(RedisCommands.PING);
                    openedConns.add(conn);
                } catch (Exception e) {
                    rejectionEx = e;
                    break;
                }
            }

            assertNotNull(rejectionEx, "Expected a connection to be rejected after filling maxclients=" + maxClients);

            Throwable cause = rejectionEx.getCause() != null ? rejectionEx.getCause() : rejectionEx;
            RedisServerRejectionException rejection =
                    (RedisServerRejectionException) findCause(cause, RedisServerRejectionException.class);

            assertNotNull(rejection,
                    "Expected RedisServerRejectionException in chain, got: " + cause);
            assertEquals(RedisServerRejectionException.Reason.MAX_CLIENTS, rejection.getReason(),
                    "Max-clients rejection must have MAX_CLIENTS reason");
            assertNotNull(rejection.getServerError(), "serverError must not be null");
            assertTrue(rejection.getServerError().toLowerCase().contains("clients"),
                    "serverError should mention clients, was: " + rejection.getServerError());
        } finally {
            for (RedisConnection c : openedConns) c.closeAsync();
            for (RedisClient c : openedClients) c.shutdown();
            redis.stop();
        }
    }

    /**
     * A Lua script that returns {@code redis.error_reply()} with an unrecognized
     * prefix exercises the else-branch in CommandDecoder. The exception must be
     * a {@link RedisServerRejectionException} and {@link RedisException#getServerError()}
     * must equal the raw wire error string without the channel/command context appended.
     */
    @Test
    public void unrecognizedServerError_preservedInGetServerError() {
        RedisClientConfig config = new RedisClientConfig();
        config.setAddress("redis://127.0.0.1:" + CONTAINER.getFirstMappedPort());
        RedisClient client = RedisClient.create(config);

        try {
            RedisConnection conn = client.connect();
            try {
                RedisException ex = assertThrows(RedisException.class, () ->
                        conn.sync(StringCodec.INSTANCE, RedisCommands.EVAL_OBJECT,
                                "return redis.error_reply('ERR rate limit exceeded')",
                                0));

                assertInstanceOf(RedisServerRejectionException.class, ex,
                        "Unrecognized server error must be RedisServerRejectionException");
                RedisServerRejectionException rejection = (RedisServerRejectionException) ex;

                // Raw wire error — no channel/command context
                assertEquals("ERR rate limit exceeded", rejection.getServerError(),
                        "getServerError() must be the raw wire error without appended context");
                assertEquals(RedisServerRejectionException.Reason.RATE_LIMITED, rejection.getReason());

                // getMessage() includes the channel/command context for logging
                assertTrue(rejection.getMessage().startsWith("ERR rate limit exceeded"),
                        "getMessage() must start with the raw error");
                assertTrue(rejection.getMessage().contains("channel:") || rejection.getMessage().contains("command:"),
                        "getMessage() should contain channel/command context");
            } finally {
                conn.closeAsync();
            }
        } finally {
            client.shutdown();
        }
    }

    /**
     * Verifies the IAM throttle string is classified as {@code IAM_THROTTLE}.
     * We simulate the wire error via a Lua script.
     */
    @Test
    public void iamThrottleError_classifiedAsIamThrottle() {
        RedisClientConfig config = new RedisClientConfig();
        config.setAddress("redis://127.0.0.1:" + CONTAINER.getFirstMappedPort());
        RedisClient client = RedisClient.create(config);

        try {
            RedisConnection conn = client.connect();
            try {
                RedisException ex = assertThrows(RedisException.class, () ->
                        conn.sync(StringCodec.INSTANCE, RedisCommands.EVAL_OBJECT,
                                "return redis.error_reply('ERR Exceeded limit of IAM Authentication requests')",
                                0));

                assertInstanceOf(RedisServerRejectionException.class, ex);
                RedisServerRejectionException rejection = (RedisServerRejectionException) ex;
                assertEquals(RedisServerRejectionException.Reason.IAM_THROTTLE, rejection.getReason());
                assertEquals("ERR Exceeded limit of IAM Authentication requests",
                        rejection.getServerError());
            } finally {
                conn.closeAsync();
            }
        } finally {
            client.shutdown();
        }
    }

    /**
     * Typed error prefixes (NOAUTH, WRONGPASS, etc.) must not be routed to the
     * else-branch and must NOT become {@link RedisServerRejectionException}.
     */
    @Test
    public void noauthError_isRedisAuthRequiredException_notServerRejection() {
        GenericContainer<?> redis = createContainer("--requirepass", "secret");
        redis.start();

        try {
            // Connect without password — AUTH is skipped in bootstrap; first command triggers NOAUTH
            RedisClientConfig config = new RedisClientConfig();
            config.setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
            // No password configured → bootstrap completes (no AUTH sent), first command returns NOAUTH
            RedisClient client = RedisClient.create(config);

            try {
                // connectAsync will trigger bootstrap; since no password, no AUTH is sent.
                // The first command after connect will get NOAUTH.
                RedisConnection conn = client.connectAsync().toCompletableFuture().join();
                try {
                    RedisException ex = assertThrows(RedisException.class, () ->
                            conn.sync(StringCodec.INSTANCE, RedisCommands.GET, "anykey"));

                    assertInstanceOf(RedisAuthRequiredException.class, ex,
                            "NOAUTH should be RedisAuthRequiredException");
                    assertFalse(ex instanceof RedisServerRejectionException,
                            "NOAUTH must NOT be RedisServerRejectionException");
                } finally {
                    conn.closeAsync();
                }
            } finally {
                client.shutdown();
            }
        } finally {
            redis.stop();
        }
    }

    /**
     * Traverses the exception cause chain for an instance of the given type.
     *
     * @return first matching cause, or null if not found within 20 levels
     */
    private static Throwable findCause(Throwable t, Class<? extends Throwable> type) {
        Throwable current = t;
        int depth = 0;
        while (current != null && depth < 20) {
            if (type.isInstance(current)) {
                return current;
            }
            current = current.getCause();
            depth++;
        }
        return null;
    }
}
