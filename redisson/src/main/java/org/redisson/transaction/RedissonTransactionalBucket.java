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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

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
    
    public RedissonTransactionalBucket(CommandAsyncExecutor commandExecutor, String name, List<TransactionalOperation> operations, AtomicBoolean executed, String transactionId) {
        super(commandExecutor, name);
        this.operations = operations;
        this.executed = executed;
        this.transactionId = transactionId;
    }

    public RedissonTransactionalBucket(Codec codec, CommandAsyncExecutor commandExecutor, String name, List<TransactionalOperation> operations, AtomicBoolean executed, String transactionId) {
        super(codec, commandExecutor, name);
        this.operations = operations;
        this.executed = executed;
        this.transactionId = transactionId;
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
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new TouchOperation(getName(), getLockName()));
                    result.trySuccess(state != NULL);
                    return;
                }
                
                isExistsAsync().addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(new TouchOperation(getName(), getLockName()));
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }

    @Override
    public RFuture<Boolean> unlinkAsync() {
        checkState();
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
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
                
                isExistsAsync().addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(new UnlinkOperation(getName(), getLockName()));
                        state = NULL;
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        checkState();
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new DeleteOperation(getName(), getLockName()));
                    if (state == NULL) {
                        result.trySuccess(false);
                    } else {
                        state = NULL;
                        result.trySuccess(true);
                    }
                    return;
                }
                
                isExistsAsync().addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(new DeleteOperation(getName(), getLockName()));
                        state = NULL;
                        result.trySuccess(future.getNow());
                    }
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
                return RedissonPromise.newSucceededFuture((V)state);
            }
        }
        
        return super.getAsync();
    }
    
    @Override
    public RFuture<Boolean> compareAndSetAsync(final V expect, final V update) {
        checkState();
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        executeLocked(result, new Runnable() {
            @Override
            public void run() {
                if (state != null) {
                    operations.add(new BucketCompareAndSetOperation<V>(getName(), getLockName(), getCodec(), expect, update));
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
                
                getAsync().addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(new BucketCompareAndSetOperation<V>(getName(), getLockName(), getCodec(), expect, update));
                        if ((future.getNow() == null && expect == null) 
                                || isEquals(future.getNow(), expect)) {
                            if (update == null) {
                                state = NULL;
                            } else {
                                state = update;
                            }
                            result.trySuccess(true);
                        } else {
                            result.trySuccess(false);
                        }
                    }
                });
            }
        });
        return result;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public RFuture<V> getAndSetAsync(final V newValue) {
        checkState();
        final RPromise<V> result = new RedissonPromise<V>();
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
                    operations.add(new BucketGetAndSetOperation<V>(getName(), getLockName(), getCodec(), newValue));
                    if (newValue == null) {
                        state = NULL;
                    } else {
                        state = newValue;
                    }
                    result.trySuccess((V) prevValue);
                    return;
                }
                
                getAsync().addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        if (newValue == null) {
                            state = NULL;
                        } else {
                            state = newValue;
                        }
                        operations.add(new BucketGetAndSetOperation<V>(getName(), getLockName(), getCodec(), newValue));
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RFuture<V> getAndDeleteAsync() {
        checkState();
        final RPromise<V> result = new RedissonPromise<V>();
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
                    operations.add(new BucketGetAndDeleteOperation<V>(getName(), getLockName(), getCodec()));
                    state = NULL;
                    result.trySuccess((V) prevValue);
                    return;
                }
                
                getAsync().addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        state = NULL;
                        operations.add(new BucketGetAndDeleteOperation<V>(getName(), getLockName(), getCodec()));
                        result.trySuccess(future.getNow());
                    }
                });
            }
        });
        return result;
    }
    
    @Override
    public RFuture<Void> setAsync(V newValue) {
        return setAsync(newValue, new BucketSetOperation<V>(getName(), getLockName(), getCodec(), newValue));
    }

    private RFuture<Void> setAsync(final V newValue, final TransactionalOperation operation) {
        checkState();
        final RPromise<Void> result = new RedissonPromise<Void>();
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
        return setAsync(value, new BucketSetOperation<V>(getName(), getLockName(), getCodec(), value, timeToLive, timeUnit));
    }
    
    @Override
    public RFuture<Boolean> trySetAsync(V newValue) {
        return trySet(newValue, new BucketTrySetOperation<V>(getName(), getLockName(), getCodec(), newValue));
    }
    
    @Override
    public RFuture<Boolean> trySetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return trySet(value, new BucketTrySetOperation<V>(getName(), getLockName(), getCodec(), value, timeToLive, timeUnit));
    }

    private RFuture<Boolean> trySet(final V newValue, final TransactionalOperation operation) {
        checkState();
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
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
                
                getAsync().addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        operations.add(operation);
                        if (future.getNow() == null) {
                            if (newValue == null) {
                                state = NULL;
                            } else {
                                state = newValue;
                            }
                            result.trySuccess(true);
                        } else {
                            result.trySuccess(false);
                        }
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
    
    protected <R> void executeLocked(final RPromise<R> promise, final Runnable runnable) {
        RLock lock = getLock();
        lock.lockAsync(timeout, TimeUnit.MILLISECONDS).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    runnable.run();
                } else {
                    promise.tryFailure(future.cause());
                }
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
