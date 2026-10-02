package org.redisson.command;

import mockit.Expectations;
import mockit.Mocked;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ServiceManager;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RedisExecutorTest {

    @Test
    void largePoolDoesNotUseEagerReleaseByDefault(@Mocked ConnectionManager connectionManager,
                                                  @Mocked ServiceManager serviceManager) {
        assertThat(isEagerConnectionRelease(connectionManager, serviceManager, 24, false)).isFalse();
    }

    @Test
    void settingEnablesEagerReleaseForLargePool(@Mocked ConnectionManager connectionManager,
                                                @Mocked ServiceManager serviceManager) {
        assertThat(isEagerConnectionRelease(connectionManager, serviceManager, 24, true)).isTrue();
    }

    @Test
    void smallPoolUsesEagerReleaseByDefault(@Mocked ConnectionManager connectionManager,
                                            @Mocked ServiceManager serviceManager) {
        assertThat(isEagerConnectionRelease(connectionManager, serviceManager, 5, false)).isTrue();
    }

    @Test
    void poolSizeAtThresholdDoesNotUseEagerReleaseByDefault(@Mocked ConnectionManager connectionManager,
                                                            @Mocked ServiceManager serviceManager) {
        assertThat(isEagerConnectionRelease(connectionManager, serviceManager, 10, false)).isFalse();
    }

    private boolean isEagerConnectionRelease(ConnectionManager connectionManager, ServiceManager serviceManager,
                                            int masterConnectionPoolSize, boolean eagerConnectionRelease) {
        Config config = new Config();
        config.setEagerConnectionRelease(eagerConnectionRelease);
        MasterSlaveServersConfig serversConfig = new MasterSlaveServersConfig();
        serversConfig.setMasterConnectionPoolSize(masterConnectionPoolSize);

        new Expectations() {{
            connectionManager.getServiceManager();
            result = serviceManager;
            minTimes = 0;
            serviceManager.getCfg();
            result = config;
            minTimes = 0;
            serviceManager.getConfig();
            result = serversConfig;
            minTimes = 0;
        }};

        RedisExecutor<Object, Object> executor = new RedisExecutor<>(false, null, null, null, null,
                new CompletableFuture<>(), false, connectionManager, null, null, false,
                0, null, 0, false, ReadMode.MASTER);
        return executor.isEagerConnectionRelease();
    }
}
