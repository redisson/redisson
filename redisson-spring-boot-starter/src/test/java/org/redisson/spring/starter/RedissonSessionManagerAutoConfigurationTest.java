package org.redisson.spring.starter;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = RedissonRestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.redis.redisson.config=classpath:redisson.yaml",
            "spring.session.store-type=redis",
            "spring.session.timeout.seconds=900",
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
