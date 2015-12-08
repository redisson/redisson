package org.redisson.pubsub;

import java.util.concurrent.ConcurrentMap;

import org.redisson.PubSubEntry;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.LongCodec;
import org.redisson.connection.ConnectionManager;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

abstract class PublishSubscribe<E extends PubSubEntry<E>> {

    private final ConcurrentMap<String, E> entries = PlatformDependent.newConcurrentHashMap();

    public void unsubscribe(E entry, String entryName, String channelName, ConnectionManager connectionManager) {
        synchronized (this) {
            if (entry.release() == 0) {
                // just an assertion
                boolean removed = entries.remove(entryName) == entry;
                if (removed) {
                    connectionManager.unsubscribe(channelName);
                }
            }
        }
    }

    public E getEntry(String entryName) {
        return entries.get(entryName);
    }

    public Future<E> subscribe(String entryName, String channelName, ConnectionManager connectionManager) {
        synchronized (this) {
            E entry = entries.get(entryName);
            if (entry != null) {
                entry.aquire();
                return entry.getPromise();
            }

            Promise<E> newPromise = connectionManager.newPromise();
            E value = createEntry(newPromise);
            value.aquire();

            E oldValue = entries.putIfAbsent(entryName, value);
            if (oldValue != null) {
                oldValue.aquire();
                return oldValue.getPromise();
            }

            RedisPubSubListener<Long> listener = createListener(channelName, value);
            connectionManager.subscribe(LongCodec.INSTANCE, channelName, listener);
            return newPromise;
        }
    }

    protected abstract E createEntry(Promise<E> newPromise);

    protected abstract RedisPubSubListener<Long> createListener(String channelName, E value);
}
