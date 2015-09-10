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

import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.SingleServerConfig;

public class SingleConnectionManager extends MasterSlaveConnectionManager {

    public SingleConnectionManager(SingleServerConfig cfg, Config config) {
        MasterSlaveServersConfig newconfig = new MasterSlaveServersConfig();
        String addr = cfg.getAddress().getHost() + ":" + cfg.getAddress().getPort();
        newconfig.setRetryAttempts(cfg.getRetryAttempts());
        newconfig.setRetryInterval(cfg.getRetryInterval());
        newconfig.setTimeout(cfg.getTimeout());
        newconfig.setPingTimeout(cfg.getPingTimeout());
        newconfig.setPassword(cfg.getPassword());
        newconfig.setDatabase(cfg.getDatabase());
        newconfig.setClientName(cfg.getClientName());
        newconfig.setMasterAddress(addr);
        newconfig.setMasterConnectionPoolSize(cfg.getConnectionPoolSize());
        newconfig.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        newconfig.setSlaveSubscriptionConnectionPoolSize(cfg.getSubscriptionConnectionPoolSize());

        init(newconfig, config);
    }

    @Override
    protected void initEntry(MasterSlaveServersConfig config) {
        SingleEntry entry = new SingleEntry(0, MAX_SLOT, this, config);
        entries.put(MAX_SLOT, entry);
    }

}
