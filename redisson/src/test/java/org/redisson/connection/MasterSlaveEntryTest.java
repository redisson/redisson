package org.redisson.connection;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.misc.RedisURI;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class MasterSlaveEntryTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MasterSlaveEntry.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private MasterSlaveConnectionManager manager;
    private MasterSlaveEntry entry;

    private void setup(ReadMode readMode, boolean withSlave) throws Exception {
        Config config = new Config();
        config.setLazyInitialization(true);
        config.setNettyThreads(1);
        config.setThreads(1);
        MasterSlaveServersConfig msConfig = config.useMasterSlaveServers();
        msConfig.setMasterAddress("redis://127.0.0.1:6379");
        msConfig.setReadMode(readMode);
        msConfig.setSubscriptionMode(SubscriptionMode.SLAVE);
        msConfig.setClientAvailabilityZone("test-zone");
        // Zero idle connections lets these lifecycle tests run without a Redis server.
        msConfig.setMasterConnectionMinimumIdleSize(0);
        msConfig.setSlaveConnectionMinimumIdleSize(0);
        msConfig.setSubscriptionConnectionMinimumIdleSize(0);
        msConfig.addSlaveAddress("redis://127.0.0.1:6380");
        manager = new MasterSlaveConnectionManager(msConfig, config);
        // The manager validates static configuration; the entry represents discovered topology.
        if (!withSlave) {
            msConfig.getSlaveAddresses().clear();
        }
        entry = new MasterSlaveEntry(manager, msConfig);
        appender.start();
        logger.addAppender(appender);
        entry.setupMasterEntry(new RedisURI(msConfig.getMasterAddress())).get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        logger.detachAppender(appender);
        appender.stop();
        try {
            if (entry != null) {
                entry.shutdownAsync().get(5, TimeUnit.SECONDS);
            }
        } finally {
            if (manager != null) {
                manager.shutdown(0, 0, TimeUnit.SECONDS);
            }
        }
    }

    private List<String> fallbackMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("is used as slave"))
                .collect(Collectors.toList());
    }

    @ParameterizedTest
    @EnumSource(value = ReadMode.class, names = {"SLAVE", "AZ_AFFINITY"})
    void testStartupWithoutSlavesLogsOnce(ReadMode readMode) throws Exception {
        setup(readMode, false);

        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);
        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);

        assertThat(fallbackMessages()).containsExactly("master " + entry.getClient().getAddr()
                + " is used as slave. readMode = " + readMode);
        assertThat(entry.getAllEntries()).containsExactly(entry.getEntry());
    }

    @Test
    void testHealthyStartupDoesNotLogFallbackOrExclusion() throws Exception {
        setup(ReadMode.SLAVE, true);

        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);

        assertThat(fallbackMessages()).isEmpty();
        assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains("excluded from slaves"));
        assertThat(entry.getAllEntries()).doesNotContain(entry.getEntry());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSlaveRecoveryAllowsNextFallbackLog(boolean useUri) throws Exception {
        setup(ReadMode.SLAVE, true);
        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);
        RedisURI slave = new RedisURI("redis://127.0.0.1:6380");
        InetSocketAddress address = entry.getEntry(slave).getClient().getAddr();

        assertThat(entry.slaveDown(slave)).isTrue();
        assertThat(entry.slaveDown(slave)).isFalse();
        assertThat(fallbackMessages()).hasSize(1);

        boolean recovered;
        if (useUri) {
            recovered = entry.slaveUpAsync(slave).get(5, TimeUnit.SECONDS);
        } else {
            recovered = entry.slaveUpAsync(address).get(5, TimeUnit.SECONDS);
        }
        assertThat(recovered).isTrue();
        assertThat(entry.getAllEntries()).doesNotContain(entry.getEntry());
        assertThat(entry.slaveDown(slave)).isTrue();
        assertThat(fallbackMessages()).hasSize(2);
    }

    @Test
    void testMasterChangeDuringFallbackLogsNewMaster() throws Exception {
        setup(ReadMode.SLAVE, false);
        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);
        InetSocketAddress oldMaster = entry.getClient().getAddr();

        entry.changeMaster(new RedisURI("redis://127.0.0.1:6381")).get(5, TimeUnit.SECONDS);
        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);

        assertThat(fallbackMessages()).containsExactly(
                "master " + oldMaster + " is used as slave. readMode = SLAVE",
                "master " + entry.getClient().getAddr() + " is used as slave. readMode = SLAVE");
        assertThat(entry.getAllEntries()).containsExactly(entry.getEntry());
    }

    @ParameterizedTest
    @EnumSource(value = ReadMode.class, names = {"MASTER", "MASTER_SLAVE",
            "AZ_AFFINITY_SLAVES_AND_MASTER", "AZ_AFFINITY_MASTER_SLAVE"})
    void testIntentionalMasterReadsDoNotLogFallback(ReadMode readMode) throws Exception {
        setup(readMode, false);

        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);
        entry.changeMaster(new RedisURI("redis://127.0.0.1:6381")).get(5, TimeUnit.SECONDS);

        assertThat(fallbackMessages()).isEmpty();
        assertThat(entry.getAllEntries()).containsExactly(entry.getEntry());
    }

    @ParameterizedTest
    @EnumSource(value = ReadMode.class, names = {"MASTER_SLAVE",
            "AZ_AFFINITY_SLAVES_AND_MASTER", "AZ_AFFINITY_MASTER_SLAVE"})
    void testMasterRemainsInPoolForMixedReadModes(ReadMode readMode) throws Exception {
        setup(readMode, true);

        entry.initSlaveBalancer(u -> null).get(5, TimeUnit.SECONDS);
        RedisURI slave = new RedisURI("redis://127.0.0.1:6380");

        assertThat(entry.getAllEntries()).contains(entry.getEntry());
        assertThat(entry.excludeMasterFromSlaves(slave)).isFalse();
        assertThat(entry.excludeMasterFromSlaves(entry.getEntry(slave).getClient().getAddr())).isFalse();
        assertThat(entry.getAllEntries()).contains(entry.getEntry());
        assertThat(fallbackMessages()).isEmpty();
    }
}
