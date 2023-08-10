/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.RedissonClient;
import org.redisson.spring.factory.RLockFactory;
import org.redisson.spring.processor.RMutexLockAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 985492783@qq.com
 * @description RedissonAspectConfiguration
 * @date 2023/8/9 15:56
 */
@Configuration
public class RedissonAspectConfiguration {

    @Bean
    public RMutexLockAspect lockAspect(RLockFactory factory) {
        return new RMutexLockAspect(factory);
    }

    @Bean
    public RLockFactory getLockFactory(RedissonClient client) {
        return new RLockFactory(client);
    }
}
