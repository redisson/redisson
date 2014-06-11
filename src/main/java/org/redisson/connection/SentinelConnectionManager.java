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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.Config;
import org.redisson.MasterSlaveConnectionConfig;
import org.redisson.Redisson;
import org.redisson.SentinelConnectionConfig;
import org.redisson.codec.StringCodec;
import org.redisson.core.MessageListener;
import org.redisson.core.RTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisAsyncConnection;
import com.lambdaworks.redis.RedisClient;

public class SentinelConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<Redisson> sentinels = new ArrayList<Redisson>();

    public SentinelConnectionManager(final SentinelConnectionConfig cfg, Config config) {
        init(cfg, config);
    }

    private void init(final SentinelConnectionConfig cfg, final Config config) {
        final MasterSlaveConnectionConfig c = new MasterSlaveConnectionConfig();
        for (URI addr : cfg.getSentinelAddresses()) {
            RedisClient client = new RedisClient(new NioEventLoopGroup(1), addr.getHost(), addr.getPort());
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

        final AtomicReference<String> master = new AtomicReference<String>();
        for (final URI addr : cfg.getSentinelAddresses()) {
            Config sc = new Config();
            sc.setCodec(new StringCodec());
            sc.useSingleConnection().setAddress(addr.getHost() + ":" + addr.getPort());
            Redisson r = Redisson.create(sc);
            sentinels.add(r);

            final RTopic<String> t = r.getTopic("+switch-master");
            t.addListener(new MessageListener<String>() {
                @Override
                public void onMessage(String msg) {
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
                        log.error("Invalid message: {} from Sentinel({}:{}) on channel {}", msg, addr.getHost(), addr.getPort(), t.getName());
                    }
                }
            });
        }

        init(c, config);
    }

    @Override
    public void shutdown() {
        for (Redisson sentinel : sentinels) {
            sentinel.shutdown();
        }

        super.shutdown();
    }
}

