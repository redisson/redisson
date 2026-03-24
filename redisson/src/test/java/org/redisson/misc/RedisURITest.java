package org.redisson.misc;

import org.junit.jupiter.api.Assertions;
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
    public void testIPv6Braces() {
        RedisURI uri1 = new RedisURI("redis://[2001:db8::1]:6379");
        assertThat(uri1.getHost()).isEqualTo("[2001:db8::1]");
        assertThat(uri1.getPort()).isEqualTo(6379);

        RedisURI uri2 = new RedisURI("redis://[2001:0db8:0000:0000:0000:ff00:0042:8329]:6379");
        assertThat(uri2.getHost()).isEqualTo("[2001:0db8:0000:0000:0000:ff00:0042:8329]");
        assertThat(uri2.getPort()).isEqualTo(6379);
    }

    @Test
    public void testHostname() {
        Assertions.assertThrowsExactly(IllegalArgumentException.class, () -> {
            new RedisURI("redis:///localhost:15319");
        }, "Redis host can't be parsed");
    }

    @Test
    public void testUsernamePassword() {
        RedisURI uri1 = new RedisURI("redis://user:password@compute-1.amazonaws.com:15319");
        assertThat(uri1.getUsername()).isEqualTo("user");
        assertThat(uri1.getPassword()).isEqualTo("password");
    }

    @Test
    public void testToken() {
        RedisURI uri1 = new RedisURI("redis://my-token@compute-1.amazonaws.com:15319");
        assertThat(uri1.getUsername()).isEqualTo(null);
        assertThat(uri1.getPassword()).isEqualTo("my-token");
    }

}
