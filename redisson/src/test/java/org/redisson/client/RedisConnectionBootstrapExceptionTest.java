package org.redisson.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class RedisConnectionBootstrapExceptionTest {

    @Test
    void constructor_preservesAllFields() {
        RuntimeException cause = new RuntimeException("auth rejected");
        RedisConnectionBootstrapException ex = new RedisConnectionBootstrapException(
                "Bootstrap failed at phase AUTH: auth rejected",
                RedisConnectionBootstrapException.Phase.AUTH,
                cause);

        assertEquals("Bootstrap failed at phase AUTH: auth rejected", ex.getMessage());
        assertEquals(RedisConnectionBootstrapException.Phase.AUTH, ex.getPhase());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRedisConnectionException_subtype() {
        RedisConnectionBootstrapException ex = new RedisConnectionBootstrapException(
                "Bootstrap failed",
                RedisConnectionBootstrapException.Phase.UNKNOWN,
                new RuntimeException("oops"));

        assertInstanceOf(RedisConnectionException.class, ex);
    }

    @ParameterizedTest
    @EnumSource(RedisConnectionBootstrapException.Phase.class)
    void allPhasesRoundTrip(RedisConnectionBootstrapException.Phase phase) {
        RedisConnectionBootstrapException ex = new RedisConnectionBootstrapException(
                "Bootstrap failed at phase " + phase,
                phase,
                new RuntimeException("cause"));

        assertEquals(phase, ex.getPhase());
        assertNotNull(ex.getPhase());
    }

    @Test
    void causeCanBeRedisServerRejectionException() {
        // Verify the full chain: IAM throttle wraps correctly
        String serverError = "ERR Exceeded limit of IAM Authentication requests";
        RedisServerRejectionException serverEx = new RedisServerRejectionException(
                serverError + ". channel: [id: 0x01] command: AUTH",
                serverError,
                RedisServerRejectionException.Reason.IAM_THROTTLE);

        RedisConnectionBootstrapException bootstrapEx = new RedisConnectionBootstrapException(
                "Bootstrap failed at phase AUTH: " + serverEx.getMessage(),
                RedisConnectionBootstrapException.Phase.AUTH,
                serverEx);

        assertInstanceOf(RedisServerRejectionException.class, bootstrapEx.getCause());
        RedisServerRejectionException wrapped = (RedisServerRejectionException) bootstrapEx.getCause();
        assertEquals(RedisServerRejectionException.Reason.IAM_THROTTLE, wrapped.getReason());
        assertEquals(serverError, wrapped.getServerError());
        assertEquals(RedisConnectionBootstrapException.Phase.AUTH, bootstrapEx.getPhase());
    }

    @Test
    void phaseNeverNull() {
        RedisConnectionBootstrapException ex = new RedisConnectionBootstrapException(
                "Bootstrap failed",
                RedisConnectionBootstrapException.Phase.SELECT,
                new RuntimeException("db unavailable"));

        assertNotNull(ex.getPhase());
        assertEquals(RedisConnectionBootstrapException.Phase.SELECT, ex.getPhase());
    }
}
