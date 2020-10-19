package org.redisson.rx;

import org.redisson.RedissonList;
import org.redisson.RedissonListMultimapCache;
import org.redisson.RedissonSet;
import org.redisson.RedissonSetMultimapCache;
import org.redisson.api.RListRx;
import org.redisson.api.RSetRx;
import org.redisson.api.RedissonRxClient;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonListMultimapCacheRx<K, V> {

    private final RedissonListMultimapCache<K, V> instance;
    private final CommandRxExecutor commandExecutor;

    public RedissonListMultimapCacheRx(RedissonListMultimapCache<K, V> instance, CommandRxExecutor commandExecutor) {
        this.instance = instance;
        this.commandExecutor = commandExecutor;
    }

    public RListRx<V> get(K key) {
        RedissonList<V> list = (RedissonList<V>) instance.get(key);
        return RxProxyBuilder.create(commandExecutor, list, new RedissonListRx<>(list), RListRx.class);
    }
}
