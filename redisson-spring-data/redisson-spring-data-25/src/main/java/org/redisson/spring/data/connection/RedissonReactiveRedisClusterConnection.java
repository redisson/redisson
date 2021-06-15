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
package org.redisson.spring.data.connection;

import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.ObjectDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveRedisClusterConnection extends RedissonReactiveRedisConnection implements ReactiveRedisClusterConnection {

    public RedissonReactiveRedisClusterConnection(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public ReactiveClusterKeyCommands keyCommands() {
        return new RedissonReactiveClusterKeyCommands(executorService);
    }

    @Override
    public ReactiveClusterStringCommands stringCommands() {
        return new RedissonReactiveClusterStringCommands(executorService);
    }

    @Override
    public ReactiveClusterNumberCommands numberCommands() {
        return new RedissonReactiveClusterNumberCommands(executorService);
    }

    @Override
    public ReactiveClusterListCommands listCommands() {
        return new RedissonReactiveClusterListCommands(executorService);
    }

    @Override
    public ReactiveClusterSetCommands setCommands() {
        return new RedissonReactiveClusterSetCommands(executorService);
    }

    @Override
    public ReactiveClusterZSetCommands zSetCommands() {
        return new RedissonReactiveClusterZSetCommands(executorService);
    }

    @Override
    public ReactiveClusterHashCommands hashCommands() {
        return new RedissonReactiveClusterHashCommands(executorService);
    }

    @Override
    public ReactiveClusterGeoCommands geoCommands() {
        return new RedissonReactiveClusterGeoCommands(executorService);
    }

    @Override
    public ReactiveClusterHyperLogLogCommands hyperLogLogCommands() {
        return new RedissonReactiveClusterHyperLogLogCommands(executorService);
    }

    @Override
    public ReactiveClusterServerCommands serverCommands() {
        return new RedissonReactiveClusterServerCommands(executorService);
    }

    @Override
    public ReactiveClusterStreamCommands streamCommands() {
        return new RedissonReactiveClusterStreamCommands(executorService);
    }

    @Override
    public Mono<String> ping(RedisClusterNode node) {
        return execute(node, RedisCommands.PING);
    }

    private static final RedisStrictCommand<List<RedisClusterNode>> CLUSTER_NODES =
                            new RedisStrictCommand<>("CLUSTER", "NODES", new ObjectDecoder(new RedisClusterNodeDecoder()));

    @Override
    public Flux<RedisClusterNode> clusterGetNodes() {
        Mono<List<RedisClusterNode>> result = read(null, StringCodec.INSTANCE, CLUSTER_NODES);
        return result.flatMapMany(e -> Flux.fromIterable(e));
    }

    @Override
    public Flux<RedisClusterNode> clusterGetSlaves(RedisClusterNode redisClusterNode) {
        Flux<RedisClusterNode> nodes = clusterGetNodes();
        Flux<RedisClusterNode> master = nodes.filter(e -> e.getHost().equals(redisClusterNode.getHost()) && e.getPort().equals(redisClusterNode.getPort()));
        return master.flatMap(node -> clusterGetNodes().filter(e -> Objects.equals(e.getMasterId(), node.getMasterId())));
    }

    @Override
    public Mono<Map<RedisClusterNode, Collection<RedisClusterNode>>> clusterGetMasterSlaveMap() {
        Flux<RedisClusterNode> nodes = clusterGetNodes();
        Flux<RedisClusterNode> masters = nodes.filter(e -> e.isMaster());
        return masters.flatMap(master -> Mono.just(master).zipWith(clusterGetNodes()
                                        .filter(e -> Objects.equals(e.getMasterId(), master.getMasterId()))
                                        .collect(Collectors.toSet())))
                      .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    @Override
    public Mono<Integer> clusterGetSlotForKey(ByteBuffer byteBuffer) {
        return read(null, StringCodec.INSTANCE, RedisCommands.KEYSLOT, toByteArray(byteBuffer));
    }

    @Override
    public Mono<RedisClusterNode> clusterGetNodeForSlot(int slot) {
        return clusterGetNodes().filter(n -> n.isMaster() && n.getSlotRange().contains(slot)).next();
    }

    @Override
    public Mono<RedisClusterNode> clusterGetNodeForKey(ByteBuffer byteBuffer) {
        int slot = executorService.getConnectionManager().calcSlot(toByteArray(byteBuffer));
        return clusterGetNodeForSlot(slot);
    }

    @Override
    public Mono<ClusterInfo> clusterGetClusterInfo() {
        Mono<Map<String, String>> mono = read(null, StringCodec.INSTANCE, RedisCommands.CLUSTER_INFO);
        return mono.map(e -> {
            Properties props = new Properties();
            for (Map.Entry<String, String> entry : e.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue());
            }
            return new ClusterInfo(props);
        });
    }

    @Override
    public Mono<Void> clusterAddSlots(RedisClusterNode redisClusterNode, int... ints) {
        List<Integer> params = convert(ints);
        return execute(redisClusterNode, RedisCommands.CLUSTER_ADDSLOTS, params.toArray());
    }

    private List<Integer> convert(int... slots) {
        List<Integer> params = new ArrayList<Integer>();
        for (int slot : slots) {
            params.add(slot);
        }
        return params;
    }

    @Override
    public Mono<Void> clusterAddSlots(RedisClusterNode redisClusterNode, RedisClusterNode.SlotRange slotRange) {
        return clusterAddSlots(redisClusterNode, slotRange.getSlotsArray());
    }

    @Override
    public Mono<Long> clusterCountKeysInSlot(int slot) {
        Mono<RedisClusterNode> node = clusterGetNodeForSlot(slot);
        return node.flatMap(e -> {
            return execute(e, RedisCommands.CLUSTER_COUNTKEYSINSLOT, slot);
        });
    }

    @Override
    public Mono<Void> clusterDeleteSlots(RedisClusterNode redisClusterNode, int... ints) {
        List<Integer> params = convert(ints);
        return execute(redisClusterNode, RedisCommands.CLUSTER_DELSLOTS, params.toArray());
    }

    @Override
    public Mono<Void> clusterDeleteSlotsInRange(RedisClusterNode redisClusterNode, RedisClusterNode.SlotRange slotRange) {
        return clusterDeleteSlots(redisClusterNode, slotRange.getSlotsArray());
    }

    @Override
    public Mono<Void> clusterForget(RedisClusterNode redisClusterNode) {
        return execute(redisClusterNode, RedisCommands.CLUSTER_FORGET, redisClusterNode.getId());
    }

    @Override
    public Mono<Void> clusterMeet(RedisClusterNode redisClusterNode) {
        return execute(redisClusterNode, RedisCommands.CLUSTER_MEET, redisClusterNode.getHost(), redisClusterNode.getPort());
    }

    @Override
    public Mono<Void> clusterSetSlot(RedisClusterNode redisClusterNode, int slot, AddSlots addSlots) {
        return execute(redisClusterNode, RedisCommands.CLUSTER_SETSLOT, slot, addSlots);
    }

    private static final RedisStrictCommand<List<String>> CLUSTER_GETKEYSINSLOT = new RedisStrictCommand<List<String>>("CLUSTER", "GETKEYSINSLOT", new ObjectListReplayDecoder<String>());

    @Override
    public Flux<ByteBuffer> clusterGetKeysInSlot(int slot, int count) {
        Mono<List<byte[]>> f = executorService.reactive(() -> {
            return executorService.readAsync((String) null, ByteArrayCodec.INSTANCE, CLUSTER_GETKEYSINSLOT, slot, count);
        });
        return f.flatMapMany(e -> Flux.fromIterable(e)).map(e -> ByteBuffer.wrap(e));
    }

    @Override
    public Mono<Void> clusterReplicate(RedisClusterNode redisClusterNode, RedisClusterNode slave) {
        return execute(redisClusterNode, RedisCommands.CLUSTER_REPLICATE, slave.getId());
    }
}
