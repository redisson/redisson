/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.config.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SingleConnectionManager extends MasterSlaveConnectionManager {

    SingleConnectionManager(SingleServerConfig cfg, Config configCopy) {
        super(create(cfg), configCopy);
    }

    private static MasterSlaveServersConfig create(SingleServerConfig cfg) {
        MasterSlaveServersConfig newconfig = new MasterSlaveServersConfig();
        
        newconfig.setPingConnectionInterval(cfg.getPingConnectionInterval());
        newconfig.setSslProvider(cfg.getSslProvider());
        newconfig.setSslKeystoreType(cfg.getSslKeystoreType());
        newconfig.setSslTruststore(cfg.getSslTruststore());
        newconfig.setSslTruststorePassword(cfg.getSslTruststorePassword());
        newconfig.setSslKeystore(cfg.getSslKeystore());
        newconfig.setSslKeystorePassword(cfg.getSslKeystorePassword());
        newconfig.setSslProtocols(cfg.getSslProtocols());
        newconfig.setSslCiphers(cfg.getSslCiphers());
        newconfig.setSslKeyManagerFactory(cfg.getSslKeyManagerFactory());
        newconfig.setSslTrustManagerFactory(cfg.getSslTrustManagerFactory());

        newconfig.setRetryAttempts(cfg.getRetryAttempts());
        newconfig.setRetryDelay(cfg.getRetryDelay());
        newconfig.setReconnectionDelay(cfg.getReconnectionDelay());
        newconfig.setTimeout(cfg.getTimeout());
        newconfig.setPassword(cfg.getPassword());
        newconfig.setUsername(cfg.getUsername());
        newconfig.setDatabase(cfg.getDatabase());
        newconfig.setClientName(cfg.getClientName());
        newconfig.setMasterAddress(cfg.getAddress());
        newconfig.setMasterConnectionPoolSize(cfg.getConnectionPoolSize());
        newconfig.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        newconfig.setSubscriptionConnectionPoolSize(cfg.getSubscriptionConnectionPoolSize());
        newconfig.setConnectTimeout(cfg.getConnectTimeout());
        newconfig.setIdleConnectionTimeout(cfg.getIdleConnectionTimeout());
        newconfig.setDnsMonitoringInterval(cfg.getDnsMonitoringInterval());

        newconfig.setMasterConnectionMinimumIdleSize(cfg.getConnectionMinimumIdleSize());
        newconfig.setSubscriptionConnectionMinimumIdleSize(cfg.getSubscriptionConnectionMinimumIdleSize());
        newconfig.setReadMode(ReadMode.MASTER);
        newconfig.setSubscriptionMode(SubscriptionMode.MASTER);
        newconfig.setKeepAlive(cfg.isKeepAlive());
        newconfig.setTcpKeepAliveCount(cfg.getTcpKeepAliveCount());
        newconfig.setTcpKeepAliveIdle(cfg.getTcpKeepAliveIdle());
        newconfig.setTcpKeepAliveInterval(cfg.getTcpKeepAliveInterval());
        newconfig.setTcpUserTimeout(cfg.getTcpUserTimeout());
        newconfig.setTcpNoDelay(cfg.isTcpNoDelay());
        newconfig.setNameMapper(cfg.getNameMapper());
        newconfig.setCredentialsResolver(cfg.getCredentialsResolver());
        newconfig.setCommandMapper(cfg.getCommandMapper());
        newconfig.setSslVerificationMode(cfg.getSslVerificationMode());
        newconfig.setSubscriptionTimeout(cfg.getSubscriptionTimeout());

        return newconfig;
    }

}
