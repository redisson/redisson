package org.redisson.pubsub;

import org.redisson.RedissonCountDownLatch;
import org.redisson.RedissonCountDownLatchEntry;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

import io.netty.util.concurrent.Promise;

public class CountDownLatchPubSub extends PublishSubscribe<RedissonCountDownLatchEntry> {

    @Override
    protected RedissonCountDownLatchEntry createEntry(Promise<RedissonCountDownLatchEntry> newPromise) {
        return new RedissonCountDownLatchEntry(newPromise);
    }

    @Override
    protected RedisPubSubListener<Long> createListener(final String channelName, final RedissonCountDownLatchEntry value) {
        RedisPubSubListener<Long> listener = new BaseRedisPubSubListener<Long>() {

            @Override
            public void onMessage(String channel, Long message) {
                if (!channelName.equals(channel)) {
                    return;
                }

                if (message.equals(RedissonCountDownLatch.zeroCountMessage)) {
                    value.getLatch().open();
                }
                if (message.equals(RedissonCountDownLatch.newCountMessage)) {
                    value.getLatch().close();
                }
            }

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (!channelName.equals(channel)) {
                    return false;
                }

                if (type == PubSubType.SUBSCRIBE) {
                    value.getPromise().trySuccess(value);
                    return true;
                }
                return false;
            }

        };
        return listener;
    }


}
