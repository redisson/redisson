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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.CommandMapper;
import org.redisson.config.Config;
import org.redisson.config.CredentialsResolver;
import org.redisson.config.DecorrelatedJitterDelay;
import org.redisson.config.DelayStrategy;
import org.redisson.config.EqualJitterDelay;
import org.redisson.config.FullJitterDelay;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReplicatedServersConfig;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.balancer.LoadBalancer;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.FilterProvider;
import tools.jackson.databind.ser.std.SimpleBeanPropertyFilter;
import tools.jackson.databind.ser.std.SimpleFilterProvider;
import tools.jackson.dataformat.yaml.YAMLMapper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.beans.PropertyEditorSupport;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author livk
 */
final class RedissonPropertyEditorRegistrar implements PropertyEditorRegistrar {

    private static final Map<Class<?>, Class<?>> REDISSON_MIXIN = initMixin();

    private static final ObjectMapper SUPPORT;

    private static Map<Class<?>, Class<?>> initMixin(){
        Map<Class<?>, Class<?>> redissonMixin = new HashMap<>();
        redissonMixin.put(Config.class, ConfigMixIn.class);
        redissonMixin.put(BaseMasterSlaveServersConfig.class, ConfigPropsMixIn.class);
        redissonMixin.put(ReferenceCodecProvider.class, ClassMixIn.class);
        redissonMixin.put(AddressResolverGroupFactory.class, ClassMixIn.class);
        redissonMixin.put(Codec.class, ClassMixIn.class);
        redissonMixin.put(RedissonNodeInitializer.class, ClassMixIn.class);
        redissonMixin.put(LoadBalancer.class, ClassMixIn.class);
        redissonMixin.put(NatMapper.class, ClassMixIn.class);
        redissonMixin.put(NameMapper.class, ClassMixIn.class);
        redissonMixin.put(NettyHook.class, ClassMixIn.class);
        redissonMixin.put(CredentialsResolver.class, ClassMixIn.class);
        redissonMixin.put(EventLoopGroup.class, ClassMixIn.class);
        redissonMixin.put(ConnectionListener.class, ClassMixIn.class);
        redissonMixin.put(ExecutorService.class, ClassMixIn.class);
        redissonMixin.put(KeyManagerFactory.class, IgnoreMixIn.class);
        redissonMixin.put(TrustManagerFactory.class, IgnoreMixIn.class);
        redissonMixin.put(CommandMapper.class, ClassMixIn.class);
        redissonMixin.put(FailedNodeDetector.class, ClassMixIn.class);
        redissonMixin.put(DelayStrategy.class, ClassMixIn.class);
        redissonMixin.put(EqualJitterDelay.class, DelayMixin.class);
        redissonMixin.put(FullJitterDelay.class, DelayMixin.class);
        redissonMixin.put(DecorrelatedJitterDelay.class, DelayMixin.class);
        return redissonMixin;
    }

    static {
        FilterProvider filterProvider = new SimpleFilterProvider().addFilter("classFilter",
                SimpleBeanPropertyFilter.filterOutAllExcept());

        JsonInclude.Value includeValue = JsonInclude.Value.construct(JsonInclude.Include.NON_NULL,
                JsonInclude.Include.NON_NULL);

        YAMLMapper.Builder builder = YAMLMapper.builder()
                .changeDefaultPropertyInclusion(value -> value.withOverrides(includeValue))
                .filterProvider(filterProvider);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Duration.class, new SpringDurationDeserializer());
        builder.addModule(module);
        REDISSON_MIXIN.forEach(builder::addMixIn);

        SUPPORT = builder.build();
    }

    @Override
    public void registerCustomEditors(@NonNull PropertyEditorRegistry registry) {
        for (Class<?> type : REDISSON_MIXIN.keySet()) {
            registry.registerCustomEditor(type, new RedissonTypePropertyEditor(type));
        }
    }

    private static final class RedissonTypePropertyEditor extends PropertyEditorSupport {

        private final Class<?> type;

        public RedissonTypePropertyEditor(Class<?> type) {
            this.type = type;
        }

        @Override
        public void setAsText(String text) {
            setValue(SUPPORT.readValue(text, type));
        }

    }

    @JsonIgnoreType
    private static final class IgnoreMixIn {

    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
    @JsonFilter("classFilter")
    public static final class ClassMixIn {

    }

    @JsonIgnoreProperties({ "slaveNotUsed" })
    private static final class ConfigPropsMixIn {

    }

    private static final class DelayMixin {

        @JsonCreator
        DelayMixin(@JsonProperty("baseDelay") Duration baseDelay, @JsonProperty("maxDelay") Duration maxDelay) {
        }

    }

    @JsonIgnoreProperties({ "clusterConfig", "sentinelConfig", "singleConfig" })
    private static final class ConfigMixIn {

        @JsonProperty
        SentinelServersConfig sentinelServersConfig;

        @JsonProperty
        MasterSlaveServersConfig masterSlaveServersConfig;

        @JsonProperty
        SingleServerConfig singleServerConfig;

        @JsonProperty
        ClusterServersConfig clusterServersConfig;

        @JsonProperty
        ReplicatedServersConfig replicatedServersConfig;

    }

    public static class SpringDurationDeserializer extends ValueDeserializer<Duration> {

        @Override
        public Duration deserialize(JsonParser p, DeserializationContext ctxt) {
            String text = p.getString().trim();
            return DurationFormatterUtils.parse(text, DurationFormat.Style.SIMPLE);
        }

    }

}

