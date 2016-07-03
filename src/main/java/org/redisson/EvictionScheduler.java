/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * Eviction scheduler for RMapCache object.
 * Deletes expired entries in time interval between 5 seconds to 2 hours.
 * It analyzes deleted amount of expired keys
 * and 'tune' next execution delay depending on it.
 *
 * @author Nikita Koksharov
 *
 */
public class EvictionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RedissonSetCache.class);

    public class RedissonCacheTask implements Runnable {

        final String name;
        final String timeoutSetName;
        final String maxIdleSetName;
        final boolean multimap;
        final Deque<Integer> sizeHistory = new LinkedList<Integer>();
        int delay = 10;

        final int minDelay = 1;
        final int maxDelay = 2*60*60;
        final int keysLimit = 300;

        public RedissonCacheTask(String name, String timeoutSetName, String maxIdleSetName, boolean multimap) {
            this.name = name;
            this.timeoutSetName = timeoutSetName;
            this.maxIdleSetName = maxIdleSetName;
            this.multimap = multimap;
        }

        public void schedule() {
            executor.getConnectionManager().getGroup().schedule(this, delay, TimeUnit.SECONDS);
        }

        @Override
        public void run() {
            Future<Integer> future = cleanupExpiredEntires(name, timeoutSetName, maxIdleSetName, keysLimit, multimap);

            future.addListener(new FutureListener<Integer>() {
                @Override
                public void operationComplete(Future<Integer> future) throws Exception {
                    if (!future.isSuccess()) {
                        schedule();
                        return;
                    }

                    Integer size = future.getNow();

                    if (sizeHistory.size() == 2) {
                        if (sizeHistory.peekFirst() > sizeHistory.peekLast()
                                && sizeHistory.peekLast() > size) {
                            delay = Math.min(maxDelay, (int)(delay*1.5));
                        }

//                        if (sizeHistory.peekFirst() < sizeHistory.peekLast()
//                                && sizeHistory.peekLast() < size) {
//                            prevDelay = Math.max(minDelay, prevDelay/2);
//                        }

                        if (sizeHistory.peekFirst().intValue() == sizeHistory.peekLast()
                                && sizeHistory.peekLast().intValue() == size) {
                            if (size == keysLimit) {
                                delay = Math.max(minDelay, delay/4);
                            }
                            if (size == 0) {
                                delay = Math.min(maxDelay, (int)(delay*1.5));
                            }
                        }

                        sizeHistory.pollFirst();
                    }

                    sizeHistory.add(size);
                    schedule();
                }
            });
        }

    }

    private final ConcurrentMap<String, RedissonCacheTask> tasks = PlatformDependent.newConcurrentHashMap();
    private final CommandAsyncExecutor executor;

    private final ConcurrentMap<String, Long> lastExpiredTime = PlatformDependent.newConcurrentHashMap();
    private final int expireTaskExecutionDelay = 1000;
    private final int valuesAmountToClean = 100;

    public EvictionScheduler(CommandAsyncExecutor executor) {
        this.executor = executor;
    }

    public void scheduleCleanMultimap(String name, String timeoutSetName) {
        RedissonCacheTask task = new RedissonCacheTask(name, timeoutSetName, null, true);
        RedissonCacheTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }
    
    public void schedule(String name, String timeoutSetName) {
        RedissonCacheTask task = new RedissonCacheTask(name, timeoutSetName, null, false);
        RedissonCacheTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }

    public void schedule(String name) {
        schedule(name, null);
    }

    public void schedule(String name, String timeoutSetName, String maxIdleSetName) {
        RedissonCacheTask task = new RedissonCacheTask(name, timeoutSetName, maxIdleSetName, false);
        RedissonCacheTask prevTask = tasks.putIfAbsent(name, task);
        if (prevTask == null) {
            task.schedule();
        }
    }

    public void runCleanTask(final String name, String timeoutSetName, long currentDate) {

        final Long lastExpired = lastExpiredTime.get(name);
        long now = System.currentTimeMillis();
        if (lastExpired == null) {
            if (lastExpiredTime.putIfAbsent(name, now) != null) {
                return;
            }
        } else if (lastExpired + expireTaskExecutionDelay >= now) {
            if (!lastExpiredTime.replace(name, lastExpired, now)) {
                return;
            }
        } else {
            return;
        }

        Future<Integer> future = cleanupExpiredEntires(name, timeoutSetName, null, valuesAmountToClean, false);

        future.addListener(new FutureListener<Integer>() {
            @Override
            public void operationComplete(Future<Integer> future) throws Exception {
                executor.getConnectionManager().getGroup().schedule(new Runnable() {
                    @Override
                    public void run() {
                        lastExpiredTime.remove(name, lastExpired);
                    }
                }, expireTaskExecutionDelay*3, TimeUnit.SECONDS);

                if (!future.isSuccess()) {
                    log.warn("Can't execute clean task for expired values. RSetCache name: " + name, future.cause());
                    return;
                }
            }
        });
    }

    private Future<Integer> cleanupExpiredEntires(String name, String timeoutSetName, String maxIdleSetName, int keysLimit, boolean multimap) {
        if (multimap) {
            return executor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                    "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
                  + "if #expiredKeys > 0 then "
                      + "redis.call('zrem', KEYS[2], unpack(expiredKeys)); "
                      
                      + "local values = redis.call('hmget', KEYS[1], unpack(expiredKeys)); "
                      + "local keys = {}; "
                      + "for i, v in ipairs(values) do "
                          + "local name = '{' .. KEYS[1] .. '}:' .. v; "
                          + "table.insert(keys, name); "
                      + "end; "
                      + "redis.call('del', unpack(keys)); "
                      
                      + "redis.call('hdel', KEYS[1], unpack(expiredKeys)); "
                  + "end; "
                  + "return #expiredKeys;",
                  Arrays.<Object>asList(name, timeoutSetName), System.currentTimeMillis(), keysLimit);
        }
        
        if (maxIdleSetName != null) {
            return executor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                    "local expiredKeys1 = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
                  + "if #expiredKeys1 > 0 then "
                      + "redis.call('zrem', KEYS[3], unpack(expiredKeys1)); "
                      + "redis.call('zrem', KEYS[2], unpack(expiredKeys1)); "
                      + "redis.call('hdel', KEYS[1], unpack(expiredKeys1)); "
                  + "end; "
                  + "local expiredKeys2 = redis.call('zrangebyscore', KEYS[3], 0, ARGV[1], 'limit', 0, ARGV[2]); "
                  + "if #expiredKeys2 > 0 then "
                      + "redis.call('zrem', KEYS[3], unpack(expiredKeys2)); "
                      + "redis.call('zrem', KEYS[2], unpack(expiredKeys2)); "
                      + "redis.call('hdel', KEYS[1], unpack(expiredKeys2)); "
                  + "end; "
                  + "return #expiredKeys1 + #expiredKeys2;",
                  Arrays.<Object>asList(name, timeoutSetName, maxIdleSetName), System.currentTimeMillis(), keysLimit);
        }
        
        if (timeoutSetName == null) {
            return executor.writeAsync(name, LongCodec.INSTANCE, RedisCommands.ZREMRANGEBYSCORE, name, 0, System.currentTimeMillis());
        }
        
        return executor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
              + "if #expiredKeys > 0 then "
                  + "redis.call('zrem', KEYS[2], unpack(expiredKeys)); "
                  + "redis.call('hdel', KEYS[1], unpack(expiredKeys)); "
              + "end; "
              + "return #expiredKeys;",
              Arrays.<Object>asList(name, timeoutSetName), System.currentTimeMillis(), keysLimit);
    }

}
