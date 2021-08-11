package org.redisson.quarkus.client.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.NativeImageTest;

@NativeImageTest
@QuarkusTestResource(NativeResource.class)
public class NativeQuarkusRedissonClientResourceIT extends QuarkusRedissonClientResourceTest {
}
