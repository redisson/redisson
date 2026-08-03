/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.redisson.api.RFuture;
import org.redisson.api.RMapsImport;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.ServiceManager;
import org.redisson.misc.Hash;
import org.redisson.misc.HashValue;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> field type
 * @param <V> value type
 */
public class RedissonMapsImport<K, V> implements RMapsImport<K, V> {

    private final CommandAsyncExecutor commandExecutor;
    private final Codec codec;
    private final List<byte[]> fields;
    private final String fieldsetName;
    private final int batchSize;

    private final Queue<Row> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger bufferedCount = new AtomicInteger();
    private final AtomicLong importedCount = new AtomicLong();

    RedissonMapsImport(CommandAsyncExecutor commandExecutor, Codec codec, List<byte[]> fields, int batchSize) {
        validateFields(fields);

        this.commandExecutor = commandExecutor;
        this.codec = codec;
        this.fields = fields;
        this.batchSize = batchSize;
        this.fieldsetName = fieldsetName(fields);
    }

    @SafeVarargs
    @Override
    public final void add(String name, V... values) {
        add(name, Arrays.asList(values));
    }

    @Override
    public void add(String name, List<V> values) {
        commandExecutor.get(addAsync(name, values));
    }

    @SafeVarargs
    @Override
    public final RFuture<Void> addAsync(String name, V... values) {
        return addAsync(name, Arrays.asList(values));
    }

    @Override
    public RFuture<Void> addAsync(String name, List<V> values) {
        if (values.size() != fields.size()) {
            throw new IllegalArgumentException("Amount of values " + values.size()
                                                + " doesn't match amount of fields " + fields.size());
        }

        addEncoded(name, encodeValues(values));
        if (bufferedCount.get() >= batchSize) {
            return flushAsync();
        }
        return CompletableFutureWrapper.completedNull();
    }

    private List<byte[]> encodeValues(List<V> values) {
        List<byte[]> encodedValues = new ArrayList<>(values.size());
        for (V value : values) {
            encodedValues.add(encodeMapValue(commandExecutor, codec, value));
        }
        return encodedValues;
    }

    @Override
    public void flush() {
        commandExecutor.get(flushAsync());
    }

    @Override
    public RFuture<Void> flushAsync() {
        return new CompletableFutureWrapper<>(flushPortions());
    }

    private CompletionStage<Void> flushPortions() {
        List<Row> rows = drainBuffer();
        if (rows.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return writeAsync(rows)
                .thenRun(() -> importedCount.addAndGet(rows.size()))
                .thenCompose(r -> flushPortions());
    }

    @Override
    public long getImportedCount() {
        return importedCount.get();
    }

    void addEncoded(String name, List<byte[]> encodedValues) {
        buffer.add(new Row(commandExecutor.getServiceManager().getNameMapper().map(name), encodedValues));
        bufferedCount.incrementAndGet();
    }

    private List<Row> drainBuffer() {
        List<Row> rows = new ArrayList<>();
        while (rows.size() < batchSize) {
            Row row = buffer.poll();
            if (row == null) {
                break;
            }
            rows.add(row);
        }
        bufferedCount.addAndGet(-rows.size());
        return rows;
    }

    private CompletionStage<Void> writeAsync(List<Row> rows) {
        if (serviceManager().isHashImportDisabled()) {
            return writeWithHset(rows);
        }

        return writeWithHashImport(rows).<CompletionStage<Void>>handle((r, e) -> {
            if (e == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (!isHashImportUnsupported(e)) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
            }

            serviceManager().disableHashImport();
            return writeWithHset(rows);
        }).thenCompose(f -> f);
    }

    private CompletionStage<Void> writeWithHashImport(List<Row> rows) {
        CommandBatchService batch = new CommandBatchService(commandExecutor);
        List<Object> prepareParams = new ArrayList<>(fields.size() + 1);
        prepareParams.add(fieldsetName);
        prepareParams.addAll(fields);

        for (Map.Entry<MasterSlaveEntry, List<Row>> node : groupByNode(rows).entrySet()) {
            batch.writeAsync(node.getKey(), StringCodec.INSTANCE,
                                RedisCommands.HIMPORT_PREPARE, prepareParams.toArray());

            for (Row row : node.getValue()) {
                List<Object> params = new ArrayList<>(row.values.size() + 2);
                params.add(row.name);
                params.add(fieldsetName);
                params.addAll(row.values);

                batch.writeAsync(row.name, StringCodec.INSTANCE,
                                    RedisCommands.HIMPORT_SET, params.toArray());
            }
        }
        return batch.executeAsync().thenApply(r -> null);
    }

    private CompletionStage<Void> writeWithHset(List<Row> rows) {
        CommandBatchService batch = new CommandBatchService(commandExecutor);
        for (Row row : rows) {
            List<Object> params = new ArrayList<>(row.values.size() * 2 + 1);
            params.add(row.name);
            for (int i = 0; i < row.values.size(); i++) {
                params.add(fields.get(i));
                params.add(row.values.get(i));
            }

            batch.writeAsync(row.name, StringCodec.INSTANCE, RedisCommands.DEL, row.name);
            batch.writeAsync(row.name, StringCodec.INSTANCE, RedisCommands.HSET_VOID, params.toArray());
        }
        return batch.executeAsync().thenApply(r -> null);
    }

    private Map<MasterSlaveEntry, List<Row>> groupByNode(List<Row> rows) {
        ConnectionManager connectionManager = commandExecutor.getConnectionManager();

        Map<MasterSlaveEntry, List<Row>> result = new LinkedHashMap<>();
        for (Row row : rows) {
            MasterSlaveEntry entry = connectionManager.getWriteEntry(connectionManager.calcSlot(row.name));
            result.computeIfAbsent(entry, k -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private ServiceManager serviceManager() {
        return commandExecutor.getServiceManager();
    }

    private static boolean isHashImportUnsupported(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String lowerCased = message.toLowerCase(Locale.ROOT);
            if (lowerCased.contains("unknown command") || lowerCased.contains("unknown subcommand")) {
                return true;
            }
        }
        return false;
    }

    static String fieldsetName(List<byte[]> fields) {
        return String.format("rs%08x", (int) fieldsHash(fields).getValue()[0]);
    }

    static HashValue fieldsHash(List<byte[]> fields) {
        byte[] lengths = new byte[fields.size() * Integer.BYTES];
        int position = 0;
        for (byte[] field : fields) {
            lengths[position++] = (byte) (field.length >>> 24);
            lengths[position++] = (byte) (field.length >>> 16);
            lengths[position++] = (byte) (field.length >>> 8);
            lengths[position++] = (byte) field.length;
        }

        byte[][] hashedArrays = new byte[fields.size() + 1][];
        hashedArrays[0] = lengths;
        for (int i = 0; i < fields.size(); i++) {
            hashedArrays[i + 1] = fields.get(i);
        }

        ByteBuf buf = Unpooled.wrappedBuffer(hashedArrays);
        try {
            return new HashValue(Hash.hash128(buf));
        } finally {
            buf.release();
        }
    }

    static void validateFields(List<byte[]> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("Fields can't be empty");
        }

        for (int i = 0; i < fields.size(); i++) {
            for (int j = i + 1; j < fields.size(); j++) {
                if (Arrays.equals(fields.get(i), fields.get(j))) {
                    throw new IllegalArgumentException("Field name duplication detected");
                }
            }
        }
    }

    static byte[] encodeMapKey(CommandAsyncExecutor commandExecutor, Codec codec, Object field) {
        return toBytes(commandExecutor.encodeMapKey(codec, field));
    }

    static byte[] encodeMapValue(CommandAsyncExecutor commandExecutor, Codec codec, Object value) {
        return toBytes(commandExecutor.encodeMapValue(codec, value));
    }

    private static byte[] toBytes(ByteBuf buf) {
        try {
            return ByteBufUtil.getBytes(buf);
        } finally {
            buf.release();
        }
    }

    private static final class Row {

        private final String name;
        private final List<byte[]> values;

        Row(String name, List<byte[]> values) {
            this.name = name;
            this.values = values;
        }

    }

}
