/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.core.RBitSet;

import io.netty.util.concurrent.Future;

public class RedissonBitSet extends RedissonExpirable implements RBitSet {

    protected RedissonBitSet(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    public int length() {
        return get(lengthAsync());
    }

    public void set(BitSet bs) {
        get(setAsync(bs));
    }

    public boolean get(int bitIndex) {
        return get(getAsync(bitIndex));
    }

    public Future<Boolean> getAsync(int bitIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GETBIT, getName(), bitIndex);
    }

    public void set(int bitIndex) {
        get(setAsync(bitIndex, true));
    }

    public void set(int fromIndex, int toIndex, boolean value) {
        get(setAsync(fromIndex, toIndex, value));
    }

    public void set(int fromIndex, int toIndex) {
        get(setAsync(fromIndex, toIndex));
    }

    public void set(int bitIndex, boolean value) {
        get(setAsync(bitIndex, value));
    }

    public Future<Void> setAsync(int bitIndex, boolean value) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SETBIT, getName(), bitIndex, value ? 1 : 0);
    }

    public byte[] toByteArray() {
        return get(toByteArrayAsync());
    }

    public Future<byte[]> toByteArrayAsync() {
        return commandExecutor.readAsync(getName(), ByteArrayCodec.INSTANCE, RedisCommands.GET, getName());
    }

    public int cardinality() {
        return get(cardinalityAsync());
    }

    public int size() {
        return get(sizeAsync());
    }

    public void clear(int fromIndex, int toIndex) {
        get(clearAsync(fromIndex, toIndex));
    }

    public void clear(int bitIndex) {
        get(clearAsync(bitIndex));
    }

    public void clear() {
        get(clearAsync());
    }

    public void or(String... bitSetNames) {
        get(orAsync(bitSetNames));
    }

    public void and(String... bitSetNames) {
        get(andAsync(bitSetNames));
    }

    public void xor(String... bitSetNames) {
        get(xorAsync(bitSetNames));
    }

    public void not() {
        get(notAsync());
    }

    private Future<Void> opAsync(String op, String... bitSetNames) {
        List<Object> params = new ArrayList<Object>(bitSetNames.length + 3);
        params.add(op);
        params.add(getName());
        params.add(getName());
        params.addAll(Arrays.asList(bitSetNames));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BITOP, params.toArray());
    }

    public BitSet asBitSet() {
        return fromByteArrayReverse(toByteArray());
    }

    //Copied from: https://github.com/xetorthio/jedis/issues/301
    private static BitSet fromByteArrayReverse(byte[] bytes) {
        BitSet bits = new BitSet();
        for (int i = 0; i < bytes.length * 8; i++) {
            if ((bytes[i / 8] & (1 << (7 - (i % 8)))) != 0) {
                bits.set(i);
            }
        }
        return bits;
    }

    //Copied from: https://github.com/xetorthio/jedis/issues/301
    private static byte[] toByteArrayReverse(BitSet bits) {
        byte[] bytes = new byte[bits.length() / 8 + 1];
        for (int i = 0; i < bits.length(); i++) {
            if (bits.get(i)) {
                final int value = bytes[i / 8] | (1 << (7 - (i % 8)));
                bytes[i / 8] = (byte) value;
            }
        }
        return bytes;
    }

    @Override
    public String toString() {
        return asBitSet().toString();
    }

    @Override
    public Future<Integer> lengthAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local fromBit = redis.call('bitpos', KEYS[1], 1, -1);"
                + "local toBit = 8*(fromBit/8 + 1) - fromBit % 8;"
                        + "for i = toBit, fromBit, -1 do "
                            + "if redis.call('getbit', KEYS[1], i) == 1 then "
                                + "return i+1;"
                            + "end;"
                       + "end;" +
                     "return fromBit+1",
                Collections.<Object>singletonList(getName()));
    }

    @Override
    public Future<Void> setAsync(int fromIndex, int toIndex, boolean value) {
        if (value) {
            return setAsync(fromIndex, toIndex);
        }
        return clearAsync(fromIndex, toIndex);
    }

    @Override
    public Future<Void> clearAsync(int fromIndex, int toIndex) {
        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        for (int i = fromIndex; i < toIndex; i++) {
            executorService.writeAsync(getName(), codec, RedisCommands.SETBIT, getName(), i, 0);
        }
        return executorService.executeAsyncVoid();
    }

    @Override
    public Future<Void> setAsync(BitSet bs) {
        return commandExecutor.writeAsync(getName(), ByteArrayCodec.INSTANCE, RedisCommands.SET, getName(), toByteArrayReverse(bs));
    }

    @Override
    public Future<Void> notAsync() {
        return opAsync("NOT");
    }

    @Override
    public Future<Void> setAsync(int fromIndex, int toIndex) {
        CommandBatchService executorService = new CommandBatchService(commandExecutor.getConnectionManager());
        for (int i = fromIndex; i < toIndex; i++) {
            executorService.writeAsync(getName(), codec, RedisCommands.SETBIT, getName(), i, 1);
        }
        return executorService.executeAsyncVoid();
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.BITS_SIZE, getName());
    }

    @Override
    public Future<Void> setAsync(int bitIndex) {
        return setAsync(bitIndex, true);
    }

    @Override
    public Future<Integer> cardinalityAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.BITCOUNT, getName());
    }

    @Override
    public Future<Void> clearAsync(int bitIndex) {
        return setAsync(bitIndex, false);
    }

    @Override
    public Future<Void> clearAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_VOID, getName());
    }

    @Override
    public Future<Void> orAsync(String... bitSetNames) {
        return opAsync("OR", bitSetNames);
    }

    @Override
    public Future<Void> andAsync(String... bitSetNames) {
        return opAsync("AND", bitSetNames);
    }

    @Override
    public Future<Void> xorAsync(String... bitSetNames) {
        return opAsync("XOR", bitSetNames);
    }

}
