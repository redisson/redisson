package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ConstantDelay;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonLockReplicatedTimeoutTest extends RedisDockerTest {

    @Test
    public void testReplicatedTryLockDoesNotResendAcquireAfterResponseTimeout() throws Exception {
        int port = CONTAINER.getFirstMappedPort();
        String address = "redis://127.0.0.1:" + port;

        Config config = new Config();
        config.setCheckLockSyncedSlaves(false);
        config.useReplicatedServers()
                .setTimeout(400)
                .setRetryAttempts(1)
                .setRetryDelay(new ConstantDelay(Duration.ofMillis(100)))
                .addNodeAddress(address);

        RedissonClient client = Redisson.create(config);
        RLock lock = client.getLock("replicated-timeout-lock");
        try {
            assertThat(lock.tryLock(2, TimeUnit.SECONDS)).isTrue();
            lock.unlock();

            pauseOtherClients(port, 5000);

            try {
                lock.tryLock(1, TimeUnit.SECONDS);
            } catch (RuntimeException ignored) {
                // A response timeout is the single-server outcome. The bug is a second acquire.
            }

            Thread.sleep(5500);
            assertThat(lock.getHoldCount()).isLessThanOrEqualTo(1);
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.forceUnlock();
                }
            } finally {
                client.shutdown();
            }
        }
    }

    private static void pauseOtherClients(int port, int millis) throws Exception {
        String payload = Integer.toString(millis);
        String command = "*3\r\n$6\r\nCLIENT\r\n$5\r\nPAUSE\r\n$" + payload.length() + "\r\n" + payload + "\r\n";
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(command.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buf = new byte[64];
            int read = in.read(buf);
            String reply = read > 0 ? new String(buf, 0, read, StandardCharsets.US_ASCII) : "";
            if (!reply.startsWith("+OK")) {
                throw new IllegalStateException("CLIENT PAUSE failed: " + reply);
            }
        }
    }
}
