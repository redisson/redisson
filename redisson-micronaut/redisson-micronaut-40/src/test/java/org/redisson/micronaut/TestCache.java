package org.redisson.micronaut;

import io.micronaut.cache.annotation.Cacheable;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class TestCache {

    private AtomicLong counter = new AtomicLong(0);

    @Cacheable("my-cache-async")
    public Mono<String> getMyValueSync(final String arg) {
        return Mono.defer(() -> Mono.just(arg + counter.incrementAndGet()));
    }

}
