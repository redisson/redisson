package org.redisson.spring.starter.lock;

import org.redisson.spring.annotation.RMutexLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @author qiyue.zhang@aloudata.com
 * @description RMutexComponent
 * @date 2023/8/10 17:17
 */
@Component
public class RMutexComponent {
    public static final String key = "test";
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @RMutexLock(name = key)
    public Boolean getLock() {
        return redisTemplate.hasKey(key);
    }
}
