package org.redisson.quarkus.client.it;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.Map;

public class RedissonResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonResource.class.getName());

    private GenericContainer<?> redisContainer;

    public static final Integer parsePort(String hostPortSpec) {
        return Integer.parseInt(hostPortSpec.replaceAll("^tcp:\\/\\/(.+):", ""));
    }

    @Override
    public Map<String, String> start() {
        this.redisContainer = new GenericContainer<>(getRedisImage())
                .withExposedPorts(6379);

        redisContainer.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.redisson.single-server-config.address", "redis://localhost:" + port("redis", redisContainer, 6379));
        return config;

    }

    private String getRedisImage() {
        //https://hub.docker.com/_/redis
        return "redis:6-alpine";
    }

    private Integer port(String name, GenericContainer rabbitMQContainer, int port) {
        Ports.Binding binding = rabbitMQContainer.getContainerInfo().getNetworkSettings().getPorts().getBindings().get(new ExposedPort(port))[0];
        Integer rabbitMqPort = parsePort(binding.getHostPortSpec());

        LOGGER.info("using " + name + " host: " + rabbitMQContainer.getHost() + ":" + rabbitMqPort + " (from binding " + binding.getHostPortSpec() + " )");
        return rabbitMqPort;
    }



    @Override
    public void stop() {
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }
}
