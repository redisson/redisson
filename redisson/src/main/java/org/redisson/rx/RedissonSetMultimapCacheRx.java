package org.redisson.rx;

import org.redisson.RedissonSet;
import org.redisson.RedissonSetMultimapCache;
import org.redisson.api.RSetRx;
import org.redisson.api.RedissonRxClient;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonSetMultimapCacheRx<K, V> {

    private final RedissonSetMultimapCache<K, V> instance;
    private final CommandRxExecutor commandExecutor;
    private final RedissonRxClient redisson;

    public RedissonSetMultimapCacheRx(RedissonSetMultimapCache<K, V> instance, CommandRxExecutor commandExecutor,
                                      RedissonRxClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
        this.commandExecutor = commandExecutor;
    }

    public RSetRx<V> get(K key) {
        RedissonSet<V> set = (RedissonSet<V>) instance.get(key);
        return RxProxyBuilder.create(commandExecutor, set, new RedissonSetRx<>(set, redisson), RSetRx.class);
    }
}
