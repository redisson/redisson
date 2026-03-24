package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonUDSContainerTest {

    static final Path TEST_SOURCE = Paths.get("src/test/java/org/redisson/RedissonUDSTest.java")
            .toAbsolutePath();

    @Test
    void test() {
        assertThat(TEST_SOURCE).exists().withFailMessage("Test source not found on host: " + TEST_SOURCE);

        ComposeContainer environment = new ComposeContainer(
                new File("src/test/resources/docker-compose-uds.yml"))
                .withEnv("TEST_SOURCE_PATH", TEST_SOURCE.toString());

        environment.start();

        try {
            Awaitility.await().atMost(Duration.ofMinutes(2))
                    .pollInterval(Duration.ofSeconds(2))
                    .until(() -> hasContainerExited(environment, "test-runner"));

            Optional<ContainerState> container = environment.getContainerByServiceName("test-runner");
            assertThat(container).isPresent();

            String logs = container.get().getLogs();
            System.out.println("=== Test Runner Output ===\n" + logs);

            Long exitCode = container.get()
                    .getCurrentContainerInfo()
                    .getState()
                    .getExitCodeLong();

            assertThat(exitCode)
                    .as("UDS test should pass. Logs:\n%s", logs)
                    .isZero();
        } finally {
            environment.stop();
        }
    }

    private boolean hasContainerExited(ComposeContainer environment, String serviceName) {
        return environment.getContainerByServiceName(serviceName)
                .map(c -> !c.getCurrentContainerInfo().getState().getRunning())
                .orElse(false);
    }
}