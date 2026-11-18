package org.redisson.helidon;

import io.helidon.service.registry.Service;
import org.redisson.api.RedissonClient;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Service.Singleton
class InjectedClients {

    private final RedissonClient defaultClient;
    private final RedissonClient simpleClient;

    @Service.Inject
    InjectedClients(RedissonClient defaultClient, @Service.Named("simple") RedissonClient simpleClient) {
        this.defaultClient = defaultClient;
        this.simpleClient = simpleClient;
    }

    RedissonClient defaultClient() {
        return defaultClient;
    }

    RedissonClient simpleClient() {
        return simpleClient;
    }

}
