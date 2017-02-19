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

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReplicatedServersConfig;
import org.redisson.misc.RPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * {@link ConnectionManager} for AWS ElastiCache Replication Groups or Azure Redis Cache. By providing all nodes
 * of the replication group to this manager, the role of each node can be polled to determine
 * if a failover has occurred resulting in a new master.
 *
 * @author Nikita Koksharov
 * @author Steve Ungerer
 */
public class ReplicatedConnectionManager extends MasterSlaveConnectionManager {

    private static final String ROLE_KEY = "role";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private AtomicReference<URL> currentMaster = new AtomicReference<URL>();

    private final Map<URL, RedisConnection> nodeConnections = new HashMap<URL, RedisConnection>();

    private ScheduledFuture<?> monitorFuture;

    private enum Role {
        master,
        slave
    }

    public ReplicatedConnectionManager(ReplicatedServersConfig cfg, Config config) {
        super(config);

        this.config = create(cfg);
        initTimer(this.config);

        for (URL addr : cfg.getNodeAddresses()) {
            RFuture<RedisConnection> connectionFuture = connect(cfg, addr);
            connectionFuture.awaitUninterruptibly();
            RedisConnection connection = connectionFuture.getNow();
            if (connection == null) {
                continue;
            }

            Role role = Role.valueOf(connection.sync(RedisCommands.INFO_REPLICATION).get(ROLE_KEY));
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

        scheduleMasterChangeCheck(cfg);
    }

    @Override
    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig res = super.create(cfg);
        res.setDatabase(((ReplicatedServersConfig)cfg).getDatabase());
        return res;
    }
    
    private RFuture<RedisConnection> connect(BaseMasterSlaveServersConfig<?> cfg, final URL addr) {
        RedisConnection connection = nodeConnections.get(addr);
        if (connection != null) {
            return newSucceededFuture(connection);
        }

        RedisClient client = createClient(addr.getHost(), addr.getPort(), cfg.getConnectTimeout(), cfg.getRetryInterval() * cfg.getRetryAttempts());
        final RPromise<RedisConnection> result = newPromise();
        RFuture<RedisConnection> future = client.connectAsync();
        future.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                RedisConnection connection = future.getNow();
                RPromise<RedisConnection> promise = newPromise();
                connectListener.onConnect(promise, connection, null, config);
                promise.addListener(new FutureListener<RedisConnection>() {
                    @Override
                    public void operationComplete(Future<RedisConnection> future) throws Exception {
                        if (!future.isSuccess()) {
                            result.tryFailure(future.cause());
                            return;
                        }

                        RedisConnection connection = future.getNow();
                        if (connection.isActive()) {
                            nodeConnections.put(addr, connection);
                            result.trySuccess(connection);
                        } else {
                            connection.closeAsync();
                            result.tryFailure(new RedisException("Connection to " + connection.getRedisClient().getAddr() + " is not active!"));
                        }
                    }
                });
            }
        });

        return result;
    }

    private void scheduleMasterChangeCheck(final ReplicatedServersConfig cfg) {
        monitorFuture = GlobalEventExecutor.INSTANCE.schedule(new Runnable() {
            @Override
            public void run() {
                final URL master = currentMaster.get();
                log.debug("Current master: {}", master);
                
                final AtomicInteger count = new AtomicInteger(cfg.getNodeAddresses().size());
                for (final URL addr : cfg.getNodeAddresses()) {
                    if (isShuttingDown()) {
                        return;
                    }

                    RFuture<RedisConnection> connectionFuture = connect(cfg, addr);
                    connectionFuture.addListener(new FutureListener<RedisConnection>() {
                        @Override
                        public void operationComplete(Future<RedisConnection> future) throws Exception {
                            if (!future.isSuccess()) {
                                log.error(future.cause().getMessage(), future.cause());
                                if (count.decrementAndGet() == 0) {
                                    scheduleMasterChangeCheck(cfg);
                                }
                                return;
                            }
                            
                            if (isShuttingDown()) {
                                return;
                            }
                            
                            RedisConnection connection = future.getNow();
                            RFuture<Map<String, String>> result = connection.async(RedisCommands.INFO_REPLICATION);
                            result.addListener(new FutureListener<Map<String, String>>() {
                                @Override
                                public void operationComplete(Future<Map<String, String>> future)
                                        throws Exception {
                                    if (!future.isSuccess()) {
                                        log.error(future.cause().getMessage(), future.cause());
                                        if (count.decrementAndGet() == 0) {
                                            scheduleMasterChangeCheck(cfg);
                                        }
                                        return;
                                    }
                                    
                                    Role role = Role.valueOf(future.getNow().get(ROLE_KEY));
                                    if (Role.master.equals(role)) {
                                        if (master.equals(addr)) {
                                            log.debug("Current master {} unchanged", master);
                                        } else if (currentMaster.compareAndSet(master, addr)) {
                                            log.info("Master has changed from {} to {}", master, addr);
                                            changeMaster(singleSlotRange.getStartSlot(), addr.getHost(), addr.getPort());
                                        }
                                    }
                                    
                                    if (count.decrementAndGet() == 0) {
                                        scheduleMasterChangeCheck(cfg);
                                    }
                                }
                            });
                        }
                    });
                }
            }

        }, cfg.getScanInterval(), TimeUnit.MILLISECONDS);
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

