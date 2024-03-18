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

@QuarkusTest
@Testcontainers
public class QuarkusRedissonClientResourceTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
                                                            .withFixedExposedPort(6379, 6379);


    @Test
    public void testRemoteService() {
        given()
                .when().get("/quarkus-redisson-client/remoteService")
                .then()
                .statusCode(200)
                .body(is("executed"));
    }

    @Test
    public void testMap() {
        given()
                .when().get("/quarkus-redisson-client/map")
                .then()
                .statusCode(200)
                .body(is("2"));
    }

    @Test
    public void testPingAll() {
        given()
                .when().get("/quarkus-redisson-client/pingAll")
                .then()
                .statusCode(200)
                .body(is("OK"));
    }

    @Test
    public void testBucket() {
        given()
                .when().get("/quarkus-redisson-client/bucket")
                .then()
                .statusCode(200)
                .body(is("world"));
    }

    @Test
    public void testDeleteBucket() {
        given()
                .when().get("/quarkus-redisson-client/delBucket")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

//    @Test
//    public void testExecuteTask() {
//        given()
//                .when().get("/quarkus-redisson-client/executeTask")
//                .then()
//                .statusCode(200)
//                .body(is("hello"));
//    }

}
