package org.redisson.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class RedisServerRejectionExceptionTest {

    // --- classifyReason ---

    @ParameterizedTest(name = "classifyReason(\"{0}\") == MAX_CLIENTS")
    @CsvSource({
        "ERR max number of clients reached",
        "ERR Max number of clients reached",
        "ERR MAX NUMBER OF CLIENTS REACHED",
        "ERR maxclients limit exceeded",
        "MAXCLIENTS"
    })
    void classifyReason_maxClients(String serverError) {
        assertEquals(RedisServerRejectionException.Reason.MAX_CLIENTS,
                RedisServerRejectionException.classifyReason(serverError));
    }

    @ParameterizedTest(name = "classifyReason(\"{0}\") == IAM_THROTTLE")
    @CsvSource({
        "ERR Exceeded limit of IAM Authentication requests",
        "ERR exceeded limit of iam authentication requests",
        "ERR IAM Authentication requests exceeded",
        "ERR iam authentication requests limit"
    })
    void classifyReason_iamThrottle(String serverError) {
        assertEquals(RedisServerRejectionException.Reason.IAM_THROTTLE,
                RedisServerRejectionException.classifyReason(serverError));
    }

    @ParameterizedTest(name = "classifyReason(\"{0}\") == RATE_LIMITED")
    @CsvSource({
        "ERR rate limit exceeded",
        "ERR ratelimit hit",
        "ERR too many connections",
        "ERR too many requests",
        "ERR request limit exceeded"
    })
    void classifyReason_rateLimited(String serverError) {
        assertEquals(RedisServerRejectionException.Reason.RATE_LIMITED,
                RedisServerRejectionException.classifyReason(serverError));
    }

    @ParameterizedTest(name = "classifyReason(\"{0}\") == OTHER")
    @CsvSource({
        "ERR some unknown error",
        "ERR syntax error",
        "WRONGTYPE Operation against a key holding the wrong kind of value",
        "ERR value is not an integer or out of range"
    })
    void classifyReason_other(String serverError) {
        assertEquals(RedisServerRejectionException.Reason.OTHER,
                RedisServerRejectionException.classifyReason(serverError));
    }

    @Test
    void classifyReason_nullInput_returnsOther() {
        assertEquals(RedisServerRejectionException.Reason.OTHER,
                RedisServerRejectionException.classifyReason(null));
    }

    // --- constructor / accessors ---

    @Test
    void constructor_preservesAllFields() {
        String serverError = "ERR max number of clients reached";
        String message = serverError + ". channel: [id: 0x00] command: GET";
        RedisServerRejectionException ex = new RedisServerRejectionException(
                message, serverError, RedisServerRejectionException.Reason.MAX_CLIENTS);

        assertEquals(message, ex.getMessage());
        assertEquals(serverError, ex.getServerError());
        assertEquals(RedisServerRejectionException.Reason.MAX_CLIENTS, ex.getReason());
    }

    @Test
    void isRedisException_subtype() {
        RedisServerRejectionException ex = new RedisServerRejectionException(
                "ERR max number of clients reached", "ERR max number of clients reached",
                RedisServerRejectionException.Reason.MAX_CLIENTS);

        assertInstanceOf(RedisException.class, ex);
    }

    @Test
    void reasonNeverNull() {
        RedisServerRejectionException ex = new RedisServerRejectionException(
                "ERR something", "ERR something", RedisServerRejectionException.Reason.OTHER);

        assertNotNull(ex.getReason());
    }
}
