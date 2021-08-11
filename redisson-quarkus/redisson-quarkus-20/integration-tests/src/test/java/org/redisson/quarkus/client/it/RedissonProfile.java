package org.redisson.quarkus.client.it;

import io.quarkus.deployment.IsDockerWorking;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Arrays;
import java.util.List;

public class RedissonProfile implements QuarkusTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        if(canUseTestcontainers()){
            return Arrays.asList(new TestResourceEntry(RedissonResource.class));
        }
        return Arrays.asList();
    }
    public static boolean canUseTestcontainers() {
        return new IsDockerWorking().getAsBoolean();
    }

}
