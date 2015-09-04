package org.redisson;

import org.redisson.client.RedisClient;

public interface RedisClientResult {

    void setRedisClient(RedisClient client);

    RedisClient getRedisClient();

}
