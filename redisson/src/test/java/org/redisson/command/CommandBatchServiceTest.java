package org.redisson.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.config.ReadMode;
import org.redisson.connection.MasterSlaveEntry;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class CommandBatchServiceTest extends RedisDockerTest {

    @Test
    public void testBitFieldROCheckViaNewConnectionDoesNotOutlastTimeout() throws Exception {
        Config config = createConfig();
        config.useSingleServer().setTimeout(500);
        RedissonClient client = Redisson.create(config);
        // accepts connections and never answers. The handshake of a new connection sends PING
        // and waits for the reply for 10 seconds
        try (ServerSocket silentNode = new ServerSocket(0)) {
            RedisClient node = RedisClient.create(new RedisClientConfig()
                    .setAddress("redis://127.0.0.1:" + silentNode.getLocalPort())
                    .setPingConnectionInterval(1000)
                    .setCommandTimeout(10000));
            try {
                CommandAsyncExecutor executor = ((Redisson) client).getCommandExecutor();
                CommandBatchService batch = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
                CompletionStage<Void> check = batch.checkBitFieldROViaNewConnection(node, "key");

                // the batches waiting for the check go on after the response timeout
                assertThat(check.toCompletableFuture()).succeedsWithin(Duration.ofSeconds(5));
            } finally {
                node.shutdown();
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testReadIsSentAfterCommandsWaitingInEventLoop() throws Exception {
        // slave read mode enables BITFIELD_RO for reads, without replicas they are served by the master.
        // All connections share one event loop
        Config config = new Config();
        config.setNettyThreads(1);
        config.useReplicatedServers()
                .addNodeAddress(redisson.getConfig().useSingleServer().getAddress())
                .setReadMode(ReadMode.SLAVE);
        RedissonClient client = Redisson.create(config);
        try {
            CommandAsyncExecutor executor = ((Redisson) client).getCommandExecutor();
            CompletableFuture<Void> check = new CompletableFuture<>();
            executor.getServiceManager().getBitFieldROCheck().set(check);

            // the event loop is held while the commands are added,
            // so the first command still waits in its queue when the check of another batch finishes there
            CountDownLatch added = new CountDownLatch(1);
            executor.getServiceManager().getGroup().execute(() -> {
                try {
                    added.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                check.complete(null);
            });

            CommandBatchService batch = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
            batch.writeAsync("readAfterQueued", StringCodec.INSTANCE, RedisCommands.INCR, "readAfterQueued");
            batch.readAsync("bits", LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG, "bits", "GET", "i64", 0);
            added.countDown();

            List<Object> responses = (List<Object>) batch.execute().getResponses();
            assertThat(responses).containsExactly(1L, 0L);
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testAtomicBatchOnBoundConnection() {
        CommandAsyncExecutor executor = ((Redisson) redisson).getCommandExecutor();
        MasterSlaveEntry entry = executor.getConnectionManager().getEntrySet().iterator().next();
        RedisConnection connection = entry.connectionWriteOp(RedisCommands.CLIENT_ID).join();
        Long clientId = connection.sync(RedisCommands.CLIENT_ID);

        CommandBatchService batch = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
        batch.bindConnection(entry, connection);
        batch.writeAsync("counter", StringCodec.INSTANCE, RedisCommands.CLIENT_ID);
        batch.writeAsync("counter", StringCodec.INSTANCE, RedisCommands.INCR, "counter");

        List<Object> responses = (List<Object>) batch.execute().getResponses();
        assertThat(responses).containsExactly(clientId, 1L);
        assertThat(entry.getEntry().getConnectionsHolder().getFreeConnections()).contains(connection);

        CommandBatchService inMemory = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.IN_MEMORY_ATOMIC));
        assertThatThrownBy(() -> inMemory.bindConnection(entry, connection)).isInstanceOf(IllegalStateException.class);

        RedisConnection watching = entry.connectionWriteOp(RedisCommands.WATCH).join();
        watching.sync(RedisCommands.WATCH, "counter");
        CommandBatchService empty = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
        empty.bindConnection(entry, watching);
        assertThat(empty.execute().getResponses()).isEmpty();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(entry.getEntry().getConnectionsHolder().getFreeConnections()).contains(watching);
        });
    }

    @ParameterizedTest
    @EnumSource(Protocol.class)
    public void testAtomicBatchAbortedByWatch(Protocol protocol) {
        Config config = createConfig();
        config.setProtocol(protocol);
        RedissonClient client = Redisson.create(config);
        try {
            CommandAsyncExecutor executor = ((Redisson) client).getCommandExecutor();
            MasterSlaveEntry entry = executor.getConnectionManager().getEntrySet().iterator().next();
            RedisConnection connection = entry.connectionWriteOp(RedisCommands.WATCH).join();
            connection.sync(RedisCommands.WATCH, "key");
            client.getBucket("key", StringCodec.INSTANCE).set("changed");

            CommandBatchService batch = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
            batch.bindConnection(entry, connection);
            batch.writeAsync("key", StringCodec.INSTANCE, RedisCommands.SET, "key", "value");
            List<Object> responses = (List<Object>) batch.execute().getResponses();

            assertThat(batch.isAborted()).isTrue();
            assertThat(responses).containsExactly((Object) null);
            assertThat(client.getBucket("key", StringCodec.INSTANCE).get()).isEqualTo("changed");
            assertThat(entry.getEntry().getConnectionsHolder().getFreeConnections()).contains(connection);

            CommandBatchService next = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
            next.writeAsync("key", StringCodec.INSTANCE, RedisCommands.SET, "key", "value");
            next.execute();
            assertThat(next.isAborted()).isFalse();
            assertThat(client.getBucket("key", StringCodec.INSTANCE).get()).isEqualTo("value");
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testAtomicBatchDiscardOnBoundConnection() {
        CommandAsyncExecutor executor = ((Redisson) redisson).getCommandExecutor();
        MasterSlaveEntry entry = executor.getConnectionManager().getEntrySet().iterator().next();
        RedisConnection connection = entry.connectionWriteOp(RedisCommands.WATCH).join();
        Long clientId = connection.sync(RedisCommands.CLIENT_ID);
        connection.sync(RedisCommands.WATCH, "key");

        CommandBatchService batch = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
        batch.bindConnection(entry, connection);
        batch.discard();

        assertThat(entry.getEntry().getConnectionsHolder().getFreeConnections()).contains(connection);
        List<String> clients = connection.sync(RedisCommands.CLIENT_LIST, "ID", clientId);
        assertThat(clients).hasSize(1);
        assertThat(clients.get(0)).contains(" flags=N ", " watch=0 ");
    }

    @Test
    public void testAtomicBatchDiscardThroughWriteAsync() {
        CommandAsyncExecutor executor = ((Redisson) redisson).getCommandExecutor();
        MasterSlaveEntry entry = executor.getConnectionManager().getEntrySet().iterator().next();
        Queue<RedisConnection> free = entry.getEntry().getConnectionsHolder().getFreeConnections();
        int freeBefore = free.size();

        for (int i = 0; i < 3; i++) {
            CommandBatchService batch = executor.createCommandBatchService(BatchOptions.defaults().executionMode(ExecutionMode.REDIS_WRITE_ATOMIC));
            batch.writeAsync("key", StringCodec.INSTANCE, RedisCommands.SET, "key", "value");
            Object reply = batch.get(batch.writeAsync((String) null, RedisCommands.DISCARD));
            assertThat(reply).isNull();
        }

        assertThat(redisson.getBucket("key", StringCodec.INSTANCE).isExists()).isFalse();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(free).hasSize(freeBefore);
        });
        assertThat(free).noneMatch(RedisConnection::isQueued);
    }

}
