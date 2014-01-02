package org.redisson;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.JsonCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class Redisson {

    // TODO drain after some time
    private final ConcurrentMap<String, RedissonMap> mapsMap = new ConcurrentHashMap<String, RedissonMap>();
    private final ConcurrentMap<String, RedissonLock> locksMap = new ConcurrentHashMap<String, RedissonLock>();

    private JsonCodec codec = new JsonCodec();

    RedisClient redisClient;

    Redisson() {
        redisClient = new RedisClient("localhost");
    }

    public static Redisson create() {
        return new Redisson();
    }

    public <K, V> ConcurrentMap<K, V> getMap(String name) {
        RedissonMap<K, V> map = mapsMap.get(name);
        if (map == null) {
            RedisConnection<Object, Object> connection = connect();
            map = new RedissonMap<K, V>(this, connection, name);
            RedissonMap<K, V> oldMap = mapsMap.putIfAbsent(name, map);
            if (oldMap != null) {
                connection.close();

                map = oldMap;
            }
        }

        return map;
    }

    RedisConnection<Object, Object> connect() {
        return redisClient.connect(codec);
    }

    public Lock getLock(String name) {
        RedissonLock lock = locksMap.get(name);
        if (lock == null) {
            RedisConnection<Object, Object> connection = connect();
            RedisPubSubConnection<Object, Object> pubSubConnection = redisClient.connectPubSub(codec);

            lock = new RedissonLock(pubSubConnection, connection, name);
            RedissonLock oldLock = locksMap.putIfAbsent(name, lock);
            if (oldLock != null) {
                connection.close();
                pubSubConnection.close();

                lock = oldLock;
            }
        }

        lock.subscribe();
        return lock;
    }

}
