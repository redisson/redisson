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
package org.redisson.reactive;

import java.util.BitSet;

import org.reactivestreams.Publisher;
import org.redisson.RedissonBitSet;
import org.redisson.api.RBitSetReactive;
import org.redisson.client.codec.BitSetCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

import reactor.rx.Streams;

public class RedissonBitSetReactive extends RedissonExpirableReactive implements RBitSetReactive {

    private final RedissonBitSet instance;
    
    public RedissonBitSetReactive(CommandReactiveExecutor connectionManager, String name) {
        super(connectionManager, name);
        this.instance = new RedissonBitSet(connectionManager, name);
    }

    public Publisher<Boolean> get(long bitIndex) {
        return reactive(instance.getAsync(bitIndex));
    }

    public Publisher<Void> set(long bitIndex, boolean value) {
        return reactive(instance.setAsync(bitIndex, value));
    }

    public Publisher<byte[]> toByteArray() {
        return reactive(instance.toByteArrayAsync());
    }

    public Publisher<BitSet> asBitSet() {
        return commandExecutor.readReactive(getName(), BitSetCodec.INSTANCE, RedisCommands.GET, getName());
    }

    @Override
    public Publisher<Long> length() {
        return reactive(instance.lengthAsync());
    }

    @Override
    public Publisher<Void> set(long fromIndex, long toIndex, boolean value) {
        return reactive(instance.setAsync(fromIndex, toIndex, value));
    }

    @Override
    public Publisher<Void> clear(long fromIndex, long toIndex) {
        return reactive(instance.clearAsync(fromIndex, toIndex));
    }

    @Override
    public Publisher<Void> set(BitSet bs) {
        return reactive(instance.setAsync(bs));
    }

    @Override
    public Publisher<Void> not() {
        return reactive(instance.notAsync());
    }

    @Override
    public Publisher<Void> set(long fromIndex, long toIndex) {
        return reactive(instance.setAsync(fromIndex, toIndex));
    }

    @Override
    public Publisher<Integer> size() {
        return reactive(instance.sizeAsync());
    }

    @Override
    public Publisher<Void> set(long bitIndex) {
        return reactive(instance.setAsync(bitIndex));
    }

    @Override
    public Publisher<Long> cardinality() {
        return reactive(instance.cardinalityAsync());
    }

    @Override
    public Publisher<Void> clear(long bitIndex) {
        return reactive(instance.clearAsync(bitIndex));
    }

    @Override
    public Publisher<Void> clear() {
        return reactive(instance.clearAsync());
    }

    @Override
    public Publisher<Void> or(String... bitSetNames) {
        return reactive(instance.orAsync(bitSetNames));
    }

    @Override
    public Publisher<Void> and(String... bitSetNames) {
        return reactive(instance.andAsync(bitSetNames));
    }

    @Override
    public Publisher<Void> xor(String... bitSetNames) {
        return reactive(instance.xorAsync(bitSetNames));
    }

    @Override
    public String toString() {
        return Streams.create(asBitSet()).next().poll().toString();
    }

}
