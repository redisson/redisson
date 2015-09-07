package org.redisson.client;

public interface ReconnectListener {

    void onReconnect(RedisConnection redisConnection);

}
