package io.quarkus.redisson.client.runtime;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Nikita Koksharov
 *
 */
//@ConfigRoot(name = "redisson", phase = ConfigPhase.RUN_TIME)
public class RedissonConfig {

    /**
     * Redis uri
     */
//    @ConfigItem
    public Optional<String> codec;

    /**
     * Redis cluster config
     */
//    @ConfigItem
    public Optional<Map<String, String>> clusterServersConfig;

}
