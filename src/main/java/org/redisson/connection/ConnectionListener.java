package org.redisson.connection;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.connection.ConnectionEntry.Mode;

public interface ConnectionListener {

    void onConnect(MasterSlaveServersConfig config, RedisConnection redisConnection, Mode serverMode) throws RedisException;

}
