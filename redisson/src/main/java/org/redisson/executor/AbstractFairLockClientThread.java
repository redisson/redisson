package org.redisson.executor;

import org.redisson.RedissonLock;
import org.redisson.command.CommandAsyncExecutor;

/**
 * @author wuqian30624
 */
public abstract class AbstractFairLockClientThread extends RedissonLock implements Runnable {
    protected String threadName;
    protected FairLockCache fairLockCache;

    public AbstractFairLockClientThread(FairLockCache fairLockCache,
                                 CommandAsyncExecutor commandExecutor, String threadName){
        super(commandExecutor, threadName);
        this.fairLockCache = fairLockCache;
        this.threadName = threadName;
    }

    @Override
    public String getName() {
        return threadName;
    }

    @Override
    public void run() {
        if (null != fairLockCache && fairLockCache.getRegisteredLocks().size() > 0) {
            for (String fairLockName:fairLockCache.getRegisteredLocks()) {
                try {
                    executeCommands(fairLockName);
                }catch (Exception e){
                    // failure during executing commands
                }
            }
        }
    }

    /**
     * Execute redis command
     * @param fairLock
     */
    protected abstract void executeCommands(String fairLock);
}
