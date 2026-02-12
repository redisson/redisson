/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.config.DefaultNameMapper;
import org.redisson.client.DefaultCredentialsResolver;
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
        
        if (cfg.getUsername() != null) {
            newconfig.setUsername(cfg.getUsername());
        }
        if (cfg.getPassword() != null) {
            newconfig.setPassword(cfg.getPassword());
        }
        if (!(cfg.getNameMapper() instanceof DefaultNameMapper)) {
            newconfig.setNameMapper(cfg.getNameMapper());
        }
        if (!(cfg.getCommandMapper() instanceof DefaultCommandMapper)) {
            newconfig.setCommandMapper(cfg.getCommandMapper());
        }
        if (!(cfg.getCredentialsResolver() instanceof DefaultCredentialsResolver)) {
            newconfig.setCredentialsResolver(cfg.getCredentialsResolver());
        }
        
        if (cfg.getSslVerificationMode() != SslVerificationMode.STRICT) {
            newconfig.setSslVerificationMode(cfg.getSslVerificationMode());
        }
        if (cfg.getSslKeystoreType() != null) {
            newconfig.setSslKeystoreType(cfg.getSslKeystoreType());
        }
        if (cfg.getSslProvider() != SslProvider.JDK) {
            newconfig.setSslProvider(cfg.getSslProvider());
        }
        if (cfg.getSslTruststore() != null) {
            newconfig.setSslTruststore(cfg.getSslTruststore());
        }
        if (cfg.getSslTruststorePassword() != null) {
            newconfig.setSslTruststorePassword(cfg.getSslTruststorePassword());
        }
        if (cfg.getSslKeystore() != null) {
            newconfig.setSslKeystore(cfg.getSslKeystore());
        }
        if (cfg.getSslKeystorePassword() != null) {
            newconfig.setSslKeystorePassword(cfg.getSslKeystorePassword());
        }
        if (cfg.getSslProtocols() != null) {
            newconfig.setSslProtocols(cfg.getSslProtocols());
        }
        if (cfg.getSslCiphers() != null) {
            newconfig.setSslCiphers(cfg.getSslCiphers());
        }
        if (cfg.getSslKeyManagerFactory() != null) {
            newconfig.setSslKeyManagerFactory(cfg.getSslKeyManagerFactory());
        }
        if (cfg.getSslTrustManagerFactory() != null) {
            newconfig.setSslTrustManagerFactory(cfg.getSslTrustManagerFactory());
        }
        
        if (cfg.isKeepAlive()) {
            newconfig.setKeepAlive(cfg.isKeepAlive());
        }
        if (cfg.getTcpKeepAliveCount() != 0) {
            newconfig.setTcpKeepAliveCount(cfg.getTcpKeepAliveCount());
        }
        if (cfg.getTcpKeepAliveIdle() != 0) {
            newconfig.setTcpKeepAliveIdle(cfg.getTcpKeepAliveIdle());
        }
        if (cfg.getTcpKeepAliveInterval() != 0) {
            newconfig.setTcpKeepAliveInterval(cfg.getTcpKeepAliveInterval());
        }
        if (cfg.getTcpUserTimeout() != 0) {
            newconfig.setTcpUserTimeout(cfg.getTcpUserTimeout());
        }
        if (!cfg.isTcpNoDelay()) {
            newconfig.setTcpNoDelay(cfg.isTcpNoDelay());
        }
        
        newconfig.setPingConnectionInterval(cfg.getPingConnectionInterval());
        newconfig.setRetryAttempts(cfg.getRetryAttempts());
        newconfig.setRetryDelay(cfg.getRetryDelay());
        newconfig.setReconnectionDelay(cfg.getReconnectionDelay());
        newconfig.setTimeout(cfg.getTimeout());
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
        newconfig.setSubscriptionTimeout(cfg.getSubscriptionTimeout());
        
        return newconfig;
    }

}
