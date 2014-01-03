package org.redisson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;
import org.redisson.core.RedisPubSubTopicListener;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class RedissonTopic<M> implements RTopic<M> {

    private final CountDownLatch subscribeLatch = new CountDownLatch(1);
    private final AtomicBoolean subscribeOnce = new AtomicBoolean();

    private final Map<Integer, RedisPubSubTopicListener> listeners = new HashMap<Integer, RedisPubSubTopicListener>();
    private final RedisPubSubConnection<Object, Object> pubSubConnection;
    private final RedisConnection<Object, Object> connection;
    private final String name;

    RedissonTopic(RedisPubSubConnection<Object, Object> pubSubConnection, RedisConnection<Object, Object> connection, final String name) {
        this.pubSubConnection = pubSubConnection;
        this.name = name;
        this.connection = connection;
    }

    public void subscribe() {
        if (subscribeOnce.compareAndSet(false, true)) {
            RedisPubSubAdapter<Object, Object> listener = new RedisPubSubAdapter<Object, Object>() {

                @Override
                public void subscribed(Object channel, long count) {
                    if (channel.equals(name)) {
                        subscribeLatch.countDown();
                    }
                }

            };
            pubSubConnection.addListener(listener);
            pubSubConnection.subscribe(name);
        }

        try {
            subscribeLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void publish(M message) {
        connection.publish(name, message);
    }

    @Override
    public int addListener(MessageListener<M> listener) {
        RedisPubSubTopicListener list = new RedisPubSubTopicListener(listener);
        listeners.put(list.hashCode(), list);
        pubSubConnection.addListener(list);
        return list.hashCode();
    }

    @Override
    public void removeListener(int listenerId) {
        RedisPubSubTopicListener list = listeners.remove(listenerId);
        pubSubConnection.removeListener(list);
    }


}
