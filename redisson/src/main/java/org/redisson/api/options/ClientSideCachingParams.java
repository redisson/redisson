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
package org.redisson.api.options;

import java.time.Duration;


/**
 *
 * @author Nikita Koksharov
 *
 */
public final class ClientSideCachingParams implements ClientSideCachingOptions {

    private EvictionPolicy evictionPolicy = EvictionPolicy.NONE;
    private int size;
    private Duration ttl = Duration.ZERO;
    private Duration idleTime = Duration.ZERO;

    @Override
    public ClientSideCachingOptions evictionPolicy(EvictionPolicy evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
        return this;
    }

    @Override
    public ClientSideCachingOptions size(int size) {
        this.size = size;
        return this;
    }

    @Override
    public ClientSideCachingOptions timeToLive(Duration ttl) {
        this.ttl = ttl;
        return this;
    }

    @Override
    public ClientSideCachingOptions maxIdle(Duration idleTime) {
        this.idleTime = idleTime;
        return this;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public int getSize() {
        return size;
    }

    public long getTtl() {
        return ttl.toMillis();
    }

    public long getIdleTime() {
        return idleTime.toMillis();
    }
}
