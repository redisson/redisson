package org.redisson.connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.RedissonShutdownException;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ServiceManagerShutdownTest {

    private static ServiceManager serviceManager;

    @BeforeAll
    static void setUp() {
        serviceManager = new ServiceManager(new MasterSlaveServersConfig(), new Config());
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        serviceManager.getTimer().stop();
        serviceManager.getGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        serviceManager.getExecutor().shutdown();
    }

    @Test
    void nullExceptionIsNotShutdown() {
        assertThatCode(() -> assertThat(serviceManager.isShuttingDown(null)).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    void shutdownExceptionAndCauseAreDetected() {
        RedissonShutdownException shutdown = new RedissonShutdownException("shutting down");

        assertThat(serviceManager.isShuttingDown(shutdown)).isTrue();
        assertThat(serviceManager.isShuttingDown(new RuntimeException(shutdown))).isTrue();
    }

    @Test
    void unrelatedExceptionIsNotShutdown() {
        assertThat(serviceManager.isShuttingDown(new RuntimeException("ordinary failure"))).isFalse();
    }

}
