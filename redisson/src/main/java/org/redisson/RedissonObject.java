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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.redisson.api.DeletedObjectListener;
import org.redisson.api.ExpiredObjectListener;
import org.redisson.api.ObjectListener;
import org.redisson.api.RFuture;
import org.redisson.api.RObject;
import org.redisson.api.RPatternTopic;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CountableListener;
import org.redisson.misc.Hash;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import io.netty.buffer.ByteBuf;

/**
 * Base Redisson object
 *
 * @author Nikita Koksharov
 *
 */
public abstract class RedissonObject implements RObject {

    protected final CommandAsyncExecutor commandExecutor;
    private String name;
    protected final Codec codec;

    public RedissonObject(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.codec = codec;
        this.name = name;
        this.commandExecutor = commandExecutor;
    }

    public RedissonObject(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    protected boolean await(RFuture<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        return commandExecutor.await(future, timeout, timeoutUnit);
    }
    
    public static String prefixName(String prefix, String name) {
        if (name.contains("{")) {
            return prefix + ":" + name;
        }
        return prefix + ":{" + name + "}";
    }
    
    public static String suffixName(String name, String suffix) {
        if (name.contains("{")) {
            return name + ":" + suffix;
        }
        return "{" + name + "}:" + suffix;
    }

    protected final <T> Stream<T> toStream(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false);
    }
    
    protected final <V> V get(RFuture<V> future) {
        return commandExecutor.get(future);
    }
    
    protected final long toSeconds(long timeout, TimeUnit unit) {
        long seconds = unit.toSeconds(timeout);
        if (timeout != 0 && seconds == 0) {
            seconds = 1;
        }
        return seconds;
    }

    @Override
    public String getName() {
        return name;
    }
    
    protected String getName(Object o) {
        return getName();
    }

    @Override
    public void rename(String newName) {
        get(renameAsync(newName));
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.MEMORY_USAGE, getName());
    }
    
    public final RFuture<Long> sizeInMemoryAsync(List<Object> keys) {
        return commandExecutor.evalWriteAsync((String) keys.get(0), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                  "local total = 0;"
                + "for j = 1, #KEYS, 1 do "
                    + "local size = redis.call('memory', 'usage', KEYS[j]); "
                    + "if size ~= false then "
                        + "total = total + size;"
                    + "end; "
                + "end; "
                + "return total; ", keys);

    }
    
    @Override
    public long sizeInMemory() {
        return get(sizeInMemoryAsync());
    }

    @Override
    public RFuture<Void> renameAsync(String newName) {
        RFuture<Void> f = commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.RENAME, getName(), newName);
        f.onComplete((r, e) -> {
            if (e == null) {
                this.name = newName;
            }
        });
        return f;
    }

    @Override
    public void migrate(String host, int port, int database, long timeout) {
        get(migrateAsync(host, port, database, timeout));
    }

    @Override
    public RFuture<Void> migrateAsync(String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.MIGRATE, host, port, getName(), database, timeout);
    }
    
    @Override
    public void copy(String host, int port, int database, long timeout) {
        get(copyAsync(host, port, database, timeout));
    }

    @Override
    public RFuture<Void> copyAsync(String host, int port, int database, long timeout) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.MIGRATE, host, port, getName(), database, timeout, "COPY");
    }
    
    @Override
    public boolean move(int database) {
        return get(moveAsync(database));
    }

    @Override
    public RFuture<Boolean> moveAsync(int database) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.MOVE, getName(), database);
    }

    @Override
    public boolean renamenx(String newName) {
        return get(renamenxAsync(newName));
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        RFuture<Boolean> f = commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.RENAMENX, getName(), newName);
        f.onComplete((value, e) -> {
            if (e == null && value) {
                this.name = newName;
            }
        });
        return f;

    }

    @Override
    public boolean delete() {
        return get(deleteAsync());
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.DEL_BOOL, getName());
    }
    
    @Override
    public boolean unlink() {
        return get(unlinkAsync());
    }

    @Override
    public RFuture<Boolean> unlinkAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.UNLINK_BOOL, getName());
    }

    @Override
    public boolean touch() {
        return get(touchAsync());
    }

    @Override
    public RFuture<Boolean> touchAsync() {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.TOUCH, getName());
    }
    
    @Override
    public boolean isExists() {
        return get(isExistsAsync());
    }

    @Override
    public RFuture<Boolean> isExistsAsync() {
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.EXISTS, getName());
    }

    @Override
    public Codec getCodec() {
        return codec;
    }
    
    protected List<ByteBuf> encode(Collection<?> values) {
        List<ByteBuf> result = new ArrayList<ByteBuf>(values.size());
        for (Object object : values) {
            result.add(encode(object));
        }
        return result;
    }
    
    public void encode(Collection<Object> params, Collection<?> values) {
        for (Object object : values) {
            params.add(encode(object));
        }
    }
    
    public String getLockByMapKey(Object key, String suffix) {
        ByteBuf keyState = encodeMapKey(key);
        try {
            return suffixName(getName(key), Hash.hash128toBase64(keyState) + ":" + suffix);
        } finally {
            keyState.release();
        }
    }

    public String getLockByValue(Object key, String suffix) {
        ByteBuf keyState = encode(key);
        try {
            return suffixName(getName(key), Hash.hash128toBase64(keyState) + ":" + suffix);
        } finally {
            keyState.release();
        }
    }
    
    protected void encodeMapKeys(Collection<Object> params, Collection<?> values) {
        for (Object object : values) {
            params.add(encodeMapKey(object));
        }
    }

    protected void encodeMapValues(Collection<Object> params, Collection<?> values) {
        for (Object object : values) {
            params.add(encodeMapValue(object));
        }
    }
    
    public ByteBuf encode(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = commandExecutor.getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public ByteBuf encodeMapKey(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = commandExecutor.getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getMapKeyEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public ByteBuf encodeMapValue(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = commandExecutor.getObjectBuilder().toReference(value);
            if (reference != null) {
                value = reference;
            }
        }

        try {
            return codec.getMapValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public byte[] dump() {
        return get(dumpAsync());
    }
    
    @Override
    public RFuture<byte[]> dumpAsync() {
        return commandExecutor.readAsync(getName(), ByteArrayCodec.INSTANCE, RedisCommands.DUMP, getName());
    }
    
    @Override
    public void restore(byte[] state) {
        get(restoreAsync(state));
    }
    
    @Override
    public RFuture<Void> restoreAsync(byte[] state) {
        return restoreAsync(state, 0, null);
    }
    
    @Override
    public void restore(byte[] state, long timeToLive, TimeUnit timeUnit) {
        get(restoreAsync(state, timeToLive, timeUnit));
    }
    
    @Override
    public RFuture<Void> restoreAsync(byte[] state, long timeToLive, TimeUnit timeUnit) {
        long ttl = 0;
        if (timeToLive > 0) {
            ttl = timeUnit.toMillis(timeToLive);
        }
        
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.RESTORE, getName(), ttl, state);
    }

    @Override
    public void restoreAndReplace(byte[] state, long timeToLive, TimeUnit timeUnit) {
        get(restoreAndReplaceAsync(state, timeToLive, timeUnit));
    }
    
    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] state, long timeToLive, TimeUnit timeUnit) {
        long ttl = 0;
        if (timeToLive > 0) {
            ttl = timeUnit.toMillis(timeToLive);
        }
        
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.RESTORE, getName(), ttl, state, "REPLACE");
    }
    
    @Override
    public void restoreAndReplace(byte[] state) {
        get(restoreAndReplaceAsync(state));
    }
    
    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] state) {
        return restoreAndReplaceAsync(state, 0, null);
    }
    
    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof ExpiredObjectListener) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
            return topic.addListener(String.class, new PatternMessageListener<String>() {
                @Override
                public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                    if (msg.equals(getName())) {
                        ((ExpiredObjectListener) listener).onExpired(msg);
                    }
                }
            });
        }
        if (listener instanceof DeletedObjectListener) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:del");
            return topic.addListener(String.class, new PatternMessageListener<String>() {
                @Override
                public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                    if (msg.equals(getName())) {
                        ((DeletedObjectListener) listener).onDeleted(msg);
                    }
                }
            });
        }
        throw new IllegalArgumentException();
    };
    
    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof ExpiredObjectListener) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
            return topic.addListenerAsync(String.class, new PatternMessageListener<String>() {
                @Override
                public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                    if (msg.equals(getName())) {
                        ((ExpiredObjectListener) listener).onExpired(msg);
                    }
                }
            });
        }
        if (listener instanceof DeletedObjectListener) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:del");
            return topic.addListenerAsync(String.class, new PatternMessageListener<String>() {
                @Override
                public void onMessage(CharSequence pattern, CharSequence channel, String msg) {
                    if (msg.equals(getName())) {
                        ((DeletedObjectListener) listener).onDeleted(msg);
                    }
                }
            });
        }
        throw new IllegalArgumentException();
    }
    
    @Override
    public void removeListener(int listenerId) {
        RPatternTopic expiredTopic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
        expiredTopic.removeListener(listenerId);

        RPatternTopic deletedTopic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:del");
        deletedTopic.removeListener(listenerId);
    }
    
    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        RPromise<Void> result = new RedissonPromise<>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, 2);
        
        RPatternTopic expiredTopic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
        expiredTopic.removeListenerAsync(listenerId).onComplete(listener);
        
        RPatternTopic deletedTopic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:del");
        deletedTopic.removeListenerAsync(listenerId).onComplete(listener);
        return result;
    }

}
