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
package org.redisson.spring.starter;

import org.junit.jupiter.api.Test;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonPropertiesTest {

    @Test
    public void testReadModeAndSubscriptionModeAccessors() {
        RedissonProperties properties = new RedissonProperties();
        assertThat(properties.getReadMode()).isNull();
        assertThat(properties.getSubscriptionMode()).isNull();

        properties.setReadMode(ReadMode.MASTER);
        properties.setSubscriptionMode(SubscriptionMode.MASTER);

        assertThat(properties.getReadMode()).isEqualTo(ReadMode.MASTER);
        assertThat(properties.getSubscriptionMode()).isEqualTo(SubscriptionMode.MASTER);

        properties.setReadMode(ReadMode.MASTER_SLAVE);
        properties.setSubscriptionMode(SubscriptionMode.SLAVE);

        assertThat(properties.getReadMode()).isEqualTo(ReadMode.MASTER_SLAVE);
        assertThat(properties.getSubscriptionMode()).isEqualTo(SubscriptionMode.SLAVE);
    }

    @Test
    public void testSpringPropertyBinding() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.redis.redisson.read-mode", "MASTER");
        environment.setProperty("spring.redis.redisson.subscription-mode", "MASTER");

        RedissonProperties properties = Binder.get(environment)
                .bind("spring.redis.redisson", RedissonProperties.class)
                .get();

        assertThat(properties.getReadMode()).isEqualTo(ReadMode.MASTER);
        assertThat(properties.getSubscriptionMode()).isEqualTo(SubscriptionMode.MASTER);
    }

}
