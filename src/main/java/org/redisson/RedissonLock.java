package org.redisson;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class RedissonLock implements Lock {

    private final RedisPubSubConnection<Object, Object> connection;
    private final String lockGroupName = "redisson_lock";
    private final String lockName;

    RedissonLock(RedisPubSubConnection<Object, Object> connection, String lockName) {
        this.connection = connection;
        this.lockName = lockName;
    }

    private CountDownLatch subscribe(final RedisPubSubConnection<Object, Object> connection) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        connection.addListener(new RedisPubSubAdapter<Object, Object>() {

            @Override
            public void subscribed(Object channel, long count) {
                if (tryLock()) {
                    connection.unsubscribe(getChannelName());
                    countDownLatch.countDown();
                }
            }

            @Override
            public void message(Object channel, Object message) {
                if (tryLock()) {
                    connection.unsubscribe(getChannelName());
                    countDownLatch.countDown();
                }
            }
        });

        connection.subscribe(getChannelName());
        return countDownLatch;
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getChannelName() {
        return lockGroupName + lockName;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (!tryLock()) {
            CountDownLatch countDownLatch = subscribe(connection);
            countDownLatch.await();
        }
    }

    @Override
    public boolean tryLock() {
        try {
            return connection.hsetnx(lockGroupName, lockName, true).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if (!tryLock()) {
            CountDownLatch countDownLatch = subscribe(connection);
            countDownLatch.await(time, unit);
        }
        return true;
    }

    @Override
    public void unlock() {
        connection.hdel(lockGroupName, lockName);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

}
