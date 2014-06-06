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

import java.util.concurrent.Semaphore;

import org.redisson.Config;
import org.redisson.MasterSlaveConnectionConfig;
import org.redisson.SingleConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleConnectionManager extends MasterSlaveConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Semaphore connections;

    public SingleConnectionManager(SingleConnectionConfig cfg, Config config) {
        MasterSlaveConnectionConfig newconfig = new MasterSlaveConnectionConfig();
        String addr = cfg.getAddress().getHost() + ":" + cfg.getAddress().getPort();
        newconfig.setMasterAddress(addr);
        newconfig.addSlaveAddress(addr);
        init(newconfig, config);

        connections = new Semaphore(cfg.getConnectionPoolSize());
    }

    void acquireMasterConnection() {
        if (!connections.tryAcquire()) {
            log.warn("Master connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            connections.acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Connection acquired, time spended: {} ms", endTime);
        }
    }

    void releaseMasterConnection() {
        connections.release();
    }

    void acquireSlaveConnection() {
        acquireMasterConnection();
    }

    void releaseSlaveConnection() {
        releaseMasterConnection();
    }

}
