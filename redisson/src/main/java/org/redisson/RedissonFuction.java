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
package org.redisson;

import org.redisson.api.FunctionLibrary;
import org.redisson.api.FunctionStats;
import org.redisson.api.RFunction;
import org.redisson.api.RFuture;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonFuction implements RFunction {

    private final Codec codec;
    private final CommandAsyncExecutor commandExecutor;

    public RedissonFuction(CommandAsyncExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
        this.codec = commandExecutor.getConnectionManager().getCodec();
    }

    public RedissonFuction(CommandAsyncExecutor commandExecutor, Codec codec) {
        this.commandExecutor = commandExecutor;
        this.codec = codec;
    }

    @Override
    public void delete(String libraryName) {
        commandExecutor.get(deleteAsync(libraryName));
    }

    @Override
    public RFuture<Void> deleteAsync(String libraryName) {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_DELETE, libraryName);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public byte[] dump() {
        return commandExecutor.get(dumpAsync());
    }

    @Override
    public RFuture<byte[]> dumpAsync() {
        return commandExecutor.readAsync((String) null, ByteArrayCodec.INSTANCE, RedisCommands.FUNCTION_DUMP);
    }

    @Override
    public void flush() {
        commandExecutor.get(flushAsync());
    }

    @Override
    public RFuture<Void> flushAsync() {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_FLUSH);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void kill() {
        commandExecutor.get(killAsync());
    }

    @Override
    public RFuture<Void> killAsync() {
        List<CompletableFuture<Void>> futures = commandExecutor.executeAll(RedisCommands.FUNCTION_KILL);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public List<FunctionLibrary> list() {
        return commandExecutor.get(listAsync());
    }

    @Override
    public RFuture<List<FunctionLibrary>> listAsync() {
        return commandExecutor.readAsync((String) null, ByteArrayCodec.INSTANCE, RedisCommands.FUNCTION_LIST);
    }

    @Override
    public List<FunctionLibrary> list(String namePattern) {
        return commandExecutor.get(listAsync(namePattern));
    }


    @Override
    public RFuture<List<FunctionLibrary>> listAsync(String namePattern) {
        return commandExecutor.readAsync((String) null, ByteArrayCodec.INSTANCE, RedisCommands.FUNCTION_LIST, "LIBRARYNAME", namePattern);
    }

    @Override
    public void load(String libraryName, String code) {
        commandExecutor.get(loadAsync(libraryName, code));
    }

    @Override
    public RFuture<Void> loadAsync(String libraryName, String code) {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_LOAD, "Lua", libraryName, code);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void loadAndReplace(String libraryName, String code) {
        commandExecutor.get(loadAndReplaceAsync(libraryName, code));
    }

    @Override
    public RFuture<Void> loadAndReplaceAsync(String libraryName, String code) {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_LOAD,
                                                "Lua", libraryName, "REPLACE", code);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void restore(byte[] payload) {
        commandExecutor.get(restoreAsync(payload));
    }

    @Override
    public RFuture<Void> restoreAsync(byte[] payload) {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_RESTORE, (Object) payload);
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void restoreAndReplace(byte[] payload) {
        commandExecutor.get(restoreAndReplaceAsync(payload));
    }

    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] payload) {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_RESTORE, payload, "REPLACE");
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void restoreAfterFlush(byte[] payload) {
        commandExecutor.get(restoreAfterFlushAsync(payload));
    }

    @Override
    public RFuture<Void> restoreAfterFlushAsync(byte[] payload) {
        List<CompletableFuture<Void>> futures = commandExecutor.executeMasters(RedisCommands.FUNCTION_RESTORE, payload, "FLUSH");
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        return new CompletableFutureWrapper<>(f);
    }

    private List<Object> encode(Collection<?> values, Codec codec) {
        List<Object> result = new ArrayList<Object>(values.size());
        for (Object object : values) {
            result.add(commandExecutor.encode(codec, object));
        }
        return result;
    }

    @Override
    public <R> R call(String key, Mode mode, String name, ReturnType returnType, List<Object> keys, Object... values) {
        return commandExecutor.get(callAsync(key, mode, name, returnType, keys, values));
    }

    @Override
    public <R> R call(Mode mode, String name, ReturnType returnType, List<Object> keys, Object... values) {
        return commandExecutor.get(callAsync(mode, name, returnType, keys, values));
    }

    @Override
    public <R> R call(Mode mode, String name, ReturnType returnType) {
        return commandExecutor.get(callAsync(mode, name, returnType));
    }

    @Override
    public FunctionStats stats() {
        return commandExecutor.get(statsAsync());
    }

    @Override
    public RFuture<FunctionStats> statsAsync() {
        return commandExecutor.readAsync((String) null, StringCodec.INSTANCE, RedisCommands.FUNCTION_STATS);
    }

    @Override
    public <R> RFuture<R> callAsync(String key, Mode mode, String name, ReturnType returnType, List<Object> keys, Object... values) {
        List<Object> args = new ArrayList<>();
        args.add(name);
        args.add(keys.size());
        if (keys.size() > 0) {
            args.add(keys);
        }
        args.addAll(encode(Arrays.asList(values), codec));
        if (mode == Mode.READ) {
            return commandExecutor.readAsync(key, codec, returnType.getCommand(), args.toArray());
        }
        return commandExecutor.writeAsync(key, codec, returnType.getCommand(), args.toArray());
    }

    @Override
    public <R> RFuture<R> callAsync(Mode mode, String name, ReturnType returnType, List<Object> keys, Object... values) {
        return callAsync(null, mode, name, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> callAsync(Mode mode, String name, ReturnType returnType) {
        return callAsync(mode, name, returnType, Collections.emptyList());

    }
}
