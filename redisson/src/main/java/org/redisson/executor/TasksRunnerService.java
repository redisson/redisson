/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.RedissonExecutorService;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CustomObjectInputStream;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.params.*;
import org.redisson.misc.Hash;
import org.redisson.misc.HashValue;
import org.redisson.misc.Injector;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.ObjectInput;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Executor service runs Callable and Runnable tasks.
 * 
 * @author Nikita Koksharov
 *
 */
public class TasksRunnerService implements RemoteExecutorService {

    private static final Map<HashValue, Codec> CODECS = new LRUCacheMap<HashValue, Codec>(500, 0, 0);
    
    private final Codec codec;
    private final String name;
    private final CommandAsyncExecutor commandExecutor;

    private final RedissonClient redisson;
    
    private String tasksCounterName;
    private String statusName;
    private String terminationTopicName;
    private String tasksName; 
    private String schedulerQueueName;
    private String schedulerChannelName;
    private String tasksRetryIntervalName;
    private String tasksExpirationTimeName;

    private BeanFactory beanFactory;
    private ConcurrentMap<String, ResponseEntry> responses;
    
    public TasksRunnerService(CommandAsyncExecutor commandExecutor, RedissonClient redisson, Codec codec, String name, ConcurrentMap<String, ResponseEntry> responses) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.redisson = redisson;
        this.responses = responses;
        
        this.codec = codec;
    }
    
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public void setTasksExpirationTimeName(String tasksExpirationTimeName) {
        this.tasksExpirationTimeName = tasksExpirationTimeName;
    }

    public void setTasksRetryIntervalName(String tasksRetryInterval) {
        this.tasksRetryIntervalName = tasksRetryInterval;
    }
    
    public void setSchedulerQueueName(String schedulerQueueName) {
        this.schedulerQueueName = schedulerQueueName;
    }
    
    public void setSchedulerChannelName(String schedulerChannelName) {
        this.schedulerChannelName = schedulerChannelName;
    }
    
    public void setTasksName(String tasksName) {
        this.tasksName = tasksName;
    }
    
    public void setTasksCounterName(String tasksCounterName) {
        this.tasksCounterName = tasksCounterName;
    }
    
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public void setTerminationTopicName(String terminationTopicName) {
        this.terminationTopicName = terminationTopicName;
    }

    @Override
    public void scheduleAtFixedRate(ScheduledAtFixedRateParameters params) {
        long start = System.nanoTime();
        executeRunnable(params, false);
        long spent = params.getSpentTime()
                                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        long newStartTime = System.currentTimeMillis() + Math.max(params.getPeriod() - spent, 0);
        params.setStartTime(newStartTime);
        spent = Math.max(spent - params.getPeriod(), 0);
        params.setSpentTime(spent);
        asyncScheduledServiceAtFixed(params.getExecutorId(), params.getRequestId()).scheduleAtFixedRate(params);
    }
    
    @Override
    public void schedule(ScheduledCronExpressionParameters params) {
        CronExpression expression = new CronExpression(params.getCronExpression());
        expression.setTimeZone(TimeZone.getTimeZone(params.getTimezone()));
        Date nextStartDate = expression.getNextValidTimeAfter(new Date());
        RFuture<Void> future = null;
        if (nextStartDate != null) {
            RemoteExecutorServiceAsync service = asyncScheduledServiceAtFixed(params.getExecutorId(), params.getRequestId());
            params.setStartTime(nextStartDate.getTime());
            future = service.schedule(params);
        }
        try {
            executeRunnable(params, nextStartDate == null);
        } catch (Exception e) {
            // cancel task if it throws an exception
            if (future != null) {
                future.cancel(true);
            }
            throw e;
        }
    }

    /**
     * Creates RemoteExecutorServiceAsync with special executor which overrides requestId generation
     * and uses current requestId. Because recurring tasks should use the same requestId.
     * 
     * @return
     */
    private RemoteExecutorServiceAsync asyncScheduledServiceAtFixed(String executorId, String requestId) {
        ScheduledTasksService scheduledRemoteService = new ScheduledTasksService(codec, name, commandExecutor, executorId, responses);
        scheduledRemoteService.setTerminationTopicName(terminationTopicName);
        scheduledRemoteService.setTasksCounterName(tasksCounterName);
        scheduledRemoteService.setStatusName(statusName);
        scheduledRemoteService.setSchedulerQueueName(schedulerQueueName);
        scheduledRemoteService.setSchedulerChannelName(schedulerChannelName);
        scheduledRemoteService.setTasksName(tasksName);
        scheduledRemoteService.setRequestId(new RequestId(requestId));
        scheduledRemoteService.setTasksExpirationTimeName(tasksExpirationTimeName);
        scheduledRemoteService.setTasksRetryIntervalName(tasksRetryIntervalName);
        RemoteExecutorServiceAsync asyncScheduledServiceAtFixed = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        return asyncScheduledServiceAtFixed;
    }
    
    @Override
    public void scheduleWithFixedDelay(ScheduledWithFixedDelayParameters params) {
        executeRunnable(params, false);
        if (!redisson.getMap(tasksName, StringCodec.INSTANCE).containsKey(params.getRequestId())) {
            return;
        }
        
        long newStartTime = System.currentTimeMillis() + params.getDelay();
        params.setStartTime(newStartTime);
        asyncScheduledServiceAtFixed(params.getExecutorId(), params.getRequestId()).scheduleWithFixedDelay(params);
    }
    
    @Override
    public Object scheduleCallable(ScheduledParameters params) {
        return executeCallable(params);
    }
    
    @Override
    public void scheduleRunnable(ScheduledParameters params) {
        executeRunnable(params);
    }
    
    @Override
    public Object executeCallable(TaskParameters params) {
        Object res;
        try {
            RFuture<Long> future = renewRetryTime(params.getRequestId());
            future.sync();

            Callable<?> callable = decode(params);
            res = callable.call();
        } catch (RedissonShutdownException e) {
            throw e;
        } catch (RedisException e) {
            finish(params.getRequestId(), true);
            throw e;
        } catch (Exception e) {
            finish(params.getRequestId(), true);
            throw new IllegalArgumentException(e);
        }
        finish(params.getRequestId(), true);
        return res;
    }

    protected void scheduleRetryTimeRenewal(String requestId, Long retryInterval) {
        if (retryInterval == null) {
            return;
        }

        commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                renewRetryTime(requestId);
            }
        }, Math.max(1000, retryInterval / 2), TimeUnit.MILLISECONDS);
    }

    protected RFuture<Long> renewRetryTime(String requestId) {
        RFuture<Long> future = commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                // check if executor service not in shutdown state
                  "local name = ARGV[2];"
                + "local scheduledName = ARGV[2];"
                + "if string.sub(scheduledName, 1, 2) ~= 'ff' then "
                    + "scheduledName = 'ff' .. scheduledName; "
                + "else "
                    + "name = string.sub(name, 3, string.len(name)); "
                + "end;"
                + "local retryInterval = redis.call('get', KEYS[4]);"
                
                + "if redis.call('exists', KEYS[1]) == 0 and retryInterval ~= false and redis.call('hexists', KEYS[5], name) == 1 then "
                    + "local startTime = tonumber(ARGV[1]) + tonumber(retryInterval);"
                    + "redis.call('zadd', KEYS[2], startTime, scheduledName);"
                    + "local v = redis.call('zrange', KEYS[2], 0, 0); "
                    // if new task added to queue head then publish its startTime 
                    // to all scheduler workers 
                    + "if v[1] == ARGV[2] then "
                        + "redis.call('publish', KEYS[3], startTime); "
                    + "end;"
                    + "return retryInterval; "
                + "end;"
                + "return nil;", 
                Arrays.<Object>asList(statusName, schedulerQueueName, schedulerChannelName, tasksRetryIntervalName, tasksName),
                System.currentTimeMillis(), requestId);
        future.onComplete((res, e) -> {
            if (e != null) {
                scheduleRetryTimeRenewal(requestId, 10000L);
                return;
            }
            
            if (res != null) {
                scheduleRetryTimeRenewal(requestId, res);
            }
        });
        return future;
    }
    
    @SuppressWarnings("unchecked")
    private <T> T decode(TaskParameters params) {
        ByteBuf classBodyBuf = Unpooled.wrappedBuffer(params.getClassBody());
        ByteBuf stateBuf = Unpooled.wrappedBuffer(params.getState());
        try {
            HashValue hash = new HashValue(Hash.hash128(classBodyBuf));
            Codec classLoaderCodec = CODECS.get(hash);
            if (classLoaderCodec == null) {
                RedissonClassLoader cl = new RedissonClassLoader(codec.getClassLoader());
                cl.loadClass(params.getClassName(), params.getClassBody());
                
                classLoaderCodec = this.codec.getClass().getConstructor(ClassLoader.class).newInstance(cl);
                CODECS.put(hash, classLoaderCodec);
            }
            
            T task;
            if (params.getLambdaBody() != null) {
                ByteArrayInputStream is = new ByteArrayInputStream(params.getLambdaBody());
                
                //set thread context class loader to be the classLoaderCodec.getClassLoader() variable as there could be reflection
                //done while reading from input stream which reflection will use thread class loader to load classes on demand
                ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();                
                try {
                    Thread.currentThread().setContextClassLoader(classLoaderCodec.getClassLoader());
                    ObjectInput oo = new CustomObjectInputStream(classLoaderCodec.getClassLoader(), is);
                    task = (T) oo.readObject();
                    oo.close();
                } finally {
                    Thread.currentThread().setContextClassLoader(currentThreadClassLoader);
                }
            } else {
                task = (T) classLoaderCodec.getValueDecoder().decode(stateBuf, null);
            }

            Injector.inject(task, RedissonClient.class, redisson);
            Injector.inject(task, String.class, params.getRequestId());
            
            if (beanFactory != null) {
                AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
                bpp.setBeanFactory(beanFactory);
                bpp.processInjection(task);
            }
            
            return task;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize codec with ClassLoader parameter", e);
        } finally {
            classBodyBuf.release();
            stateBuf.release();
        }
    }

    public void executeRunnable(TaskParameters params, boolean removeTask) {
        try {
            if (params.getRequestId() != null && params.getRequestId().startsWith("00")) {
                RFuture<Long> future = renewRetryTime(params.getRequestId());
                try {
                    future.sync();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            Runnable runnable = decode(params);
            runnable.run();
        } catch (RedissonShutdownException e) {
            throw e;
        } catch (RedisException e) {
            finish(params.getRequestId(), removeTask);
            throw e;
        }
        finish(params.getRequestId(), removeTask);
    }
    
    @Override
    public void executeRunnable(TaskParameters params) {
        executeRunnable(params, true);
    }

    /**
     * Check shutdown state. If tasksCounter equals <code>0</code>
     * and executor in <code>shutdown</code> state, then set <code>terminated</code> state 
     * and notify terminationTopicName
     * <p>
     * If <code>scheduledRequestId</code> is not null then
     * delete scheduled task
     * 
     * @param requestId
     */
    void finish(String requestId, boolean removeTask) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        String script = "";
        if (removeTask) {
           script +=  "local scheduled = redis.call('zscore', KEYS[5], ARGV[3]);"
                    + "if scheduled == false then "
                        + "redis.call('hdel', KEYS[4], ARGV[3]); "
                    + "end;";
        }
        script += "redis.call('zrem', KEYS[5], 'ff' .. ARGV[3]);" +
                  "if redis.call('decr', KEYS[1]) == 0 then "
                   + "redis.call('del', KEYS[1]);"
                    + "if redis.call('get', KEYS[2]) == ARGV[1] then "
                        + "redis.call('del', KEYS[6]);"
                        + "redis.call('set', KEYS[2], ARGV[2]);"
                        + "redis.call('publish', KEYS[3], ARGV[2]);"
                    + "end;"
                + "end;";  

        RFuture<Object> f = commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                script,
                Arrays.asList(tasksCounterName, statusName, terminationTopicName, tasksName, schedulerQueueName, tasksRetryIntervalName),
                RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE, requestId);
        commandExecutor.get(f);
    }

}
