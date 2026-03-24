package org.redisson.api.stream;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamParamsTest {

    @Test
    public void testStreamRangeParamsEqualsAndHashCode() {
        StreamRangeParams params1 = new StreamRangeParams(new StreamMessageId(1, 1), true);
        params1.endIdExclusive(new StreamMessageId(2, 2));
        params1.count(10);

        StreamRangeParams params2 = new StreamRangeParams(new StreamMessageId(1, 1), true);
        params2.endIdExclusive(new StreamMessageId(2, 2));
        params2.count(10);

        StreamRangeParams params3 = new StreamRangeParams(new StreamMessageId(1, 1), true);
        params3.endIdExclusive(new StreamMessageId(2, 2));
        params3.count(11);

        assertThat(params1).isEqualTo(params2);
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
        assertThat(params1).isNotEqualTo(params3);
    }

    @Test
    public void testStreamPendingRangeParamsEqualsAndHashCode() {
        StreamPendingRangeParams params1 = new StreamPendingRangeParams("group");
        params1.consumerName("consumer");
        params1.startIdExclusive(new StreamMessageId(1, 1));
        params1.endIdExclusive(new StreamMessageId(2, 2));
        params1.count(10);
        params1.idleTime(Duration.ofSeconds(5));

        StreamPendingRangeParams params2 = new StreamPendingRangeParams("group");
        params2.consumerName("consumer");
        params2.startIdExclusive(new StreamMessageId(1, 1));
        params2.endIdExclusive(new StreamMessageId(2, 2));
        params2.count(10);
        params2.idleTime(Duration.ofSeconds(5));

        StreamPendingRangeParams params3 = new StreamPendingRangeParams("group");
        params3.consumerName("consumer");
        params3.startIdExclusive(new StreamMessageId(1, 1));
        params3.endIdExclusive(new StreamMessageId(2, 2));
        params3.count(10);
        params3.idleTime(Duration.ofSeconds(6));

        assertThat(params1).isEqualTo(params2);
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
        assertThat(params1).isNotEqualTo(params3);
    }
}
