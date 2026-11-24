package org.redisson.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.QueueCommand;
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

    @ParameterizedTest
    @CsvSource({"false, 0", "true, 0", "false, 1", "true, 1"})
    void testRejectedReplyOffPreservesFollowingReplies(boolean atomic, int fragmentSize) {
        CommandsData batch = skipResultBatch(atomic, SyncMode.NONE);
        assertRejectedBatch(batch, "-NOPERM User limited has no permissions to run the 'client|reply' command\r\n"
                + (atomic ? "+OK\r\n+QUEUED\r\n*1\r\n+OK\r\n" : "+OK\r\n")
                + "-NOPERM User limited has no permissions to run the 'client|reply' command\r\n", fragmentSize, false);
    }

    @ParameterizedTest
    @CsvSource({"false, 0", "true, 0", "false, 1", "true, 1"})
    void testRejectedQueuedReplyOffPreservesFollowingReplies(boolean replica, int fragmentSize) {
        CommandsData batch = queuedSkipResultBatch(SyncMode.NONE);
        String replies = replica
                ? "+QUEUED\r\n-EXECABORT Transaction discarded because of: READONLY You can't write against a read only replica.\r\n+OK\r\n"
                : "-NOPERM User limited has no permissions to run the 'client|reply' command\r\n"
                    + "-EXECABORT Transaction discarded because of previous errors.\r\n"
                    + "-NOPERM User limited has no permissions to run the 'client|reply' command\r\n";
        assertRejectedBatch(batch, replies, fragmentSize, true);
        assertThat(batch.getCommands().get(1).getPromise()).isCompletedExceptionally();
    }

    private void assertRejectedBatch(CommandsData batch, String replies, int fragmentSize, boolean failed) {
        CommandData<String, String> ping = command(RedisCommands.PING);
        CommandData<Integer, Integer> cardinality = command(RedisCommands.SCARD_INT, "set");
        channel.writeOutbound(batch, ping, cardinality);

        receive(replies + "+PONG\r\n:5\r\n", fragmentSize);

        if (failed) {
            assertThatThrownBy(batch.getPromise()::join).hasCauseInstanceOf(RedisException.class);
        } else {
            assertThat(batch.getPromise()).isCompletedWithValue(null);
        }
        assertThat(ping.getPromise()).isCompletedWithValue("PONG");
        assertThat(cardinality.getPromise()).isCompletedWithValue(5);
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

    private static class ResumingDecoder extends CommandDecoder {

        private boolean split;
        private final boolean completeAfterFirstReply;

        ResumingDecoder() {
            this(false);
        }

        ResumingDecoder(boolean completeAfterFirstReply) {
            super("redis");
            this.completeAfterFirstReply = completeAfterFirstReply;
        }

        @Override
        protected void decodeCommand(Channel channel, ByteBuf in, QueueCommand data, int endIndex, State state)
                throws Exception {
            if (data instanceof CommandsData && !split) {
                split = true;
                int size = skipCommand(in) - in.readerIndex();
                // Return after one complete frame through the protected decoding entry point.
                // The next invocation resumes the same batch with its real checkpointed state.
                ByteBuf firstReply = in.readSlice(size);
                super.decodeCommand(channel, firstReply, data, size, state);
                if (completeAfterFirstReply) {
                    ((CommandsData) data).getPromise().completeExceptionally(new java.util.concurrent.TimeoutException());
                }
                return;
            }
            super.decodeCommand(channel, in, data, endIndex, state);
        }
    }

    @ParameterizedTest
    @CsvSource({"NONE, 0", "NONE, 1", "WAIT, 0", "WAIT, 1", "WAITAOF, 0", "WAITAOF, 1"})
    void testQueuedBatchResumesAfterQueuedReply(SyncMode syncMode, int fragmentSize) {
        channel.pipeline().replace(CommandDecoder.class, "resuming-decoder", new ResumingDecoder());
        assertFollowingReplies(queuedSkipResultBatch(syncMode), syncMode, fragmentSize,
                "+QUEUED\r\n*2\r\n+OK\r\n+OK\r\n");
    }

    @ParameterizedTest
    @CsvSource({"false, 0", "true, 0", "false, 1", "true, 1"})
    void testRejectedReplyOffResumesWithRepliesEnabled(boolean atomic, int fragmentSize) {
        channel.pipeline().replace(CommandDecoder.class, "resuming-decoder", new ResumingDecoder());
        testRejectedReplyOffPreservesFollowingReplies(atomic, fragmentSize);
    }

    @ParameterizedTest
    @CsvSource({"WAIT, 0", "WAIT, 1", "WAITAOF, 0", "WAITAOF, 1"})
    void testSkippedBatchResumesAtSyncReply(SyncMode syncMode, int fragmentSize) {
        channel.pipeline().replace(CommandDecoder.class, "resuming-decoder", new ResumingDecoder());
        assertFollowingReplies(skipResultBatch(false, syncMode), syncMode, fragmentSize, "+OK\r\n");
    }

    @ParameterizedTest
    @CsvSource({"0", "1"})
    void testBatchFailureSurvivesResumedDecoding(int fragmentSize) {
        channel.pipeline().replace(CommandDecoder.class, "resuming-decoder", new ResumingDecoder());
        CommandData<Integer, Integer> wrongType = command(RedisCommands.SCARD_INT, "string-key");
        CommandData<Integer, Integer> cardinality = command(RedisCommands.SCARD_INT, "set");
        CommandsData batch = new CommandsData(new CompletableFuture<>(), List.of(wrongType, cardinality),
                false, false, false, false);
        CommandData<String, String> ping = command(RedisCommands.PING);
        channel.writeOutbound(batch, ping);
        receive("-WRONGTYPE Operation against a key holding the wrong kind of value\r\n:5\r\n+PONG\r\n", fragmentSize);

        assertThatThrownBy(batch.getPromise()::join).hasCauseInstanceOf(RedisException.class);
        assertThat(cardinality.getPromise()).isCompletedWithValue(5);
        assertThat(ping.getPromise()).isCompletedWithValue("PONG");
        assertThat(channel.attr(CommandsQueue.COMMANDS_QUEUE).get()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"true, NONE", "false, WAIT", "true, WAIT", "false, WAITAOF", "true, WAITAOF"})
    void testCompletedBatchSkipsOnlyRemainingRepliesAfterResume(boolean queued, SyncMode syncMode) {
        channel.pipeline().replace(CommandDecoder.class, "resuming-decoder", new ResumingDecoder(true));
        CommandsData batch = queued ? queuedSkipResultBatch(syncMode) : skipResultBatch(false, syncMode);
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

        assertThatThrownBy(batch.getPromise()::join).hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
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
