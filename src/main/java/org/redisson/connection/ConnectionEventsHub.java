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
