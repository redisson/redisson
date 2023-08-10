package org.redisson.spring.starter.lock;

import org.junit.jupiter.api.Test;
import org.redisson.spring.starter.RedissonApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.Assert;

/**
 * @author 985492783@qq.com
 * @description LockTestBean
 * @date 2023/8/10 16:35
 */
@SpringJUnitConfig
@SpringBootTest(
        classes = RedissonApplication.class,
        properties = {
                "spring.redis.redisson.file=classpath:redisson.yaml",
                "spring.redis.timeout=10000"
        })
public class RMutexLockTest {
    @Autowired
    private RMutexComponent component;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void testMethod() {
        Assert.isTrue(component.getLock(), "lock fail!");
        Assert.isTrue(!redisTemplate.hasKey(RMutexComponent.key), "unlock fail!");
    }
}
