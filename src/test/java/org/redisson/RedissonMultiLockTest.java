package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.Test;
import org.redisson.core.RedissonMultiLock;
import org.redisson.core.RLock;

import io.netty.channel.nio.NioEventLoopGroup;

public class RedissonMultiLockTest {

    @Test
    public void test() throws IOException, InterruptedException {
        Process redis1 = RedisRunner.runRedis("/redis_multiLock_test_instance1.conf");
        Process redis2 = RedisRunner.runRedis("/redis_multiLock_test_instance2.conf");
        Process redis3 = RedisRunner.runRedis("/redis_multiLock_test_instance3.conf");

        NioEventLoopGroup group = new NioEventLoopGroup();
        Config config1 = new Config();
        config1.useSingleServer().setAddress("127.0.0.1:6320");
        config1.setEventLoopGroup(group);
        RedissonClient client1 = Redisson.create(config1);

        Config config2 = new Config();
        config2.useSingleServer().setAddress("127.0.0.1:6321");
        config2.setEventLoopGroup(group);
        RedissonClient client2 = Redisson.create(config2);

        Config config3 = new Config();
        config3.useSingleServer().setAddress("127.0.0.1:6322");
        config3.setEventLoopGroup(group);
        RedissonClient client3 = Redisson.create(config3);

        RLock lock1 = client1.getLock("lock1");
        RLock lock2 = client2.getLock("lock2");
        RLock lock3 = client3.getLock("lock3");

        RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);
        lock.lock();
        lock.unlock();

        redis1.destroy();
        assertThat(redis1.waitFor()).isEqualTo(1);

        redis2.destroy();
        assertThat(redis2.waitFor()).isEqualTo(1);

        redis3.destroy();
        assertThat(redis3.waitFor()).isEqualTo(1);
    }

}
