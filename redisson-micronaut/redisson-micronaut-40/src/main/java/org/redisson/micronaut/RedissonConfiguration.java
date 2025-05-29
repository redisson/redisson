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
package org.redisson.micronaut;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.naming.conventions.StringConvention;
import jakarta.inject.Inject;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.config.*;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ConnectionListener;

import java.util.Collection;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ConfigurationProperties("redisson")
@Requires(missingBeans = Config.class)
@Requires(property = "redisson")
public class RedissonConfiguration extends Config {

    @Inject
    public RedissonConfiguration(PropertySourcePropertyResolver propertyResolver) {
        Collection<String> props = propertyResolver.getProperties("redisson", StringConvention.CAMEL_CASE).keySet();
        for (String prop : props) {
            if (prop.startsWith("clusterServersConfig")) {
                useClusterServers();
                break;
            }
            if (prop.startsWith("singleServerConfig")) {
                useSingleServer();
                break;
            }
            if (prop.startsWith("replicatedServersConfig")) {
                useReplicatedServers();
                break;
            }
            if (prop.startsWith("sentinelServersConfig")) {
                useSentinelServers();
                break;
            }
            if (prop.startsWith("masterSlaveServersConfig")) {
                useMasterSlaveServers();
                break;
            }
        }
        if (props.contains("codec")) {
            setCodec(propertyResolver.getProperty("redisson.codec", String.class).get());
        }
        if (props.contains("addressResolverGroupFactory")) {
            setAddressResolverGroupFactory(propertyResolver.getProperty("redisson.address-resolver-group-factory", String.class).get());
        }
        if (props.contains("connectionListener")) {
            setConnectionListener(propertyResolver.getProperty("redisson.connection-listener", String.class).get());
        }
        if (props.contains("nettyHook")) {
            setNettyHook(propertyResolver.getProperty("redisson.netty-hook", String.class).get());
        }
    }

    @Override
    @ConfigurationBuilder("singleServerConfig")
    public SingleServerConfig getSingleServerConfig() {
        return super.getSingleServerConfig();
    }

    @Override
    @ConfigurationBuilder(value = "clusterServersConfig")
    public ClusterServersConfig getClusterServersConfig() {
        return super.getClusterServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "replicatedServersConfig")
    public ReplicatedServersConfig getReplicatedServersConfig() {
        return super.getReplicatedServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "sentinelServersConfig")
    public SentinelServersConfig getSentinelServersConfig() {
        return super.getSentinelServersConfig();
    }

    @Override
    @ConfigurationBuilder(value = "masterSlaveServersConfig")
    public MasterSlaveServersConfig getMasterSlaveServersConfig() {
        return super.getMasterSlaveServersConfig();
    }

    public Config setCodec(String className) {
        try {
            Codec codec = (Codec) Class.forName(className).getDeclaredConstructor().newInstance();
            return super.setCodec(codec);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Config setNettyHook(String className) {
        try {
            NettyHook nettyHook = (NettyHook) Class.forName(className).getDeclaredConstructor().newInstance();
            return super.setNettyHook(nettyHook);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Config setAddressResolverGroupFactory(String className) {
        try {
            AddressResolverGroupFactory value = (AddressResolverGroupFactory) Class.forName(className).getDeclaredConstructor().newInstance();
            return super.setAddressResolverGroupFactory(value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Config setConnectionListener(String className) {
        try {
            ConnectionListener connectionListener = (ConnectionListener) Class.forName(className).getDeclaredConstructor().newInstance();
            return super.setConnectionListener(connectionListener);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
