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
package org.redisson.transaction;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.RedissonBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.transaction.operation.DeleteOperation;
import org.redisson.transaction.operation.TouchOperation;
import org.redisson.transaction.operation.TransactionalOperation;
import org.redisson.transaction.operation.UnlinkOperation;
import org.redisson.transaction.operation.bucket.BucketCompareAndSetOperation;
import org.redisson.transaction.operation.bucket.BucketGetAndDeleteOperation;
import org.redisson.transaction.operation.bucket.BucketGetAndSetOperation;
import org.redisson.transaction.operation.bucket.BucketSetOperation;
import org.redisson.transaction.operation.bucket.BucketTrySetOperation;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonTransactionalBucket<V> extends RedissonBucket<V> {

    static final Object NULL = new Object();
    
    private long timeout;
    private final AtomicBoolean executed;
    private final List<TransactionalOperation> operations;
    private Object state;
    private final String transactionId;
    
    public RedissonTransactionalBucket(CommandAsyncExecutor commandExecutor, long timeout, String name, List<TransactionalOperation> operations, AtomicBoolean executed, String transactionId) {
        super(commandExecutor, name);
        this.operations = operations;
        this.executed = executed;
        this.transactionId = transactionId;
        this.timeout = timeout;
    }

    public RedissonTransactionalBucket(Codec codec, CommandAsyncExecutor commandExecutor, long timeout, String name, List<TransactionalOperation> operations, AtomicBoolean executed, String transactionId) {
        super(codec, commandExecutor, name);
        this.operations = operations;
        this.executed = executed;
        this.transactionId = transactionId;
        this.timeout = timeout;
    }
    
    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("expire method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> expireAtAsync(Date timestamp) {
        throw new UnsupportedOperationException("expireAt method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        throw new UnsupportedOperationException("expireAt method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> clearExpireAsync() {
        throw new UnsupportedOperationException("clearExpire method is not supported in transaction");
    }
    
    @Override
    public RFuture<Boolean> moveAsync(int database) {
        throw new UnsupportedOperationException("moveAsync method is not supported in transaction");
    }
    
    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database, long timeout) {
        throw new UnsupportedOperationException("migrateAsync method is not supported in transaction");
    }
    
    @Override
    public RFuture<Long> sizeAsync() {
        checkState();
        if (state != null) {
            if (state == NULL) {
                return RedissonPromise.newSucceededFuture(0L);
            } else {
                ByteBuf buf = encode(state);
                long size = buf.readableBytes();
                buf.release();
                return RedissonPromise.newSucceededFuture(size);
            }
        }

        return super.sizeAsync();
    }
    
    @Override
    public RFuture<Boolean> isExistsAsync() {
        checkState();
        if (state != null) {
            if (state == NULL) {
                return RedissonPromise.newSucceededFuture(null);
            } else {
                return RedissonPromise.newSucceededFuture(true);
            }
        }
        
        return super.isExistsAsync();
    }
    
    @Override
    public RFuture<Boolean> touchAsync() {
        checkState();
        RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new TouchOperation(getName(), getLockName()));
                    result.trySuccess(state != NULL);
                    return;
                }
                
                isExistsAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    operations.add(new TouchOperation(getName(), getLockName()));
                    result.trySuccess(res);
                });
            }
        });
        return result;
    }

    @Override
    public RFuture<Boolean> unlinkAsync() {
        checkState();
        RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new UnlinkOperation(getName(), getLockName()));
                    if (state == NULL) {
                        result.trySuccess(false);
                    } else {
                        state = NULL;
                        result.trySuccess(true);
                    }
                    return;
                }
                
                isExistsAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    operations.add(new UnlinkOperation(getName(), getLockName()));
                    state = NULL;
                    result.trySuccess(res);
                });
            }
        });
        return result;
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        checkState();
        RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new DeleteOperation(getName(), getLockName(), transactionId));
                    if (state == NULL) {
                        result.trySuccess(false);
                    } else {
                        state = NULL;
                        result.trySuccess(true);
                    }
                    return;
                }
                
                isExistsAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    operations.add(new DeleteOperation(getName(), getLockName(), transactionId));
                    state = NULL;
                    result.trySuccess(res);
                });
            }
        });
        return result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public RFuture<V> getAsync() {
        checkState();
        if (state != null) {
            if (state == NULL) {
                return RedissonPromise.newSucceededFuture(null);
            } else {
                return RedissonPromise.newSucceededFuture((V) state);
            }
        }
        
        return super.getAsync();
    }
    
    @Override
    public RFuture<Boolean> compareAndSetAsync(V expect, V update) {
        checkState();
        RPromise<Boolean> result = new RedissonPromise<>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new BucketCompareAndSetOperation<V>(getName(), getLockName(), getCodec(), expect, update, transactionId));
                    if ((state == NULL && expect == null)
                            || isEquals(state, expect)) {
                        if (update == null) {
                            state = NULL;
                        } else {
                            state = update;
                        }
                        result.trySuccess(true);
                    } else {
                        result.trySuccess(false);
                    }
                    return;
                }
                
                getAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    operations.add(new BucketCompareAndSetOperation<V>(getName(), getLockName(), getCodec(), expect, update, transactionId));
                    if ((res == null && expect == null) 
                            || isEquals(res, expect)) {
                        if (update == null) {
                            state = NULL;
                        } else {
                            state = update;
                        }
                        result.trySuccess(true);
                    } else {
                        result.trySuccess(false);
                    }
                });
            }
        });
        return result;
    }
    
    @Override
    public RFuture<V> getAndSetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return getAndSet(value, new BucketGetAndSetOperation<V>(getName(), getLockName(), getCodec(), value, timeToLive, timeUnit, transactionId));
    }
    
    @Override
    public RFuture<V> getAndSetAsync(V value) {
        return getAndSet(value, new BucketGetAndSetOperation<V>(getName(), getLockName(), getCodec(), value, transactionId));
    }
    
    @SuppressWarnings("unchecked")
    private RFuture<V> getAndSet(V newValue, TransactionalOperation operation) {
        checkState();
        RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    Object prevValue;
                    if (state == NULL) {
                        prevValue = null;
                    } else {
                        prevValue = state;
                    }
                    operations.add(operation);
                    if (newValue == null) {
                        state = NULL;
                    } else {
                        state = newValue;
                    }
                    result.trySuccess((V) prevValue);
                    return;
                }
                
                getAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    if (newValue == null) {
                        state = NULL;
                    } else {
                        state = newValue;
                    }
                    operations.add(operation);
                    result.trySuccess(res);
                });
            }
        });
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RFuture<V> getAndDeleteAsync() {
        checkState();
        RPromise<V> result = new RedissonPromise<V>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    Object prevValue;
                    if (state == NULL) {
                        prevValue = null;
                    } else {
                        prevValue = state;
                    }
                    operations.add(new BucketGetAndDeleteOperation<V>(getName(), getLockName(), getCodec(), transactionId));
                    state = NULL;
                    result.trySuccess((V) prevValue);
                    return;
                }
                
                getAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    state = NULL;
                    operations.add(new BucketGetAndDeleteOperation<V>(getName(), getLockName(), getCodec(), transactionId));
                    result.trySuccess(res);
                });
            }
        });
        return result;
    }
    
    @Override
    public RFuture<Void> setAsync(V newValue) {
        return setAsync(newValue, new BucketSetOperation<V>(getName(), getLockName(), getCodec(), newValue, transactionId));
    }

    private RFuture<Void> setAsync(V newValue, TransactionalOperation operation) {
        checkState();
        RPromise<Void> result = new RedissonPromise<Void>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                operations.add(operation);
                if (newValue == null) {
                    state = NULL;
                } else {
                    state = newValue;
                }
                result.trySuccess(null);
            }
        });
        return result;
    }
    
    @Override
    public RFuture<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return setAsync(value, new BucketSetOperation<V>(getName(), getLockName(), getCodec(), value, timeToLive, timeUnit, transactionId));
    }
    
    @Override
    public RFuture<Boolean> trySetAsync(V newValue) {
        return trySet(newValue, new BucketTrySetOperation<V>(getName(), getLockName(), getCodec(), newValue, transactionId));
    }
    
    @Override
    public RFuture<Boolean> trySetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return trySet(value, new BucketTrySetOperation<V>(getName(), getLockName(), getCodec(), value, timeToLive, timeUnit, transactionId));
    }

    private RFuture<Boolean> trySet(V newValue, TransactionalOperation operation) {
        checkState();
        RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(operation);
                    if (state == NULL) {
                        if (newValue == null) {
                            state = NULL;
                        } else {
                            state = newValue;
                        }
                        result.trySuccess(true);
                    } else {
                        result.trySuccess(false);
                    }
                    return;
                }
                
                getAsync().onComplete((res, e) -> {
                    if (e != null) {
                        result.tryFailure(e);
                        return;
                    }
                    
                    operations.add(operation);
                    if (res == null) {
                        if (newValue == null) {
                            state = NULL;
                        } else {
                            state = newValue;
                        }
                        result.trySuccess(true);
                    } else {
                        result.trySuccess(false);
                    }
                });
            }
        });
        return result;
    }

    
    private boolean isEquals(Object value, Object oldValue) {
        ByteBuf valueBuf = encode(value);
        ByteBuf oldValueBuf = encode(oldValue);
        
        try {
            return valueBuf.equals(oldValueBuf);
        } finally {
            valueBuf.readableBytes();
            oldValueBuf.readableBytes();
        }
    }
    
    protected <R> void executeLocked(RPromise<R> promise, Runnable runnable) {
        RLock lock = getLock();
        lock.lockAsync(timeout, TimeUnit.MILLISECONDS).onComplete((res, e) -> {
            if (e == null) {
                runnable.run();
            } else {
                promise.tryFailure(e);
            }
        });
    }

    private RLock getLock() {
        return new RedissonTransactionalLock(commandExecutor, getLockName(), transactionId);
    }

    private String getLockName() {
        return getName() + ":transaction_lock";
    }

    protected void checkState() {
        if (executed.get()) {
            throw new IllegalStateException("Unable to execute operation. Transaction is in finished state!");
        }
    }
    
}
