package io.quarkus.redisson.client.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Recorder
public class RedissonClientRecorder {

    public void configureRedisson(String config) {
        RedissonClientProducer producer = Arc.container().instance(RedissonClientProducer.class).get();
        producer.setConfig(config);
    }

}
