package org.redisson.pubsub;

import org.redisson.RedissonLock;
import org.redisson.RedissonLockEntry;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

import io.netty.util.concurrent.Promise;

public class LockPubSub extends PublishSubscribe<RedissonLockEntry> {

    @Override
    protected RedissonLockEntry createEntry(Promise<RedissonLockEntry> newPromise) {
        return new RedissonLockEntry(newPromise);
    }

    @Override
    protected RedisPubSubListener<Long> createListener(final String channelName, final RedissonLockEntry value) {
        RedisPubSubListener<Long> listener = new BaseRedisPubSubListener<Long>() {

            @Override
            public void onMessage(String channel, Long message) {
                if (!channelName.equals(channel)) {
                    return;
                }

                if (message.equals(RedissonLock.unlockMessage)) {
                    value.getLatch().release();
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
