package org.redisson.executor;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Arrays;

/**
 * @author wuqian30624
 */
public class FairLockClientRefreshThread extends AbstractFairLockClientThread{
    private static String threadName = "fairLock-refresh";

    public FairLockClientRefreshThread(FairLockCache fairLockCache,
                                       CommandAsyncExecutor commandExecutor){
        super(fairLockCache, commandExecutor, threadName);
    }

    @Override
    protected void executeCommands(String fairLock) {
        evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "redis.call('set', KEYS[1], 1);" +
                        "redis.call('pexpire', KEYS[1], ARGV[1]);",
                Arrays.<Object>asList(fairLock),
                fairLockCache.getTtl());
    }

}
