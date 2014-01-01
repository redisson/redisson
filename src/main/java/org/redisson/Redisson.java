package org.redisson;

import java.util.Map;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.JsonCodec;

public class Redisson {

    private final RedisConnection<Object, Object> connection;

    Redisson() {
        RedisClient redisClient = new RedisClient("localhost");
        connection = redisClient.connect(new JsonCodec());
    }

    public static Redisson create() {
        return new Redisson();
    }

    public <K, V> Map<K, V> getMap(String name) {
        return new RedissonMap<K, V>(connection, name);
    }

}
