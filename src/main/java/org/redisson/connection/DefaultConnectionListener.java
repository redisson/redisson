package org.redisson.connection;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionEntry.Mode;

public class DefaultConnectionListener implements ConnectionListener {

    @Override
    public void onConnect(MasterSlaveServersConfig config, RedisConnection conn, Mode serverMode)
            throws RedisException {
        if (config.getPassword() != null) {
            conn.sync(RedisCommands.AUTH, config.getPassword());
        }
        if (config.getDatabase() != 0) {
            conn.sync(RedisCommands.SELECT, config.getDatabase());
        }
        if (config.getClientName() != null) {
            conn.sync(RedisCommands.CLIENT_SETNAME, config.getClientName());
        }
    }

}
