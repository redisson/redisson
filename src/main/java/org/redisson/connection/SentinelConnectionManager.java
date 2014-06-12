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

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.Config;
import org.redisson.MasterSlaveConnectionConfig;
import org.redisson.SentinelConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class SentinelConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<RedisClient> sentinels = new ArrayList<RedisClient>();

    public SentinelConnectionManager(final SentinelConnectionConfig cfg, Config config) {
        init(cfg, config);
    }

    private void init(final SentinelConnectionConfig cfg, final Config config) {
        init(config);

        final MasterSlaveConnectionConfig c = new MasterSlaveConnectionConfig();
        for (URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = new RedisClient(group, addr.getHost(), addr.getPort());
            RedisAsyncConnection<String, String> connection = client.connectAsync();

            // TODO async
            List<String> master = connection.getMasterAddrByKey(cfg.getMasterName()).awaitUninterruptibly().getNow();
            String masterHost = master.get(0) + ":" + master.get(1);
            c.setMasterAddress(masterHost);
            log.info("master: {}", masterHost);

            // TODO async
            List<Map<String, String>> slaves = connection.slaves(cfg.getMasterName()).awaitUninterruptibly().getNow();
            for (Map<String, String> map : slaves) {
                String ip = map.get("ip");
                String port = map.get("port");
                log.info("slave: {}:{}", ip, port);
                c.addSlaveAddress(ip + ":" + port);
            }
            if (slaves.isEmpty()) {
                log.info("master added as slave");
                c.addSlaveAddress(masterHost);
            }

            client.shutdown();
            break;
        }

        init(c);

        final AtomicReference<String> master = new AtomicReference<String>();
        for (final URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = new RedisClient(group, addr.getHost(), addr.getPort());
            sentinels.add(client);

            RedisPubSubConnection<String, String> pubsub = client.connectPubSub();
//            Future<RedisPubSubConnection<String, String>> pubsubFuture = client.connectAsyncPubSub();
//            pubsubFuture.addListener(new FutureListener<RedisPubSubConnection<String, String>>() {
//                @Override
//                public void operationComplete(Future<RedisPubSubConnection<String, String>> future)
//                        throws Exception {
//                    if (!future.isSuccess()) {
//                        log.error("Can't connect to Sentinel {}:{}", addr.getHost(), addr.getPort());
//                        return;
//                    }
//                    RedisPubSubConnection<String, String> pubsub = future.get();
                    pubsub.addListener(new RedisPubSubAdapter<String, String>() {
                        @Override
                        public void subscribed(String channel, long count) {
                            log.info("subscribed to channel: {} from Sentinel {}:{}", channel, addr.getHost(), addr.getPort());
                        }

                        @Override
                        public void message(String channel, String msg) {
                            String[] parts = msg.split(" ");

                            if (parts.length > 3) {
                                if (cfg.getMasterName().equals(parts[0])) {
                                    String ip = parts[3];
                                    String port = parts[4];

                                    String current = master.get();
                                    String newMaster = ip + ":" + port;
                                    if (!newMaster.equals(current)
                                            && master.compareAndSet(current, newMaster)) {
                                        log.debug("changing master to {}:{}", ip, port);
                                        changeMaster(ip, Integer.valueOf(port));
                                    }
                                }
                            } else {
                                log.error("Invalid message: {} from Sentinel {}:{}", msg, addr.getHost(), addr.getPort());
                            }
                        }
                    });
                    pubsub.subscribe("+switch-master");
//                }
//            });
        }
    }

    @Override
    public void shutdown() {
        for (RedisClient sentinel : sentinels) {
            sentinel.shutdown();
        }

        super.shutdown();
    }
}

