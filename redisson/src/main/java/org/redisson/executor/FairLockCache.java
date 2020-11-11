/**
 * Copyright (c) 2013-2020 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.executor;

import org.redisson.RedissonFairLock;
import org.redisson.RedissonObject;
import org.redisson.command.CommandAsyncExecutor;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Store all fairlock names and ttl
 * @author wuqian30624
 */
public class FairLockCache {
    private ScheduledExecutorService executorRefresh = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService executorClean = Executors.newSingleThreadScheduledExecutor();
    private volatile ConcurrentHashMap<String, String> registeredLocks = new ConcurrentHashMap<String, String>();
    private String clientId;
    private long ttl =  RedissonFairLock.DEFAULT_THREAD_WAIT_TIME;
    private static final String DEFAULT_LOCK_VALUE = "1";

    public FairLockCache(String clientId, long ttl){
        this.clientId = clientId;
        this.ttl = ttl;
    }

    public boolean isLockRegistered(String name){
        String elementName = getClientQueueElementName(name);
        return registeredLocks.containsKey(elementName);
    }

    public void registerLockIfAbsent(String name){
        String elementName = getClientQueueElementName(name);
        registeredLocks.putIfAbsent(elementName, DEFAULT_LOCK_VALUE);
    }

    public Set<String> getRegisteredLocks() {
        return registeredLocks.keySet();
    }

    public String getClientId() {
        return clientId;
    }

    public long getTtl() {
        return ttl;
    }

    public void startRefresh(CommandAsyncExecutor commandAsyncExecutor){
        long interval = ttl;
        if (ttl > 2){
            interval = ttl / 2;
        }
        FairLockClientRefreshThread refreshThread = new FairLockClientRefreshThread(this, commandAsyncExecutor);
        executorRefresh.scheduleAtFixedRate(refreshThread, 0, interval, TimeUnit.MILLISECONDS);
    }

    public void endRefresh(CommandAsyncExecutor commandAsyncExecutor){
        try {
            executorRefresh.shutdownNow();
            new Thread(new FairLockClientCleanThread(this, commandAsyncExecutor)).run();
        }catch (Exception e){
            // Failure during shutdown. Ignore
        }
    }

    /**
     * Get formated queue with threadid name
     * @param name
     * @return
     */
    public String getClientQueueElementName(String name){
        return getClientQueueName(name) + ":" + getClientId();
    }

    /**
     * Get formated client queue name for fairlock
     * @param name
     * @return
     */
    public String getClientQueueName(String name){
        return RedissonObject.prefixName(RedissonFairLock.REGISTERED_CLIENT_PREFIX, name);
    }
}
