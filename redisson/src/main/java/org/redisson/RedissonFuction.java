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

import org.redisson.api.*;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        this.codec = commandExecutor.getServiceManager().getCfg().getCodec();
    }

    public RedissonFuction(CommandAsyncExecutor commandExecutor, Codec codec) {
        this.commandExecutor = commandExecutor;
        this.codec = commandExecutor.getServiceManager().getCodec(codec);
    }

    @Override
    public void delete(String libraryName) {
        commandExecutor.get(deleteAsync(libraryName));
    }

    @Override
    public RFuture<Void> deleteAsync(String libraryName) {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_DELETE, libraryName);
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
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_FLUSH);
    }

    @Override
    public void kill() {
        commandExecutor.get(killAsync());
    }

    @Override
    public RFuture<Void> killAsync() {
        List<CompletableFuture<Void>> futures = commandExecutor.executeAllAsync(RedisCommands.FUNCTION_KILL);
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
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_LOAD, "#!lua name=" + libraryName + " \n " + code);
    }

    @Override
    public void loadAndReplace(String libraryName, String code) {
        commandExecutor.get(loadAndReplaceAsync(libraryName, code));
    }

    @Override
    public RFuture<Void> loadAndReplaceAsync(String libraryName, String code) {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_LOAD, "REPLACE",
                                                                                "#!lua name=" + libraryName + " \n " + code);
    }

    @Override
    public void restore(byte[] payload) {
        commandExecutor.get(restoreAsync(payload));
    }

    @Override
    public RFuture<Void> restoreAsync(byte[] payload) {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_RESTORE, payload);
    }

    @Override
    public void restoreAndReplace(byte[] payload) {
        commandExecutor.get(restoreAndReplaceAsync(payload));
    }

    @Override
    public RFuture<Void> restoreAndReplaceAsync(byte[] payload) {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_RESTORE, payload, "REPLACE");
    }

    @Override
    public void restoreAfterFlush(byte[] payload) {
        commandExecutor.get(restoreAfterFlushAsync(payload));
    }

    @Override
    public RFuture<Void> restoreAfterFlushAsync(byte[] payload) {
        return commandExecutor.writeAllVoidAsync(RedisCommands.FUNCTION_RESTORE, payload, "FLUSH");
    }

    private List<Object> encode(Collection<?> values, Codec codec) {
        List<Object> result = new ArrayList<>(values.size());
        for (Object object : values) {
            result.add(commandExecutor.encode(codec, object));
        }
        return result;
    }

    @Override
    public <R> R call(String key, FunctionMode mode, String name, FunctionResult returnType, List<Object> keys, Object... values) {
        return commandExecutor.get(callAsync(key, mode, name, returnType, keys, values));
    }

    @Override
    public <R> R call(FunctionMode mode, String name, FunctionResult returnType, List<Object> keys, Object... values) {
        return commandExecutor.get(callAsync(mode, name, returnType, keys, values));
    }

    @Override
    public <R> R call(FunctionMode mode, String name, FunctionResult returnType) {
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
    public <R> RFuture<R> callAsync(String key, FunctionMode mode, String name, FunctionResult returnType, List<Object> keys, Object... values) {
        List<Object> args = new ArrayList<>();
        args.add(name);
        args.add(keys.size());
        if (!keys.isEmpty()) {
            args.addAll(keys.stream().map(k -> {
                                         if (k instanceof String) {
                                             return commandExecutor.getServiceManager().getConfig().getNameMapper().map(k.toString());
                                         }
                                         return k;
                                     })
                                     .collect(Collectors.toList()));
        }
        args.addAll(encode(Arrays.asList(values), codec));
        if (mode == FunctionMode.READ) {
            RedisCommand cmd = new RedisCommand("FCALL_RO", returnType.getCommand().getReplayMultiDecoder(), returnType.getCommand().getConvertor());
            return commandExecutor.readAsync(key, codec, cmd, args.toArray());
        }
        return commandExecutor.writeAsync(key, codec, returnType.getCommand(), args.toArray());
    }

    @Override
    public <R> RFuture<R> callAsync(FunctionMode mode, String name, FunctionResult returnType, List<Object> keys, Object... values) {
        String key = null;
        if (!keys.isEmpty()) {
            if (keys.get(0) instanceof byte[]) {
                key = new String((byte[]) keys.get(0));
            } else {
                key = keys.get(0).toString();
            }
            key = commandExecutor.getServiceManager().getConfig().getNameMapper().map(key);
        }
        return callAsync(key, mode, name, returnType, keys, values);
    }

    @Override
    public <R> RFuture<R> callAsync(FunctionMode mode, String name, FunctionResult returnType) {
        return callAsync(mode, name, returnType, Collections.emptyList());

    }
}
