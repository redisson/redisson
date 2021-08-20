/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.micronaut;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import org.redisson.config.*;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ConfigurationProperties("redisson")
@Requires(missingBeans = Config.class)
@Requires(property = "redisson")
public class RedissonConfiguration extends Config {

    public RedissonConfiguration() {
    }

    @Override
    public SingleServerConfig getSingleServerConfig() {
        if (isNotDefined()) {
            return useSingleServer();
        }
        return super.getSingleServerConfig();
    }

    @Override
    @ConfigurationBuilder("singleServerConfig")
    protected void setSingleServerConfig(SingleServerConfig singleConnectionConfig) {
        super.setSingleServerConfig(singleConnectionConfig);
    }

    @Override
    public ClusterServersConfig getClusterServersConfig() {
        if (isNotDefined()) {
            return useClusterServers();
        }
        return super.getClusterServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "clusterServersConfig", includes = {"nodeAddresses"})
    protected void setClusterServersConfig(ClusterServersConfig clusterServersConfig) {
        super.setClusterServersConfig(clusterServersConfig);
    }

    private boolean isNotDefined() {
        return super.getSingleServerConfig() == null
                && super.getClusterServersConfig() == null
                && super.getReplicatedServersConfig() == null
                && super.getSentinelServersConfig() == null
                && super.getMasterSlaveServersConfig() == null;
    }

    @Override
    public ReplicatedServersConfig getReplicatedServersConfig() {
        if (isNotDefined()) {
            return useReplicatedServers();
        }
        return super.getReplicatedServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "replicatedServersConfig", includes = {"nodeAddresses"})
    protected void setReplicatedServersConfig(ReplicatedServersConfig replicatedServersConfig) {
        super.setReplicatedServersConfig(replicatedServersConfig);
    }

    @Override
    public SentinelServersConfig getSentinelServersConfig() {
        if (isNotDefined()) {
            return useSentinelServers();
        }
        return super.getSentinelServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "sentinelServersConfig", includes = {"sentinelAddresses"})
    protected void setSentinelServersConfig(SentinelServersConfig sentinelConnectionConfig) {
        super.setSentinelServersConfig(sentinelConnectionConfig);
    }

    @Override
    public MasterSlaveServersConfig getMasterSlaveServersConfig() {
        if (isNotDefined()) {
            return useMasterSlaveServers();
        }
        return super.getMasterSlaveServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "masterSlaveServersConfig", includes = {"slaveAddresses"})
    protected void setMasterSlaveServersConfig(MasterSlaveServersConfig masterSlaveConnectionConfig) {
        super.setMasterSlaveServersConfig(masterSlaveConnectionConfig);
    }
}
