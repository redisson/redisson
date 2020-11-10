package org.redisson.executor;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.util.Arrays;

/**
 * Remove all registered fairlock client info
 * from current client
 * @author wuqian30624
 */
public class FairLockClientCleanThread extends AbstractFairLockClientThread {
    private static String threadName = "fairLock-clean";

    public FairLockClientCleanThread(FairLockCache fairLockCache,
                                     CommandAsyncExecutor commandExecutor){
        super(fairLockCache, commandExecutor, threadName);
    }

    @Override
    protected void executeCommands(String fairLock) {
        evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "redis.call('del', KEYS[1], 1);",
                Arrays.<Object>asList(fairLock));
    }
}

