/**
 * Copyright 2018 Nikita Koksharov
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.ClusterInfo;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisClusterNode.SlotRange;
import org.springframework.data.redis.core.types.RedisClientInfo;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonClusterConnection extends RedissonConnection implements RedisClusterConnection {

    public RedissonClusterConnection(RedissonClient redisson) {
        super(redisson);
    }

    @Override
    public Iterable<RedisClusterNode> clusterGetNodes() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<RedisClusterNode> clusterGetSlaves(RedisClusterNode master) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<RedisClusterNode, Collection<RedisClusterNode>> clusterGetMasterSlaveMap() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Integer clusterGetSlotForKey(byte[] key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RedisClusterNode clusterGetNodeForSlot(int slot) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RedisClusterNode clusterGetNodeForKey(byte[] key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ClusterInfo clusterGetClusterInfo() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void clusterAddSlots(RedisClusterNode node, int... slots) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clusterAddSlots(RedisClusterNode node, SlotRange range) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Long clusterCountKeysInSlot(int slot) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void clusterDeleteSlots(RedisClusterNode node, int... slots) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clusterDeleteSlotsInRange(RedisClusterNode node, SlotRange range) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clusterForget(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clusterMeet(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clusterSetSlot(RedisClusterNode node, int slot, AddSlots mode) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<byte[]> clusterGetKeysInSlot(int slot, Integer count) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void clusterReplicate(RedisClusterNode master, RedisClusterNode slave) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String ping(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void bgReWriteAof(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void bgSave(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Long lastSave(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void save(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Long dbSize(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void flushDb(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void flushAll(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Properties info(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Properties info(RedisClusterNode node, String section) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<byte[]> keys(RedisClusterNode node, byte[] pattern) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public byte[] randomKey(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void shutdown(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public List<String> getConfig(RedisClusterNode node, String pattern) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setConfig(RedisClusterNode node, String param, String value) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void resetConfigStats(RedisClusterNode node) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public Long time(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<RedisClientInfo> getClientList(RedisClusterNode node) {
        // TODO Auto-generated method stub
        return null;
    }

}
