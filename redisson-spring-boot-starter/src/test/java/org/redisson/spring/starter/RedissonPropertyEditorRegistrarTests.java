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

import org.junit.jupiter.api.Test;
import org.redisson.client.DefaultNettyHook;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.DelayStrategy;
import org.redisson.config.FullJitterDelay;
import org.redisson.connection.AddressResolverGroupFactory;
import org.springframework.beans.SimpleTypeConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author livk
 */
class RedissonPropertyEditorRegistrarTests {

    @Test
    void testConverter() {
        SimpleTypeConverter converter = new SimpleTypeConverter();

        RedissonPropertyEditorRegistrar registrar = new RedissonPropertyEditorRegistrar();
        registrar.registerCustomEditors(converter);

        DelayStrategy fullJitterDelay = converter.convertIfNecessary(
                "!<org.redisson.config.FullJitterDelay> {baseDelay: 2h,maxDelay: 2h}", DelayStrategy.class);
        assertThat(fullJitterDelay).isInstanceOf(FullJitterDelay.class);

        Codec codec = converter.convertIfNecessary("!<org.redisson.codec.JsonJacksonCodec> {}", Codec.class);
        assertThat(codec).isNotNull().isInstanceOf(JsonJacksonCodec.class);

        NettyHook hook = converter.convertIfNecessary("!<org.redisson.client.DefaultNettyHook> {}", NettyHook.class);
        assertThat(hook).isNotNull().isInstanceOf(DefaultNettyHook.class);

        AddressResolverGroupFactory factory = converter.convertIfNecessary(
                "!<org.redisson.connection.SequentialDnsAddressResolverFactory> {}", AddressResolverGroupFactory.class);
        assertThat(factory).isNotNull().isInstanceOf(AddressResolverGroupFactory.class);
    }

}
