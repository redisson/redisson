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
import java.util.concurrent.Callable;

import org.redisson.RedissonClient;
import org.redisson.RedissonExecutorService;
import org.redisson.api.annotation.RInject;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteExecutorServiceImpl implements RemoteExecutorService {

    private final ClassLoaderDelegator classLoader = new ClassLoaderDelegator();
    
    private final Codec codec;
    private final String name;
    private final CommandExecutor commandExecutor;

    private final RedissonClient redisson;
    private String tasksCounterName;
    private String statusName;
    private String topicName;
    
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
    
    public void setTasksCounterName(String tasksCounterName) {
        this.tasksCounterName = tasksCounterName;
    }
    
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }
    
    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    @Override
    public Object execute(String className, byte[] classBody, byte[] state) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(state);
            
            RedissonClassLoader cl = new RedissonClassLoader(getClass().getClassLoader());
            cl.loadClass(className, classBody);
            classLoader.setCurrentClassLoader(cl);
            
            Callable<?> callable = decode(buf);
            return callable.call();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            buf.release();
            finish();
        }
    }

    private <T> T decode(ByteBuf buf) throws IOException {
        T task = (T) codec.getValueDecoder().decode(buf, null);
        Field[] fields = task.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (RedissonClient.class.isAssignableFrom(field.getType())
                    && field.isAnnotationPresent(RInject.class)) {
                field.setAccessible(true);
                try {
                    field.set(task, redisson);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return task;
    }
    
    @Override
    public void executeVoid(String className, byte[] classBody, byte[] state) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(state);
            
            RedissonClassLoader cl = new RedissonClassLoader(getClass().getClassLoader());
            cl.loadClass(className, classBody);
            classLoader.setCurrentClassLoader(cl);
        
            Runnable runnable = decode(buf);
            runnable.run();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            buf.release();
            finish();
        }
    }

    private void finish() {
        classLoader.clearCurrentClassLoader();

        commandExecutor.evalWrite(name, codec, RedisCommands.EVAL_VOID_WITH_VALUES_6, 
                "if redis.call('decr', KEYS[1]) == 0 and redis.call('get', KEYS[2]) == ARGV[1] then "
                    + "redis.call('set', KEYS[2], ARGV[2]);"
                    + "redis.call('publish', KEYS[3], ARGV[2]);"
                + "end;",  
                Arrays.<Object>asList(tasksCounterName, statusName, topicName),
                RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

}
