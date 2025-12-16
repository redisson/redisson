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
package org.redisson.quarkus.client.it;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class CachedService {

    static final String CACHE1 = "cache1";
    static final String CACHE2 = "cache2";

    @CacheResult(cacheName = CACHE1)
    public String cache1(String key) {
        return UUID.randomUUID().toString();
    }

    @CacheResult(cacheName = CACHE2)
    public Long cache2(Long val) {
        return ThreadLocalRandom.current().nextLong();
    }
}