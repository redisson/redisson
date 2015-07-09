package org.redisson.client;

public interface RedisPubSubListener<V> {

    void onMessage(String channel, V message);

    void onPatternMessage(String pattern, String channel, V message);

}
