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
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.Callable;

import org.redisson.RedissonExecutorService;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.api.annotation.RInject;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.Injector;
import org.redisson.remote.RemoteParams;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Executor service runs Callable and Runnable tasks.
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteExecutorServiceImpl implements RemoteExecutorService, RemoteParams {

    private final ClassLoaderDelegator classLoader = new ClassLoaderDelegator();
    
    private final ThreadLocal<String> requestId = new ThreadLocal<String>();
    private final Codec codec;
    private final String name;
    private final CommandExecutor commandExecutor;

    private final RedissonClient redisson;
    
    private String tasksCounterName;
    private String statusName;
    private String terminationTopicName;
    private String schedulerTasksName; 
    private String schedulerQueueName;
    private String schedulerChannelName;
    
    public RemoteExecutorServiceImpl(CommandExecutor commandExecutor, RedissonClient redisson, Codec codec, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.redisson = redisson;
        
        try {
            this.codec = codec.getClass().getConstructor(ClassLoader.class).newInstance(classLoader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
    
    public void setSchedulerQueueName(String schedulerQueueName) {
        this.schedulerQueueName = schedulerQueueName;
    }
    
    public void setSchedulerChannelName(String schedulerChannelName) {
        this.schedulerChannelName = schedulerChannelName;
    }
    
    public void setSchedulerTasksName(String schedulerTasksName) {
        this.schedulerTasksName = schedulerTasksName;
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
    public void scheduleAtFixedRate(String className, byte[] classBody, byte[] state, long startTime, long period) {
        long newStartTime = System.currentTimeMillis() + period;
        RFuture<Void> future = asyncScheduledServiceAtFixed().scheduleAtFixedRate(className, classBody, state, newStartTime, period);
        try {
            executeRunnable(className, classBody, state);
        } catch (RuntimeException e) {
            // cancel task if it throws an exception
            future.cancel(true);
            throw e;
        }
    }
    
    @Override
    public void schedule(String className, byte[] classBody, byte[] state, long startTime, String cronExpression) {
        Date nextStartDate = new CronExpression(cronExpression).getNextValidTimeAfter(new Date());
        RFuture<Void> future = asyncScheduledServiceAtFixed().schedule(className, classBody, state, nextStartDate.getTime(), cronExpression);
        try {
            executeRunnable(className, classBody, state);
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
    private RemoteExecutorServiceAsync asyncScheduledServiceAtFixed() {
        ScheduledExecutorRemoteService scheduledRemoteService = new ScheduledExecutorRemoteService(codec, redisson, name, commandExecutor);
        scheduledRemoteService.setTerminationTopicName(terminationTopicName);
        scheduledRemoteService.setTasksCounterName(tasksCounterName);
        scheduledRemoteService.setStatusName(statusName);
        scheduledRemoteService.setSchedulerQueueName(schedulerQueueName);
        scheduledRemoteService.setSchedulerChannelName(schedulerChannelName);
        scheduledRemoteService.setSchedulerTasksName(schedulerTasksName);
        scheduledRemoteService.setRequestId(requestId.get());
        RemoteExecutorServiceAsync asyncScheduledServiceAtFixed = scheduledRemoteService.get(RemoteExecutorServiceAsync.class, RemoteInvocationOptions.defaults().noAck().noResult());
        return asyncScheduledServiceAtFixed;
    }
    
    @Override
    public void scheduleWithFixedDelay(String className, byte[] classBody, byte[] state, long startTime, long delay) {
        executeRunnable(className, classBody, state);
        long newStartTime = System.currentTimeMillis() + delay;
        asyncScheduledServiceAtFixed().scheduleWithFixedDelay(className, classBody, state, newStartTime, delay);
    }
    
    @Override
    public Object scheduleCallable(String className, byte[] classBody, byte[] state, long startTime) {
        return executeCallable(className, classBody, state, requestId.get());
    }
    
    @Override
    public void scheduleRunnable(String className, byte[] classBody, byte[] state, long startTime) {
        executeRunnable(className, classBody, state, requestId.get());
    }
    
    @Override
    public Object executeCallable(String className, byte[] classBody, byte[] state) {
        return executeCallable(className, classBody, state, null);
    }
    
    private Object executeCallable(String className, byte[] classBody, byte[] state, String scheduledRequestId) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(state);
            
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
            finish(scheduledRequestId);
        }
    }


    private <T> T decode(ByteBuf buf) throws IOException {
        T task = (T) codec.getValueDecoder().decode(buf, null);
        Injector.inject(task, redisson);
        return task;
    }

    @Override
    public void executeRunnable(String className, byte[] classBody, byte[] state) {
        executeRunnable(className, classBody, state, null);
    }
    
    private void executeRunnable(String className, byte[] classBody, byte[] state, String scheduledRequestId) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(state);
            
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
            finish(scheduledRequestId);
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
                    Arrays.<Object>asList(tasksCounterName, statusName, terminationTopicName, schedulerTasksName),
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

    @Override
    public void setRequestId(String id) {
        requestId.set(id);
    }

}
