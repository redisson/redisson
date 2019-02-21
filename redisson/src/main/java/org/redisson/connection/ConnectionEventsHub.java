/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionEventsHub {

    public enum Status {CONNECTED, DISCONNECTED};

    private final ConcurrentMap<InetSocketAddress, Status> maps = new ConcurrentHashMap<>();
    private final Map<Integer, ConnectionListener> listenersMap = new ConcurrentHashMap<>();

    public int addListener(ConnectionListener listener) {
        int id = System.identityHashCode(listener);
        listenersMap.put(id, listener);
        return id;
    }

    public void removeListener(int listenerId) {
        listenersMap.remove(listenerId);
    }

    public void fireConnect(InetSocketAddress addr) {
        if (maps.get(addr) == Status.CONNECTED) {
            return;
        }

        if (maps.putIfAbsent(addr, Status.CONNECTED) == null
                || maps.replace(addr, Status.DISCONNECTED, Status.CONNECTED)) {
            for (ConnectionListener listener : listenersMap.values()) {
                listener.onConnect(addr);
            }
        }
    }

    public void fireDisconnect(InetSocketAddress addr) {
        if (addr == null || maps.get(addr) == Status.DISCONNECTED) {
            return;
        }

        if (maps.replace(addr, Status.CONNECTED, Status.DISCONNECTED)) {
            for (ConnectionListener listener : listenersMap.values()) {
                listener.onDisconnect(addr);
            }
        }
    }


}
