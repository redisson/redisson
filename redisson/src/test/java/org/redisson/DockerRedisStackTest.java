package org.redisson;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.NatMapper;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.misc.RedisURI;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Testcontainers
public class DockerRedisStackTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis/redis-stack-server:latest")
                    .withExposedPorts(6379);

    protected static RedissonClient redisson;

    @BeforeAll
    public static void beforeAll() {
        Config config = createConfig();
        redisson = Redisson.create(config);
    }

    protected static Config createConfig() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + REDIS.getFirstMappedPort());
        return config;
    }

    @BeforeEach
    public void beforeEach() {
        redisson.getKeys().flushall();
    }

    @AfterAll
    public static void afterAll() {
        redisson.shutdown();
    }

    protected void withNewCluster(BiConsumer<List<ContainerState>, RedissonClient> callback) {
        LogMessageWaitStrategy wait2 = new LogMessageWaitStrategy().withRegEx(".*Verifying\scluster\sstatus.*");

        DockerComposeContainer environment =
                new DockerComposeContainer(new File("src/test/resources/docker-compose-stack.yml"))
                        .withExposedService("redis-node-1", 6379)
                        .withExposedService("redis-node-2", 6379)
                        .withExposedService("redis-node-3", 6379)
                        .withExposedService("redis-node-4", 6379)
                        .withExposedService("redis-node-5", 6379)
                        .withExposedService("redis-node-6", 6379)
                        .waitingFor("cluster-init", wait2)
//                        .withLogConsumer("redis-node-1", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")))
//                        .withLogConsumer("redis-node-2", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")))
//                        .withLogConsumer("redis-node-3", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")))
//                        .withLogConsumer("redis-node-4", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")))
//                        .withLogConsumer("redis-node-5", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")))
//                        .withLogConsumer("redis-node-6", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")))
                        .withLogConsumer("cluster-init", new Slf4jLogConsumer(LoggerFactory.getLogger("redis")));

        environment.start();

        List<ContainerState> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Optional<ContainerState> cc = environment.getContainerByServiceName("redis-node-" + i);
            nodes.add(cc.get());
        }

        Optional<ContainerState> cc2 = environment.getContainerByServiceName("redis-node-1");
        Ports.Binding[] mp = cc2.get().getContainerInfo().getNetworkSettings()
                .getPorts().getBindings().get(new ExposedPort(cc2.get().getExposedPorts().get(0)));

        Config config = new Config();
        config.useClusterServers()
                .setNatMapper(new NatMapper() {

                    @Override
                    public RedisURI map(RedisURI uri) {
                        for (ContainerState state : nodes) {
                            if (state.getContainerInfo() == null) {
                                continue;
                            }

                            InspectContainerResponse node = state.getContainerInfo();
                            Ports.Binding[] mappedPort = node.getNetworkSettings()
                                    .getPorts().getBindings().get(new ExposedPort(uri.getPort()));

                            Map<String, ContainerNetwork> ss = node.getNetworkSettings().getNetworks();
                            ContainerNetwork s = ss.values().iterator().next();

                            if (mappedPort != null
                                    && s.getIpAddress().equals(uri.getHost())) {
                                return new RedisURI(uri.getScheme(), "127.0.0.1", Integer.valueOf(mappedPort[0].getHostPortSpec()));
                            }
                        }
                        return uri;
                    }
                })
                .addNodeAddress("redis://127.0.0.1:" + mp[0].getHostPortSpec());

        RedissonClient redisson = Redisson.create(config);

        try {
            callback.accept(nodes, redisson);
        } finally {
            redisson.shutdown();
            environment.stop();
        }
    }


}
