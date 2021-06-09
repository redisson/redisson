package io.quarkus.redisson.client.runtime;

import io.quarkus.arc.DefaultBean;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ApplicationScoped
public class RedissonClientProducer {

    private String config;
    private RedissonClient redisson;

    @Produces
    @Singleton
    @DefaultBean
    public RedissonClient create() throws IOException {
        if (config != null){
            Config c = Config.fromYAML(config);
            redisson = Redisson.create(c);
        } else {
            redisson = Redisson.create();
        }
        return redisson;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    @PreDestroy
    public void close() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

}
