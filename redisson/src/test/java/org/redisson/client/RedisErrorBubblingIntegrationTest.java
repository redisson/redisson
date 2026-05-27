package org.redisson.client;

import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;

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
     * {@code "ERR max number of clients reached"} from the server.
     * This must surface as a {@link RedisServerRejectionException} with
     * {@code reason == MAX_CLIENTS}.
     * <p>
     * We use {@code --maxclients 1} so the first connection fills the limit
     * and the second connection is immediately rejected.
     */
    @Test
    public void maxClientsReached_emitsRedisServerRejectionException() throws Exception {
        // maxclients 1: first connected client fills the slot; second connection rejected
        GenericContainer<?> redis = createContainer("--maxclients", "1");
        redis.start();

        RedisClientConfig firstConfig = new RedisClientConfig();
        firstConfig.setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
        RedisClient firstClient = RedisClient.create(firstConfig);
        RedisConnection firstConn = firstClient.connect();
        firstConn.sync(RedisCommands.PING); // establish and hold the slot

        try {
            RedisClientConfig secondConfig = new RedisClientConfig();
            secondConfig.setAddress("redis://127.0.0.1:" + redis.getFirstMappedPort());
            RedisClient secondClient = RedisClient.create(secondConfig);

            try {
                Exception ex = assertThrows(Exception.class,
                        () -> secondClient.connectAsync().toCompletableFuture().get());

                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                RedisServerRejectionException rejection =
                        (RedisServerRejectionException) findCause(cause, RedisServerRejectionException.class);

                assertNotNull(rejection,
                        "Expected RedisServerRejectionException in chain, got: " + cause);
                assertEquals(RedisServerRejectionException.Reason.MAX_CLIENTS, rejection.getReason(),
                        "Max-clients rejection must have MAX_CLIENTS reason");
                assertNotNull(rejection.getServerError(),
                        "serverError must not be null");
                assertTrue(rejection.getServerError().toLowerCase().contains("clients"),
                        "serverError should mention clients, was: " + rejection.getServerError());
            } finally {
                secondClient.shutdown();
            }
        } finally {
            firstConn.closeAsync();
            firstClient.shutdown();
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
