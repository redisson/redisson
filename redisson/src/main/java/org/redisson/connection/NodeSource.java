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
package org.redisson.connection;

import org.redisson.client.RedisClient;
import org.redisson.misc.RedisURI;

import java.util.Objects;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class NodeSource {

    public enum Redirect {MOVED, ASK, REDIRECT}

    private Integer slot;
    private RedisURI addr;
    private RedisClient redisClient;
    private Redirect redirect;
    private MasterSlaveEntry entry;

    public NodeSource(NodeSource nodeSource, RedisClient redisClient) {
        this.slot = nodeSource.slot;
        this.addr = nodeSource.addr;
        this.redisClient = redisClient;
        this.redirect = nodeSource.getRedirect();
        this.entry = nodeSource.getEntry();
    }

    public NodeSource(MasterSlaveEntry entry) {
        this.entry = entry;
    }

    public NodeSource(Integer slot) {
        this.slot = slot;
    }

    public NodeSource(MasterSlaveEntry entry, RedisClient redisClient) {
        this.entry = entry;
        this.redisClient = redisClient;
    }
    
    public NodeSource(RedisClient redisClient) {
        this.redisClient = redisClient;
    }
    
    public NodeSource(Integer slot, RedisClient redisClient) {
        this.slot = slot;
        this.redisClient = redisClient;
    }
    
    public NodeSource(Integer slot, RedisURI addr, Redirect redirect) {
        this.slot = slot;
        this.addr = addr;
        this.redirect = redirect;
    }

    public MasterSlaveEntry getEntry() {
        return entry;
    }
    
    public Redirect getRedirect() {
        return redirect;
    }

    public Integer getSlot() {
        return slot;
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public RedisURI getAddr() {
        return addr;
    }

    @Override
    public String toString() {
        return "NodeSource [slot=" + slot + ", addr=" + addr + ", redisClient=" + redisClient + ", redirect=" + redirect
                + ", entry=" + entry + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeSource that = (NodeSource) o;
        return Objects.equals(slot, that.slot) && Objects.equals(addr, that.addr) && Objects.equals(redisClient, that.redisClient) && redirect == that.redirect && Objects.equals(entry, that.entry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, addr, redisClient, redirect, entry);
    }
}
