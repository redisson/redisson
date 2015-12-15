/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
import java.util.concurrent.ConcurrentMap;

import io.netty.util.internal.PlatformDependent;

public class ConnectionEventsHub {

    public enum Status {CONNECTED, DISCONNECTED};

    private final ConcurrentMap<InetSocketAddress, Status> maps = PlatformDependent.newConcurrentHashMap();

    private final ConnectionListener connectionListener;

    public ConnectionEventsHub(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    public void fireConnect(InetSocketAddress addr) {
        if (connectionListener == null || maps.get(addr) == Status.CONNECTED) {
            return;
        }

        if (maps.putIfAbsent(addr, Status.CONNECTED) == null
                || maps.replace(addr, Status.DISCONNECTED, Status.CONNECTED)) {
            connectionListener.onConnect(addr);
        }
    }

    public void fireDisconnect(InetSocketAddress addr) {
        if (connectionListener == null || maps.get(addr) == Status.DISCONNECTED) {
            return;
        }

        if (maps.replace(addr, Status.CONNECTED, Status.DISCONNECTED)) {
            connectionListener.onDisconnect(addr);
        }
    }


}
