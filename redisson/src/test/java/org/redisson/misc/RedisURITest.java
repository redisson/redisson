package org.redisson.misc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisURITest {

    @Test
    public void testIPv6() {
        RedisURI uri1 = new RedisURI("redis://2001:db8::1:6379");
        assertThat(uri1.getHost()).isEqualTo("[2001:db8::1]");
        assertThat(uri1.getPort()).isEqualTo(6379);

        RedisURI uri2 = new RedisURI("redis://2001:0db8:0000:0000:0000:ff00:0042:8329:6379");
        assertThat(uri2.getHost()).isEqualTo("[2001:0db8:0000:0000:0000:ff00:0042:8329]");
        assertThat(uri2.getPort()).isEqualTo(6379);
    }

    @Test
    public void testUsernamePassword() {
        RedisURI uri1 = new RedisURI("redis://user:password@compute-1.amazonaws.com:15319");
        assertThat(uri1.getUsername()).isEqualTo("user");
        assertThat(uri1.getPassword()).isEqualTo("password");
    }

}
