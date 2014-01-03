package org.redisson;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

import org.redisson.core.RTopic;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.JsonCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class Redisson {

    // TODO drain after some time
    private final ConcurrentMap<String, RedissonTopic> topicsMap = new ConcurrentHashMap<String, RedissonTopic>();
    private final ConcurrentMap<String, RedissonSet> setsMap = new ConcurrentHashMap<String, RedissonSet>();
    private final ConcurrentMap<String, RedissonList> listsMap = new ConcurrentHashMap<String, RedissonList>();
    private final ConcurrentMap<String, RedissonMap> mapsMap = new ConcurrentHashMap<String, RedissonMap>();
    private final ConcurrentMap<String, RedissonLock> locksMap = new ConcurrentHashMap<String, RedissonLock>();

    private JsonCodec codec = new JsonCodec();

    RedisClient redisClient;

    Redisson(String host, int port) {
        redisClient = new RedisClient(host, port);
    }

    public static Redisson create() {
        return create("localhost");
    }

    public static Redisson create(String host) {
        return create(host, 6379);
    }

    public static Redisson create(String host, int port) {
        return new Redisson(host, port);
    }

    public <V> List<V> getList(String name) {
        RedissonList<V> list = listsMap.get(name);
        if (list == null) {
            RedisConnection<Object, Object> connection = connect();
            list = new RedissonList<V>(this, connection, name);
            RedissonList<V> oldList = listsMap.putIfAbsent(name, list);
            if (oldList != null) {
                connection.close();

                list = oldList;
            }
        }

        return list;
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

    public <V> Set<V> getSet(String name) {
        RedissonSet<V> set = setsMap.get(name);
        if (set == null) {
            RedisConnection<Object, Object> connection = connect();
            set = new RedissonSet<V>(this, connection, name);
            RedissonSet<V> oldSet = setsMap.putIfAbsent(name, set);
            if (oldSet != null) {
                connection.close();

                set = oldSet;
            }
        }

        return set;
    }

    public <M> RTopic<M> getTopic(String name) {
        RedissonTopic<M> topic = topicsMap.get(name);
        if (topic == null) {
            RedisConnection<Object, Object> connection = connect();
            RedisPubSubConnection<Object, Object> pubSubConnection = redisClient.connectPubSub(codec);

            topic = new RedissonTopic<M>(pubSubConnection, connection, name);
            RedissonTopic<M> oldTopic = topicsMap.putIfAbsent(name, topic);
            if (oldTopic != null) {
                connection.close();
                pubSubConnection.close();

                topic = oldTopic;
            }
        }

        topic.subscribe();
        return topic;

    }

    public void getQueue() {

    }

    public void getAtomicLong() {

    }

    public void getCountDownLatch() {

    }

    public void getSemaphore() {

    }

    public void getExecutorService() {

    }

    public void shutdown() {
        redisClient.shutdown();
    }

    RedisConnection<Object, Object> connect() {
        return redisClient.connect(codec);
    }
}

