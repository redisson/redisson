package org.redisson.quarkus.client.it;

import org.redisson.connection.ConnectionListener;

import java.net.InetSocketAddress;

/**
 * @author Nikita Koksharov
 */
public class RemoteServiceImpl implements RemService {


    @Override
    public String executeMe() {
        return "executed";
    }
}
