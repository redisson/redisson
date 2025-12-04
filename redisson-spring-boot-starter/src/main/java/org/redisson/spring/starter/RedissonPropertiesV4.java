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
package org.redisson.spring.starter;

import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReplicatedServersConfig;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.starter.RedissonPropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.function.Consumer;

/**
 * @author livk
 */
@ConfigurationProperties(RedissonPropertiesV4.PREFIX)
public class RedissonPropertiesV4 {

    public static final String PREFIX = "spring.redisson";

    private RedissonConfig config = new RedissonConfig();

    public RedissonConfig getConfig() {
        return config;
    }

    public void setConfig(RedissonConfig config) {
        this.config = config;
    }

    public static RedissonPropertiesV4 load(ConfigurableEnvironment environment) {
        Iterable<ConfigurationPropertySource> sources = ConfigurationPropertySources.get(environment);
        ConfigurableConversionService conversionService = environment.getConversionService();
        PlaceholdersResolver resolver = new PropertySourcesPlaceholdersResolver(environment);
        Consumer<PropertyEditorRegistry> consumer = registry -> new RedissonPropertyEditorRegistrar()
                .registerCustomEditors(registry);
        Binder binder = new Binder(sources, resolver, conversionService, consumer);
        return binder.bind(RedissonPropertiesV4.PREFIX, RedissonPropertiesV4.class).orElse(new RedissonPropertiesV4());
    }

    public static class RedissonConfig extends Config {

        @Override
        @NestedConfigurationProperty
        public void setSingleServerConfig(SingleServerConfig singleServerConfig) {
            super.setSingleServerConfig(singleServerConfig);
        }

        @Override
        @NestedConfigurationProperty
        public void setMasterSlaveServersConfig(MasterSlaveServersConfig masterSlaveServersConfig) {
            super.setMasterSlaveServersConfig(masterSlaveServersConfig);
        }

        @Override
        @NestedConfigurationProperty
        public void setClusterServersConfig(ClusterServersConfig clusterServersConfig) {
            super.setClusterServersConfig(clusterServersConfig);
        }

        @Override
        @NestedConfigurationProperty
        public void setReplicatedServersConfig(ReplicatedServersConfig replicatedServersConfig) {
            super.setReplicatedServersConfig(replicatedServersConfig);
        }

        @Override
        @NestedConfigurationProperty
        public void setSentinelServersConfig(SentinelServersConfig sentinelServersConfig) {
            super.setSentinelServersConfig(sentinelServersConfig);
        }

        @Override
        @NestedConfigurationProperty

        public SingleServerConfig getSingleServerConfig() {
            return super.getSingleServerConfig();
        }

        @Override
        @NestedConfigurationProperty

        public MasterSlaveServersConfig getMasterSlaveServersConfig() {
            return super.getMasterSlaveServersConfig();
        }

        @Override
        @NestedConfigurationProperty

        public ClusterServersConfig getClusterServersConfig() {
            return super.getClusterServersConfig();
        }

        @Override
        @NestedConfigurationProperty

        public ReplicatedServersConfig getReplicatedServersConfig() {
            return super.getReplicatedServersConfig();
        }

        @Override
        @NestedConfigurationProperty

        public SentinelServersConfig getSentinelServersConfig() {
            return super.getSentinelServersConfig();
        }

    }
}
