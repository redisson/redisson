package org.redisson.cluster;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.DefaultConnectionListener;
import org.redisson.connection.ConnectionEntry.Mode;

public class ClusterConnectionListener extends DefaultConnectionListener {

    private final boolean readFromSlaves;

    public ClusterConnectionListener(boolean readFromSlaves) {
        this.readFromSlaves = readFromSlaves;
    }

    @Override
    public void onConnect(MasterSlaveServersConfig config, RedisConnection conn, Mode serverMode) throws RedisException {
        super.onConnect(config, conn, serverMode);
        if (serverMode == Mode.SLAVE && readFromSlaves) {
            conn.sync(RedisCommands.READONLY);
        }
    }

}
