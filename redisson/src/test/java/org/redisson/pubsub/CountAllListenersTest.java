package org.redisson.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.client.codec.LongCodec;

public class CountAllListenersTest extends RedisDockerTest {

    @Test
    public void testCountAllListeners() {
        final PublishSubscribeService subscribeService = ((Redisson) redisson)
                .getConnectionManager().getSubscribeService();
        assertThat(subscribeService.countAllListeners()).isZero();

        RTopic topic1 = redisson.getTopic("topic", LongCodec.INSTANCE);
        assertThat(topic1.countListeners()).isZero();
        int id = topic1.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(topic1.countListeners()).isOne();
        assertThat(subscribeService.countAllListeners()).isOne();

        RTopic topic2 = redisson.getTopic("topic2", LongCodec.INSTANCE);
        assertThat(topic2.countListeners()).isZero();
        int id2 = topic2.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(subscribeService.countAllListeners()).isEqualTo(2);
        assertThat(topic2.countListeners()).isOne();
        int id3 = topic2.addListener(Long.class, (channel, msg) -> {
        });
        assertThat(topic2.countListeners()).isEqualTo(2);
        assertThat(subscribeService.countAllListeners()).isEqualTo(3);

        topic1.removeListener(id);
        assertThat(topic1.countListeners()).isZero();
        assertThat(subscribeService.countAllListeners()).isEqualTo(2);

        topic2.removeListener(id2);
        topic2.removeListener(id3);
        assertThat(topic2.countListeners()).isZero();
        assertThat(subscribeService.countAllListeners()).isZero();
    }

}
