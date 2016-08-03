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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.BaseMasterSlaveServersConfig;
import org.redisson.Config;
import org.redisson.ElasticacheServersConfig;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * {@link ConnectionManager} for AWS ElastiCache Replication Groups. By providing all nodes
 * of the replication group to this manager, the role of each node can be polled to determine
 * if a failover has occurred resulting in a new master.
 *
 * @author Steve Ungerer
 */
public class ElasticacheConnectionManager extends MasterSlaveConnectionManager {

    private static final String ROLE_KEY = "role:";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private AtomicReference<URI> currentMaster = new AtomicReference<URI>();

    private final Map<URI, RedisConnection> nodeConnections = new HashMap<URI, RedisConnection>();

    private ScheduledFuture<?> monitorFuture;

    private enum Role {
        master,
        slave
    }

    public ElasticacheConnectionManager(ElasticacheServersConfig cfg, Config config) {
        super(config);

        this.config = create(cfg);

        for (URI addr : cfg.getNodeAddresses()) {
            RedisConnection connection = connect(cfg, addr);
            if (connection == null) {
                continue;
            }

            Role role = determineRole(connection.sync(RedisCommands.INFO_REPLICATION));
            if (Role.master.equals(role)) {
                if (currentMaster.get() != null) {
                    throw new RedisException("Multiple masters detected");
                }
                currentMaster.set(addr);
                log.info("{} is the master", addr);
                this.config.setMasterAddress(addr);
            } else {
                log.info("{} is a slave", addr);
                this.config.addSlaveAddress(addr);
            }
        }

        if (currentMaster.get() == null) {
            throw new RedisConnectionException("Can't connect to servers!");
        }

        init(this.config);

        monitorRoleChange(cfg);
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(((ElasticacheServersConfig)cfg).getDatabase());
        return res;
    }

    private RedisConnection connect(ElasticacheServersConfig cfg, URI addr) {
        RedisConnection connection = nodeConnections.get(addr);
        if (connection != null) {
            return connection;
        }
        RedisClient client = createClient(addr.getHost(), addr.getPort(), cfg.getConnectTimeout(), cfg.getRetryInterval() * cfg.getRetryAttempts());
        try {
            connection = client.connect();
            Promise<RedisConnection> future = newPromise();
            connectListener.onConnect(future, connection, null, config);
            future.syncUninterruptibly();
            nodeConnections.put(addr, connection);
        } catch (RedisConnectionException e) {
            log.warn(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return connection;
    }

    private void monitorRoleChange(final ElasticacheServersConfig cfg) {
        monitorFuture = GlobalEventExecutor.INSTANCE.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    URI master = currentMaster.get();
                    log.debug("Current master: {}", master);
                    for (URI addr : cfg.getNodeAddresses()) {
                        RedisConnection connection = connect(cfg, addr);
                        String replInfo = connection.sync(RedisCommands.INFO_REPLICATION);
                        log.trace("{} repl info: {}", addr, replInfo);

                        Role role = determineRole(replInfo);
                        log.debug("node {} is {}", addr, role);

                        if (Role.master.equals(role) && master.equals(addr)) {
                            log.debug("Current master {} unchanged", master);
                        } else if (Role.master.equals(role) && !master.equals(addr) && currentMaster.compareAndSet(master, addr)) {
                            log.info("Master has changed from {} to {}", master, addr);
                            changeMaster(singleSlotRange.getStartSlot(), addr.getHost(), addr.getPort());
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }

        }, cfg.getScanInterval(), cfg.getScanInterval(), TimeUnit.MILLISECONDS);
    }

    private Role determineRole(String data) {
        for (String s : data.split("\\r\\n")) {
            if (s.startsWith(ROLE_KEY)) {
                return Role.valueOf(s.substring(ROLE_KEY.length()));
            }
        }
        throw new RedisException("Cannot determine node role from provided 'INFO replication' data");
    }

    @Override
    public void shutdown() {
        monitorFuture.cancel(true);
        super.shutdown();

        for (RedisConnection connection : nodeConnections.values()) {
            connection.getRedisClient().shutdown();
        }
    }
}

