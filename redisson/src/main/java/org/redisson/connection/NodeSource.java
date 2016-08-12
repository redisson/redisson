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
package org.redisson.connection;

import java.net.InetSocketAddress;

public class NodeSource {

    public static final NodeSource ZERO = new NodeSource(0);

    public enum Redirect {MOVED, ASK}

    private final Integer slot;
    private final InetSocketAddress addr;
    private final Redirect redirect;
    private MasterSlaveEntry entry;

    public NodeSource(MasterSlaveEntry entry) {
        this(null, null, null);
        this.entry = entry;
    }

    public NodeSource(MasterSlaveEntry entry, InetSocketAddress addr) {
        this(null, addr, null);
        this.entry = entry;
    }
    
    public NodeSource(Integer slot) {
        this(slot, null, null);
    }

    public NodeSource(Integer slot, InetSocketAddress addr) {
        this(slot, addr, null);
    }

    public NodeSource(Integer slot, InetSocketAddress addr, Redirect redirect) {
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

    public InetSocketAddress getAddr() {
        return addr;
    }

    @Override
    public String toString() {
        return "NodeSource [slot=" + slot + ", addr=" + addr + ", redirect=" + redirect + "]";
    }

}
