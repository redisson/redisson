package org.redisson.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigTest {

    @Test
    public void testCopyConstructorKeepsTcpSettings() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        config.setTcpKeepAlive(false)
                .setTcpKeepAliveCount(7)
                .setTcpKeepAliveIdle(11)
                .setTcpKeepAliveInterval(13)
                .setTcpUserTimeout(17000)
                .setTcpNoDelay(false);

        Config copy = new Config(config);

        assertThat(copy.isTcpKeepAlive()).isFalse();
        assertThat(copy.getTcpKeepAliveCount()).isEqualTo(7);
        assertThat(copy.getTcpKeepAliveIdle()).isEqualTo(11);
        assertThat(copy.getTcpKeepAliveInterval()).isEqualTo(13);
        assertThat(copy.getTcpUserTimeout()).isEqualTo(17000);
        assertThat(copy.isTcpNoDelay()).isFalse();
    }

    @Test
    public void testCopyConstructorKeepsTcpDefaults() {
        Config copy = new Config(new Config());

        assertThat(copy.isTcpKeepAlive()).isTrue();
        assertThat(copy.isTcpNoDelay()).isTrue();
    }
}
