package org.redisson.client.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.CommandMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandDecoderBatchTest {

    private enum SyncMode {
        NONE, WAIT, WAITAOF
    }

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new CommandEncoder(CommandMapper.direct()),
                CommandBatchEncoder.INSTANCE, new CommandsQueue(), new CommandDecoder("redis"));
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @ParameterizedTest
    @CsvSource({
            "false, NONE, 0", "true, NONE, 0",
            "false, NONE, 1", "true, NONE, 1",
            "false, WAIT, 0", "true, WAIT, 0",
            "false, WAIT, 1", "true, WAIT, 1",
            "false, WAITAOF, 0", "true, WAITAOF, 0",
            "false, WAITAOF, 1", "true, WAITAOF, 1"
    })
    void testSkipResultBatchPreservesFollowingReplies(boolean atomic, SyncMode syncMode, int fragmentSize) {
        assertFollowingReplies(skipResultBatch(atomic, syncMode), syncMode, fragmentSize, "+OK\r\n");
    }

    @ParameterizedTest
    @CsvSource({"NONE, 0", "NONE, 1", "WAIT, 0", "WAIT, 1", "WAITAOF, 0", "WAITAOF, 1"})
    void testQueuedSkipResultBatchPreservesFollowingReplies(SyncMode syncMode, int fragmentSize) {
        CommandsData batch = queuedSkipResultBatch(syncMode);
        // CLIENT REPLY OFF is queued inside MULTI. EXEC returns the earlier SET result and
        // its CLIENT REPLY OFF result, whose suppressed reply is replaced by CLIENT REPLY ON's OK.
        assertFollowingReplies(batch, syncMode, fragmentSize, "+QUEUED\r\n*2\r\n+OK\r\n+OK\r\n");
        assertThat(batch.getCommands().get(1).getPromise()).isCompleted();
    }

    private void assertFollowingReplies(CommandsData skippedBatch, SyncMode syncMode,
                                        int fragmentSize, String batchReply) {
        CommandData<String, String> ping = command(RedisCommands.PING);
        CommandData<Integer, Integer> cardinality = command(RedisCommands.SCARD_INT, "set");
        CommandData<Void, Void> set = command(RedisCommands.SET, "after", "value");
        CommandsData followingBatch = new CommandsData(new CompletableFuture<>(),
                List.of(cardinality, set), false, false, false, false);
        channel.writeOutbound(skippedBatch, ping, followingBatch);

        // TCP may deliver batch replies together with replies to subsequent queued commands.
        String replies = batchReply;
        if (syncMode == SyncMode.WAIT) {
            replies += ":1\r\n";
        } else if (syncMode == SyncMode.WAITAOF) {
            replies += "*2\r\n:1\r\n:1\r\n";
        }
        replies += "+PONG\r\n:5\r\n+OK\r\n";
        receive(replies, fragmentSize);

        assertThat(skippedBatch.getPromise()).isCompletedWithValue(null);
        assertThat(ping.getPromise()).isCompletedWithValue("PONG");
        assertThat(cardinality.getPromise()).isCompletedWithValue(5);
        assertThat(set.getPromise()).isCompletedWithValue(null);
        assertThat(followingBatch.getPromise()).isCompletedWithValue(null);
        if (syncMode != SyncMode.NONE) {
            Object syncResult = skippedBatch.getCommands().get(skippedBatch.getCommands().size() - 1)
                    .getPromise().getNow(null);
            assertThat(syncResult).isEqualTo(syncMode == SyncMode.WAIT ? 1 : List.of(1, 1));
        }
        assertThat(channel.attr(CommandsQueue.COMMANDS_QUEUE).get()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = SyncMode.class, names = {"WAIT", "WAITAOF"})
    void testSkipResultBatchWaitsForSyncReplyAndPropagatesFailure(SyncMode syncMode) {
        CommandsData batch = skipResultBatch(false, syncMode);
        CommandData<String, String> ping = command(RedisCommands.PING);
        channel.writeOutbound(batch, ping);

        receive("+OK\r\n", 0);
        assertThat(batch.getPromise()).isNotDone();
        assertThat(ping.getPromise()).isNotDone();

        receive("-ERR synchronization failed\r\n+PONG\r\n", 0);
        assertThatThrownBy(batch.getPromise()::join)
                .hasCauseInstanceOf(RedisException.class)
                .hasMessageContaining("synchronization failed");
        assertThat(ping.getPromise()).isCompletedWithValue("PONG");
        assertThat(channel.attr(CommandsQueue.COMMANDS_QUEUE).get()).isEmpty();
    }

    @Test
    void testFailedSkipResultBatchPreservesFollowingReply() {
        CommandsData batch = skipResultBatch(false, SyncMode.NONE);
        CommandData<String, String> ping = command(RedisCommands.PING);
        channel.writeOutbound(batch, ping);

        receive("-ERR rejected batch\r\n+PONG\r\n", 0);

        assertThatThrownBy(batch.getPromise()::join)
                .hasCauseInstanceOf(RedisException.class)
                .hasMessageContaining("rejected batch");
        assertThat(ping.getPromise()).isCompletedWithValue("PONG");
        assertThat(channel.attr(CommandsQueue.COMMANDS_QUEUE).get()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"false, NONE", "true, NONE", "false, WAIT", "true, WAIT", "false, WAITAOF", "true, WAITAOF"})
    void testCompletedSkipResultBatchSkipsAllItsReplies(boolean queued, SyncMode syncMode) {
        CommandsData batch = queued ? queuedSkipResultBatch(syncMode) : skipResultBatch(false, syncMode);
        batch.getPromise().completeExceptionally(new java.util.concurrent.TimeoutException());
        CommandData<String, String> ping = command(RedisCommands.PING);
        CommandData<Integer, Integer> cardinality = command(RedisCommands.SCARD_INT, "set");
        channel.writeOutbound(batch, ping, cardinality);

        String replies = queued ? "+QUEUED\r\n*2\r\n+OK\r\n+OK\r\n" : "+OK\r\n";
        if (syncMode == SyncMode.WAIT) {
            replies += ":1\r\n";
        } else if (syncMode == SyncMode.WAITAOF) {
            replies += "*2\r\n:1\r\n:1\r\n";
        }
        receive(replies + "+PONG\r\n:5\r\n", 1);

        assertThat(ping.getPromise()).isCompletedWithValue("PONG");
        assertThat(cardinality.getPromise()).isCompletedWithValue(5);
        assertThat(channel.attr(CommandsQueue.COMMANDS_QUEUE).get()).isEmpty();
    }

    private CommandsData queuedSkipResultBatch(SyncMode syncMode) {
        List<CommandData<?, ?>> commands = new ArrayList<>();
        commands.add(command(RedisCommands.CLIENT_REPLY, "OFF"));
        commands.add(command(RedisCommands.EXEC));
        commands.add(command(RedisCommands.CLIENT_REPLY, "ON"));
        if (syncMode == SyncMode.WAIT) {
            commands.add(command(RedisCommands.WAIT, 1, 1000));
        } else if (syncMode == SyncMode.WAITAOF) {
            commands.add(command(RedisCommands.WAITAOF, 1, 1, 1000));
        }
        List<CommandData<?, ?>> attached = List.of(command(RedisCommands.SET, "key", "value"),
                command(RedisCommands.EXEC));
        return new CommandsData(new CompletableFuture<>(), commands, attached,
                true, false, true, syncMode != SyncMode.NONE);
    }

    private CommandsData skipResultBatch(boolean atomic, SyncMode syncMode) {
        List<CommandData<?, ?>> commands = new ArrayList<>();
        commands.add(command(RedisCommands.CLIENT_REPLY, "OFF"));
        if (atomic) {
            commands.add(command(RedisCommands.MULTI));
        }
        commands.add(command(RedisCommands.SET, "key", "value"));
        if (atomic) {
            commands.add(command(RedisCommands.EXEC));
        }
        commands.add(command(RedisCommands.CLIENT_REPLY, "ON"));
        if (syncMode == SyncMode.WAIT) {
            commands.add(command(RedisCommands.WAIT, 1, 1000));
        } else if (syncMode == SyncMode.WAITAOF) {
            commands.add(command(RedisCommands.WAITAOF, 1, 1, 1000));
        }
        return new CommandsData(new CompletableFuture<>(), commands,
                true, atomic, false, syncMode != SyncMode.NONE);
    }

    private <T> CommandData<T, T> command(RedisCommand<T> command, Object... params) {
        return new CommandData<>(new CompletableFuture<>(), StringCodec.INSTANCE, command, params);
    }

    private void receive(String replies, int fragmentSize) {
        int size = fragmentSize == 0 ? replies.length() : fragmentSize;
        for (int start = 0; start < replies.length(); start += size) {
            channel.writeInbound(Unpooled.copiedBuffer(
                    replies.substring(start, Math.min(start + size, replies.length())), CharsetUtil.UTF_8));
        }
    }
}
