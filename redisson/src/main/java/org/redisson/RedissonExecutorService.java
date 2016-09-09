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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RScheduledFuture;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RInject;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.connection.ConnectionManager;
import org.redisson.executor.ExecutorRemoteService;
import org.redisson.executor.RedissonScheduledFuture;
import org.redisson.executor.RemoteExecutorService;
import org.redisson.executor.RemoteExecutorServiceAsync;
import org.redisson.executor.RemoteExecutorServiceImpl;
import org.redisson.executor.RemotePromise;
import org.redisson.executor.ScheduledExecutorRemoteService;
import org.redisson.misc.RPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorService implements RScheduledExecutorService {

    private static final Logger log = LoggerFactory.getLogger(RedissonExecutorService.class);
    
    public static final int SHUTDOWN_STATE = 1;
    public static final int TERMINATED_STATE = 2;
    
    private final CommandExecutor commandExecutor;
    private final ConnectionManager connectionManager;
    private final Codec codec;
    private final Redisson redisson;
    
    private final String schedulerTasksName;
    private final String schedulerQueueName;
    private final String schedulerChannelName;
    
    private final String tasksCounterName;
    private final String statusName;
    private final RTopic<Integer> terminationTopic;

    private final RemoteExecutorServiceAsync asyncScheduledService;
    private final RemoteExecutorServiceAsync asyncScheduledServiceAtFixed;
    private final RemoteExecutorServiceAsync asyncService;
    private final RemoteExecutorServiceAsync asyncServiceWithoutResult;
    private final ScheduledExecutorRemoteService scheduledRemoteService;
    
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
        tasksCounterName = objectName + ":counter";
        statusName = objectName + ":status";
        terminationTopic = redisson.getTopic(objectName + ":termination-topic", codec);

        schedulerChannelName = objectName + ":scheduler-channel";
        schedulerQueueName = objectName + ":scheduler";
        schedulerTasksName = objectName + ":scheduler-tasks";
        
        ExecutorRemoteService remoteService = new ExecutorRemoteService(codec, redisson, name, commandExecutor);
        remoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        remoteService.setTasksCounterName(tasksCounterName);
        remoteService.setStatusName(statusName);
        asyncService = remoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.DAYS));
        asyncServiceWithoutResult = remoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        
        scheduledRemoteService = new ScheduledExecutorRemoteService(codec, redisson, name, commandExecutor);
        scheduledRemoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        scheduledRemoteService.setTasksCounterName(tasksCounterName);
        scheduledRemoteService.setStatusName(statusName);
        scheduledRemoteService.setSchedulerQueueName(schedulerQueueName);
        scheduledRemoteService.setSchedulerChannelName(schedulerChannelName);
        scheduledRemoteService.setSchedulerTasksName(schedulerTasksName);
        asyncScheduledService = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.DAYS));
        asyncScheduledServiceAtFixed = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
    }
    
    private void registerScheduler() {
        final AtomicReference<Timeout> timeoutReference = new AtomicReference<Timeout>();
        
        RTopic<Long> schedulerTopic = redisson.getTopic(schedulerChannelName, LongCodec.INSTANCE);
        schedulerTopic.addListener(new BaseStatusListener() {
            @Override
            public void onSubscribe(String channel) {
                RFuture<Long> startTimeFuture = commandExecutor.evalReadAsync(schedulerQueueName, LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                     // get startTime from scheduler queue head task
                        "local v = redis.call('zrange', KEYS[1], 0, 0, 'WITHSCORES'); "
                      + "if v[1] ~= nil then "
                          + "return v[2]; "
                      + "end "
                      + "return nil;",
                      Collections.<Object>singletonList(schedulerQueueName));

                addListener(timeoutReference, startTimeFuture);
            }
        });
        
        schedulerTopic.addListener(new MessageListener<Long>() {
            @Override
            public void onMessage(String channel, Long startTime) {
                scheduleTask(timeoutReference, startTime);
            }
        });
    }

    private void scheduleTask(final AtomicReference<Timeout> timeoutReference, final Long startTime) {
        if (startTime == null) {
            return;
        }
        
        if (timeoutReference.get() != null) {
            timeoutReference.get().cancel();
            timeoutReference.set(null);
        }
        
        long delay = startTime - System.currentTimeMillis();
        if (delay > 10) {
            Timeout timeout = connectionManager.newTimeout(new TimerTask() {                    
                @Override
                public void run(Timeout timeout) throws Exception {
                    pushTask(timeoutReference, startTime);
                }
            }, delay, TimeUnit.MILLISECONDS);
            timeoutReference.set(timeout);
        } else {
            pushTask(timeoutReference, startTime);
        }
    }

    private void pushTask(AtomicReference<Timeout> timeoutReference, Long startTime) {
        RFuture<Long> startTimeFuture = commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local expiredTaskIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
              + "if #expiredTaskIds > 0 then "
                  + "redis.call('zrem', KEYS[2], unpack(expiredTaskIds));"
                  + "local expiredTasks = redis.call('hmget', KEYS[3], unpack(expiredTaskIds));"
                  + "redis.call('rpush', KEYS[1], unpack(expiredTasks));"
              + "end; "
                // get startTime from scheduler queue head task
              + "local v = redis.call('zrange', KEYS[2], 0, 0, 'WITHSCORES'); "
              + "if v[1] ~= nil then "
                 + "return v[2]; "
              + "end "
              + "return nil;",
              Arrays.<Object>asList(requestQueueName, schedulerQueueName, schedulerTasksName), 
              System.currentTimeMillis(), 10);

        addListener(timeoutReference, startTimeFuture);
    }

    private void addListener(final AtomicReference<Timeout> timeoutReference, RFuture<Long> startTimeFuture) {
        startTimeFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    if (future.cause() instanceof RedissonShutdownException) {
                        return;
                    }
                    log.error(future.cause().getMessage(), future.cause());
                    scheduleTask(timeoutReference, System.currentTimeMillis() + 5 * 1000L);
                    return;
                }
                
                if (future.getNow() != null) {
                    scheduleTask(timeoutReference, future.getNow());
                }
            }
        });
    }
    
    @Override
    public void registerWorkers(int workers, ExecutorService executor) {
        registerScheduler();
        
        RemoteExecutorServiceImpl service = 
                new RemoteExecutorServiceImpl(commandExecutor, redisson, codec, requestQueueName);
        service.setStatusName(statusName);
        service.setTasksCounterName(tasksCounterName);
        service.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        service.setSchedulerTasksName(schedulerTasksName);
        service.setSchedulerChannelName(schedulerChannelName);
        service.setSchedulerQueueName(schedulerQueueName);
        
        redisson.getRemoteSerivce(name, codec).register(RemoteExecutorService.class, service, workers, executor);
    }

    @Override
    public void execute(Runnable task) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<Void> promise = (RemotePromise<Void>)asyncServiceWithoutResult.executeRunnable(task.getClass().getName(), classBody, state);
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
        commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "if redis.call('exists', KEYS[2]) == 0 then "
                     + "if redis.call('get', KEYS[1]) == '0' or redis.call('exists', KEYS[1]) == 0 then "
                        + "redis.call('set', KEYS[2], ARGV[2]);"
                        + "redis.call('publish', KEYS[3], ARGV[2]);"
                     + "else "
                        + "redis.call('set', KEYS[2], ARGV[1]);"
                     + "end;"
                + "end;", 
                Arrays.<Object>asList(tasksCounterName, statusName, terminationTopic.getChannelNames().get(0)),
                SHUTDOWN_STATE, TERMINATED_STATE);
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public boolean delete() {
        return commandExecutor.get(deleteAsync());
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        final RPromise<Boolean> result = connectionManager.newPromise();
        RFuture<Long> deleteFuture = redisson.getKeys().deleteAsync(
                requestQueueName, statusName, tasksCounterName, schedulerQueueName, schedulerTasksName);
        deleteFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                result.trySuccess(future.getNow() > 0);
            }
        });
        return result;
    }
    
    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return checkState(SHUTDOWN_STATE);
    }

    private boolean checkState(int state) {
        return commandExecutor.evalWrite(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[1]) == 1 and tonumber(redis.call('get', KEYS[1])) >= tonumber(ARGV[1]) then "
                + "return 1;"
            + "end;"
            + "return 0;", 
                Arrays.<Object>asList(statusName),
                state);
    }

    @Override
    public boolean isTerminated() {
        return checkState(TERMINATED_STATE);
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
        int listenerId = terminationTopic.addListener(listener);

        if (isTerminated()) {
            terminationTopic.removeListener(listenerId);
            return true;
        }
        
        boolean res = latch.await(timeout, unit);
        terminationTopic.removeListener(listenerId);
        return res;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        RemotePromise<T> promise = (RemotePromise<T>) submitAsync(task);
        execute(promise);
        return promise;
    }
    
    public <T> RFuture<T> submitAsync(Callable<T> task) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<T> result = (RemotePromise<T>) asyncService.executeCallable(task.getClass().getName(), classBody, state);
        addListener(result);
        return result;
    }

    private <T> void addListener(final RemotePromise<T> result) {
        result.getAddFuture().addListener(new FutureListener<Boolean>() {

            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (!future.getNow()) {
                    result.tryFailure(new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state"));
                }
                
            }
        });
    }
    
    private void check(Object task) {
        if (task == null) {
            throw new NullPointerException("Task is not defined");
        }
        if (task.getClass().isAnonymousClass()) {
            throw new IllegalArgumentException("Task can't be created using anonymous class");
        }
        if (task.getClass().isMemberClass()
                && !Modifier.isStatic(task.getClass().getModifiers())) {
            throw new IllegalArgumentException("Task class is an inner class and it should be static");
        }
    }

    private <T> void execute(RemotePromise<T> promise) {
        RFuture<Boolean> addFuture = promise.getAddFuture();
        addFuture.syncUninterruptibly();
        Boolean res = addFuture.getNow();
        if (!res) {
            throw new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
        }
    }

    @Override
    public <T> RFuture<T> submit(Runnable task, final T result) {
        final RPromise<T> resultFuture = connectionManager.newPromise();
        RFuture<Object> future = (RFuture<Object>) submit(task);
        future.addListener(new FutureListener<Object>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Object> future) throws Exception {
                if (!future.isSuccess()) {
                    resultFuture.tryFailure(future.cause());
                    return;
                }
                resultFuture.trySuccess(result);
            }
        });
        return resultFuture;
    }

    @Override
    public Future<?> submit(Runnable task) {
        RemotePromise<Void> promise = (RemotePromise<Void>) submitAsync(task);
        execute(promise);
        return promise;
    }
    
    @Override
    public RFuture<?> submitAsync(Runnable task) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncService.executeRunnable(task.getClass().getName(), classBody, state);
        addListener(result);
        return result;
    }
    
    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(task, delay, unit);
        execute((RemotePromise<?>)future.getInnerPromise());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(delay);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledService.scheduleRunnable(task.getClass().getName(), classBody, state, startTime);
        addListener(result);
        return new RedissonScheduledFuture<Void>(result, startTime);
    }
    
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        RedissonScheduledFuture<V> future = (RedissonScheduledFuture<V>) scheduleAsync(task, delay, unit);
        execute((RemotePromise<V>)future.getInnerPromise());
        return future;
    }
    
    @Override
    public <V> RScheduledFuture<V> scheduleAsync(Callable<V> task, long delay, TimeUnit unit) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(delay);
        RemotePromise<V> result = (RemotePromise<V>) asyncScheduledService.scheduleCallable(task.getClass().getName(), classBody, state, startTime);
        addListener(result);
        return new RedissonScheduledFuture<V>(result, startTime);
    }
    
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAtFixedRateAsync(task, initialDelay, period, unit);
        execute((RemotePromise<?>)future.getInnerPromise());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleAtFixedRateAsync(Runnable task, long initialDelay, long period, TimeUnit unit) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.scheduleAtFixedRate(task.getClass().getName(), classBody, state, startTime, unit.toMillis(period));
        addListener(result);
        return new RedissonScheduledFuture<Void>(result, startTime);
    }

    @Override
    public RScheduledFuture<?> schedule(Runnable task, CronSchedule cronSchedule) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(task, cronSchedule);
        execute((RemotePromise<?>)future.getInnerPromise());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, CronSchedule cronSchedule) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        final Date startDate = cronSchedule.getExpression().getNextValidTimeAfter(new Date());
        long startTime = startDate.getTime();
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.schedule(task.getClass().getName(), classBody, state, startTime, cronSchedule.getExpression().getCronExpression());
        addListener(result);
        return new RedissonScheduledFuture<Void>(result, startTime) {
            public long getDelay(TimeUnit unit) {
                return unit.convert(startDate.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            };
        };
    }
    
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleWithFixedDelayAsync(task, initialDelay, delay, unit);
        execute((RemotePromise<?>)future.getInnerPromise());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleWithFixedDelayAsync(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        check(task);
        byte[] classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.scheduleWithFixedDelay(task.getClass().getName(), classBody, state, startTime, unit.toMillis(delay));
        addListener(result);
        return new RedissonScheduledFuture<Void>(result, startTime);
    }

    @Override
    public boolean cancelScheduledTask(String taskId) {
        return scheduledRemoteService.cancelExecution(taskId);
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
            RFuture<T> f = (RFuture<T>) future;
            f.addListener(listener);
        }
        
        if (timeout == -1) {
            latch.await();
        } else {
            latch.await(timeout, timeUnit);
        }
        
        for (Future<T> future : futures) {
            RFuture<T> f = (RFuture<T>) future;
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
                        RPromise<T> cancelledFuture = connectionManager.newPromise();
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
