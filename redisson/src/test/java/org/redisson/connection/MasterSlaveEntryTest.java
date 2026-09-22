package org.redisson.connection;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnectionException;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class MasterSlaveEntryTest {

    private static final RedisURI OLD_MASTER = new RedisURI("redis://127.0.0.1:6379");
    private static final RedisURI NEW_MASTER = new RedisURI("redis://127.0.0.1:6380");

    private MasterSlaveConnectionManager manager;
    private ControlledMasterSlaveEntry entry;
    private final List<RedisClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.setLazyInitialization(true);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress(OLD_MASTER.toString());
        msConfig.setReadMode(ReadMode.MASTER);
        manager = new MasterSlaveConnectionManager(msConfig, config);

        entry = new ControlledMasterSlaveEntry(manager, msConfig);
        entry.masterEntry = newEntry(OLD_MASTER);
    }

    @AfterEach
    void tearDown() {
        clients.forEach(c -> c.shutdownAsync());
        manager.shutdown(0, 0, TimeUnit.SECONDS);
    }

    @Test
    void rejectsConcurrentChangeMasterWhileAnotherIsInProgress() {
        CompletableFuture<RedisClient> first = entry.changeMaster(NEW_MASTER);
        Assertions.assertThat(first).isNotDone();

        CompletableFuture<RedisClient> second = entry.changeMaster(NEW_MASTER);
        Assertions.assertThat(second).isCompletedExceptionally();
        Assertions.assertThatThrownBy(second::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RedisConnectionException.class)
                .hasMessageContaining("another master change is in progress");

        CompletableFuture<RedisClient> third = entry.changeMaster(
                new InetSocketAddress(NEW_MASTER.getHost(), NEW_MASTER.getPort()), NEW_MASTER);
        Assertions.assertThat(third).isCompletedExceptionally();

        Assertions.assertThat(entry.setupCalls).isEqualTo(1);
    }

    @Test
    void allowsNextChangeMasterAfterSuccessfulChange() {
        CompletableFuture<RedisClient> first = entry.changeMaster(NEW_MASTER);

        ClientConnectionsEntry newMaster = newEntry(NEW_MASTER);
        entry.masterEntry = newMaster;
        entry.pending.complete(newMaster.getClient());

        Assertions.assertThat(first.join()).isSameAs(newMaster.getClient());
        Assertions.assertThat(entry.masterEntry).isSameAs(newMaster);
        Assertions.assertThat(entry.masterChangeStartedAt).hasValue(MasterSlaveEntry.NO_MASTER_CHANGE);

        entry.pending = new CompletableFuture<>();
        CompletableFuture<RedisClient> second = entry.changeMaster(OLD_MASTER);
        Assertions.assertThat(second).isNotDone();
        Assertions.assertThat(entry.setupCalls).isEqualTo(2);
    }

    @Test
    void allowsNextChangeMasterAfterFailedChange() {
        ClientConnectionsEntry oldMaster = entry.masterEntry;
        CompletableFuture<RedisClient> first = entry.changeMaster(NEW_MASTER);

        entry.pending.completeExceptionally(new RedisConnectionException("connect failed"));

        Assertions.assertThatThrownBy(first::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RedisConnectionException.class)
                .hasMessageContaining("connect failed");
        Assertions.assertThat(entry.masterEntry).isSameAs(oldMaster);
        Assertions.assertThat(entry.masterChangeStartedAt).hasValue(MasterSlaveEntry.NO_MASTER_CHANGE);

        entry.pending = new CompletableFuture<>();
        CompletableFuture<RedisClient> second = entry.changeMaster(NEW_MASTER);
        Assertions.assertThat(second).isNotDone();
        Assertions.assertThat(entry.setupCalls).isEqualTo(2);
    }

    @Test
    void allowsNextChangeMasterWhenInFlightChangeOutlivesItsTimeout() {
        CompletableFuture<RedisClient> first = entry.changeMaster(NEW_MASTER);
        Assertions.assertThat(first).isNotDone();

        entry.timeoutNanos = 0;
        CompletableFuture<RedisClient> firstPending = entry.pending;
        entry.pending = new CompletableFuture<>();
        CompletableFuture<RedisClient> second = entry.changeMaster(NEW_MASTER);
        Assertions.assertThat(second).isNotDone();
        Assertions.assertThat(entry.setupCalls).isEqualTo(2);

        entry.timeoutNanos = Long.MAX_VALUE;
        firstPending.completeExceptionally(new RedisConnectionException("stale change finally failed"));
        Assertions.assertThat(first).isCompletedExceptionally();
        Assertions.assertThat(entry.changeMaster(NEW_MASTER)).isCompletedExceptionally();
        Assertions.assertThat(entry.setupCalls).isEqualTo(2);

        ClientConnectionsEntry newMaster = newEntry(NEW_MASTER);
        entry.masterEntry = newMaster;
        entry.pending.complete(newMaster.getClient());
        Assertions.assertThat(second.join()).isSameAs(newMaster.getClient());
        Assertions.assertThat(entry.masterChangeStartedAt).hasValue(MasterSlaveEntry.NO_MASTER_CHANGE);
    }

    @Test
    void masterChangeTimeoutCoversSequentialConnectionInit() {
        MasterSlaveServersConfig config = entry.getConfig();
        long perConnection = config.getConnectTimeout() + config.getTimeout();
        long connections = config.getMasterConnectionMinimumIdleSize()
                            + config.getSubscriptionConnectionMinimumIdleSize() + 1;

        long timeoutNanos = new MasterSlaveEntry(manager, config).masterChangeTimeoutNanos();
        Assertions.assertThat(TimeUnit.NANOSECONDS.toMillis(timeoutNanos)).isEqualTo(connections * perConnection);
    }

    private ClientConnectionsEntry newEntry(RedisURI uri) {
        RedisClientConfig clientConfig = new RedisClientConfig();
        clientConfig.setAddress(new InetSocketAddress(uri.getHost(), uri.getPort()), uri);
        RedisClient client = RedisClient.create(clientConfig);
        clients.add(client);
        return new ClientConnectionsEntry(client, 0, 1, manager, NodeType.MASTER, entry.getConfig());
    }

    private static final class ControlledMasterSlaveEntry extends MasterSlaveEntry {

        CompletableFuture<RedisClient> pending = new CompletableFuture<>();
        int setupCalls;
        long timeoutNanos = Long.MAX_VALUE;

        ControlledMasterSlaveEntry(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
            super(connectionManager, config);
        }

        @Override
        long masterChangeTimeoutNanos() {
            return timeoutNanos;
        }

        @Override
        public CompletableFuture<RedisClient> setupMasterEntry(RedisURI address) {
            setupCalls++;
            return pending;
        }

        @Override
        public CompletableFuture<RedisClient> setupMasterEntry(InetSocketAddress address, RedisURI uri) {
            setupCalls++;
            return pending;
        }
    }
}
