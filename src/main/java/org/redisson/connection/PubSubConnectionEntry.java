package org.redisson.connection;

import java.util.concurrent.Semaphore;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

public class PubSubConnectionEntry {

    private final Semaphore semaphore;
    private final RedisPubSubConnection conn;
    private final int subscriptionsPerConnection;

    public PubSubConnectionEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
        super();
        this.conn = conn;
        this.subscriptionsPerConnection = subscriptionsPerConnection;
        this.semaphore = new Semaphore(subscriptionsPerConnection);
    }

    public void addListener(RedisPubSubListener listener) {
        conn.addListener(listener);
    }

    public void removeListener(RedisPubSubListener listener) {
        conn.removeListener(listener);
    }

    public boolean subscribe(RedisPubSubAdapter listener, Object channel) {
        if (semaphore.tryAcquire()) {
            conn.addListener(listener);
            conn.subscribe(channel);
            return true;
        }
        return false;
    }

    public void unsubscribe(Object channel) {
        conn.unsubscribe(channel);
        semaphore.release();
    }

    public boolean tryClose() {
        if (semaphore.tryAcquire(subscriptionsPerConnection)) {
            conn.close();
            return true;
        }
        return false;
    }

}
