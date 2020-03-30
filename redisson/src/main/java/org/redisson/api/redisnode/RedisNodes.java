package org.redisson.api.redisnode;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class RedisNodes<T extends BaseRedisNodes> {

    public static final RedisNodes<RedisCluster> CLUSTER = new RedisNodes<>(RedisCluster.class);
    public static final RedisNodes<RedisMasterSlave> MASTER_SLAVE = new RedisNodes<>(RedisMasterSlave.class);
    public static final RedisNodes<RedisSentinelMasterSlave> SENTINEL_MASTER_SLAVE = new RedisNodes<>(RedisSentinelMasterSlave.class);
    public static final RedisNodes<RedisSingle> SINGLE = new RedisNodes<>(RedisSingle.class);

    private final Class<T> clazz;

    RedisNodes(Class<T> clazz) {
        this.clazz = clazz;
    }

    public Class<T> getClazz() {
        return clazz;
    }
}
