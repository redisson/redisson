package org.redisson.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchOptions.ExecutionMode;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.connection.MasterSlaveEntry;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

public class CommandBatchServiceTest extends RedisDockerTest {

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

}
