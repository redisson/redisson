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
public class RedissonSessionManagerAutoConfigurationTest {

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
