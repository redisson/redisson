package org.redisson.quarkus.client.it;

import io.quarkus.deployment.IsDockerWorking;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.*;

/**
 * @author ineednousername https://github.com/ineednousername
 */
public class NativeResource implements QuarkusTestResourceLifecycleManager {

    List<QuarkusTestResourceLifecycleManager> resources = Collections.emptyList();

    public static boolean canUseTestcontainers() {
        return new IsDockerWorking().getAsBoolean();
    }


    @Override
    public Map<String, String> start() {
        Map<String, String> properties = new HashMap<>();
        if (canUseTestcontainers()) {
            resources = Arrays.asList(
                    new RedissonResource()
            );

            resources.stream().map(r -> r.start()).forEach(p -> properties.putAll(p));
        }

        return properties;
    }

    @Override
    public void stop() {
        resources.forEach(r -> r.stop());
    }

    @Override
    public int order() {
        int startedLast = Integer.MAX_VALUE;
        return startedLast;
    }
}
