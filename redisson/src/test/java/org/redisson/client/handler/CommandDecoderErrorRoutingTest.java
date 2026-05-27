package org.redisson.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.local.LocalChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.client.RedisAuthRequiredException;
import org.redisson.client.RedisBusyException;
import org.redisson.client.RedisClusterDownException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisLoadingException;
import org.redisson.client.RedisMasterDownException;
import org.redisson.client.RedisNoReplicasException;
import org.redisson.client.RedisNoScriptException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.RedisReadonlyException;
import org.redisson.client.RedisServerRejectionException;
import org.redisson.client.RedisServerRejectionException.Reason;
import org.redisson.client.RedisWaitException;
import org.redisson.client.RedisWrongPasswordException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the error-response routing in {@link CommandDecoder#decode}.
 * Calls the protected {@code decode(ByteBuf, CommandData, ...)} method directly
 * so tests remain fast and don't require a live Redis instance.
 */
class CommandDecoderErrorRoutingTest {

    /** Thin subclass that exposes the protected decode method for testing. */
    static class TestableDecoder extends CommandDecoder {
        TestableDecoder() {
            super("redis");
        }

        void decodeError(ByteBuf buf, CommandData<Object, Object> data, Channel channel)
                throws IOException {
            decode(buf, data, null, channel, false, null, 0, new State());
        }
    }

    private TestableDecoder decoder;
    private Channel channel;

    @BeforeEach
    void setUp() {
        decoder = new TestableDecoder();
        channel = new LocalChannel();
    }

    private ByteBuf errorResponse(String serverError) {
        // RESP error frame: "-{message}\r\n"
        String frame = "-" + serverError + "\r\n";
        return Unpooled.copiedBuffer(frame, StandardCharsets.US_ASCII);
    }

    private CommandData<Object, Object> newCommand() {
        return new CommandData<>(new CompletableFuture<>(), StringCodec.INSTANCE,
                RedisCommands.GET, new Object[]{"key"});
    }

    // ---- else-branch: RedisServerRejectionException ----

    @Test
    void elseError_maxClients_emitsRedisServerRejectionException() throws IOException {
        String serverError = "ERR max number of clients reached";
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse(serverError), cmd, channel);

        Throwable cause = cmd.cause();
        assertInstanceOf(RedisServerRejectionException.class, cause);
        RedisServerRejectionException ex = (RedisServerRejectionException) cause;
        assertEquals(Reason.MAX_CLIENTS, ex.getReason());
        assertEquals(serverError, ex.getServerError());
        assertTrue(ex.getMessage().startsWith(serverError));
    }

    @Test
    void elseError_iamThrottle_emitsRedisServerRejectionException() throws IOException {
        String serverError = "ERR Exceeded limit of IAM Authentication requests";
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse(serverError), cmd, channel);

        Throwable cause = cmd.cause();
        assertInstanceOf(RedisServerRejectionException.class, cause);
        RedisServerRejectionException ex = (RedisServerRejectionException) cause;
        assertEquals(Reason.IAM_THROTTLE, ex.getReason());
        assertEquals(serverError, ex.getServerError());
    }

    @Test
    void elseError_unknownError_emitsOtherReason() throws IOException {
        String serverError = "ERR some completely unknown error";
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse(serverError), cmd, channel);

        Throwable cause = cmd.cause();
        assertInstanceOf(RedisServerRejectionException.class, cause);
        assertEquals(Reason.OTHER, ((RedisServerRejectionException) cause).getReason());
    }

    @Test
    void elseError_serverErrorPreservedInGetServerError() throws IOException {
        String serverError = "ERR rate limit exceeded";
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse(serverError), cmd, channel);

        RedisServerRejectionException ex = (RedisServerRejectionException) cmd.cause();
        assertEquals(serverError, ex.getServerError());
    }

    @Test
    void elseError_isRedisExceptionSubtype() throws IOException {
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse("ERR something"), cmd, channel);

        assertInstanceOf(RedisException.class, cmd.cause());
    }

    // ---- typed branches still emit their typed exceptions ----

    static Stream<Arguments> typedErrorBranches() {
        return Stream.of(
            Arguments.of("NOAUTH Authentication required", RedisAuthRequiredException.class),
            Arguments.of("WRONGPASS invalid username-password pair", RedisWrongPasswordException.class),
            Arguments.of("OOM command not allowed", RedisOutOfMemoryException.class),
            Arguments.of("CLUSTERDOWN The cluster is down", RedisClusterDownException.class),
            Arguments.of("MASTERDOWN Link with MASTER is down", RedisMasterDownException.class),
            Arguments.of("BUSY Redis is busy running a script", RedisBusyException.class),
            Arguments.of("READONLY You can't write against a read only replica", RedisReadonlyException.class),
            Arguments.of("NOSCRIPT No matching script", RedisNoScriptException.class),
            Arguments.of("NOREPLICAS Not enough replicas connected", RedisNoReplicasException.class),
            Arguments.of("LOADING Redis is loading the dataset in memory", RedisLoadingException.class),
            Arguments.of("WAIT numreplicas timeout", RedisWaitException.class)
        );
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @MethodSource("typedErrorBranches")
    void typedError_emitsCorrectException(String serverError, Class<? extends RedisException> expectedType)
            throws IOException {
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse(serverError), cmd, channel);

        assertInstanceOf(expectedType, cmd.cause(),
                "Expected " + expectedType.getSimpleName() + " for error: " + serverError);
    }

    @ParameterizedTest(name = "\"{0}\" -> typed, NOT RedisServerRejectionException")
    @MethodSource("typedErrorBranches")
    void typedError_isNotRedisServerRejectionException(String serverError, Class<?> ignored)
            throws IOException {
        CommandData<Object, Object> cmd = newCommand();

        decoder.decodeError(errorResponse(serverError), cmd, channel);

        assertFalse(cmd.cause() instanceof RedisServerRejectionException,
                "Typed error should NOT become RedisServerRejectionException: " + serverError);
    }

    // ---- null data path (no command on queue): must not throw ----

    @Test
    void elseError_nullData_noException() {
        String serverError = "ERR max number of clients reached";
        ByteBuf buf = errorResponse(serverError);

        assertDoesNotThrow(() -> decoder.decodeError(buf, null, channel));
    }
}
