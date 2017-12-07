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
package org.redisson.executor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

import org.redisson.RedissonExecutorService;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.Injector;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Executor service runs Callable and Runnable tasks.
 * 
 * @author Nikita Koksharov
 *
 */
public class TasksRunnerService implements RemoteExecutorService {

    private final ClassLoaderDelegator classLoader = new ClassLoaderDelegator();
    
    private final Codec codec;
    private final String name;
    private final CommandExecutor commandExecutor;

    private final RedissonClient redisson;
    
    private String tasksCounterName;
    private String statusName;
    private String terminationTopicName;
    private String tasksName; 
    private String schedulerQueueName;
    private String schedulerChannelName;
    private ConcurrentMap<String, ResponseEntry> responses;
    
    public TasksRunnerService(CommandExecutor commandExecutor, RedissonClient redisson, Codec codec, String name, ConcurrentMap<String, ResponseEntry> responses) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.redisson = redisson;
        this.responses = responses;
        
        try {
            this.codec = codec.getClass().getConstructor(ClassLoader.class).newInstance(classLoader);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize codec with ClassLoader parameter", e);
        }
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
    public void scheduleAtFixedRate(String className, byte[] classBody, byte[] state, long startTime, long period, String executorId, String requestId) {
        long newStartTime = System.currentTimeMillis() + period;
        RFuture<Void> future = asyncScheduledServiceAtFixed(executorId, requestId).scheduleAtFixedRate(className, classBody, state, newStartTime, period, executorId, requestId);
        try {
            executeRunnable(className, classBody, state, null);
        } catch (RuntimeException e) {
            // cancel task if it throws an exception
            future.cancel(true);
            throw e;
        }
    }
    
    @Override
    public void schedule(String className, byte[] classBody, byte[] state, long startTime, String cronExpression, String executorId, String requestId) {
        Date nextStartDate = new CronExpression(cronExpression).getNextValidTimeAfter(new Date());
        RFuture<Void> future = asyncScheduledServiceAtFixed(executorId, requestId).schedule(className, classBody, state, nextStartDate.getTime(), cronExpression, executorId, requestId);
        try {
            executeRunnable(className, classBody, state, null);
        } catch (RuntimeException e) {
            // cancel task if it throws an exception
            future.cancel(true);
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
        ScheduledTasksService scheduledRemoteService = new ScheduledTasksService(codec, redisson, name, commandExecutor, executorId, responses);
        scheduledRemoteService.setTerminationTopicName(terminationTopicName);
        scheduledRemoteService.setTasksCounterName(tasksCounterName);
        scheduledRemoteService.setStatusName(statusName);
        scheduledRemoteService.setSchedulerQueueName(schedulerQueueName);
        scheduledRemoteService.setSchedulerChannelName(schedulerChannelName);
        scheduledRemoteService.setTasksName(tasksName);
        scheduledRemoteService.setRequestId(new RequestId(requestId));
        RemoteExecutorServiceAsync asyncScheduledServiceAtFixed = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        return asyncScheduledServiceAtFixed;
    }
    
    @Override
    public void scheduleWithFixedDelay(String className, byte[] classBody, byte[] state, long startTime, long delay, String executorId, String requestId) {
        executeRunnable(className, classBody, state, null);
        long newStartTime = System.currentTimeMillis() + delay;
        asyncScheduledServiceAtFixed(executorId, requestId).scheduleWithFixedDelay(className, classBody, state, newStartTime, delay, executorId, requestId);
    }
    
    @Override
    public Object scheduleCallable(String className, byte[] classBody, byte[] state, long startTime, String requestId) {
        return executeCallable(className, classBody, state, requestId);
    }
    
    @Override
    public void scheduleRunnable(String className, byte[] classBody, byte[] state, long startTime, String requestId) {
        executeRunnable(className, classBody, state, requestId);
    }
    
    @Override
    public Object executeCallable(String className, byte[] classBody, byte[] state, String requestId) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(state.length);
        try {
            buf.writeBytes(state);
            
            RedissonClassLoader cl = new RedissonClassLoader(getClass().getClassLoader());
            cl.loadClass(className, classBody);
            classLoader.setCurrentClassLoader(cl);
            
            Callable<?> callable = decode(buf);
            return callable.call();
        } catch (RedissonShutdownException e) {
            return null;
            // skip
        } catch (RedisException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            buf.release();
            finish(requestId);
        }
    }


    @SuppressWarnings("unchecked")
    private <T> T decode(ByteBuf buf) throws IOException {
        T task = (T) codec.getValueDecoder().decode(buf, null);
        Injector.inject(task, redisson);
        return task;
    }

    @Override
    public void executeRunnable(String className, byte[] classBody, byte[] state, String requestId) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(state.length);
        try {
            buf.writeBytes(state);
            
            RedissonClassLoader cl = new RedissonClassLoader(getClass().getClassLoader());
            cl.loadClass(className, classBody);
            classLoader.setCurrentClassLoader(cl);
        
            Runnable runnable = decode(buf);
            runnable.run();
        } catch (RedissonShutdownException e) {
            // skip
        } catch (RedisException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            buf.release();
            finish(requestId);
        }
    }

    /**
     * Check shutdown state. If tasksCounter equals <code>0</code>
     * and executor in <code>shutdown</code> state, then set <code>terminated</code> state 
     * and notify terminationTopicName
     * <p>
     * If <code>scheduledRequestId</code> is not null then
     * delete scheduled task
     * 
     * @param scheduledRequestId
     */
    private void finish(String scheduledRequestId) {
        classLoader.clearCurrentClassLoader();

        if (scheduledRequestId != null) {
            commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_VOID,
                    "redis.call('hdel', KEYS[4], ARGV[3]); " +
                    "if redis.call('decr', KEYS[1]) == 0 then "
                    + "redis.call('del', KEYS[1]);"
                    + "if redis.call('get', KEYS[2]) == ARGV[1] then "
                        + "redis.call('set', KEYS[2], ARGV[2]);"
                        + "redis.call('publish', KEYS[3], ARGV[2]);"
                    + "end;"
                  + "end;",  
                    Arrays.<Object>asList(tasksCounterName, statusName, terminationTopicName, tasksName),
                    RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE, scheduledRequestId);
            return;
        }
        
        commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_VOID, 
                "if redis.call('decr', KEYS[1]) == 0 then "
                + "redis.call('del', KEYS[1]);"
                + "if redis.call('get', KEYS[2]) == ARGV[1] then "
                    + "redis.call('set', KEYS[2], ARGV[2]);"
                    + "redis.call('publish', KEYS[3], ARGV[2]);"
                + "end;"
              + "end;",  
                Arrays.<Object>asList(tasksCounterName, statusName, terminationTopicName),
                RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

}
