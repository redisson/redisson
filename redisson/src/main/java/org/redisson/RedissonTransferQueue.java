/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import io.netty.util.Timeout;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.redisson.api.RFuture;
import org.redisson.api.RRemoteService;
import org.redisson.api.RTransferQueue;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RRemoteAsync;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.ListDrainToDecoder;
import org.redisson.executor.RemotePromise;
import org.redisson.iterator.RedissonListIterator;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.remote.RemoteServiceRequest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;




/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTransferQueue<V> extends RedissonExpirable implements RTransferQueue<V> {

    public interface TransferQueueService {

        <V> void invoke(V value);

    }

    @RRemoteAsync(TransferQueueService.class)
    public interface TransferQueueServiceAsync {

        <V> RFuture<Void> invoke(V value);

    }

    public static class TransferQueueServiceImpl implements TransferQueueService {

        private Object result;

        @Override
        public <V> void invoke(V value) {
            result = value;
        }

        public Object getResult() {
            return result;
        }

    }

    private static final Convertor<Object> CONVERTER = obj -> {
        if (obj != null) {
            RemoteServiceRequest request = (RemoteServiceRequest) obj;
            return request.getArgs()[0];
        }
        return null;
    };

    private static final RedisStrictCommand<Object> EVAL_REQUEST = new RedisStrictCommand<>("EVAL", CONVERTER);
    private static final RedisCommand EVAL_LIST = new RedisCommand("EVAL", new ObjectListReplayDecoder<>(), CONVERTER);

    private final String queueName;
    private final String mapName;
    private final TransferQueueServiceAsync service;
    private final RRemoteService remoteService;

    public RedissonTransferQueue(Codec codec, CommandAsyncExecutor commandExecutor, String name, RRemoteService remoteService) {
        super(codec, commandExecutor, name);
        service = remoteService.get(TransferQueueServiceAsync.class, RemoteInvocationOptions.defaults().noAck());
        this.remoteService = remoteService;

        queueName = ((RedissonRemoteService) remoteService).getRequestQueueName(TransferQueueService.class);
        mapName = ((RedissonRemoteService) remoteService).getRequestTasksMapName(TransferQueueService.class);
    }

    public RedissonTransferQueue(CommandAsyncExecutor commandExecutor, String name, RRemoteService remoteService) {
        super(commandExecutor, name);
        service = remoteService.get(TransferQueueServiceAsync.class, RemoteInvocationOptions.defaults().noAck());
        this.remoteService = remoteService;

        queueName = ((RedissonRemoteService) remoteService).getRequestQueueName(TransferQueueService.class);
        mapName = ((RedissonRemoteService) remoteService).getRequestTasksMapName(TransferQueueService.class);
    }

    @Override
    public boolean tryTransfer(V v) {
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(v).toCompletableFuture();
        boolean added = commandExecutor.get(future.getAddFuture());
        if (added && !future.cancel(false)) {
            commandExecutor.get(future);
            return true;
        }
        return false;
    }

    public RFuture<Boolean> tryTransferAsync(V v) {
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(v).toCompletableFuture();
        CompletableFuture<Boolean> result = future.getAddFuture().thenCompose(added -> {
            if (!added) {
                return CompletableFuture.completedFuture(false);
            }

            return future.cancelAsync(false).thenCompose(canceled -> {
                if (canceled) {
                    return CompletableFuture.completedFuture(false);
                } else {
                    return future.thenApply(res -> true);
                }
            });
        });

        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public void transfer(V v) throws InterruptedException {
        RFuture<Void> future = service.invoke(v);
        commandExecutor.getInterrupted(future);
    }

    @Override
    public RFuture<Void> transferAsync(V v) {
        return service.invoke(v);
    }

    @Override
    public boolean tryTransfer(V v, long timeout, TimeUnit unit) throws InterruptedException {
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(v).toCompletableFuture();
        long remainTime = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();
        CompletableFuture<Boolean> addFuture = future.getAddFuture().toCompletableFuture();
        try {
            addFuture.get(remainTime, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            if (!addFuture.cancel(false)) {
                if (!future.cancel(false)) {
                    commandExecutor.getInterrupted(future);
                    return true;
                }
            }
            return false;
        }
        remainTime -= System.currentTimeMillis() - startTime;

        try {
            future.toCompletableFuture().get(remainTime, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            if (!future.cancel(false)) {
                commandExecutor.getInterrupted(future);
                return true;
            }
            return false;
        }
        commandExecutor.getInterrupted(future);
        return true;
    }

    @Override
    public RFuture<Boolean> tryTransferAsync(V v, long timeout, TimeUnit unit) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(v).toCompletableFuture();

        long remainTime = unit.toMillis(timeout);
        long startTime = System.currentTimeMillis();

        Timeout timeoutFuture = getServiceManager().newTimeout(tt -> {
            if (!future.getAddFuture().cancel(false)) {
                future.cancelAsync(false);
            }
        }, remainTime, TimeUnit.MILLISECONDS);


        future.whenComplete((res, exc) -> {
            if (future.isCancelled()) {
                result.complete(false);
                return;
            }

            timeoutFuture.cancel();
            if (exc != null) {
                result.completeExceptionally(exc);
                return;
            }

            result.complete(true);
        });

        future.getAddFuture().whenComplete((added, e) -> {
            if (future.getAddFuture().isCancelled()) {
                result.complete(false);
                return;
            }

            if (e != null) {
                timeoutFuture.cancel();
                result.completeExceptionally(e);
                return;
            }

            if (!added) {
                timeoutFuture.cancel();
                result.complete(false);
                return;
            }

            Runnable task = () -> {
                future.cancelAsync(false).whenComplete((canceled, ex) -> {
                    if (ex != null) {
                        timeoutFuture.cancel();
                        result.completeExceptionally(ex);
                        return;
                    }

                    if (canceled) {
                        timeoutFuture.cancel();
                        result.complete(false);
                    }
                });
            };

            long time = remainTime - (System.currentTimeMillis() - startTime);
            if (time > 0) {
                getServiceManager().newTimeout(tt -> {
                    task.run();
                }, time, TimeUnit.MILLISECONDS);
            } else {
                task.run();
            }
        });
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public boolean hasWaitingConsumer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getWaitingConsumerCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(V v) {
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(v).toCompletableFuture();
        return commandExecutor.get(future.getAddFuture());
    }

    public RFuture<Boolean> addAsync(V v) {
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(v).toCompletableFuture();
        return new CompletableFutureWrapper<>(future.getAddFuture());
    }

    @Override
    public boolean offer(V v) {
        return add(v);
    }

    @Override
    public V remove() {
        V value = poll();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public V poll() {
        TransferQueueServiceImpl s = new TransferQueueServiceImpl();
        RFuture<Boolean> r = remoteService.tryExecuteAsync(TransferQueueService.class, s, ImmediateEventExecutor.INSTANCE, -1, null);
        get(r);
        return (V) s.getResult();
    }

    public RFuture<V> pollAsync() {
        TransferQueueServiceImpl s = new TransferQueueServiceImpl();
        RFuture<Boolean> future = remoteService.tryExecuteAsync(TransferQueueService.class, s, ImmediateEventExecutor.INSTANCE, -1, null);

        CompletionStage<V> f = future.thenApply(r -> (V) s.getResult());
        return new CompletableFutureWrapper<>(f);
    }


    @Override
    public V element() {
        V value = peek();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public V peek() {
        return get(peekAsync());
    }

    public RFuture<V> peekAsync() {
        return commandExecutor.evalReadAsync(queueName, codec, EVAL_REQUEST,
                "local id = redis.call('lindex', KEYS[1], 0); "
                    + "if id ~= false then "
                        + "return redis.call('hget', KEYS[2], id); "
                    + "end "
                    + "return nil;",
                Arrays.asList(queueName, mapName));
    }

    @Override
    public void put(V v) throws InterruptedException {
        add(v);
    }

    @Override
    public boolean offer(V v, long timeout, TimeUnit unit) throws InterruptedException {
        return add(v);
    }

    @Override
    public V take() throws InterruptedException {
        return poll(0, TimeUnit.MILLISECONDS);
    }

    public RFuture<V> takeAsync() {
        return pollAsync(0, TimeUnit.MILLISECONDS);
    }

    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        TransferQueueServiceImpl s = new TransferQueueServiceImpl();
        remoteService.tryExecute(TransferQueueService.class, s, ImmediateEventExecutor.INSTANCE, timeout, unit);
        return (V) s.getResult();
    }

    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        TransferQueueServiceImpl s = new TransferQueueServiceImpl();
        RFuture<Boolean> future = remoteService.tryExecuteAsync(TransferQueueService.class, s, ImmediateEventExecutor.INSTANCE, timeout, unit);
        CompletionStage<V> f = future.thenApply(r -> (V) s.getResult());
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (c.isEmpty()) {
            return true;
        }

        boolean all = true;
        for (Object obj : c) {
            all &= contains(obj);
        }
        return all;
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return false;
        }

        boolean added = false;
        for (V obj : c) {
            added |= add(obj);
        }
        return added;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        RedissonKeys keys = new RedissonKeys(commandExecutor);
        keys.delete(queueName, mapName);
    }

    public RFuture<Void> clearAsync() {
        RedissonKeys keys = new RedissonKeys(commandExecutor);
        CompletionStage<Void> f = keys.deleteAsync(queueName, mapName).thenApply(r -> null);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public int size() {
        return remoteService.getPendingInvocations(TransferQueueService.class);
    }

    public RFuture<Integer> sizeAsync() {
        return remoteService.getPendingInvocationsAsync(TransferQueueService.class);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        ByteBuf encodedObject = encode(o);
        boolean result = stream().anyMatch(v -> {
            ByteBuf encodedValue = encode(v);
            boolean res = encodedValue.equals(encodedObject);
            encodedValue.release();
            return res;
        });
        encodedObject.release();
        return result;
    }

    public RFuture<V> getValueAsync(int index) {
        return commandExecutor.evalReadAsync(queueName, codec, EVAL_REQUEST,
                "local id = redis.call('lindex', KEYS[1], ARGV[1]); "
                    + "if id ~= false then "
                        + "return redis.call('hget', KEYS[2], id); "
                    + "end "
                    + "return nil;",
                Arrays.asList(queueName, mapName), index);
    }


    @Override
    public Iterator<V> iterator() {
        return new RedissonListIterator<V>(0) {

            @Override
            public V getValue(int index) {
                RFuture<V> future = getValueAsync(index);
                return get(future);
            }

            @Override
            public V remove(int index) {
                if (index == 0) {
                    RFuture<V> future = commandExecutor.evalWriteNoRetryAsync(queueName, codec, EVAL_REQUEST,
                            "local id = redis.call('lpop', KEYS[1]); "
                                + "if id ~= false then "
                                    + "return redis.call('hget', KEYS[2], id); "
                                + "end "
                                + "return nil;",
                            Arrays.asList(queueName, mapName));

                    return get(future);
                }

                RFuture<V> future = commandExecutor.evalWriteAsync(queueName, codec, EVAL_REQUEST,
                        "local id = redis.call('lindex', KEYS[1], ARGV[1]); " +
                                "if id ~= false then " +
                                    "redis.call('lset', KEYS[1], ARGV[1], 'DELETED_BY_REDISSON');" +
                                    "redis.call('lrem', KEYS[1], 1, 'DELETED_BY_REDISSON');" +
                                    "local val = redis.call('hget', KEYS[2], id); " +
                                    "redis.call('hdel', KEYS[2], id); " +
                                    "return val; " +
                                "end; " +
                                "return nil;",
                            Arrays.asList(queueName, mapName), index);

                return get(future);
            }

            @Override
            public void fastSet(int index, V value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(int index, V value) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Object[] toArray() {
        List<V> list = readAll();
        return list.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<V> list = readAll();
        return list.toArray(a);
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        return get(drainToAsync(c));
    }

    public RFuture<Integer> drainToAsync(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c), CONVERTER),
        "local ids = redis.call('lrange', KEYS[1], 0, -1); " +
                "local result = {};"
              + "for i=1, #ids, 5000 do "
                 + "local vals = redis.call('hmget', KEYS[2], unpack(ids, i, math.min(i+4999, #ids))); "
                 + "for k,v in ipairs(vals) do "
                 +     "table.insert(result, v); "
                 + "end; "
              + "end; " +
              "redis.call('del', KEYS[1], KEYS[2]); " +
              "return result",
                Arrays.asList(queueName, mapName));
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        if (maxElements <= 0) {
            return 0;
        }

        return get(drainToAsync(c, maxElements));
    }

    public RFuture<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c), CONVERTER),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                "local ids = redis.call('lrange', KEYS[1], 0, elemNum); " +
                "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                "local result = {};"
              + "for i=1, #ids, 5000 do "
                 + "local vals = redis.call('hmget', KEYS[2], unpack(ids, i, math.min(i+4999, #ids))); "
                 + "redis.call('hdel', KEYS[2], unpack(ids, i, math.min(i+4999, #ids)));"
                 + "for k,v in ipairs(vals) do "
                 +     "table.insert(result, v); "
                 + "end; "
              + "end; " +
              "return result",
                Arrays.asList(queueName, mapName), maxElements);
    }

    @Override
    public List<V> readAll() {
        return get(readAllAsync());
    }

    public RFuture<List<V>> readAllAsync() {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_LIST,
        "local ids = redis.call('lrange', KEYS[1], 0, -1); " +
                "local result = {};"
              + "for i=1, #ids, 5000 do "
                 + "local vals = redis.call('hmget', KEYS[2], unpack(ids, i, math.min(i+4999, #ids))); "
                 + "for k,v in ipairs(vals) do "
                 +     "table.insert(result, v); "
                 + "end; "
              + "end; " +
                "return result;",
                Arrays.asList(queueName, mapName));
    }

    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, List<V>> pollFirstFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, List<V>> pollLastFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Map<String, List<V>>> pollFirstFromAnyAsync(Duration duration, int count, String... queueNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Map<String, List<V>>> pollLastFromAnyAsync(Duration duration, int count, String... queueNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public V takeLastAndOfferFirstTo(String queueName) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int subscribeOnElements(Consumer<V> consumer) {
        return getServiceManager().getElementsSubscribeService().subscribeOnElements(this::takeAsync, consumer);
    }

    @Override
    public void unsubscribe(int listenerId) {
        commandExecutor.getServiceManager().getElementsSubscribeService().unsubscribe(listenerId);
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<V> poll(int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> pollFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<V> takeLastAndOfferFirstToAsync(String queueName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Void> putAsync(V value) {
        RemotePromise<Void> future = (RemotePromise<Void>) service.invoke(value).toCompletableFuture();
        CompletableFuture<Void> f = future.getAddFuture().thenApply(r -> null);
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public RFuture<Boolean> offerAsync(V e) {
        return addAsync(e);
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<List<V>> pollAsync(int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> containsAsync(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        throw new UnsupportedOperationException();
    }
}
