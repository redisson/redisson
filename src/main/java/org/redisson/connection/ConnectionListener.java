package org.redisson.connection;

import java.net.InetSocketAddress;

public interface ConnectionListener {

    void onConnect(InetSocketAddress addr);

    void onDisconnect(InetSocketAddress addr);

}
