/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.connection.decoder.ListDrainToDecoder;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * <p>Distributed and concurrent implementation of bounded {@link java.util.concurrent.BlockingQueue}.
 *
 * @author Nikita Koksharov
 */
public class RedissonBoundedBlockingQueue<V> extends RedissonQueue<V> implements RBoundedBlockingQueue<V> {

    private final CommandExecutor commandExecutor;
    
    protected RedissonBoundedBlockingQueue(CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        this.commandExecutor = commandExecutor;
    }

    protected RedissonBoundedBlockingQueue(Codec codec, CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        this.commandExecutor = commandExecutor;
    }
    
    private String getSemaphoreName() {
        return prefixName("redisson_bqs", getName());
    }
    
    @Override
    public RFuture<Boolean> addAsync(V e) {
        RPromise<Boolean> result = new RedissonPromise<Boolean>();
        RFuture<Boolean> future = offerAsync(e);
        future.onComplete((res, ex) -> {
            if (ex != null) {
                result.tryFailure(ex);
                return;
            }
            
            if (!res) {
                result.tryFailure(new IllegalStateException("Queue is full"));
                return;
            }
            
            result.trySuccess(res);
        });
        return result;
    }

    @Override
    public RFuture<Void> putAsync(V e) {
        RedissonQueueSemaphore semaphore = createSemaphore(e);
        return semaphore.acquireAsync();
    }

    private RedissonQueueSemaphore createSemaphore(V e) {
        RedissonQueueSemaphore semaphore = new RedissonQueueSemaphore(commandExecutor, getSemaphoreName());
        semaphore.setQueueName(getName());
        semaphore.setValue(e);
        return semaphore;
    }
    
    @Override
    public void put(V e) throws InterruptedException {
        RedissonQueueSemaphore semaphore = createSemaphore(e);
        semaphore.acquire();
    }
    
    @Override
    public RFuture<Boolean> offerAsync(V e) {
        RedissonQueueSemaphore semaphore = createSemaphore(e);
        return semaphore.tryAcquireAsync();
    }

    @Override
    public boolean offer(V e, long timeout, TimeUnit unit) throws InterruptedException {
        RedissonQueueSemaphore semaphore = createSemaphore(e);
        return semaphore.tryAcquire(timeout, unit);
    }
    
    @Override
    public RFuture<Boolean> offerAsync(V e, long timeout, TimeUnit unit) {
        RedissonQueueSemaphore semaphore = createSemaphore(e);
        return semaphore.tryAcquireAsync(timeout, unit);
    }

    @Override
    public RFuture<V> takeAsync() {
        RFuture<V> takeFuture = commandExecutor.writeAsync(getName(), codec, RedisCommands.BLPOP_VALUE, getName(), 0);
        return wrapTakeFuture(takeFuture);
    }

    private RPromise<V> wrapTakeFuture(RFuture<V> takeFuture) {
        RPromise<V> result = new RedissonPromise<V>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                super.cancel(mayInterruptIfRunning);
                return takeFuture.cancel(mayInterruptIfRunning);
            };
        };
        
        takeFuture.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            if (res == null) {
                result.trySuccess(takeFuture.getNow());
                return;
            }
            createSemaphore(null).releaseAsync().onComplete((r, ex) -> {
                result.trySuccess(takeFuture.getNow());
            });
        });
        return result;
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        return removeAllAsync(Collections.singleton(o));
    }
    
    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }

        String channelName = RedissonSemaphore.getChannelName(getSemaphoreName());
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                        "local count = 0; " +
                        "for i = 1, #ARGV, 1 do "
                            + "if redis.call('lrem', KEYS[1], 0, ARGV[i]) == 1 then "
                            + "count = count + 1; "
                            + "end; "
                        +"end; "
                        + "if count > 0 then "
                            + "local value = redis.call('incrby', KEYS[2], count); "
                            + "redis.call('publish', KEYS[3], value); "
                            + "return 1;"
                        + "end;"
                       + "return 0 ",
                       Arrays.<Object>asList(getName(), getSemaphoreName(), channelName), encode(c).toArray());
    }
    
    @Override
    public RFuture<V> pollAsync() {
        String channelName = RedissonSemaphore.getChannelName(getSemaphoreName());
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_OBJECT,
                "local res = redis.call('lpop', KEYS[1]);"
                + "if res ~= false then " +
                    "local value = redis.call('incrby', KEYS[2], ARGV[1]); " +
                    "redis.call('publish', KEYS[3], value); "
                + "end;"
                + "return res;",
                Arrays.<Object>asList(getName(), getSemaphoreName(), channelName), 1);
    }
    
    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#take()
     */
    @Override
    public V take() throws InterruptedException {
        return get(takeAsync());
    }

    @Override
    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        RFuture<V> takeFuture = commandExecutor.writeAsync(getName(), codec, RedisCommands.BLPOP_VALUE, getName(), toSeconds(timeout, unit));
        return wrapTakeFuture(takeFuture);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#poll(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        return get(pollAsync(timeout, unit));
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueue#pollFromAny(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return get(pollFromAnyAsync(timeout, unit, queueNames));
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueueAsync#pollFromAnyAsync(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public RFuture<V> pollFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        RFuture<V> takeFuture = commandExecutor.pollFromAnyAsync(getName(), codec, RedisCommands.BLPOP_VALUE, toSeconds(timeout, unit), queueNames);
        return wrapTakeFuture(takeFuture);
    }

    @Override
    public V takeLastAndOfferFirstTo(String queueName) throws InterruptedException {
        return get(takeLastAndOfferFirstToAsync(queueName));
    }
    
    @Override
    public RFuture<V> takeLastAndOfferFirstToAsync(String queueName) {
        return pollLastAndOfferFirstToAsync(queueName, 0, TimeUnit.SECONDS);
    }
    
    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        RFuture<V> takeFuture = commandExecutor.writeAsync(getName(), codec, RedisCommands.BRPOPLPUSH, getName(), queueName, unit.toSeconds(timeout));
        return wrapTakeFuture(takeFuture);
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        return get(pollLastAndOfferFirstToAsync(queueName, timeout, unit));
    }

    @Override
    public int remainingCapacity() {
        return createSemaphore(null).availablePermits();
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        return get(drainToAsync(c));
    }

    @Override
    public RFuture<Integer> drainToAsync(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        
        String channelName = RedissonSemaphore.getChannelName(getSemaphoreName());
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder((Collection<Object>) c)),
              "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
              "redis.call('del', KEYS[1]); " +
              "if #vals > 0 then "
              + "local value = redis.call('incrby', KEYS[2], #vals); " +
                "redis.call('publish', KEYS[3], value); "
            + "end; " +
              "return vals", 
              Arrays.<Object>asList(getName(), getSemaphoreName(), channelName));
    }
    
    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        if (maxElements <= 0) {
            return 0;
        }

        return get(drainToAsync(c, maxElements));
    }

    @Override
    public RFuture<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        
        String channelName = RedissonSemaphore.getChannelName(getSemaphoreName());
        
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder((Collection<Object>) c)),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "if #vals > 0 then "
                        + "local value = redis.call('incrby', KEYS[2], #vals); " +
                          "redis.call('publish', KEYS[3], value); "
                      + "end; " +
                        "return vals",
                        Arrays.<Object>asList(getName(), getSemaphoreName(), channelName), maxElements);
    }
    
    @Override
    public RFuture<Boolean> trySetCapacityAsync(int capacity) {
        String channelName = RedissonSemaphore.getChannelName(getSemaphoreName());
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('get', KEYS[1]); " +
                "if (value == false) then "
                    + "redis.call('set', KEYS[1], ARGV[1]); "
                    + "redis.call('publish', KEYS[2], ARGV[1]); "
                    + "return 1;"
                + "end;"
                + "return 0;",
                Arrays.<Object>asList(getSemaphoreName(), channelName), capacity);
    }
    
    @Override
    public boolean trySetCapacity(int capacity) {
        return get(trySetCapacityAsync(capacity));
    }
    
    @Override
    public void clear() {
        String channelName = RedissonSemaphore.getChannelName(getSemaphoreName());
        commandExecutor.evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
              "local len = redis.call('llen', KEYS[1]); " +
              "if len > 0 then "
              + "redis.call('del', KEYS[1]); "
              + "local value = redis.call('incrby', KEYS[2], len); " +
                "redis.call('publish', KEYS[3], value); "
            + "end; ", 
              Arrays.<Object>asList(getName(), getSemaphoreName(), channelName));

    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getSemaphoreName());
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getName(), getSemaphoreName());
        return super.sizeInMemoryAsync(keys);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('pexpire', KEYS[2], ARGV[1]); " +
                "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getSemaphoreName()), timeUnit.toMillis(timeToLive));
    }

    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('pexpireat', KEYS[2], ARGV[1]); " +
                "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getSemaphoreName()), timestamp);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "redis.call('persist', KEYS[2]); " +
                  "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getName(), getSemaphoreName()));
    }
    
    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }

        RedissonQueueSemaphore semaphore = new RedissonQueueSemaphore(commandExecutor, getSemaphoreName());
        semaphore.setQueueName(getName());
        semaphore.setValues(c);
        return semaphore.tryAcquireAsync();
    }


}