package org.redisson.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.config.Config;

/**
 * Tests config loading from <code>configPath</code>.
 */
public class RedissonSessionManagerConfigTest {

    private static final String CONFIG_CONTENT =
            "singleServerConfig:\n  address: \"redis://127.0.0.1:6390\"\n";

    @TempDir
    Path tempDir;

    /**
     * Config is loaded from a plain file path.
     */
    @Test
    public void testFileConfig() throws IOException {
        Path file = tempDir.resolve("redisson.yml");
        Files.write(file, CONFIG_CONTENT.getBytes(StandardCharsets.UTF_8));

        RedissonSessionManager manager = new RedissonSessionManager();
        manager.setConfigPath(file.toString());

        Config config = manager.readConfig();

        assertEquals("redis://127.0.0.1:6390", config.useSingleServer().getAddress());
    }

    /**
     * Config is loaded from a file URL.
     */
    @Test
    public void testFileUrlConfig() throws IOException {
        Path file = tempDir.resolve("redisson.yml");
        Files.write(file, CONFIG_CONTENT.getBytes(StandardCharsets.UTF_8));

        RedissonSessionManager manager = new RedissonSessionManager();
        manager.setConfigPath(file.toUri().toString());

        Config config = manager.readConfig();

        assertEquals("redis://127.0.0.1:6390", config.useSingleServer().getAddress());
    }

    /**
     * Config is loaded from a jar URL. Archive URL based paths are used by
     * packaged web applications, including the nested jar URL form produced
     * by Spring Boot 3.2+.
     */
    @Test
    public void testJarUrlConfig() throws IOException {
        Path jarFile = tempDir.resolve("app.jar");
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jarOut.putNextEntry(new JarEntry("redisson.yml"));
            jarOut.write(CONFIG_CONTENT.getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
        }

        RedissonSessionManager manager = new RedissonSessionManager();
        manager.setConfigPath("jar:" + jarFile.toUri().toString() + "!/redisson.yml");

        Config config = manager.readConfig();

        assertEquals("redis://127.0.0.1:6390", config.useSingleServer().getAddress());
    }
}
