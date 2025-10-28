package org.redisson.spring.starter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

@SpringJUnitConfig
@SpringBootTest(
        classes = RedissonRestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.redis.redisson.file=classpath:redisson.yaml",
            "spring.session.store-type=redis",
//            "spring.session.timeout.seconds=900",
        })
@Testcontainers
public class RedissonSessionManagerAutoConfigurationTest {

    @Container
    public static final GenericContainer REDIS = new FixedHostPortGenericContainer("redis:latest")
            .withFixedExposedPort(6379, 6379)
            .withCommand("redis-server", "--requirepass", "123456");


    @LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

    @Test
    public void testApp() {
        List<String> cookies = this.restTemplate.getForEntity("http://localhost:" + port + "/api/set", String.class).getHeaders().get("Set-Cookie");
        Assertions.assertThat(cookies).isNotEmpty();

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.put(HttpHeaders.COOKIE, cookies);
        HttpEntity<Void> request = new HttpEntity<>(requestHeaders);

        ResponseEntity<String> response = restTemplate.exchange("http://localhost:" + port + "/api/get", HttpMethod.GET, request, String.class);
        Assertions.assertThat(response.getBody()).isEqualTo("1");
    }
    
}
