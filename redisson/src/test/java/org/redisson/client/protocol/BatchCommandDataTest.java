package org.redisson.client.protocol;

import org.junit.jupiter.api.Test;
import org.redisson.client.RedisException;
import org.redisson.client.RedisReadonlyException;

import static org.assertj.core.api.Assertions.assertThat;

class BatchCommandDataTest {

    @Test
    void testReadonlyErrorCanBeClearedBetweenAttempts() {
        BatchCommandData<String, String> command = new BatchCommandData<>(
                RedisCommands.PING, new Object[0], 0);
        RedisReadonlyException first = new RedisReadonlyException("READONLY");
        RedisReadonlyException second = new RedisReadonlyException("READONLY");

        assertThat(command.tryFailure(first)).isTrue();
        assertThat(command.cause()).isSameAs(first);
        assertThat(command.isSuccess()).isFalse();
        assertThat(command.getPromise().isDone()).isFalse();
        assertThat(command.tryFailure(second)).isFalse();
        assertThat(command.cause()).isSameAs(first);

        command.clearError();
        assertThat(command.cause()).isNull();
        assertThat(command.tryFailure(second)).isTrue();
        assertThat(command.cause()).isSameAs(second);
        assertThat(command.getPromise().isDone()).isFalse();

        command.clearError();
        assertThat(command.getPromise().complete("PONG")).isTrue();
        assertThat(command.isSuccess()).isTrue();
        assertThat(command.cause()).isNull();
        assertThat(command.getPromise().join()).isEqualTo("PONG");
    }

    @Test
    void testNonRetryableErrorRemainsAfterClear() {
        BatchCommandData<String, String> command = new BatchCommandData<>(
                RedisCommands.PING, new Object[0], 0);
        RedisException error = new RedisException("ERR invalid command");

        assertThat(command.tryFailure(error)).isTrue();
        command.clearError();

        assertThat(command.cause()).isSameAs(error);
        assertThat(command.getPromise().isCompletedExceptionally()).isTrue();
        assertThat(command.getPromise().complete("PONG")).isFalse();
        assertThat(command.isSuccess()).isFalse();
    }
}
