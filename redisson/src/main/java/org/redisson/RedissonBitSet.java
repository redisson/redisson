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

import org.redisson.api.RBitSet;
import org.redisson.api.RFuture;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;

import java.util.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBitSet extends RedissonExpirable implements RBitSet {

    public RedissonBitSet(CommandAsyncExecutor connectionManager, String name) {
        super(null, connectionManager, name);
    }

    @Override
    public long getSigned(int size, long offset) {
        return get(getSignedAsync(size, offset));
    }

    @Override
    public long setSigned(int size, long offset, long value) {
        return get(setSignedAsync(size, offset, value));
    }

    @Override
    public long incrementAndGetSigned(int size, long offset, long increment) {
        return get(incrementAndGetSignedAsync(size, offset, increment));
    }

    @Override
    public long getUnsigned(int size, long offset) {
        return get(getUnsignedAsync(size, offset));
    }

    @Override
    public long setUnsigned(int size, long offset, long value) {
        return get(setUnsignedAsync(size, offset, value));
    }

    @Override
    public long incrementAndGetUnsigned(int size, long offset, long increment) {
        return get(incrementAndGetUnsignedAsync(size, offset, increment));
    }

    @Override
    public RFuture<Long> getSignedAsync(int size, long offset) {
        if (size > 64) {
            throw new IllegalArgumentException("Size can't be greater than 64 bits");
        }
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "GET", "i" + size, offset);
    }

    @Override
    public RFuture<Long> setSignedAsync(int size, long offset, long value) {
        if (size > 64) {
            throw new IllegalArgumentException("Size can't be greater than 64 bits");
        }
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "SET", "i" + size, offset, value);
    }

    @Override
    public RFuture<Long> incrementAndGetSignedAsync(int size, long offset, long increment) {
        if (size > 64) {
            throw new IllegalArgumentException("Size can't be greater than 64 bits");
        }
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "INCRBY", "i" + size, offset, increment);
    }

    @Override
    public RFuture<Long> getUnsignedAsync(int size, long offset) {
        if (size > 63) {
            throw new IllegalArgumentException("Size can't be greater than 63 bits");
        }
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "GET", "u" + size, offset);
    }

    @Override
    public RFuture<Long> setUnsignedAsync(int size, long offset, long value) {
        if (size > 63) {
            throw new IllegalArgumentException("Size can't be greater than 63 bits");
        }
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "SET", "u" + size, offset, value);
    }

    @Override
    public RFuture<Long> incrementAndGetUnsignedAsync(int size, long offset, long increment) {
        if (size > 63) {
            throw new IllegalArgumentException("Size can't be greater than 63 bits");
        }
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "INCRBY", "u" + size, offset, increment);
    }

    @Override
    public byte getByte(long offset) {
        return get(getByteAsync(offset));
    }

    @Override
    public byte setByte(long offset, byte value) {
        return get(setByteAsync(offset, value));
    }

    @Override
    public byte incrementAndGetByte(long offset, byte increment) {
        return get(incrementAndGetByteAsync(offset, increment));
    }

    @Override
    public short getShort(long offset) {
        return get(getShortAsync(offset));
    }

    @Override
    public short setShort(long offset, short value) {
        return get(setShortAsync(offset, value));
    }

    @Override
    public short incrementAndGetShort(long offset, short increment) {
        return get(incrementAndGetShortAsync(offset, increment));
    }

    @Override
    public int getInteger(long offset) {
        return get(getIntegerAsync(offset));
    }

    @Override
    public int setInteger(long offset, int value) {
        return get(setIntegerAsync(offset, value));
    }

    @Override
    public int incrementAndGetInteger(long offset, int increment) {
        return get(incrementAndGetIntegerAsync(offset, increment));
    }

    @Override
    public long getLong(long offset) {
        return get(getLongAsync(offset));
    }

    @Override
    public long setLong(long offset, long value) {
        return get(setLongAsync(offset, value));
    }

    @Override
    public long incrementAndGetLong(long offset, long increment) {
        return get(incrementAndGetLongAsync(offset, increment));
    }

    @Override
    public RFuture<Byte> getByteAsync(long offset) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_BYTE,
                                            getRawName(), "GET", "i8", offset);
    }

    @Override
    public RFuture<Byte> setByteAsync(long offset, byte value) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_BYTE,
                                            getRawName(), "SET", "i8", offset, value);
    }

    @Override
    public RFuture<Byte> incrementAndGetByteAsync(long offset, byte increment) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_BYTE,
                                            getRawName(), "INCRBY", "i8", offset, increment);
    }

    @Override
    public RFuture<Short> getShortAsync(long offset) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_SHORT,
                                            getRawName(), "GET", "i16", offset);
    }

    @Override
    public RFuture<Short> setShortAsync(long offset, short value) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_SHORT,
                                            getRawName(), "SET", "i16", offset, value);
    }

    @Override
    public RFuture<Short> incrementAndGetShortAsync(long offset, short increment) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_SHORT,
                                            getRawName(), "INCRBY", "i16", offset, increment);
    }

    @Override
    public RFuture<Integer> getIntegerAsync(long offset) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_INT,
                                            getRawName(), "GET", "i32", offset);
    }

    @Override
    public RFuture<Integer> setIntegerAsync(long offset, int value) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_INT,
                                            getRawName(), "SET", "i32", offset, value);
    }

    @Override
    public RFuture<Integer> incrementAndGetIntegerAsync(long offset, int increment) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_INT,
                                            getRawName(), "INCRBY", "i32", offset, increment);
    }

    @Override
    public RFuture<Long> getLongAsync(long offset) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "GET", "i64", offset);
    }

    @Override
    public RFuture<Long> setLongAsync(long offset, long value) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "SET", "i64", offset, value);
    }

    @Override
    public RFuture<Long> incrementAndGetLongAsync(long offset, long increment) {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITFIELD_LONG,
                                            getRawName(), "INCRBY", "i64", offset, increment);
    }

    @Override
    public long length() {
        return get(lengthAsync());
    }

    @Override
    public void set(BitSet bs) {
        get(setAsync(bs));
    }

    @Override
    public void set(long[] indexArray, boolean value) {
        get(setAsync(indexArray, value));
    }

    @Override
    public boolean get(long bitIndex) {
        return get(getAsync(bitIndex));
    }

    @Override
    public RFuture<Boolean> getAsync(long bitIndex) {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.GETBIT, getRawName(), bitIndex);
    }

    @Override
    public boolean set(long bitIndex) {
        return get(setAsync(bitIndex, true));
    }

    @Override
    public void set(long fromIndex, long toIndex, boolean value) {
        get(setAsync(fromIndex, toIndex, value));
    }

    @Override
    public void set(long fromIndex, long toIndex) {
        get(setAsync(fromIndex, toIndex));
    }

    @Override
    public boolean set(long bitIndex, boolean value) {
        return get(setAsync(bitIndex, value));
    }

    @Override
    public RFuture<Boolean> setAsync(long bitIndex, boolean value) {
        int val = toInt(value);
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.SETBIT, getRawName(), bitIndex, val);
    }

    protected int toInt(boolean value) {
        return Boolean.compare(value, false);
    }

    @Override
    public RFuture<Void> setAsync(long[] indexArray, boolean value) {
        int val = toInt(value);
        Object[] paramArray = new Object[indexArray.length * 4 + 1];
        int j = 0;
        paramArray[j++] = getRawName();
        for (int i = 0; i < indexArray.length; i++) {
            paramArray[j++] = "set";
            paramArray[j++] = "u1";
            paramArray[j++] = indexArray[i];
            paramArray[j++] = val;
        }
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BITFIELD_VOID, paramArray);
    }

    @Override
    public byte[] toByteArray() {
        return get(toByteArrayAsync());
    }

    @Override
    public RFuture<byte[]> toByteArrayAsync() {
        return commandExecutor.readAsync(getRawName(), ByteArrayCodec.INSTANCE, RedisCommands.GET, getRawName());
    }

    @Override
    public long cardinality() {
        return get(cardinalityAsync());
    }

    @Override
    public long size() {
        return get(sizeAsync());
    }

    @Override
    public void clear(long fromIndex, long toIndex) {
        get(clearAsync(fromIndex, toIndex));
    }

    @Override
    public boolean clear(long bitIndex) {
        return get(clearAsync(bitIndex));
    }

    @Override
    public void clear() {
        get(clearAsync());
    }

    @Override
    public void or(String... bitSetNames) {
        get(orAsync(bitSetNames));
    }

    @Override
    public void and(String... bitSetNames) {
        get(andAsync(bitSetNames));
    }

    @Override
    public void xor(String... bitSetNames) {
        get(xorAsync(bitSetNames));
    }

    @Override
    public void not() {
        get(notAsync());
    }

    private RFuture<Void> opAsync(String op, String... bitSetNames) {
        List<Object> params = new ArrayList<Object>(bitSetNames.length + 3);
        params.add(op);
        params.add(getRawName());
        params.add(getRawName());
        params.addAll(Arrays.asList(bitSetNames));
        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.BITOP, params.toArray());
    }

    @Override
    public BitSet asBitSet() {
        return fromByteArrayReverse(toByteArray());
    }

    //Copied from: https://github.com/xetorthio/jedis/issues/301
    private static BitSet fromByteArrayReverse(byte[] bytes) {
        if (bytes == null) {
            return new BitSet();
        }

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
    public RFuture<Long> lengthAsync() {
        return commandExecutor.evalReadAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local fromBit = redis.call('bitpos', KEYS[1], 1, -1);"
                + "local toBit = 8*(fromBit/8 + 1) - fromBit % 8;"
                        + "for i = toBit, fromBit, -1 do "
                            + "if redis.call('getbit', KEYS[1], i) == 1 then "
                                + "return i+1;"
                            + "end;"
                       + "end;" +
                     "return fromBit+1",
                Collections.<Object>singletonList(getRawName()));
    }

    @Override
    public RFuture<Void> setAsync(long fromIndex, long toIndex, boolean value) {
        int val = toInt(value);
        CommandBatchService executorService = new CommandBatchService(commandExecutor);
        for (long i = fromIndex; i < toIndex; i++) {
            executorService.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.SETBIT_VOID, getRawName(), i, val);
        }
        return executorService.executeAsyncVoid();
    }

    @Override
    public RFuture<Void> clearAsync(long fromIndex, long toIndex) {
        return setAsync(fromIndex, toIndex, false);
    }

    @Override
    public RFuture<Void> setAsync(BitSet bs) {
        return commandExecutor.writeAsync(getRawName(), ByteArrayCodec.INSTANCE, RedisCommands.SET, getRawName(), toByteArrayReverse(bs));
    }

    @Override
    public RFuture<Void> notAsync() {
        return opAsync("NOT");
    }

    @Override
    public RFuture<Void> setAsync(long fromIndex, long toIndex) {
        return setAsync(fromIndex, toIndex, true);
    }

    @Override
    public RFuture<Long> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITS_SIZE, getRawName());
    }

    @Override
    public RFuture<Boolean> setAsync(long bitIndex) {
        return setAsync(bitIndex, true);
    }

    @Override
    public RFuture<Long> cardinalityAsync() {
        return commandExecutor.readAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.BITCOUNT, getRawName());
    }

    @Override
    public RFuture<Boolean> clearAsync(long bitIndex) {
        return setAsync(bitIndex, false);
    }

    @Override
    public RFuture<Void> clearAsync() {
        return commandExecutor.writeAsync(getRawName(), RedisCommands.DEL_VOID, getRawName());
    }

    @Override
    public RFuture<Void> orAsync(String... bitSetNames) {
        return opAsync("OR", bitSetNames);
    }

    @Override
    public RFuture<Void> andAsync(String... bitSetNames) {
        return opAsync("AND", bitSetNames);
    }

    @Override
    public RFuture<Void> xorAsync(String... bitSetNames) {
        return opAsync("XOR", bitSetNames);
    }

}
