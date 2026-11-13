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
package org.redisson.spring.data.connection;

import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ObjectDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.StringMapDataDecoder;
import org.redisson.client.protocol.decoder.TimeLongObjectDecoder;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.CompletableFutureWrapper;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.RedisClusterNode.SlotRange;
import org.springframework.data.redis.connection.convert.Converters;
import org.springframework.data.redis.connection.convert.StringToRedisClientInfoConverter;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.ScanCursor;
import org.springframework.data.redis.core.ScanIteration;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.data.redis.util.ByteUtils;
import org.springframework.util.Assert;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonClusterConnection extends RedissonConnection implements RedisClusterConnection, DefaultedRedisClusterConnection {

    public RedissonClusterConnection(RedissonClient redisson) {
        super(redisson);
    }
    
    public RedissonClusterConnection(RedissonClient redisson, boolean filterOkResponses) {
        super(redisson, filterOkResponses);
    }

    @Override
    public Iterable<RedisClusterNode> clusterGetNodes() {
        RedisStrictCommand<List<RedisClusterNode>> cluster
                = new RedisStrictCommand<List<RedisClusterNode>>("CLUSTER", "NODES",
                            new ObjectDecoder(new RedisClusterNodeDecoder(executorService.getServiceManager())));
        return read(null, StringCodec.INSTANCE, cluster);
    }

    @Override
    public Collection<RedisClusterNode> clusterGetReplicas(RedisClusterNode master) {
        Iterable<RedisClusterNode> res = clusterGetNodes();
        RedisClusterNode masterNode = null;
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            if (master.getHost().equals(redisClusterNode.getHost()) 
                    && master.getPort().equals(redisClusterNode.getPort())) {
                masterNode = redisClusterNode;
                break;
            }
        }
        
        if (masterNode == null) {
            throw new IllegalStateException("Unable to find master node: " + master);
        }
        
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            if (redisClusterNode.getMasterId() == null 
                    || !redisClusterNode.getMasterId().equals(masterNode.getId())) {
                iterator.remove();
            }
        }
        return (Collection<RedisClusterNode>) res;
    }

    @Override
    public Map<RedisClusterNode, Collection<RedisClusterNode>> clusterGetMasterReplicaMap() {
        Iterable<RedisClusterNode> res = clusterGetNodes();
        
        Map<RedisClusterNode, Collection<RedisClusterNode>> result = new HashMap<RedisClusterNode, Collection<RedisClusterNode>>();
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            if (redisClusterNode.isMaster()) {
                result.put(redisClusterNode, new ArrayList<RedisClusterNode>());
            }
        }
        
        for (Iterator<RedisClusterNode> iterator = res.iterator(); iterator.hasNext();) {
            RedisClusterNode redisClusterNode = iterator.next();
            
            for (Entry<RedisClusterNode, Collection<RedisClusterNode>> master : result.entrySet()) {
                if (redisClusterNode.getMasterId() != null 
                        && redisClusterNode.getMasterId().equals(master.getKey().getId())) {
                    master.getValue().add(redisClusterNode);
                }
            }
        }
        return result;
    }

    @Override
    public Integer clusterGetSlotForKey(byte[] key) {
        RFuture<Integer> f = executorService.readAsync((String)null, StringCodec.INSTANCE, RedisCommands.KEYSLOT, key);
        return syncFuture(f);
    }

    @Override
    public RedisClusterNode clusterGetNodeForSlot(int slot) {
        Iterable<RedisClusterNode> res = clusterGetNodes();
        for (RedisClusterNode redisClusterNode : res) {
            if (redisClusterNode.isMaster() && redisClusterNode.getSlotRange().contains(slot)) {
                return redisClusterNode;
            }
        }
        return null;
    }

    @Override
    public RedisClusterNode clusterGetNodeForKey(byte[] key) {
        int slot = executorService.getConnectionManager().calcSlot(key);
        return clusterGetNodeForSlot(slot);
    }

    @Override
    public ClusterInfo clusterGetClusterInfo() {
        RFuture<Map<String, String>> f = executorService.readAsync((String)null, StringCodec.INSTANCE, RedisCommands.CLUSTER_INFO);
        Map<String, String> entries = syncFuture(f);

        Properties props = new Properties();
        for (Entry<String, String> entry : entries.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        return new ClusterInfo(props);
    }

    @Override
    public void clusterAddSlots(RedisClusterNode node, int... slots) {
        RedisClient entry = getEntry(node);
        List<Integer> params = convert(slots);
        RFuture<Map<String, String>> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_ADDSLOTS, params.toArray());
        syncFuture(f);
    }

    protected List<Integer> convert(int... slots) {
        List<Integer> params = new ArrayList<Integer>();
        for (int slot : slots) {
            params.add(slot);
        }
        return params;
    }

    @Override
    public void clusterAddSlots(RedisClusterNode node, SlotRange range) {
        clusterAddSlots(node, range.getSlotsArray());
    }

    @Override
    public Long clusterCountKeysInSlot(int slot) {
        RedisClusterNode node = clusterGetNodeForSlot(slot);
        MasterSlaveEntry entry = executorService.getConnectionManager().getEntry(new InetSocketAddress(node.getHost(), node.getPort()));
        RFuture<Long> f = executorService.readAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_COUNTKEYSINSLOT, slot);
        return syncFuture(f);
    }

    @Override
    public void clusterDeleteSlots(RedisClusterNode node, int... slots) {
        RedisClient entry = getEntry(node);
        List<Integer> params = convert(slots);
        RFuture<Long> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_DELSLOTS, params.toArray());
        syncFuture(f);
    }

    @Override
    public void clusterDeleteSlotsInRange(RedisClusterNode node, SlotRange range) {
        clusterDeleteSlots(node, range.getSlotsArray());
    }

    @Override
    public void clusterForget(RedisClusterNode node) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (RedisClusterNode clusterNode : clusterGetNodes()) {
            if (clusterNode.isConnected() && !clusterNode.getId().equals(node.getId())) {
                RFuture<Void> f = executorService.writeAsync(getEntry(clusterNode), StringCodec.INSTANCE, RedisCommands.CLUSTER_FORGET, node.getId());
                futures.add(f.toCompletableFuture());
            }
        }
        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        syncFuture(new CompletableFutureWrapper<>(f));
    }

    @Override
    public void clusterMeet(RedisClusterNode node) {
        Assert.notNull(node, "Cluster node must not be null for CLUSTER MEET command!");
        Assert.hasText(node.getHost(), "Node to meet cluster must have a host!");
        Assert.isTrue(node.getPort() > 0, "Node to meet cluster must have a port greater 0!");
        
        RFuture<Void> f = executorService.writeAsync((String)null, StringCodec.INSTANCE, RedisCommands.CLUSTER_MEET, node.getHost(), node.getPort());
        syncFuture(f);
    }

    @Override
    public void clusterSetSlot(RedisClusterNode node, int slot, AddSlots mode) {
        RedisClient entry = getEntry(node);
        RFuture<Map<String, String>> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_SETSLOT, slot, mode);
        syncFuture(f);
    }
    
    private static final RedisStrictCommand<List<String>> CLUSTER_GETKEYSINSLOT = new RedisStrictCommand<List<String>>("CLUSTER", "GETKEYSINSLOT", new ObjectListReplayDecoder<String>());

    @Override
    public List<byte[]> clusterGetKeysInSlot(int slot, Integer count) {
        RedisClient entry = getEntry(clusterGetNodeForSlot(slot));
        RFuture<List<byte[]>> f = executorService.writeAsync(entry, ByteArrayCodec.INSTANCE, CLUSTER_GETKEYSINSLOT, slot, count);
        return syncFuture(f);
    }

    @Override
    public void clusterReplicate(RedisClusterNode master, RedisClusterNode slave) {
        RedisClient entry = getEntry(slave);
        RFuture<Long> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CLUSTER_REPLICATE, master.getId());
        syncFuture(f);
    }

//    @Override
//    public String ping(RedisClusterNode node) {
//        return execute(node, RedisCommands.PING);
//    }

    @Override
    public void bgReWriteAof(RedisClusterNode node) {
        execute(node, RedisCommands.BGREWRITEAOF);
    }

    @Override
    public void bgSave(RedisClusterNode node) {
        execute(node, RedisCommands.BGSAVE);
    }

    @Override
    public Long lastSave(RedisClusterNode node) {
        return execute(node, RedisCommands.LASTSAVE);
    }

    @Override
    public void save(RedisClusterNode node) {
        execute(node, RedisCommands.SAVE);
    }

    @Override
    public Long dbSize(RedisClusterNode node) {
        return execute(node, RedisCommands.DBSIZE);
    }

    private <T> T execute(RedisClusterNode node, RedisCommand<T> command, Object... params) {
        RedisClient entry = getEntry(node);
        RFuture<T> f = executorService.writeAsync(entry, StringCodec.INSTANCE, command, params);
        return syncFuture(f);
    }

    protected RedisClient getEntry(RedisClusterNode node) {
        InetSocketAddress addr = new InetSocketAddress(node.getHost(), node.getPort());
        MasterSlaveEntry entry = executorService.getConnectionManager().getEntry(addr);
        ClientConnectionsEntry e = null;
        if (entry != null) {
            e = entry.getEntry(addr);
        }
        if (e == null) {
            throw new IllegalArgumentException("Node " + node + " is unknown to cluster");
        }
        return e.getClient();
    }

    @Override
    public void flushDb(RedisClusterNode node) {
        execute(node, RedisCommands.FLUSHDB);
    }

    @Override
    public void flushAll(RedisClusterNode node) {
        execute(node, RedisCommands.FLUSHALL);
    }

    @Override
    public void flushDb(RedisClusterNode node, FlushOption option) {
        execute(node, RedisCommands.FLUSHDB, option.toString());
    }

    @Override
    public void flushAll(RedisClusterNode node, FlushOption option) {
        execute(node, RedisCommands.FLUSHALL, option.toString());
    }

    @Override
    public Properties info(RedisClusterNode node) {
        Map<String, String> info = execute(node, RedisCommands.INFO_ALL);
        Properties result = new Properties();
        for (Entry<String, String> entry : info.entrySet()) {
            result.setProperty(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public Properties info(RedisClusterNode node, String section) {
        RedisStrictCommand<Map<String, String>> command = new RedisStrictCommand<Map<String, String>>("INFO", section, new StringMapDataDecoder());

        Map<String, String> info = execute(node, command);
        Properties result = new Properties();
        for (Entry<String, String> entry : info.entrySet()) {
            result.setProperty(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public Properties info() {
        if (isQueueing() || isPipelined()) {
            return super.info();
        }
        return collectFromMasters(master -> info(master, "default"));
    }

    @Override
    public Properties info(String section) {
        if (isQueueing() || isPipelined()) {
            return super.info(section);
        }
        return collectFromMasters(master -> info(master, section));
    }

    private List<RedisClusterNode> getMasters() {
        List<RedisClusterNode> masters = new ArrayList<>();
        for (MasterSlaveEntry entry : executorService.getConnectionManager().getEntrySet()) {
            InetSocketAddress addr = entry.getClient().getAddr();
            masters.add(new RedisClusterNode(addr.getHostString(), addr.getPort()));
        }
        return masters;
    }

    private Properties collectFromMasters(Function<RedisClusterNode, Properties> command) {
        Properties result = new Properties();
        for (RedisClusterNode master : getMasters()) {
            for (Entry<Object, Object> entry : command.apply(master).entrySet()) {
                result.put(master.asString() + "." + entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private final RedisStrictCommand<List<byte[]>> KEYS = new RedisStrictCommand<>("KEYS");

    @Override
    public Set<byte[]> keys(RedisClusterNode node, byte[] pattern) {
        RedisClient entry = getEntry(node);
        RFuture<Collection<byte[]>> f = executorService.readAsync(entry, ByteArrayCodec.INSTANCE, KEYS, pattern);
        Collection<byte[]> keys = syncFuture(f);
        return new HashSet<>(keys);
    }

    @Override
    public byte[] randomKey(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<byte[]> f = executorService.readRandomAsync(entry, ByteArrayCodec.INSTANCE, RedisCommands.RANDOM_KEY);
        return syncFuture(f);
    }

    @Override
    public void shutdown(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<Void> f = executorService.readAsync(entry, ByteArrayCodec.INSTANCE, RedisCommands.SHUTDOWN);
        syncFuture(f);
    }

    @Override
    public Properties getConfig(RedisClusterNode node, String pattern) {
        RedisClient entry = getEntry(node);
        RFuture<List<String>> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CONFIG_GET, pattern);
        List<String> r = syncFuture(f);
        if (r != null) {
            return Converters.toProperties(r);
        }
        return null;
    }

    @Override
    public void setConfig(RedisClusterNode node, String param, String value) {
        RedisClient entry = getEntry(node);
        RFuture<Void> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CONFIG_SET, param, value);
        syncFuture(f);
    }

    @Override
    public void resetConfigStats(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<Void> f = executorService.writeAsync(entry, StringCodec.INSTANCE, RedisCommands.CONFIG_RESETSTAT);
        syncFuture(f);
    }

    @Override
    public void rewriteConfig(RedisClusterNode node) {
        execute(node, RedisCommands.CONFIG_REWRITE);
    }

    @Override
    public Properties getConfig(String pattern) {
        if (isQueueing() || isPipelined()) {
            return super.getConfig(pattern);
        }
        return collectFromMasters(master -> getConfig(master, pattern));
    }

    @Override
    public void setConfig(String param, String value) {
        if (isQueueing() || isPipelined()) {
            super.setConfig(param, value);
            return;
        }
        for (RedisClusterNode master : getMasters()) {
            setConfig(master, param, value);
        }
    }

    @Override
    public void resetConfigStats() {
        if (isQueueing() || isPipelined()) {
            super.resetConfigStats();
            return;
        }
        for (RedisClusterNode master : getMasters()) {
            resetConfigStats(master);
        }
    }

    @Override
    public void rewriteConfig() {
        if (isQueueing() || isPipelined()) {
            super.rewriteConfig();
            return;
        }
        for (RedisClusterNode master : getMasters()) {
            rewriteConfig(master);
        }
    }

    @Override
    public Long time(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<Long> f = executorService.readAsync(entry, LongCodec.INSTANCE, RedisCommands.TIME_LONG);
        return syncFuture(f);
    }

    @Override
    public Long time(RedisClusterNode node, TimeUnit timeUnit) {
        RedisClient entry = getEntry(node);
        RFuture<Long> f = executorService.readAsync(entry, LongCodec.INSTANCE,
                new RedisStrictCommand<>("TIME", new TimeLongObjectDecoder(timeUnit)));
        return syncFuture(f);
    }

    private static final StringToRedisClientInfoConverter CONVERTER = new StringToRedisClientInfoConverter();
    
    @Override
    public List<RedisClientInfo> getClientList(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<List<String>> f = executorService.readAsync(entry, StringCodec.INSTANCE, RedisCommands.CLIENT_LIST);
        List<String> list = syncFuture(f);
        return CONVERTER.convert(list.toArray(new String[list.size()]));
    }

    @Override
    public Cursor<byte[]> scan(RedisClusterNode node, ScanOptions options) {
        return new ScanCursor<byte[]>(0, options) {

            private RedisClient client = getEntry(node);
            
            @Override
            protected ScanIteration<byte[]> doScan(CursorId cursorId, ScanOptions options) {
                if (isQueueing() || isPipelined()) {
                    throw new UnsupportedOperationException("'SSCAN' cannot be called in pipeline / transaction mode.");
                }

                if (client == null) {
                    return null;
                }
                
                List<Object> args = new ArrayList<Object>();
                args.add(cursorId);
                if (options.getBytePattern() != null) {
                    args.add("MATCH");
                    args.add(options.getBytePattern());
                }
                if (options.getCount() != null) {
                    args.add("COUNT");
                    args.add(options.getCount());
                }
                if (options instanceof KeyScanOptions && ((KeyScanOptions) options).getType() != null) {
                    args.add("TYPE");
                    args.add(((KeyScanOptions) options).getType());
                }
                
                RFuture<ListScanResult<byte[]>> f = executorService.readAsync(client, ByteArrayCodec.INSTANCE, RedisCommands.SCAN, args.toArray());
                ListScanResult<byte[]> res = syncFuture(f);
                String pos = res.getPos();
                client = res.getRedisClient();
                if (CursorId.isInitial(pos)) {
                    client = null;
                }
                
                return new ScanIteration<byte[]>(CursorId.of(pos), res.getValues());
            }
        }.open();
    }

    @Override
    public void rename(byte[] oldName, byte[] newName) {

        if (isPipelined()) {
            throw new InvalidDataAccessResourceUsageException("Clustered rename is not supported in a pipeline");
        }

        if (executorService.getConnectionManager().calcSlot(oldName) == executorService.getConnectionManager().calcSlot(newName)) {
            super.rename(oldName, newName);
            return;
        }

        final byte[] value = dump(oldName);

        if (null != value) {

            final Long sourceTtlInSeconds = ttl(oldName);

            final long ttlInMilliseconds;
            if (null != sourceTtlInSeconds && sourceTtlInSeconds > 0) {
                ttlInMilliseconds = sourceTtlInSeconds * 1000;
            } else {
                ttlInMilliseconds = 0;
            }

            restore(newName, ttlInMilliseconds, value, true);
            del(oldName);
        }
    }

    @Override
    public Boolean renameNX(byte[] oldName, byte[] newName) {
        if (isPipelined()) {
            throw new InvalidDataAccessResourceUsageException("Clustered rename is not supported in a pipeline");
        }

        if (executorService.getConnectionManager().calcSlot(oldName) == executorService.getConnectionManager().calcSlot(newName)) {
            return super.renameNX(oldName, newName);
        }

        final byte[] value = dump(oldName);

        if (null != value && !exists(newName)) {

            final Long sourceTtlInSeconds = ttl(oldName);

            final long ttlInMilliseconds;
            if (null != sourceTtlInSeconds && sourceTtlInSeconds > 0) {
                ttlInMilliseconds = sourceTtlInSeconds * 1000;
            } else {
                ttlInMilliseconds = 0;
            }

            restore(newName, ttlInMilliseconds, value);
            del(oldName);

            return true;
        }

        return false;
    }

    @Override
    public Long del(byte[]... keys) {
        if (isQueueing() || isPipelined()) {
            for (byte[] key: keys) {
                write(key, LongCodec.INSTANCE, RedisCommands.DEL, key);
            }

            return null;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (byte[] key: keys) {
            es.writeAsync(key, StringCodec.INSTANCE, RedisCommands.DEL, key);
        }
        BatchResult<Long> b = (BatchResult<Long>) es.execute();
        return b.getResponses().stream().collect(Collectors.summarizingLong(v -> v)).getSum();
    }

    @Override
    public List<byte[]> mGet(byte[]... keys) {
        if (isQueueing() || isPipelined()) {
            for (byte[] key : keys) {
                read(key, ByteArrayCodec.INSTANCE, RedisCommands.GET, key);
            }
            return null;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (byte[] key: keys) {
            es.readAsync(key, ByteArrayCodec.INSTANCE, RedisCommands.GET, key);
        }
        BatchResult<byte[]> r = (BatchResult<byte[]>) es.execute();
        return r.getResponses();
    }

    @Override
    public Boolean mSet(Map<byte[], byte[]> tuple) {
        if (isQueueing() || isPipelined()) {
            for (Entry<byte[], byte[]> entry: tuple.entrySet()) {
                write(entry.getKey(), StringCodec.INSTANCE, RedisCommands.SET, entry.getKey(), entry.getValue());
            }
            return true;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (Entry<byte[], byte[]> entry: tuple.entrySet()) {
            es.writeAsync(entry.getKey(), StringCodec.INSTANCE, RedisCommands.SET, entry.getKey(), entry.getValue());
        }
        es.execute();
        return true;
    }

    private boolean isCrossSlot(byte[]... keys) {
        if (isQueueing() || isPipelined()) {
            return false;
        }
        return !ClusterSlotHashUtil.isSameSlotForAllKeys(keys);
    }

    private long sumPerKey(RedisCommand<Long> command, byte[]... keys) {
        CommandBatchService es = new CommandBatchService(executorService);
        for (byte[] key : keys) {
            es.writeAsync(key, StringCodec.INSTANCE, command, key);
        }
        BatchResult<?> result = syncFuture(es.executeAsync());
        long sum = 0;
        for (Object count : result.getResponses()) {
            sum += (Long) count;
        }
        return sum;
    }

    @Override
    public Long exists(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.exists(keys);
        }
        return sumPerKey(RedisCommands.EXISTS_LONG, keys);
    }

    @Override
    public Long touch(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.touch(keys);
        }
        return sumPerKey(RedisCommands.TOUCH_LONG, keys);
    }

    @Override
    public Long unlink(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.unlink(keys);
        }
        return sumPerKey(RedisCommands.UNLINK, keys);
    }

    @Override
    public Boolean mSetNX(Map<byte[], byte[]> tuple) {
        byte[][] keys = tuple.keySet().toArray(new byte[0][]);
        if (!isCrossSlot(keys)) {
            return super.mSetNX(tuple);
        }
        if (sumPerKey(RedisCommands.EXISTS_LONG, keys) > 0) {
            return false;
        }

        CommandBatchService es = new CommandBatchService(executorService);
        for (Entry<byte[], byte[]> entry : tuple.entrySet()) {
            es.writeAsync(entry.getKey(), StringCodec.INSTANCE, RedisCommands.SET_BOOLEAN, entry.getKey(), entry.getValue(), "NX");
        }
        BatchResult<?> result = syncFuture(es.executeAsync());
        return !result.getResponses().contains(false);
    }

    @Override
    public Long sort(byte[] key, SortParameters sortParams, byte[] storeKey) {
        if (!isCrossSlot(key, storeKey)) {
            return super.sort(key, sortParams, storeKey);
        }

        List<byte[]> sorted = write(key, ByteArrayCodec.INSTANCE, RedisCommands.SORT_LIST, sortArgs(key, sortParams).toArray());
        del(storeKey);
        if (!sorted.isEmpty()) {
            rPush(storeKey, sorted.toArray(new byte[0][]));
        }
        return (long) sorted.size();
    }

    private List<Set<ByteBuffer>> members(byte[]... keys) {
        CommandBatchService es = new CommandBatchService(executorService);
        for (byte[] key : keys) {
            es.writeAsync(key, ByteArrayCodec.INSTANCE, RedisCommands.SMEMBERS, key);
        }
        BatchResult<?> result = syncFuture(es.executeAsync());
        List<Set<ByteBuffer>> members = new ArrayList<>();
        for (Object values : result.getResponses()) {
            Set<ByteBuffer> set = new LinkedHashSet<>();
            for (Object value : (Set<?>) values) {
                set.add(ByteBuffer.wrap((byte[]) value));
            }
            members.add(set);
        }
        return members;
    }

    private Long store(byte[] destKey, Set<byte[]> members) {
        del(destKey);
        if (!members.isEmpty()) {
            sAdd(destKey, members.toArray(new byte[0][]));
        }
        return (long) members.size();
    }

    @Override
    public Set<byte[]> sInter(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.sInter(keys);
        }

        List<Set<ByteBuffer>> members = members(keys);
        Set<ByteBuffer> result = members.get(0);
        for (Set<ByteBuffer> set : members.subList(1, members.size())) {
            result.retainAll(set);
        }
        return result.stream().map(ByteBuffer::array).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Long sInterCard(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.sInterCard(keys);
        }
        return (long) sInter(keys).size();
    }

    @Override
    public Long sInterStore(byte[] destKey, byte[]... keys) {
        if (!isCrossSlot(ByteUtils.mergeArrays(destKey, keys))) {
            return super.sInterStore(destKey, keys);
        }
        return store(destKey, sInter(keys));
    }

    @Override
    public Set<byte[]> sUnion(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.sUnion(keys);
        }

        Set<ByteBuffer> result = new LinkedHashSet<>();
        for (Set<ByteBuffer> set : members(keys)) {
            result.addAll(set);
        }
        return result.stream().map(ByteBuffer::array).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Long sUnionStore(byte[] destKey, byte[]... keys) {
        if (!isCrossSlot(ByteUtils.mergeArrays(destKey, keys))) {
            return super.sUnionStore(destKey, keys);
        }
        return store(destKey, sUnion(keys));
    }

    @Override
    public Set<byte[]> sDiff(byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.sDiff(keys);
        }

        List<Set<ByteBuffer>> members = members(keys);
        Set<ByteBuffer> result = members.get(0);
        for (Set<ByteBuffer> set : members.subList(1, members.size())) {
            result.removeAll(set);
        }
        return result.stream().map(ByteBuffer::array).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Long sDiffStore(byte[] destKey, byte[]... keys) {
        if (!isCrossSlot(ByteUtils.mergeArrays(destKey, keys))) {
            return super.sDiffStore(destKey, keys);
        }
        return store(destKey, sDiff(keys));
    }

    @Override
    public Boolean sMove(byte[] srcKey, byte[] destKey, byte[] value) {
        if (!isCrossSlot(srcKey, destKey)) {
            return super.sMove(srcKey, destKey, value);
        }
        if (sRem(srcKey, value) == 0) {
            return false;
        }
        sAdd(destKey, value);
        return true;
    }

    @Override
    public byte[] rPopLPush(byte[] srcKey, byte[] dstKey) {
        if (!isCrossSlot(srcKey, dstKey)) {
            return super.rPopLPush(srcKey, dstKey);
        }

        byte[] value = rPop(srcKey);
        if (value != null) {
            lPush(dstKey, value);
        }
        return value;
    }

    @Override
    public byte[] bRPopLPush(int timeout, byte[] srcKey, byte[] dstKey) {
        if (!isCrossSlot(srcKey, dstKey)) {
            return super.bRPopLPush(timeout, srcKey, dstKey);
        }

        List<byte[]> popped = bRPop(timeout, srcKey);
        if (popped == null || popped.isEmpty()) {
            return null;
        }
        lPush(dstKey, popped.get(1));
        return popped.get(1);
    }

    private List<byte[]> popFromAny(RedisCommand<?> pop, RedisCommand<?> blockingPop, int timeout, byte[]... keys) {
        Assert.isTrue(timeout >= 0, "Timeout must not be negative!");

        for (byte[] key : keys) {
            byte[] value = write(key, ByteArrayCodec.INSTANCE, pop, key);
            if (value != null) {
                return Arrays.asList(key, value);
            }
        }

        long attempts = 0;
        while (true) {
            for (byte[] key : keys) {
                List<byte[]> popped = write(key, ByteArrayCodec.INSTANCE, blockingPop, key, 1);
                attempts++;
                if ((popped != null && !popped.isEmpty()) || attempts == timeout) {
                    return popped;
                }
            }
        }
    }

    @Override
    public List<byte[]> bLPop(int timeout, byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.bLPop(timeout, keys);
        }
        return popFromAny(RedisCommands.LPOP, RedisCommands.BLPOP, timeout, keys);
    }

    @Override
    public List<byte[]> bRPop(int timeout, byte[]... keys) {
        if (!isCrossSlot(keys)) {
            return super.bRPop(timeout, keys);
        }
        return popFromAny(RedisCommands.RPOP, RedisCommands.BRPOP, timeout, keys);
    }

    @Override
    public RedisClusterCommands clusterCommands() {
        return this;
    }

    @Override
    public RedisClusterServerCommands serverCommands() {
        return this;
    }

    @Override
    public String ping(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        RFuture<String> f = executorService.readAsync(entry, LongCodec.INSTANCE, RedisCommands.PING);
        return syncFuture(f);
    }


}
