package org.redisson;

import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.pubsub.LockPubSub;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RedissonIntentLock extends RedissonLock {

    private static final String INTENT_LOCK_PREFIX = "INTENT_LOCK";

    public RedissonIntentLock(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonIntentLock(String name, CommandAsyncExecutor commandExecutor) {
        super(name, commandExecutor);
    }

    @Override
    public boolean isHeldByCurrentThread() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isHeldByThread(long threadId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> isHeldByThreadAsync(long threadId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Integer> getHoldCountAsync() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHoldCount() {
        throw new UnsupportedOperationException();
    }

    private String getIntentLockName(String key) {
        return INTENT_LOCK_PREFIX + ":" + key.hashCode();
    }

    @Override
    <T> RFuture<T> tryLockInnerAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
        return evalWriteSyncedNoRetryAsync(getRawName(), LongCodec.INSTANCE, command,
                "if (redis.call('exists', KEYS[1]) == 0) then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                        "redis.call('hincrby', KEYS[1], ARGV[3], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]); " +
                        "return nil; " +
                        "end; " +
                        "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
                        "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
                        "redis.call('hincrby', KEYS[1], ARGV[3], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);" +
                        "return nil;" +
                        "end; " +
                        "return redis.call('pttl', KEYS[1]);",
                Collections.singletonList(getRawName()), unit.toMillis(leaseTime),getIntentLockName(getRawName()), getLockName(threadId));
    }

    @Override
    protected RFuture<Boolean> unlockInnerAsync(long threadId, String requestId, int timeout) {
        return evalWriteSyncedNoRetryAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local val = redis.call('get', KEYS[3]);  " +
                        "      if val ~= false then  " +
                        "          return tonumber(val); " +
                        "       end; " +
                        "       if (redis.call('hexists', KEYS[1], ARGV[3]) == 0 " +
                        "  or redis.call('hexists', KEYS[1], ARGV[6]) == 0) then  " +
                        "           return nil; " +
                        "       end; " +
                        "       local counter = 1;" +
                        "       local intentCount = redis.call('hincrby',KEYS[1], ARGV[6],-1);" +
                        "       if (intentCount >= 0) then " +
                        "           counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); " +
                        "           end;" +
                        "       if (counter > 0) then " +
                        "           redis.call('pexpire', KEYS[1], ARGV[2]); " +
                        "           redis.call('set', KEYS[3], 0, 'px', ARGV[5]);" +
                        "           return 0;  " +
                        "       else  " +
                        "           redis.call('del', KEYS[1]);  " +
                        "           redis.call(ARGV[4], KEYS[2], ARGV[1]); " +
                        "           redis.call('set', KEYS[3], 1, 'px', ARGV[5]); " +
                        "           return 1; " +
                        "       end;  ",
                Arrays.asList(getRawName(), getChannelName(), getUnlockLatchName(requestId)),
                LockPubSub.UNLOCK_MESSAGE, internalLockLeaseTime,
                getIntentLockName(getRawName()), getSubscribeService().getPublishCommand(), timeout, getLockName(threadId));
    }
}