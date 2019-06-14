/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.spring.session;

import org.redisson.api.RedissonClient;
import org.redisson.spring.session.RedissonSessionRepository.RedissonSession;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.ReactiveSessionRepository;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ReactiveRedissonSessionRepository implements ReactiveSessionRepository<RedissonSession> {

    private final RedissonSessionRepository repository;
    
    public ReactiveRedissonSessionRepository(RedissonClient redissonClient, ApplicationEventPublisher eventPublisher,
            String keyPrefix) {
        this.repository = new RedissonSessionRepository(redissonClient, eventPublisher, keyPrefix);
    }
    
    public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
        repository.setDefaultMaxInactiveInterval(defaultMaxInactiveInterval);
    }

    @Override
    public Mono<RedissonSession> createSession() {
        return Mono.fromCallable(() -> {
            return repository.createSession();
        });
    }

    @Override
    public Mono<Void> save(RedissonSession session) {
        // session changes are stored in real-time
        return Mono.empty();
    }

    @Override
    public Mono<RedissonSession> findById(String id) {
        return Mono.fromCallable(() -> {
            return repository.findById(id);
        });
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> {
            repository.deleteById(id);
        });
    }

}
