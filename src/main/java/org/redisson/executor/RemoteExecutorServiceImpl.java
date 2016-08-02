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

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.redisson.RedissonClient;
import org.redisson.RedissonExecutorService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class RemoteExecutorServiceImpl implements RemoteExecutorService {

    private final ClassLoaderDelegator classLoader = new ClassLoaderDelegator();
    
    private final Codec codec;
    private final String name;
    private final CommandExecutor commandExecutor;

    private final RAtomicLong tasksCounter;
    private final RBucket<Integer> status;
    private final RTopic<Integer> topic;
    
    public RemoteExecutorServiceImpl(CommandExecutor commandExecutor, RedissonClient redisson, Codec codec, String name) {
        this.commandExecutor = commandExecutor;
        
        this.name = name + ":{"+ RemoteExecutorService.class.getName() + "}";
        tasksCounter = redisson.getAtomicLong(this.name + ":counter");
        status = redisson.getBucket(this.name + ":status");
        topic = redisson.getTopic(this.name + ":topic");
        
        try {
            this.codec = codec.getClass().getConstructor(ClassLoader.class).newInstance(classLoader);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object execute(String className, byte[] classBody, byte[] state) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(state);
            
            RedissonClassLoader cl = new RedissonClassLoader(getClass().getClassLoader());
            cl.loadClass(className, classBody);
            classLoader.setCurrentClassLoader(cl);
            
            Callable<?> callable = (Callable<?>) codec.getValueDecoder().decode(buf, null);
            return callable.call();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            buf.release();
            finish();
        }
    }

    @Override
    public void executeVoid(String className, byte[] classBody, byte[] state) {
        ByteBuf buf = null;
        try {
            buf = Unpooled.wrappedBuffer(state);
            
            RedissonClassLoader cl = new RedissonClassLoader(getClass().getClassLoader());
            cl.loadClass(className, classBody);
            classLoader.setCurrentClassLoader(cl);
        
            Runnable runnable = (Runnable) codec.getValueDecoder().decode(buf, null);
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

        commandExecutor.evalWrite(name, LongCodec.INSTANCE, RedisCommands.EVAL_VOID, 
                "if redis.call('decr', KEYS[1]) == 0 and redis.call('get', KEYS[2]) == ARGV[1] then "
                    + "redis.call('set', KEYS[2], ARGV[2]);"
                    + "redis.call('publish', KEYS[3], ARGV[2]);"
                + "end;",  
                Arrays.<Object>asList(tasksCounter.getName(), status.getName(), topic.getChannelNames().get(0)),
                RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

}
