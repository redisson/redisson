package org.redisson.quarkus.client.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusRedissonClientResourceTest {

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

//    @Test
//    public void testExecuteTask() {
//        given()
//                .when().get("/quarkus-redisson-client/executeTask")
//                .then()
//                .statusCode(200)
//                .body(is("hello"));
//    }

}
