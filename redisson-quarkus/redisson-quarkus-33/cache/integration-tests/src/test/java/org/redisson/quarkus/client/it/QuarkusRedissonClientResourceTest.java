package org.redisson.quarkus.client.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@QuarkusTest
public class QuarkusRedissonClientResourceTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                            .withFixedExposedPort(6379, 6379);

    @Test
    public void testCacheResult() {
        given()
                .when().get("/quarkus-redisson-client/cacheResult")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

}
