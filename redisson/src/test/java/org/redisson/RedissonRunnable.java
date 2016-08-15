package org.redisson;

import org.redisson.api.RedissonClient;

public interface RedissonRunnable {

    void run(RedissonClient redisson);

}
