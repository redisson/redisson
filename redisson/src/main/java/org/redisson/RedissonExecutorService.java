/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.*;
import org.redisson.executor.params.*;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.Injector;
import org.redisson.misc.WrappedLock;
import org.redisson.remote.ResponseEntry;
import org.redisson.remote.ResponseEntry.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.SerializedLambda;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorService implements RScheduledExecutorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonExecutorService.class);
    
    private static final RemoteInvocationOptions RESULT_OPTIONS = RemoteInvocationOptions.defaults().noAck().expectResultWithin(1, TimeUnit.HOURS);
    
    public static final int SHUTDOWN_STATE = 1;
    public static final int TERMINATED_STATE = 2;
    
    private final CommandAsyncExecutor commandExecutor;
    private final Codec codec;
    private final Redisson redisson;
    
    private final String tasksName;
    private final String schedulerQueueName;
    private final String schedulerChannelName;
    private final String tasksRetryIntervalName;
    private final String tasksExpirationTimeName;
    
    private final String workersChannelName;
    private final String workersSemaphoreName;
    private final String workersCounterName;
    
    private final String tasksCounterName;
    private final String statusName;
    private final RTopic terminationTopic;
    private final RedissonExecutorRemoteService remoteService;
    private final RTopic workersTopic;
    private int workersGroupListenerId;

    private final RemoteExecutorServiceAsync asyncScheduledService;
    private final RemoteExecutorServiceAsync asyncScheduledServiceAtFixed;
    private final RemoteExecutorServiceAsync asyncService;
    private final RemoteExecutorServiceAsync asyncServiceWithoutResult;
    
    private final ScheduledTasksService scheduledRemoteService;
    private final TasksService executorRemoteService;
    
    private final Map<Class<?>, ClassBody> class2body = new ConcurrentHashMap<>();

    private final String name;
    private final String requestQueueName;
    private final String responseQueueName;
    private final QueueTransferService queueTransferService;
    private final String executorId;
    private final Map<String, ResponseEntry> responses;

    private final ReferenceQueue<RExecutorFuture<?>> referenceDueue = new ReferenceQueue<>();
    private final Collection<RedissonExecutorFutureReference> references = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final IdGenerator idGenerator;
    
    public RedissonExecutorService(Codec codec, CommandAsyncExecutor commandExecutor, Redisson redisson,
                                   String name, ExecutorOptions options) {
        super();
        this.codec = commandExecutor.getServiceManager().getCodec(codec);
        this.commandExecutor = commandExecutor;
        this.name = commandExecutor.getServiceManager().getConfig().getNameMapper().map(name);
        this.redisson = redisson;
        this.queueTransferService = commandExecutor.getServiceManager().getQueueTransferService();
        this.responses = commandExecutor.getServiceManager().getResponses();

        if (codec == commandExecutor.getServiceManager().getCfg().getCodec()) {
            this.executorId = commandExecutor.getServiceManager().getId();
        } else {
            this.executorId = commandExecutor.getServiceManager().getId() + ":" + RemoteExecutorServiceAsync.class.getName() + ":" + name;
        }
        
        remoteService = new RedissonExecutorRemoteService(codec, name, commandExecutor, executorId);
        requestQueueName = remoteService.getRequestQueueName(RemoteExecutorService.class);
        responseQueueName = remoteService.getResponseQueueName(executorId);
        String objectName = requestQueueName;
        tasksCounterName = objectName + ":counter";
        tasksName = objectName + ":tasks";
        statusName = objectName + ":status";
        terminationTopic = RedissonTopic.createRaw(LongCodec.INSTANCE, commandExecutor, objectName + ":termination-topic");

        tasksRetryIntervalName = objectName + ":retry-interval";
        tasksExpirationTimeName = objectName + ":expiration";
        schedulerChannelName = objectName + ":scheduler-channel";
        schedulerQueueName = objectName + ":scheduler";
        
        workersChannelName = objectName + ":workers-channel";
        workersSemaphoreName = objectName + ":workers-semaphore";
        workersCounterName = objectName + ":workers-counter";
        
        workersTopic = RedissonTopic.createRaw(commandExecutor, workersChannelName);

        remoteService.setStatusName(statusName);
        remoteService.setSchedulerQueueName(schedulerQueueName);
        remoteService.setTasksCounterName(tasksCounterName);
        remoteService.setTasksExpirationTimeName(tasksExpirationTimeName);
        remoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        remoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));

        executorRemoteService = new TasksService(codec, name, commandExecutor, executorId);
        executorRemoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        executorRemoteService.setTasksCounterName(tasksCounterName);
        executorRemoteService.setStatusName(statusName);
        executorRemoteService.setTasksName(tasksName);
        executorRemoteService.setSchedulerChannelName(schedulerChannelName);
        executorRemoteService.setSchedulerQueueName(schedulerQueueName);
        executorRemoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        executorRemoteService.setTasksExpirationTimeName(tasksExpirationTimeName);
        executorRemoteService.setTasksRetryInterval(options.getTaskRetryInterval());
        asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        asyncServiceWithoutResult = executorRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        
        scheduledRemoteService = new ScheduledTasksService(codec, name, commandExecutor, executorId);
        scheduledRemoteService.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        scheduledRemoteService.setTasksCounterName(tasksCounterName);
        scheduledRemoteService.setStatusName(statusName);
        scheduledRemoteService.setSchedulerQueueName(schedulerQueueName);
        scheduledRemoteService.setSchedulerChannelName(schedulerChannelName);
        scheduledRemoteService.setTasksName(tasksName);
        scheduledRemoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        scheduledRemoteService.setTasksExpirationTimeName(tasksExpirationTimeName);
        scheduledRemoteService.setTasksRetryInterval(options.getTaskRetryInterval());
        asyncScheduledService = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        asyncScheduledServiceAtFixed = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());

        idGenerator = options.getIdGenerator();
    }
    
    @Override
    public int getTaskCount() {
        return commandExecutor.get(getTaskCountAsync());
    }

    @Override
    public RFuture<Integer> getTaskCountAsync() {
        return commandExecutor.readAsync(getName(), LongCodec.INSTANCE, RedisCommands.GET_INTEGER, tasksCounterName);
    }

    @Override
    public boolean hasTask(String taskId) {
        return commandExecutor.get(hasTaskAsync(taskId));
    }

    @Override
    public Set<String> getTaskIds() {
        return commandExecutor.get(getTaskIdsAsync());
    }

    @Override
    public RFuture<Set<String>> getTaskIdsAsync() {
        return commandExecutor.writeAsync(tasksName, StringCodec.INSTANCE, RedisCommands.HKEYS, tasksName);
    }

    @Override
    public RFuture<Boolean> hasTaskAsync(String taskId) {
        return commandExecutor.writeAsync(tasksName, LongCodec.INSTANCE, RedisCommands.HEXISTS, tasksName, taskId);
    }

    @Override
    public int countActiveWorkers() {
        String id = commandExecutor.getServiceManager().generateId();
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
        registerWorkers(WorkerOptions.defaults().workers(workers));
    }
    
    @Override
    public void registerWorkers(WorkerOptions options) {
        if (options.getWorkers() == 0) {
            throw new IllegalArgumentException("workers amount can't be zero");
        }
        
        QueueTransferTask task = new QueueTransferTask(commandExecutor.getServiceManager()) {
            @Override
            protected RTopic getTopic() {
                return RedissonTopic.createRaw(LongCodec.INSTANCE, commandExecutor, schedulerChannelName);
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
                                  + "if string.sub(scheduledName, 1, 3) ~= 'ff:' then "
                                      + "scheduledName = 'ff:' .. scheduledName; "
                                  + "else "
                                      + "name = string.sub(name, 4, string.len(name)); "
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
                new TasksRunnerService(commandExecutor, redisson, codec, requestQueueName);
        service.setStatusName(statusName);
        service.setTasksCounterName(tasksCounterName);
        service.setTasksName(tasksName);
        service.setTerminationTopicName(terminationTopic.getChannelNames().get(0));
        service.setSchedulerChannelName(schedulerChannelName);
        service.setSchedulerQueueName(schedulerQueueName);
        service.setTasksExpirationTimeName(tasksExpirationTimeName);
        service.setTasksRetryIntervalName(tasksRetryIntervalName);
        if (options.getTasksInjector() != null) {
            service.setTasksInjector(options.getTasksInjector());
        }

        ExecutorService es = commandExecutor.getServiceManager().getExecutor();
        if (options.getExecutorService() != null) {
            es = options.getExecutorService();
        }

        remoteService.setListeners(options.getListeners());
        remoteService.setTaskTimeout(options.getTaskTimeout());
        remoteService.register(RemoteExecutorService.class, service, options.getWorkers(), es);
        workersGroupListenerId = workersTopic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String id) {
                redisson.getAtomicLong(workersCounterName + ":" + id).getAndAdd(options.getWorkers());
                redisson.getSemaphore(workersSemaphoreName + ":" + id).release();
            }
        });
    }
    
    @Override
    public void registerWorkers(int workers, ExecutorService executor) {
        registerWorkers(WorkerOptions.defaults().workers(workers).executorService(executor));
    }
    
    @Override
    public void execute(Runnable task) {
        check(task);
        RemotePromise<Void> promise = (RemotePromise<Void>) asyncServiceWithoutResult.executeRunnable(
                                            createTaskParameters(task)).toCompletableFuture();
        syncExecute(promise);
    }
    
    @Override
    public void execute(Runnable... tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncServiceWithoutResult = executorRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        for (Runnable task : tasks) {
            check(task);
            asyncServiceWithoutResult.executeRunnable(createTaskParameters(task));
        }
        
        List<Boolean> result = executorRemoteService.executeAdd();
        if (!result.get(0)) {
            throw new RejectedExecutionException("Tasks have been rejected. ExecutorService is in shutdown state");
        }
    }

    private TasksBatchService createBatchService() {
        TasksBatchService executorRemoteService = new TasksBatchService(codec, getName(), commandExecutor, executorId);
        executorRemoteService.setTasksExpirationTimeName(tasksExpirationTimeName);
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
        if (workersGroupListenerId != 0) {
            workersTopic.removeListener(workersGroupListenerId);
        }

        commandExecutor.get(commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "if redis.call('exists', KEYS[2]) == 0 then "
                     + "if redis.call('get', KEYS[1]) == '0' or redis.call('exists', KEYS[1]) == 0 then "
                        + "redis.call('set', KEYS[2], ARGV[2]);"
                        + "redis.call('publish', KEYS[3], ARGV[2]);"
                     + "else "
                        + "redis.call('set', KEYS[2], ARGV[1]);"
                     + "end;"
                + "end;", 
                Arrays.<Object>asList(tasksCounterName, statusName, terminationTopic.getChannelNames().get(0), tasksRetryIntervalName),
                SHUTDOWN_STATE, TERMINATED_STATE));
    }

    @Override
    public String getName() {
        return commandExecutor.getServiceManager().getConfig().getNameMapper().unmap(name);
    }
    
    @Override
    public boolean delete() {
        return commandExecutor.get(deleteAsync());
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        RFuture<Long> deleteFuture = redisson.getKeys().deleteAsync(
                requestQueueName, statusName, tasksCounterName, schedulerQueueName, tasksName, tasksRetryIntervalName);
        CompletionStage<Boolean> f = deleteFuture.thenApply(res -> res > 0);
        return new CompletableFutureWrapper<>(f);
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
        return commandExecutor.get(commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[1]) == 1 and tonumber(redis.call('get', KEYS[1])) >= tonumber(ARGV[1]) then "
                + "return 1;"
            + "end;"
            + "return 0;", 
                Arrays.<Object>asList(statusName),
                state));
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
        
        CountDownLatch latch = new CountDownLatch(1);
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
        return submit(idGenerator.generateId(), task);
    }

    @Override
    public <T> RExecutorFuture<T> submit(Callable<T> task, long timeToLive, TimeUnit timeUnit) {
        return submit(idGenerator.generateId(), task, Duration.ofMillis(timeUnit.toMillis(timeToLive)));
    }

    @Override
    public <T> RExecutorFuture<T> submitAsync(Callable<T> task, long timeToLive, TimeUnit timeUnit) {
        return submitAsync(idGenerator.generateId(), task, Duration.ofMillis(timeUnit.toMillis(timeToLive)));
    }

    @Override
    public <T> RExecutorFuture<T> submitAsync(Callable<T> task) {
        return submitAsync(idGenerator.generateId(), task);
    }
    
    @Override
    public RExecutorBatchFuture submit(Callable<?>... tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        List<RExecutorFuture<?>> result = new ArrayList<>();
        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        for (Callable<?> task : tasks) {
            check(task);
            RemotePromise<?> promise = (RemotePromise<?>) asyncService.executeCallable(createTaskParameters(task)).toCompletableFuture();
            RedissonExecutorFuture<?> executorFuture = new RedissonExecutorFuture(promise);
            result.add(executorFuture);
        }
        
        List<Boolean> addResult = executorRemoteService.executeAdd();
        if (!addResult.get(0)) {
            throw new RejectedExecutionException("Tasks have been rejected. ExecutorService is in shutdown state");
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(result.stream()
                                                                    .map(CompletionStage::toCompletableFuture)
                                                                    .toArray(CompletableFuture[]::new));
        return new RedissonExecutorBatchFuture(future, result);
    }

    protected TaskParameters createTaskParameters(Callable<?> task) {
        return createTaskParameters(idGenerator.generateId(), task);
    }

    protected TaskParameters createTaskParameters(String taskId, Callable<?> task) {
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        String id = taskId;
        return new TaskParameters(id, classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state);
    }
    
    protected TaskParameters createTaskParameters(Runnable task) {
        return createTaskParameters(idGenerator.generateId(), task);
    }

    protected TaskParameters createTaskParameters(String taskId, Runnable task) {
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        String id = taskId;
        return new TaskParameters(id, classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state);
    }

    @Override
    public RExecutorBatchFuture submitAsync(Callable<?>... tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        List<RExecutorFuture<?>> result = new ArrayList<>();
        for (Callable<?> task : tasks) {
            check(task);
            RemotePromise<?> promise = (RemotePromise<?>) asyncService.executeCallable(createTaskParameters(task)).toCompletableFuture();
            RedissonExecutorFuture<?> executorFuture = new RedissonExecutorFuture(promise);
            result.add(executorFuture);
        }
        
        executorRemoteService.executeAddAsync().whenComplete((res, e) -> {
            if (e != null) {
                for (RExecutorFuture<?> executorFuture : result) {
                    executorFuture.toCompletableFuture().completeExceptionally(e);
                }
                return;
            }
            
            for (Boolean bool : res) {
                if (!bool) {
                    RejectedExecutionException ex = new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
                    for (RExecutorFuture<?> executorFuture : result) {
                        executorFuture.toCompletableFuture().completeExceptionally(ex);
                    }
                    break;
                }
            }
        });

        CompletableFuture<Void> future = CompletableFuture.allOf(result.stream()
                                                                    .map(CompletionStage::toCompletableFuture)
                                                                    .toArray(CompletableFuture[]::new));
        return new RedissonExecutorBatchFuture(future, result);
    }


    private <T> void addListener(RemotePromise<T> result) {
        result.getAddFuture().whenComplete((res, e) -> {
            if (e != null) {
                result.toCompletableFuture().completeExceptionally(e);
                return;
            }
            
            if (!res) {
                result.toCompletableFuture().completeExceptionally(new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state"));
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
        CompletableFuture<Boolean> addFuture = promise.getAddFuture();
        Boolean res = addFuture.join();
        if (!res) {
            throw new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
        }
    }

    @Override
    public <T> RExecutorFuture<T> submit(Runnable task, T result) {
        RemotePromise<T> future = (RemotePromise<T>) submit(task).toCompletableFuture();
        CompletableFuture<T> f = future.thenApply(res -> result);
        return new RedissonExecutorFuture<T>(f, future.getRequestId());
    }

    @Override
    public RExecutorBatchFuture submit(Runnable... tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        List<RExecutorFuture<?>> result = new ArrayList<>();
        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        for (Runnable task : tasks) {
            check(task);
            RemotePromise<Void> promise = (RemotePromise<Void>) asyncService.executeRunnable(createTaskParameters(task)).toCompletableFuture();
            RedissonExecutorFuture<Void> executorFuture = new RedissonExecutorFuture<Void>(promise);
            result.add(executorFuture);
        }
        
        List<Boolean> addResult = executorRemoteService.executeAdd();
        if (!addResult.get(0)) {
            throw new RejectedExecutionException("Tasks have been rejected. ExecutorService is in shutdown state");
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(result.stream()
                                                                    .map(CompletionStage::toCompletableFuture)
                                                                    .toArray(CompletableFuture[]::new));
        return new RedissonExecutorBatchFuture(future, result);
    }
    
    @Override
    public RExecutorBatchFuture submitAsync(Runnable... tasks) {
        if (tasks.length == 0) {
            throw new NullPointerException("Tasks are not defined");
        }

        TasksBatchService executorRemoteService = createBatchService();
        RemoteExecutorServiceAsync asyncService = executorRemoteService.get(RemoteExecutorServiceAsync.class, RESULT_OPTIONS);
        List<RExecutorFuture<?>> result = new ArrayList<>();
        for (Runnable task : tasks) {
            check(task);
            RemotePromise<Void> promise = (RemotePromise<Void>) asyncService.executeRunnable(createTaskParameters(task)).toCompletableFuture();
            RedissonExecutorFuture<Void> executorFuture = new RedissonExecutorFuture<Void>(promise);
            result.add(executorFuture);
        }
        
        executorRemoteService.executeAddAsync().whenComplete((res, e) -> {
            if (e != null) {
                for (RExecutorFuture<?> executorFuture : result) {
                    executorFuture.toCompletableFuture().completeExceptionally(e);
                }
                return;
            }
            
            for (Boolean bool : res) {
                if (!bool) {
                    RejectedExecutionException ex = new RejectedExecutionException("Task rejected. ExecutorService is in shutdown state");
                    for (RExecutorFuture<?> executorFuture : result) {
                        executorFuture.toCompletableFuture().completeExceptionally(ex);
                    }
                    break;
                }
            }
        });

        CompletableFuture<Void> future = CompletableFuture.allOf(result.stream()
                                                                    .map(CompletionStage::toCompletableFuture)
                                                                    .toArray(CompletableFuture[]::new));
        return new RedissonExecutorBatchFuture(future, result);
    }

    
    @Override
    public RExecutorFuture<?> submit(Runnable task) {
        return submit(idGenerator.generateId(), task);
    }

    @Override
    public RExecutorFuture<?> submit(Runnable task, long timeToLive, TimeUnit timeUnit) {
        return submit(idGenerator.generateId(), task, Duration.ofMillis(timeUnit.toMillis(timeToLive)));
    }

    @Override
    public RExecutorFuture<?> submitAsync(Runnable task, long timeToLive, TimeUnit timeUnit) {
        return submitAsync(idGenerator.generateId(), task, Duration.ofMillis(timeUnit.toMillis(timeToLive)));
    }

    @Override
    public RExecutorFuture<?> submitAsync(Runnable task) {
        return submitAsync(idGenerator.generateId(), task);
    }
    
    private void cancelResponseHandling(String requestId) {
        responses.computeIfPresent(responseQueueName, (key, entry) -> {
            List<Result> list = entry.getResponses().remove(requestId);
            if (list != null) {
                for (Result result : list) {
                    result.cancelResponseTimeout();
                }
            }
            if (entry.getResponses().isEmpty()) {
                return null;
            }
            return entry;
        });
    }
    
    @Override
    public RScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return schedule(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(delay)));
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
    
    private void storeReference(RExecutorFuture<?> future, String requestId) {
        while (true) {
            RedissonExecutorFutureReference r = (RedissonExecutorFutureReference) referenceDueue.poll();
            if (r == null) {
                break;
            }
            references.remove(r);
            
            if (r.getPromise().getNumberOfDependents() == 0) {
                cancelResponseHandling(r.getRequestId());
            }
        }
        
        CompletableFuture<?> promise = ((CompletableFutureWrapper<?>) future).toCompletableFuture();
        RedissonExecutorFutureReference reference = new RedissonExecutorFutureReference(requestId, future, referenceDueue, promise);
        references.add(reference);
    }
    
    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit) {
        return scheduleAsync(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(delay)));
    }
    
    @Override
    public <V> RScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit unit) {
        return schedule(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(delay)));
    }
    
    @Override
    public <V> RScheduledFuture<V> scheduleAsync(Callable<V> task, long delay, TimeUnit unit) {
        return scheduleAsync(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(delay)));
    }

    @Override
    public RScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit, long ttl, TimeUnit ttlUnit) {
        return schedule(idGenerator.generateId(), command, Duration.ofMillis(unit.toMillis(delay)), Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, long delay, TimeUnit unit, long timeToLive, TimeUnit ttlUnit) {
        return scheduleAsync(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(delay)), Duration.ofMillis(ttlUnit.toMillis(timeToLive)));
    }

    @Override
    public <V> RScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit, long timeToLive, TimeUnit ttlUnit) {
        return schedule(idGenerator.generateId(), callable, Duration.ofMillis(unit.toMillis(delay)), Duration.ofMillis(ttlUnit.toMillis(timeToLive)));
    }

    @Override
    public <V> RScheduledFuture<V> scheduleAsync(Callable<V> task, long delay, TimeUnit unit, long timeToLive, TimeUnit ttlUnit) {
        return scheduleAsync(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(delay)), Duration.ofMillis(ttlUnit.toMillis(timeToLive)));
    }

    private ScheduledParameters createScheduledParameters(String id, Duration timeToLive, ClassBody classBody, byte[] state, long startTime) {
        ScheduledParameters params = new ScheduledParameters(id, classBody.getClazzName(), classBody.getClazz(), classBody.getLambda(), state, startTime);
        if (timeToLive.toMillis() > 0) {
            params.setTtl(timeToLive.toMillis());
        }
        return params;
    }

    @Override
    public RScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduleAtFixedRate(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(initialDelay)), Duration.ofMillis(unit.toMillis(period)));
    }
    
    @Override
    public RScheduledFuture<?> scheduleAtFixedRateAsync(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduleAtFixedRate(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(initialDelay)), Duration.ofMillis(unit.toMillis(period)));
    }

    @Override
    public RScheduledFuture<?> schedule(Runnable task, CronSchedule cronSchedule) {
        return schedule(idGenerator.generateId(), task, cronSchedule);
    }
    
    @Override
    public RScheduledFuture<?> scheduleAsync(Runnable task, CronSchedule cronSchedule) {
        return scheduleAsync(idGenerator.generateId(), task, cronSchedule);
    }
    
    @Override
    public RScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithFixedDelay(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(initialDelay)), Duration.ofMillis(unit.toMillis(delay)));
    }
    
    @Override
    public RScheduledFuture<?> scheduleWithFixedDelayAsync(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduleWithFixedDelayAsync(idGenerator.generateId(), task, Duration.ofMillis(unit.toMillis(initialDelay)), Duration.ofMillis(unit.toMillis(delay)));
    }

    @Override
    public Boolean cancelTask(String taskId) {
        return commandExecutor.get(cancelTaskAsync(taskId));
    }

    @Override
    public RFuture<Boolean> cancelTaskAsync(String taskId) {
        return scheduledRemoteService.cancelExecutionAsync(taskId);
    }

    private <T> T poll(List<CompletableFuture<?>> futures, long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
        CompletableFuture<Object> future = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
        try {
            if (timeout == -1) {
                return (T) future.get();
            } else {
                return (T) future.get(timeout, timeUnit);
            }
        } catch (ExecutionException e) {
            throw commandExecutor.convertException(e);
        }
    }
    
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        try {
            return invokeAny(tasks, -1, null);
        } catch (TimeoutException cannotHappen) {
            return null;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null) {
            throw new NullPointerException();
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Callable<T> callable : tasks) {
            RExecutorFuture<T> future = submit(callable);
            futures.add(future.toCompletableFuture());
        }

        T result = poll(futures, timeout, unit);
        for (CompletableFuture<?> f : futures) {
            f.cancel(true);
        }
        return result;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        
        RExecutorBatchFuture future = submit(tasks.toArray(new Callable[0]));
        try {
            future.toCompletableFuture().join();
        } catch (Exception e) {
            // skip
        }
        List<?> futures = future.getTaskFutures();
        return (List<Future<T>>) futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null || unit == null) {
            throw new NullPointerException();
        }
        
        RExecutorBatchFuture future = submit(tasks.toArray(new Callable[0]));
        try {
            future.toCompletableFuture().get(timeout, unit);
        } catch (ExecutionException e) {
            LOGGER.error(e.getMessage(), e);
        } catch (TimeoutException | CancellationException e) {
            // skip
        }
        List<?> futures = future.getTaskFutures();
        return (List<Future<T>>) futures;
    }

    @Override
    public <T> RExecutorFuture<T> submit(String id, Callable<T> task) {
        RemotePromise<T> promise = (RemotePromise<T>) submitAsync(id, task).toCompletableFuture();
        syncExecute(promise);
        return createFuture(promise);
    }

    @Override
    public <T> RExecutorFuture<T> submitAsync(String id, Callable<T> task) {
        check(task);
        TaskParameters params = createTaskParameters(id, task);
        RemotePromise<T> result = (RemotePromise<T>) asyncService.executeCallable(params).toCompletableFuture();
        addListener(result);
        return createFuture(result);
    }

    @Override
    public <T> RExecutorFuture<T> submit(String id, Callable<T> task, Duration timeToLive) {
        RemotePromise<T> promise = (RemotePromise<T>) submitAsync(id, task, timeToLive).toCompletableFuture();
        syncExecute(promise);
        return createFuture(promise);
    }

    @Override
    public <T> RExecutorFuture<T> submitAsync(String id, Callable<T> task, Duration timeToLive) {
        check(task);
        TaskParameters taskParameters = createTaskParameters(id, task);
        taskParameters.setTtl(timeToLive.toMillis());
        RemotePromise<T> result = (RemotePromise<T>) asyncService.executeCallable(taskParameters).toCompletableFuture();
        addListener(result);
        return createFuture(result);
    }

    @Override
    public RExecutorFuture<?> submit(String id, Runnable task, Duration timeToLive) {
        RemotePromise<Void> promise = (RemotePromise<Void>) submitAsync(id, task, timeToLive).toCompletableFuture();
        syncExecute(promise);
        return createFuture(promise);
    }

    @Override
    public RExecutorFuture<?> submitAsync(String id, Runnable task, Duration timeToLive) {
        check(task);
        TaskParameters taskParameters = createTaskParameters(id, task);
        taskParameters.setTtl(timeToLive.toMillis());
        RemotePromise<Void> result = (RemotePromise<Void>) asyncService.executeRunnable(taskParameters).toCompletableFuture();
        addListener(result);
        return createFuture(result);
    }

    @Override
    public RExecutorFuture<?> submit(String id, Runnable task) {
        RemotePromise<Void> promise = (RemotePromise<Void>) submitAsync(id, task).toCompletableFuture();
        syncExecute(promise);
        return createFuture(promise);
    }

    @Override
    public RExecutorFuture<?> submitAsync(String id, Runnable task) {
        check(task);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncService.executeRunnable(createTaskParameters(id, task)).toCompletableFuture();
        addListener(result);
        return createFuture(result);
    }

    @Override
    public RScheduledFuture<?> schedule(String id, Runnable command, Duration delay) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(id, command, delay);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public RScheduledFuture<?> scheduleAsync(String id, Runnable task, Duration delay) {
        return scheduleAsync(id, task, delay, Duration.ZERO);
    }

    @Override
    public RScheduledFuture<?> schedule(String id, Runnable command, Duration delay, Duration timeToLive) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(id, command, delay, timeToLive);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public RScheduledFuture<?> scheduleAsync(String id, Runnable task, Duration delay, Duration timeToLive) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + delay.toMillis();
        ScheduledParameters params = createScheduledParameters(id, timeToLive, classBody, state, startTime);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledService.scheduleRunnable(params).toCompletableFuture();
        addListener(result);
        return createFuture(result, startTime);
    }

    @Override
    public <V> RScheduledFuture<V> schedule(String id, Callable<V> callable, Duration delay) {
        RedissonScheduledFuture<V> future = (RedissonScheduledFuture<V>) scheduleAsync(id, callable, delay);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public <V> RScheduledFuture<V> scheduleAsync(String id, Callable<V> task, Duration delay) {
        return scheduleAsync(id, task, delay, Duration.ZERO);
    }

    @Override
    public <V> RScheduledFuture<V> schedule(String id, Callable<V> callable, Duration delay, Duration timeToLive) {
        RedissonScheduledFuture<V> future = (RedissonScheduledFuture<V>) scheduleAsync(id, callable, delay, timeToLive);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public <V> RScheduledFuture<V> scheduleAsync(String id, Callable<V> task, Duration delay, Duration timeToLive) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + delay.toMillis();
        ScheduledParameters params = createScheduledParameters(id, timeToLive, classBody, state, startTime);
        RemotePromise<V> result = (RemotePromise<V>) asyncScheduledService.scheduleCallable(params).toCompletableFuture();
        addListener(result);
        return createFuture(result, startTime);
    }

    @Override
    public RScheduledFuture<?> scheduleAtFixedRate(String id, Runnable command, Duration initialDelay, Duration period) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAtFixedRateAsync(id, command, initialDelay, period);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public RScheduledFuture<?> scheduleAtFixedRateAsync(String id, Runnable task, Duration initialDelay, Duration period) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + initialDelay.toMillis();

        String taskId = id;
        ScheduledAtFixedRateParameters params = new ScheduledAtFixedRateParameters(taskId);
        params.setClassName(classBody.getClazzName());
        params.setClassBody(classBody.getClazz());
        params.setLambdaBody(classBody.getLambda());
        params.setState(state);
        params.setStartTime(startTime);
        params.setPeriod(period.toMillis());
        params.setExecutorId(executorId);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.scheduleAtFixedRate(params).toCompletableFuture();
        addListener(result);
        return createFuture(result, startTime);
    }

    @Override
    public RScheduledFuture<?> scheduleWithFixedDelay(String id, Runnable command, Duration initialDelay, Duration delay) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleWithFixedDelayAsync(id, command, initialDelay, delay);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public RScheduledFuture<?> scheduleWithFixedDelayAsync(String id, Runnable task, Duration initialDelay, Duration delay) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        long startTime = System.currentTimeMillis() + initialDelay.toMillis();

        String taskId = id;
        ScheduledWithFixedDelayParameters params = new ScheduledWithFixedDelayParameters(taskId);
        params.setClassName(classBody.getClazzName());
        params.setClassBody(classBody.getClazz());
        params.setLambdaBody(classBody.getLambda());
        params.setState(state);
        params.setStartTime(startTime);
        params.setDelay(delay.toMillis());
        params.setExecutorId(executorId);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.scheduleWithFixedDelay(params).toCompletableFuture();
        addListener(result);
        return createFuture(result, startTime);
    }

    @Override
    public RScheduledFuture<?> schedule(String id, Runnable task, CronSchedule cronSchedule) {
        RedissonScheduledFuture<?> future = (RedissonScheduledFuture<?>) scheduleAsync(id, task, cronSchedule);
        RemotePromise<?> rp = future.getInnerPromise();
        syncExecute(rp);
        return future;
    }

    @Override
    public RScheduledFuture<?> scheduleAsync(String id, Runnable task, CronSchedule cronSchedule) {
        check(task);
        ClassBody classBody = getClassBody(task);
        byte[] state = encode(task);
        Date startDate = cronSchedule.getExpression().getNextValidTimeAfter(new Date());
        if (startDate == null) {
            throw new IllegalArgumentException("Wrong cron expression! Unable to calculate start date");
        }
        long startTime = startDate.getTime();

        String taskId = id;
        ScheduledCronExpressionParameters params = new ScheduledCronExpressionParameters(taskId);
        params.setClassName(classBody.getClazzName());
        params.setClassBody(classBody.getClazz());
        params.setLambdaBody(classBody.getLambda());
        params.setState(state);
        params.setStartTime(startTime);
        params.setCronExpression(cronSchedule.getExpression().getCronExpression());
        params.setTimezone(cronSchedule.getZoneId().toString());
        params.setExecutorId(executorId);
        RemotePromise<Void> result = (RemotePromise<Void>) asyncScheduledServiceAtFixed.schedule(params).toCompletableFuture();
        addListener(result);
        RedissonScheduledFuture<Void> f = new RedissonScheduledFuture<Void>(result, startTime) {
            public long getDelay(TimeUnit unit) {
                return unit.convert(startDate.getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            };
        };
        storeReference(f, result.getRequestId());
        return f;
    }

}
