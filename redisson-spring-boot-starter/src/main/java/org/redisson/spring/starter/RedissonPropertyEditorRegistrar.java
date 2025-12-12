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

import io.netty.channel.EventLoopGroup;
import org.jspecify.annotations.NonNull;
import org.redisson.api.NameMapper;
import org.redisson.api.NatMapper;
import org.redisson.api.RedissonNodeInitializer;
import org.redisson.client.FailedNodeDetector;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.CommandMapper;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.config.CredentialsResolver;
import org.redisson.config.DecorrelatedJitterDelay;
import org.redisson.config.DelayStrategy;
import org.redisson.config.EqualJitterDelay;
import org.redisson.config.FullJitterDelay;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.balancer.LoadBalancer;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * @author livk
 */
final class RedissonPropertyEditorRegistrar implements PropertyEditorRegistrar {

    private static final Set<Class<?>> REDISSON_TYPE = initType();

    private static Set<Class<?>> initType() {
        Set<Class<?>> redissonType = new HashSet<>();
        redissonType.add(Config.class);
        redissonType.add(BaseMasterSlaveServersConfig.class);
        redissonType.add(ReferenceCodecProvider.class);
        redissonType.add(AddressResolverGroupFactory.class);
        redissonType.add(Codec.class);
        redissonType.add(RedissonNodeInitializer.class);
        redissonType.add(LoadBalancer.class);
        redissonType.add(NatMapper.class);
        redissonType.add(NameMapper.class);
        redissonType.add(NettyHook.class);
        redissonType.add(CredentialsResolver.class);
        redissonType.add(EventLoopGroup.class);
        redissonType.add(ConnectionListener.class);
        redissonType.add(ExecutorService.class);
        redissonType.add(KeyManagerFactory.class);
        redissonType.add(TrustManagerFactory.class);
        redissonType.add(CommandMapper.class);
        redissonType.add(FailedNodeDetector.class);
        redissonType.add(DelayStrategy.class);
        redissonType.add(EqualJitterDelay.class);
        redissonType.add(FullJitterDelay.class);
        redissonType.add(DecorrelatedJitterDelay.class);
        return redissonType;
    }

    private static final ConfigSupport SUPPORT = new ConfigSupport();

    @Override
    public void registerCustomEditors(@NonNull PropertyEditorRegistry registry) {
        for (Class<?> type : REDISSON_TYPE) {
            registry.registerCustomEditor(type, new RedissonTypePropertyEditor(type));
        }
    }

    private static final class RedissonTypePropertyEditor extends PropertyEditorSupport {

        private final Class<?> type;

        RedissonTypePropertyEditor(Class<?> type) {
            this.type = type;
        }

        @Override
        public void setAsText(String text) {
            try {
                setValue(SUPPORT.fromYAML(text, type));
            } catch (IOException e) {
                throw new IllegalArgumentException(text, e);
            }
        }

    }

}

