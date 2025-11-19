package org.redisson.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

public class JitterDelayTest {

    @Test
    public void testFull() {
        FullJitterDelay delay = new FullJitterDelay(Duration.ofMillis(10), Duration.ofMillis(100));
        for (int i = 0; i < 1000; i++) {
            delay.calcDelay(i);
        }
    }

    @Test
    public void testEqual() {
        EqualJitterDelay delay = new EqualJitterDelay(Duration.ofMillis(10), Duration.ofMillis(100));
        for (int i = 0; i < 1000; i++) {
            delay.calcDelay(i);
        }
    }

}
