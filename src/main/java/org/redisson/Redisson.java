package org.redisson;

import java.util.Map;
import java.util.concurrent.locks.Lock;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.JsonCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class Redisson {

    RedisClient redisClient;

    Redisson() {
        redisClient = new RedisClient("localhost");
    }

    public static Redisson create() {
        return new Redisson();
    }

    public <K, V> Map<K, V> getMap(String name) {
        RedisConnection<Object, Object> connection = redisClient.connect(new JsonCodec());
        return new RedissonMap<K, V>(connection, name);
    }

    public Lock getLock(String name) {
        RedisPubSubConnection<Object, Object> connection = redisClient.connectPubSub(new JsonCodec());
        return new RedissonLock(connection, name);
    }


}
