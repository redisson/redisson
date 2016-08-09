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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.MessageListener;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RExecutorService;
import org.redisson.api.RKeys;
import org.redisson.api.RTopic;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RInject;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.connection.ConnectionManager;
import org.redisson.executor.ExecutorRemoteService;
import org.redisson.executor.RemoteExecutorService;
import org.redisson.executor.RemoteExecutorServiceAsync;
import org.redisson.executor.RemoteExecutorServiceImpl;
import org.redisson.executor.RemotePromise;

import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorService implements RExecutorService {

    public static final int SHUTDOWN_STATE = 1;
    public static final int TERMINATED_STATE = 2;
    
    private final CommandExecutor commandExecutor;
    private final ConnectionManager connectionManager;
    private final Codec codec;
    private final Redisson redisson;
    
    private final RAtomicLong tasksCounter;
    private final RBucket<Integer> status;
    private final RTopic<Integer> topic;
    private final RKeys keys;

    private final RemoteExecutorServiceAsync asyncService;
    private final RemoteExecutorServiceAsync asyncServiceWithoutResult;
    
    private final Map<Class<?>, byte[]> class2bytes = PlatformDependent.newConcurrentHashMap();

    private final String name;
    private final String requestQueueName;
    
    public RedissonExecutorService(Codec codec, CommandExecutor commandExecutor, Redisson redisson, String name) {
        super();
        this.codec = codec;
        this.commandExecutor = commandExecutor;
        this.connectionManager = commandExecutor.getConnectionManager();
        this.name = name;
        this.redisson = redisson;
        
        requestQueueName = "{" + name + ":"+ RemoteExecutorService.class.getName() + "}";
        String objectName = requestQueueName;
        tasksCounter = redisson.getAtomicLong(objectName + ":counter");
        status = redisson.getBucket(objectName + ":status", codec);
        topic = redisson.getTopic(objectName + ":topic", codec);
        keys = redisson.getKeys();
        
        ExecutorRemoteService remoteService = new ExecutorRemoteService(codec, redisson, name, commandExecutor);
        remoteService.setTasksCounterName(tasksCounter.getName());
        remoteService.setStatusName(status.getName());
        
        asyncService = remoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck());
        asyncServiceWithoutResult = remoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
    }
    
    @Override
    public void registerWorkers(int executors) {
        registerWorkers(executors, null);
    }
    
    @Override
    public void registerWorkers(int executors, Executor executor) {
        RemoteExecutorServiceImpl service = new RemoteExecutorServiceImpl(commandExecutor, redisson, codec, requestQueueName);
        service.setStatusName(status.getName());
        service.setTasksCounterName(tasksCounter.getName());
        service.setTopicName(topic.getChannelNames().get(0));
        
        redisson.getRemoteSerivce(name, codec).register(RemoteExecutorService.class, service, executors, executor);
    }

    @Override
    public void execute(Runnable task) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<Void> promise = (RemotePromise<Void>)asyncServiceWithoutResult.executeVoid(task.getClass().getName(), classBody, state);
        execute(promise);
    }
    
    private byte[] encode(Object task) {
        // erase RedissonClient field to avoid its serialization
        Field[] fields = task.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (RedissonClient.class.isAssignableFrom(field.getType())
                    && field.isAnnotationPresent(RInject.class)) {
                field.setAccessible(true);
                try {
                    field.set(task, null);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        
        try {
            return codec.getValueEncoder().encode(task);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private byte[] getClassBody(Object task) {
        Class<?> c = task.getClass();
        byte[] classBody = class2bytes.get(c);
        if (classBody == null) {
            String className = c.getName();
            String classAsPath = className.replace('.', '/') + ".class";
            InputStream classStream = c.getClassLoader().getResourceAsStream(classAsPath);
            
            DataInputStream s = new DataInputStream(classStream);
            try {
                classBody = new byte[s.available()];
                s.readFully(classBody);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            
            class2bytes.put(c, classBody);
        }
        return classBody;
    }

    @Override
    public void shutdown() {
        commandExecutor.evalWrite(getName(), codec, RedisCommands.EVAL_VOID_WITH_VALUES_6,
                "if redis.call('exists', KEYS[2]) == 0 then "
                     + "if redis.call('get', KEYS[1]) == '0' or redis.call('exists', KEYS[1]) == 0 then "
                        + "redis.call('set', KEYS[2], ARGV[2]);"
                        + "redis.call('publish', KEYS[3], ARGV[2]);"
                     + "else "
                        + "redis.call('set', KEYS[2], ARGV[1]);"
                     + "end;"
                + "end;", 
                Arrays.<Object>asList(tasksCounter.getName(), status.getName(), topic.getChannelNames().get(0)),
                SHUTDOWN_STATE, TERMINATED_STATE);
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public boolean delete() {
        return keys.delete(requestQueueName, status.getName(), tasksCounter.getName()) > 0;
    }
    
    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return status.isExists() && status.get() >= SHUTDOWN_STATE;
    }

    @Override
    public boolean isTerminated() {
        return status.isExists() && status.get() == TERMINATED_STATE;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (isTerminated()) {
            return true;
        }
        
        final CountDownLatch latch = new CountDownLatch(1);
        MessageListener<Integer> listener = new MessageListener<Integer>() {
            @Override
            public void onMessage(String channel, Integer msg) {
                if (msg == TERMINATED_STATE) {
                    latch.countDown();
                }
            }
        };
        int listenerId = topic.addListener(listener);

        if (isTerminated()) {
            topic.removeListener(listenerId);
            return true;
        }
        
        boolean res = latch.await(timeout, unit);
        topic.removeListener(listenerId);
        return res;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<T> promise = (RemotePromise<T>)asyncService.execute(task.getClass().getName(), classBody, state);
        execute(promise);
        return promise;
    }
    
    private void check(Object task) {
        if (task.getClass().isAnonymousClass()) {
            throw new IllegalArgumentException("Task can't be created using anonymous class");
        }
        if (!Serializable.class.isAssignableFrom(task.getClass())) {
            throw new IllegalArgumentException("Task class should implement Serializable interface");
        }
    }

    private <T> void execute(RemotePromise<T> promise) {
        io.netty.util.concurrent.Future<Boolean> addFuture = promise.getAddFuture();
        addFuture.syncUninterruptibly();
        Boolean res = addFuture.getNow();
        if (!res) {
            throw new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
        }
    }

    @Override
    public <T> Future<T> submit(Runnable task, final T result) {
        final Promise<T> resultFuture = connectionManager.newPromise();
        io.netty.util.concurrent.Future<Object> future = (io.netty.util.concurrent.Future<Object>) submit(task);
        future.addListener(new FutureListener<Object>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Object> future) throws Exception {
                if (!future.isSuccess()) {
                    resultFuture.setFailure(future.cause());
                    return;
                }
                resultFuture.setSuccess(result);
            }
        });
        return resultFuture;
    }

    @Override
    public Future<?> submit(Runnable task) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<Void> promise = (RemotePromise<Void>) asyncService.executeVoid(task.getClass().getName(), classBody, state);
        execute(promise);
        return promise;
    }

    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                            boolean timed, long millis) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null) {
            throw new NullPointerException();
        }

        int ntasks = tasks.size();
        if (ntasks == 0) {
            throw new IllegalArgumentException();
        }

        List<Future<T>> futures = new ArrayList<Future<T>>(ntasks);

        try {
            ExecutionException ee = null;
            long lastTime = timed ? System.currentTimeMillis() : 0;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // Start one task for sure; the rest incrementally
            futures.add(submit(it.next()));
            --ntasks;
            int active = 1;

            for (;;) {
                Future<T> f = poll(futures);
                if (f == null) {
                    if (ntasks > 0) {
                        --ntasks;
                        futures.add(submit(it.next()));
                        ++active;
                    }
                    else if (active == 0)
                        break;
                    else if (timed) {
                        f = poll(futures, millis, TimeUnit.MILLISECONDS);
                        if (f == null)
                            throw new TimeoutException();
                        long now = System.currentTimeMillis();
                        millis -= now - lastTime;
                        lastTime = now;
                    }
                    else
                        f = poll(futures, -1, null);
                }
                if (f != null) {
                    --active;
                    try {
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }

            if (ee == null)
                ee = new ExecutionException("No tasks were finised", null);
            throw ee;

        } finally {
            for (Future<T> f : futures)
                f.cancel(true);
        }
    }
    
    private <T> Future<T> poll(List<Future<T>> futures, long timeout, TimeUnit timeUnit) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Future<T>> result = new AtomicReference<Future<T>>();
        FutureListener<T> listener = new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                latch.countDown();
                result.compareAndSet(null, future);
            }
        };
        for (Future<T> future : futures) {
            io.netty.util.concurrent.Future<T> f = (io.netty.util.concurrent.Future<T>) future;
            f.addListener(listener);
        }
        
        if (timeout == -1) {
            latch.await();
        } else {
            latch.await(timeout, timeUnit);
        }
        
        for (Future<T> future : futures) {
            io.netty.util.concurrent.Future<T> f = (io.netty.util.concurrent.Future<T>) future;
            f.removeListener(listener);
        }

        return result.get();
    }
    
    private <T> Future<T> poll(List<Future<T>> futures) {
        for (Future<T> future : futures) {
            if (future.isDone()) {
                return future;
            }
        }
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            return null;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toMillis(timeout));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                Future<T> future = submit(t);
                futures.add(future);
            }
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    try {
                        f.get();
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (Future<T> f : futures)
                    f.cancel(true);
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null || unit == null) {
            throw new NullPointerException();
        }
        
        long millis = unit.toMillis(timeout);
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        
        try {
            long lastTime = System.currentTimeMillis();

            for (Callable<T> task : tasks) {
                Future<T> future = submit(task);
                futures.add(future);
                
                long now = System.currentTimeMillis();
                millis -= now - lastTime;
                lastTime = now;
                if (millis <= 0) {
                    int remainFutures = tasks.size() - futures.size();
                    for (int i = 0; i < remainFutures; i++) {
                        Promise<T> cancelledFuture = connectionManager.newPromise();
                        cancelledFuture.cancel(true);
                        futures.add(cancelledFuture);
                        
                    }
                    return futures;
                }
            }

            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    if (millis <= 0)
                        return futures;
                    try {
                        f.get(millis, TimeUnit.MILLISECONDS);
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;
                    }
                    long now = System.currentTimeMillis();
                    millis -= now - lastTime;
                    lastTime = now;
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (Future<T> f : futures)
                    f.cancel(true);
        }
    }

}
