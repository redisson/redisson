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
import java.util.List;

import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.RBitSet;

import io.netty.util.concurrent.Future;

public class RedissonBitSet extends RedissonExpirable implements RBitSet {

    protected RedissonBitSet(CommandExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    public boolean get(int bitIndex) {
        return get(getAsync(bitIndex));
    }

    public Future<Boolean> getAsync(int bitIndex) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.GETBIT, getName(), bitIndex);
    }

    public void set(int bitIndex) {
        set(bitIndex, true);
    }

    public void set(int fromIndex, int toIndex) {
        CommandBatchExecutorService executorService = new CommandBatchExecutorService(commandExecutor.getConnectionManager());
        for (int i = fromIndex; i < toIndex; i++) {
            executorService.writeAsync(getName(), codec, RedisCommands.SETBIT, getName(), i, 1);
        }
        executorService.execute();
    }

    public void set(int bitIndex, boolean value) {
        get(setAsync(bitIndex, value));
    }

    public Future<Void> setAsync(int bitIndex, boolean value) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SETBIT, getName(), bitIndex, value ? 1 : 0);
    }

    public byte[] toByteArray() {
        return commandExecutor.read(getName(), ByteArrayCodec.INSTANCE, RedisCommands.GET, getName());
    }

    public int cardinality() {
        return commandExecutor.read(getName(), codec, RedisCommands.BITCOUNT, getName());
    }

    public int size() {
        int r = commandExecutor.read(getName(), codec, RedisCommands.STRLEN, getName());
        return r * 8;
    }

    public void clear(int bitIndex) {
        set(bitIndex, false);
    }

    public void clear() {
        delete();
    }

    public void or(String... bitSetNames) {
        op("OR", bitSetNames);
    }

    public void and(String... bitSetNames) {
        op("AND", bitSetNames);
    }

    public void xor(String... bitSetNames) {
        op("XOR", bitSetNames);
    }

    private void op(String op, String... bitSetNames) {
        List<Object> params = new ArrayList<Object>(bitSetNames.length + 3);
        params.add(op);
        params.add(getName());
        params.add(getName());
        params.addAll(Arrays.asList(bitSetNames));
        commandExecutor.write(getName(), codec, RedisCommands.BITOP, params.toArray());
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

}
