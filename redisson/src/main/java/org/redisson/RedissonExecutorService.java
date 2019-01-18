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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.invoke.SerializedLambda;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.CronSchedule;
import org.redisson.api.ExecutorOptions;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RExecutorBatchFuture;
import org.redisson.api.RExecutorFuture;
import org.redisson.api.RFuture;
import org.redisson.api.RRemoteService;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RScheduledFuture;
import org.redisson.api.RSemaphore;
import org.redisson.api.RTopic;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.connection.ConnectionManager;
import org.redisson.executor.RedissonExecutorBatchFuture;
import org.redisson.executor.RedissonExecutorFuture;
import org.redisson.executor.RedissonExecutorFutureReference;
import org.redisson.executor.RedissonExecutorRemoteService;
import org.redisson.executor.RedissonScheduledFuture;
import org.redisson.executor.RemoteExecutorService;
import org.redisson.executor.RemoteExecutorServiceAsync;
import org.redisson.executor.RemotePromise;
import org.redisson.executor.ScheduledTasksService;
import org.redisson.executor.TasksBatchService;
import org.redisson.executor.TasksRunnerService;
import org.redisson.executor.TasksService;
import org.redisson.executor.params.ScheduledAtFixedRateParameters;
import org.redisson.executor.params.ScheduledCronExpressionParameters;
import org.redisson.executor.params.ScheduledParameters;
import org.redisson.executor.params.ScheduledWithFixedDelayParameters;
import org.redisson.executor.params.TaskParameters;
import org.redisson.misc.Injector;
import org.redisson.misc.PromiseDelegator;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;
import org.redisson.remote.ResponseEntry.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorService implements RScheduledExecutorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonExecutorService.class);
    
    private static RemoteInvocationOptions RESULT_OPTIONS = RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.HOURS);
    
    public static final int SHUTDOWN_STATE = 1;
    public static final int TERMINATED_STATE = 2;
    
    private final CommandExecutor commandExecutor;
    private final ConnectionManager connectionManager;
    private final Codec codec;
    private final Redisson redisson;
    
    private final String tasksName;
    private final String schedulerQueueName;
    private final String schedulerChannelName;
    private final String tasksRetryIntervalName;
    
    private final String workersChannelName;
    private final String workersSemaphoreName;
    private final String workersCounterName;
    
    private final String tasksCounterName;
    private final String statusName;
    private final RTopic terminationTopic;
    private final RRemoteService remoteService;
    private final RTopic workersTopic;
    private int workersGroupListenerId;

    private final RemoteExecutorServiceAsync asyncScheduledService;
    private final RemoteExecutorServiceAsync asyncScheduledServiceAtFixed;
    private final RemoteExecutorServiceAsync asyncService;
    private final RemoteExecutorServiceAsync asyncServiceWithoutResult;
    
    private final ScheduledTasksService scheduledRemoteService;
    private final TasksService executorRemoteService;
    
    private final Map<Class<?>, ClassBody> class2body = PlatformDependent.newConcurrentHashMap();

    private final String name;
    private final String requestQueueName;
    private final String responseQueueName;
    private final QueueTransferService queueTransferService;
    private final String executorId;
    private final ConcurrentMap<String, ResponseEntry> responses;

    private final ReferenceQueue<RExecutorFuture<?>> referenceDueue = new ReferenceQueue<RExecutorFuture<?>>();
    private final Collection<RedissonExecutorFutureReference> references = Collections.newSetFromMap(PlatformDependent.<RedissonExecutorFutureReference, Boolean>newConcurrentHashMap());
    
    public RedissonExecutorService(Codec codec, CommandExecutor commandExecutor, Redisson redisson, 
            String name, QueueTransferService queueTransferService, ConcurrentMap<String, ResponseEntry> responses, ExecutorOptions options) {
        super();
        this.codec = codec;
        this.commandExecutor = commandExecutor;
        this.connectionManager = commandExecutor.getConnectionManager();
        this.name = name;
        this.redisson = redisson;
        this.queueTransferService = queueTransferService;
        this.responses = responses;

        if (codec == connectionManager.getCodec()) {
            this.executorId = connectionManager.getId().toString();
        } else {
            this.executorId = connectionManager.getId().toString() + ":" + RemoteExecutorServiceAsync.class.getName() + ":" + name;
        }
        
        remoteService = new RedissonExecutorRemoteService(codec, redisson, name, connectionManager.getCommandExecutor(), executorId, responses);
        requestQueueName = ((RedissonRemoteService)remoteService).getRequestQueueName(RemoteExecutorService.class);
        responseQueueName = ((RedissonRemoteService)remoteService).getResponseQueueName(executorId);
        String objectName = requestQueueName;
        tasksCounterName = objectName + ":counter";
        tasksName = objectName + ":tasks";
        statusName = objectName + ":status";
        terminationTopic = redisson.getTopic(objectName + ":termination-topic", LongCodec.INSTANCE);

        tasksRetryIntervalName = objectName + ":retry-interval";
        schedulerChannelName = objectName + ":scheduler-channel";
        schedulerQueueName = objectName + ":scheduler";
        
        workersChannelName = objectName + ":workers-channel";
        workersSemaphoreName = objectName + ":workers-semaphore";
        workersCounterName = objectName + ":workers-counter";
        
        workersTopic = redisson.getTopic(workersChannelName);
        
        executorRemoteService = new TasksService(codec, redisson, name, commandExecutor, executorId, responses);
        executorRemoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        executorRemoteService.setTasksCounterName(tasksCounterName);
        executorRemoteService.setStatusName(statusName);
        executorRemoteService.setTasksName(tasksName);
        executorRemoteService.setSchedulerChannelName(schedulerChannelName);
        executorRemoteService.setSchedulerQueueName(schedulerQueueName);
        executorRemoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        executorRemoteService.setTasksRetryInterval(options.getTaskRetryInterval());
        asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        asyncServiceWithoutResult = executorRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        
        scheduledRemoteService = new ScheduledTasksService(codec, redisson, name, commandExecutor, executorId, responses);
        scheduledRemoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        scheduledRemoteService.setTasksCounterName(tasksCounterName);
        scheduledRemoteService.setStatusName(statusName);
        scheduledRemoteService.setSchedulerQueueName(schedulerQueueName);
        scheduledRemoteService.setSchedulerChannelName(schedulerChannelName);
        scheduledRemoteService.setTasksName(tasksName);
        scheduledRemoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        scheduledRemoteService.setTasksRetryInterval(options.getTaskRetryInterval());
        asyncScheduledService = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        asyncScheduledServiceAtFixed = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
    }
    
    protected String generateRequestId() {
        byte[] id = new byte[16];
        // TODO JDK UPGRADE replace to native ThreadLocalRandom
        PlatformDependent.threadLocalRandom().nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }
    
    @Override
    public int countActiveWorkers() {
        String id = generateRequestId();
        int subscribers = (int) workersTopic.publish(id);
        if (subscribers == 0) {
            return 0;
        }

        RSemaphore semaphore = redisson.getSemaphore(workersSemaphoreName + ":" + id);
        try {
            semaphore.tryAcquire(subscribers, 10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        RAtomicLong atomicLong = redisson.getAtomicLong(workersCounterName + ":" + id);
        long result = atomicLong.get();
        redisson.getKeys().delete(semaphore, atomicLong);
        return (int) result;
    }
    
    @Override
    public void registerWorkers(int workers) {
        registerWorkers(workers, commandExecutor.getConnectionManager().getExecutor());
    }
    
    @Override
    public void registerWorkers(final int workers, ExecutorService executor) {
        QueueTransferTask task = new QueueTransferTask(connectionManager) {
            @Override
            protected RTopic getTopic() {
                return new RedissonTopic(LongCodec.INSTANCE, commandExecutor, schedulerChannelName);
            }

            @Override
            protected RFuture<Long> pushTaskAsync() {
                return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                        "local expiredTaskIds = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
                      + "local retryInterval = redis.call('get', KEYS[4]);"
                      + "if #expiredTaskIds > 0 then "
                          + "redis.call('zrem', KEYS[2], unpack(expiredTaskIds));"
                          + "if retryInterval ~= false then "
                              + "local startTime = tonumber(ARGV[1]) + tonumber(retryInterval);"
                          
                              + "for i = 1, #expiredTaskIds, 1 do "
                                  + "local name = expiredTaskIds[i];"
                                  + "local scheduledName = expiredTaskIds[i];"
                                  + "if string.sub(scheduledName, 1, 2) ~= 'ff' then "
                                      + "scheduledName = 'ff' .. scheduledName; "
                                  + "else "
                                      + "name = string.sub(name, 3, string.len(name)); "
                                  + "end;"
                                      
                                  + "redis.call('zadd', KEYS[2], startTime, scheduledName);"
                                  + "local v = redis.call('zrange', KEYS[2], 0, 0); "
                                  // if new task added to queue head then publish its startTime 
                                  // to all scheduler workers 
                                  + "if v[1] == expiredTaskIds[i] then "
                                      + "redis.call('publish', KEYS[3], startTime); "
                                  + "end;"
                                    
                                + "if redis.call('linsert', KEYS[1], 'before', name, name) < 1 then "
                                    + "redis.call('rpush', KEYS[1], name); "
                                + "else "
                                    + "redis.call('lrem', KEYS[1], -1, name); "
                                + "end; "
                              + "end; "
                          + "else "
                              + "redis.call('rpush', KEYS[1], unpack(expiredTaskIds));"
                          + "end; "
                      + "end; "
                        // get startTime from scheduler queue head task
                      + "local v = redis.call('zrange', KEYS[2], 0, 0, 'WITHSCORES'); "
                      + "if v[1] ~= nil then "
                         + "return v[2]; "
                      + "end "
                      + "return nil;",
                      Arrays.<Object>asList(requestQueueName, schedulerQueueName, schedulerChannelName, tasksRetryIntervalName), 
                      System.currentTimeMillis(), 50);
            }
        };
        queueTransferService.schedule(getName(), task);
        
        TasksRunnerService service = 
                new TasksRunnerService(commandExecutor, redisson, codec, requestQueueName, responses);
        service.setStatusName(statusName);
        service.setTasksCounterName(tasksCounterName);
        service.setTasksName(tasksName);
        service.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        service.setSchedulerChannelName(schedulerChannelName);
        service.setSchedulerQueueName(schedulerQueueName);
        service.setTasksRetryIntervalName(tasksRetryIntervalName);
        
        remoteService.register(RemoteExecutorService.class, service, workers, executor);
        workersGroupListenerId = workersTopic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String id) {
                redisson.getAtomicLong(workersCounterName + ":" + id).getAndAdd(workers);
                redisson.getSemaphore(workersSemaphoreName + ":" + id).release();
            }
        });
    }
    
    @Override
    public void execute(Runnable task) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<Void> promise = (RemotePromise<Void>)asyncServiceWithoutResult.executeRunnable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
        syncExecute(promise);
    }
    
    @Override
    public void execute(Runnable ...tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncServiceWithoutResult = executorRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        for (Runnable task : tasks) {
            check(task);
            ClassBody classBody = getClassBody(task);
            byte[] state = encode(task);
            asyncServiceWithoutResult.executeRunnable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
        }
        
        List<Boolean> result = (List<Boolean>) executorRemoteService.executeAdd();
        if (!result.get(0)) {
            throw new RejectedExecutionException("Tasks have been rejected. ExecutorService is in shutdown state");
        }
    }

    private TasksBatchService createBatchService() {
        TasksBatchService executorRemoteService = new TasksBatchService(codec, redisson, name, commandExecutor, executorId, responses);
        executorRemoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        executorRemoteService.setTasksCounterName(tasksCounterName);
        executorRemoteService.setStatusName(statusName);
        executorRemoteService.setTasksName(tasksName);
        executorRemoteService.setSchedulerChannelName(schedulerChannelName);
        executorRemoteService.setSchedulerQueueName(schedulerQueueName);
        executorRemoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        return executorRemoteService;
    }
    
    private byte[] encode(Object task) {
        // erase RedissonClient field to avoid its serialization
        Injector.inject(task, null);
        
        ByteBuf buf = null;
        try {
            buf = codec.getValueEncoder().encode(task);
            byte[] dst = new byte[buf.readableBytes()];
            buf.readBytes(dst);
            return dst;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }
    
    public static class ClassBody {
        
        private byte[] lambda;
        private byte[] clazz;
        private String clazzName;
        
        public ClassBody(byte[] lambda, byte[] clazz, String clazzName) {
            super();
            this.lambda = lambda;
            this.clazz = clazz;
            this.clazzName = clazzName;
        }
        
        public String getClazzName() {
            return clazzName;
        }
        
        public byte[] getClazz() {
            return clazz;
        }
        
        public byte[] getLambda() {
            return lambda;
        }
        
    }

    private ClassBody getClassBody(Object task) {
        Class<?> c = task.getClass();
        ClassBody result = class2body.get(c);
        if (result == null) {
            String className = c.getName();
            String classAsPath = className.replace('.', '/') + ".class";
            InputStream classStream = c.getClassLoader().getResourceAsStream(classAsPath);
            
            byte[] lambdaBody = null;
            if (classStream == null) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try {
                    ObjectOutput oo = new ObjectOutputStream(os);
                    oo.writeObject(task);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Unable to serialize lambda", e);
                }
                lambdaBody = os.toByteArray();
                
                SerializedLambda lambda;
                try {
                    Method writeReplace = task.getClass().getDeclaredMethod("writeReplace");
                    writeReplace.setAccessible(true);
                    lambda = (SerializedLambda) writeReplace.invoke(task);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Lambda should implement java.io.Serializable interface", ex);
                }
                
                className = lambda.getCapturingClass().replace('/', '.');
                classStream = task.getClass().getClassLoader().getResourceAsStream(lambda.getCapturingClass() + ".class");
            }
            
            byte[] classBody;
            try {
                DataInputStream s = new DataInputStream(classStream);
                classBody = new byte[s.available()];
                s.readFully(classBody);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            } finally {
                try {
                    classStream.close();
                } catch (IOException e) {
                    // skip
                }
            }
            
            result = new ClassBody(lambdaBody, classBody, className);
            class2body.put(c, result);
        }
        return result;
    }

    @Override
    public void shutdown() {
        queueTransferService.remove(getName());
        remoteService.deregister(RemoteExecutorService.class);
        workersTopic.removeListener(workersGroupListenerId);
        
        commandExecutor.evalWrite(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "if redis.call('exists', KEYS[2]) == 0 then "
                     + "if redis.call('get', KEYS[1]) == '0' or redis.call('exists', KEYS[1]) == 0 then "
                        + "redis.call('set', KEYS[2], ARGV[2]);"
                        + "redis.call('publish', KEYS[3], ARGV[2]);"
                     + "else "
                        + "redis.call('set', KEYS[2], ARGV[1]);"
                     + "end;"
                + "end;", 
                Arrays.<Object>asList(tasksCounterName, statusName, terminationTopic.getChannelNames().get(0), tasksRetryIntervalName),
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
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        RFuture<Long> deleteFuture = redisson.getKeys().deleteAsync(
                requestQueueName, statusName, tasksCounterName, schedulerQueueName, tasksName, tasksRetryIntervalName);
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
        MessageListener<Long> listener = new MessageListener<Long>() {
            @Override
            public void onMessage(CharSequence channel, Long msg) {
                if (msg == TERMINATED_STATE) {
                    latch.countDown();
                }
            }
        };
        int listenerId = terminationTopic.addListener(Long.class, listener);

        if (isTerminated()) {
            terminationTopic.removeListener(listenerId);
            return true;
        }
        
        boolean res = latch.await(timeout, unit);
        terminationTopic.removeListener(listenerId);
        return res;
    }

    @Override
    public <T> RExecutorFuture<T> submit(Callable<T> task) {
        RemotePromise<T> promise = (RemotePromise<T>) ((PromiseDelegator<T>) submitAsync(task)).getInnerPromise();
        syncExecute(promise);
        return createFuture(promise);
    }
    
    @Override
    public <T> RExecutorFuture<T> submitAsync(Callable<T> task) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<T> result = (RemotePromise<T>) asyncService.executeCallable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
        addListener(result);
        return createFuture(result);
    }
    
    @Override
    public RExecutorBatchFuture submit(Callable<?> ...tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        List<RExecutorFuture<?>> result = new ArrayList<RExecutorFuture<?>>();
        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        for (Callable<?> task : tasks) {
            check(task);
            ClassBody classBody = getClassBody(task);
            byte[] state = encode(task);
            RemotePromise<?> promise = (RemotePromise<?>)asyncService.executeCallable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
            RedissonExecutorFuture<?> executorFuture = new RedissonExecutorFuture(promise);
            result.add(executorFuture);
        }
        
        List<Boolean> addResult = (List<Boolean>) executorRemoteService.executeAdd();
        if (!addResult.get(0)) {
            throw new RejectedExecutionException("Tasks have been rejected. ExecutorService is in shutdown state");
        }
        
        return new RedissonExecutorBatchFuture(result);
    }
    
    @Override
    public RExecutorBatchFuture submitAsync(Callable<?> ...tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        final List<RExecutorFuture<?>> result = new ArrayList<RExecutorFuture<?>>();
        for (Callable<?> task : tasks) {
            check(task);
            ClassBody classBody = getClassBody(task);
            byte[] state = encode(task);
            RemotePromise<?> promise = (RemotePromise<?>)asyncService.executeCallable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
            RedissonExecutorFuture<?> executorFuture = new RedissonExecutorFuture(promise);
            result.add(executorFuture);
        }
        
        executorRemoteService.executeAddAsync().addListener(new FutureListener<List<Boolean>>() {

            @Override
            public void operationComplete(io.netty.util.concurrent.Future<List<Boolean>> future) throws Exception {
                if (!future.isSuccess()) {
                    for (RExecutorFuture<?> executorFuture : result) {
                        ((RPromise<Void>)executorFuture).tryFailure(future.cause());
                    }
                    return;
                }
                
                for (Boolean bool : future.getNow()) {
                    if (!bool) {
                        RejectedExecutionException ex = new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
                        for (RExecutorFuture<?> executorFuture : result) {
                            ((RPromise<Void>)executorFuture).tryFailure(ex);
                        }
                        break;
                    }
                }
            }
        });

        return new RedissonExecutorBatchFuture(result);
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

    private <T> void syncExecute(RemotePromise<T> promise) {
        RFuture<Boolean> addFuture = promise.getAddFuture();
        addFuture.syncUninterruptibly();
        Boolean res = addFuture.getNow();
        if (!res) {
            throw new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
        }
    }

    @Override
    public <T> RExecutorFuture<T> submit(Runnable task, final T result) {
        final RPromise<T> resultFuture = new RedissonPromise<T>();
        RemotePromise<T> future = (RemotePromise<T>) ((PromiseDelegator<T>) submit(task)).getInnerPromise();
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
        return new RedissonExecutorFuture<T>(resultFuture, future.getRequestId());
    }

    @Override
    public RExecutorBatchFuture submit(Runnable ...tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        List<RExecutorFuture<?>> result = new ArrayList<RExecutorFuture<?>>();
        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        for (Runnable task : tasks) {
            check(task);
            ClassBody classBody = getClassBody(task);
            byte[] state = encode(task);
            RemotePromise<Void> promise = (RemotePromise<Void>)asyncService.executeRunnable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
            RedissonExecutorFuture<Void> executorFuture = new RedissonExecutorFuture<Void>(promise);
            result.add(executorFuture);
        }
        
        List<Boolean> addResult = (List<Boolean>) executorRemoteService.executeAdd();
        if (!addResult.get(0)) {
            throw new RejectedExecutionException("Tasks have been rejected. ExecutorService is in shutdown state");
        }
        
        return new RedissonExecutorBatchFuture(result);
    }
    
    @Override
    public RExecutorBatchFuture submitAsync(Runnable ...tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        final List<RExecutorFuture<?>> result = new ArrayList<RExecutorFuture<?>>();
        for (Runnable task : tasks) {
            check(task);
            ClassBody classBody = getClassBody(task);
            byte[] state = encode(task);
            RemotePromise<Void> promise = (RemotePromise<Void>)asyncService.executeRunnable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
            RedissonExecutorFuture<Void> executorFuture = new RedissonExecutorFuture<Void>(promise);
            result.add(executorFuture);
        }
        
        executorRemoteService.executeAddAsync().addListener(new FutureListener<List<Boolean>>() {

            @Override
            public void operationComplete(io.netty.util.concurrent.Future<List<Boolean>> future) throws Exception {
                if (!future.isSuccess()) {
                    for (RExecutorFuture<?> executorFuture : result) {
                        ((RPromise<Void>)executorFuture).tryFailure(future.cause());
                    }
                    return;
                }
                
                for (Boolean bool : future.getNow()) {
                    if (!bool) {
                        RejectedExecutionException ex = new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
                        for (RExecutorFuture<?> executorFuture : result) {
                            ((RPromise<Void>)executorFuture).tryFailure(ex);
                        }
                        break;
                    }
                }
            }
        });

        return new RedissonExecutorBatchFuture(result);
    }

    
    @Override
    public RExecutorFuture<?> submit(Runnable task) {
        RemotePromise<Void> promise = (RemotePromise<Void>) ((PromiseDelegator<Void>) submitAsync(task)).getInnerPromise();
        syncExecute(promise);
        return createFuture(promise);
    }
    
    @Override
    public RExecutorFuture<?> submitAsync(Runnable task) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncService.executeRunnable(new TaskParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state));
        addListener(result);
        return createFuture(result);
    }
    
    private void cancelResponseHandling(RequestId requestId) {
        synchronized (responses) {
            ResponseEntry entry = responses.get(responseQueueName);
            if (entry == null) {
                return;
            }
            
            List<Result> list = entry.getResponses().remove(requestId);
            if (list != null) {
                for (Result result : list) {
                    result.getScheduledFuture().cancel(true);
                }
            }
            if (entry.getResponses().isEmpty()) {
                responses.remove(responseQueueName, entry);
            }
        }
    }
    
    @Override
    public RScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(task, delay, unit);
        RemotePromise<?> rp = (RemotePromise<?>)future.getInnerPromise();
        syncExecute(rp);
        storeReference(future, rp.getRequestId());
        return future;
    }

    private <T> RExecutorFuture<T> createFuture(RemotePromise<T> promise) {
        RExecutorFuture<T> f = new RedissonExecutorFuture<T>(promise);
        storeReference(f, promise.getRequestId());
        return f;
    }
    
    private <T> RScheduledFuture<T> createFuture(RemotePromise<T> promise, long scheduledExecutionTime) {
        RedissonScheduledFuture<T> f = new RedissonScheduledFuture<T>(promise, scheduledExecutionTime);
        storeReference(f, promise.getRequestId());
        return f;
    }
    
    private void storeReference(RExecutorFuture<?> future, RequestId requestId) {
        while (true) {
            RedissonExecutorFutureReference r = (RedissonExecutorFutureReference) referenceDueue.poll();
            if (r == null) {
                break;
            }
            references.remove(r);
            
            if (!r.getPromise().hasListeners()) {
                cancelResponseHandling(r.getRequestId());
            }
        }
        
        RPromise<?> promise = ((PromiseDelegator<?>) future).getInnerPromise();
        RedissonExecutorFutureReference reference = new RedissonExecutorFutureReference(requestId, future, referenceDueue, promise);
        references.add(reference);
    }
    
    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(delay);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledService.scheduleRunnable(new ScheduledParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state, startTime));
        addListener(result);
        
        return createFuture(result, startTime);
    }
    
    @Override
    public <V> RScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        RedissonScheduledFuture<V> future = (RedissonScheduledFuture<V>) scheduleAsync(task, delay, unit);
        RemotePromise<?> rp = (RemotePromise<?>)future.getInnerPromise();
        syncExecute(rp);
        storeReference(future, rp.getRequestId());
        return future;
    }
    
    @Override
    public <V> RScheduledFuture<V> scheduleAsync(Callable<V> task, long delay, TimeUnit unit) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(delay);
        RemotePromise<V> result = (RemotePromise<V>) asyncScheduledService.scheduleCallable(new ScheduledParameters(classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state, startTime));
        addListener(result);
        return createFuture(result, startTime);
    }
    
    @Override
    public RScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAtFixedRateAsync(task, initialDelay, period, unit);
        RemotePromise<?> rp = (RemotePromise<?>)future.getInnerPromise();
        syncExecute(rp);
        storeReference(future, rp.getRequestId());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleAtFixedRateAsync(Runnable task, long initialDelay, long period, TimeUnit unit) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        ScheduledAtFixedRateParameters params = new ScheduledAtFixedRateParameters();
        params.setClassName(classBody.getClazzName());
        params.setClassBody(classBody.getClazz());
        params.setLambdaBody(classBody.getLambda());
        params.setState(state);
        params.setStartTime(startTime);
        params.setPeriod(unit.toMillis(period));
        params.setExecutorId(executorId);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.scheduleAtFixedRate(params);
        addListener(result);
        return createFuture(result, startTime);
    }

    @Override
    public RScheduledFuture<?> schedule(Runnable task, CronSchedule cronSchedule) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(task, cronSchedule);
        RemotePromise<?> rp = (RemotePromise<?>)future.getInnerPromise();
        syncExecute(rp);
        storeReference(future, rp.getRequestId());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, CronSchedule cronSchedule) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        final Date startDate = cronSchedule.getExpression().getNextValidTimeAfter(new Date());
        if (startDate == null) {
            throw new IllegalArgumentException("Wrong cron expression! Unable to calculate start date");
        }
        long startTime = startDate.getTime();
        
        ScheduledCronExpressionParameters params = new ScheduledCronExpressionParameters();
        params.setClassName(classBody.getClazzName());
        params.setClassBody(classBody.getClazz());
        params.setLambdaBody(classBody.getLambda());
        params.setState(state);
        params.setStartTime(startTime);
        params.setCronExpression(cronSchedule.getExpression().getCronExpression());
        params.setTimezone(cronSchedule.getExpression().getTimeZone().getID());
        params.setExecutorId(executorId);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.schedule(params);
        addListener(result);
        RedissonScheduledFuture<Void> f = new RedissonScheduledFuture<Void>(result, startTime) {
            public long getDelay(TimeUnit unit) {
                return unit.convert(startDate.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            };
        };
        storeReference(f, result.getRequestId());
        return f;
    }
    
    @Override
    public RScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleWithFixedDelayAsync(task, initialDelay, delay, unit);
        RemotePromise<?> rp = (RemotePromise<?>)future.getInnerPromise();
        syncExecute(rp);
        storeReference(future, rp.getRequestId());
        return future;
    }
    
    @Override
    public RScheduledFuture<?> scheduleWithFixedDelayAsync(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        
        ScheduledWithFixedDelayParameters params = new ScheduledWithFixedDelayParameters();
        params.setClassName(classBody.getClazzName());
        params.setClassBody(classBody.getClazz());
        params.setLambdaBody(classBody.getLambda());
        params.setState(state);
        params.setStartTime(startTime);
        params.setDelay(unit.toMillis(delay));
        params.setExecutorId(executorId);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.scheduleWithFixedDelay(params);
        addListener(result);
        return createFuture(result, startTime);
    }

    @Override
    public boolean cancelScheduledTask(String taskId) {
        return cancelTask(taskId);
    }
    
    @Override
    public boolean cancelTask(String taskId) {
        if (taskId.startsWith("01")) {
            RFuture<Boolean> scheduledFuture = scheduledRemoteService.cancelExecutionAsync(new RequestId(taskId));
            return commandExecutor.get(scheduledFuture);
        }
        RFuture<Boolean> scheduledFuture = executorRemoteService.cancelExecutionAsync(new RequestId(taskId));
        return commandExecutor.get(scheduledFuture);
        
    }
    
    private <T> io.netty.util.concurrent.Future<T> poll(List<RExecutorFuture<?>> futures, long timeout, TimeUnit timeUnit) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<io.netty.util.concurrent.Future<T>> result = new AtomicReference<io.netty.util.concurrent.Future<T>>();
        FutureListener<T> listener = new FutureListener<T>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<T> future) throws Exception {
                latch.countDown();
                result.compareAndSet(null, future);
            }
        };
        for (Future<?> future : futures) {
            RFuture<T> f = (RFuture<T>) future;
            f.addListener(listener);
        }
        
        if (timeout == -1) {
            latch.await();
        } else {
            latch.await(timeout, timeUnit);
        }
        
        for (Future<?> future : futures) {
            RFuture<T> f = (RFuture<T>) future;
            f.removeListener(listener);
        }

        return result.get();
    }
    
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return invokeAny(tasks, -1, null);
        } catch (TimeoutException cannotHappen) {
            return null;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
                                   throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null) {
            throw new NullPointerException();
        }

        List<RExecutorFuture<?>> futures = new ArrayList<RExecutorFuture<?>>();
        for (Callable<T> callable : tasks) {
            RExecutorFuture<T> future = submit(callable);
            futures.add(future);
        }

        io.netty.util.concurrent.Future<T> result = poll(futures, timeout, unit);
        if (result == null) {
            throw new TimeoutException();
        }
        for (RExecutorFuture<?> f : futures) {
            f.cancel(true);
        }
        return result.getNow();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        
        RExecutorBatchFuture future = submit(tasks.toArray(new Callable[tasks.size()]));
        future.await();
        List<?> futures = future.getTaskFutures();
        return (List<Future<T>>)futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null || unit == null) {
            throw new NullPointerException();
        }
        
        RExecutorBatchFuture future = submit(tasks.toArray(new Callable[tasks.size()]));
        future.await(timeout, unit);
        List<?> futures = future.getTaskFutures();
        return (List<Future<T>>)futures;
    }

}
