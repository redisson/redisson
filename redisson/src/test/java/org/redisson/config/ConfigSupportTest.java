package org.redisson.config;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigSupportTest {
    
    @Test
    public void testParsingLiteral() throws IOException {
        mockHostEnv("1.1.1.1", null);
        SingleServerConfig config = mkConfig("127.0.0.1");
        
        assertEquals("redis://127.0.0.1", config.getAddress());
    }
    
    @Test
    public void testParsingEnv() throws IOException {
        mockHostEnv("1.1.1.1", null);
        SingleServerConfig config = mkConfig("${REDIS_HOST}");
        
        assertEquals("redis://1.1.1.1", config.getAddress());
    }

    @Test
    public void testParsingProperty() throws IOException {
        mockHostProperty("1.1.1.1", null);
        SingleServerConfig config = mkConfig("${REDIS_HOST}");

        assertEquals("redis://1.1.1.1", config.getAddress());
    }
    
    @Test
    public void testParsingEnv2() throws IOException {
        mockHostEnv("1.1.1.1", "6379");
        SingleServerConfig config = mkConfig("${REDIS_HOST}:${REDIS_PORT}");

        assertEquals("redis://1.1.1.1:6379", config.getAddress());
    }

    @Test
    public void testParsingProperty2() throws IOException {
        mockHostProperty("1.1.1.1", "6379");
        SingleServerConfig config = mkConfig("${REDIS_HOST}:${REDIS_PORT}");

        assertEquals("redis://1.1.1.1:6379", config.getAddress());
    }

    @Test
    public void testParsingEnv_envMissing() throws IOException {
        final SingleServerConfig config = mkConfig("${REDIS_URI}");

        assertEquals("redis://${REDIS_URI}", config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envPresent() throws IOException {
        mockHostEnv("11.0.0.1", null);
        SingleServerConfig config = mkConfig("${REDIS_HOST:-10.0.0.1}");
        
        assertEquals("redis://11.0.0.1", config.getAddress());
    }

    @Test
    public void testParsingDefault_propertyPresent() throws IOException {
        mockHostProperty("11.0.0.1", null);
        SingleServerConfig config = mkConfig("${REDIS_HOST:-10.0.0.1}");

        assertEquals("redis://11.0.0.1", config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envPresent2() throws IOException {
        mockHostEnv("11.0.0.1", "1234");
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}");

        assertEquals("redis://11.0.0.1:1234", config.getAddress());
    }

    @Test
    public void testParsingDefault_propertyPresent2() throws IOException {
        mockHostProperty("11.0.0.1", "1234");
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}");

        assertEquals("redis://11.0.0.1:1234", config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envMissing() throws IOException {
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}");
        
        assertEquals("redis://10.0.0.1", config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envMissing2() throws IOException {
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}");

        assertEquals("redis://127.0.0.1:6379", config.getAddress());
    }

    @Test
    public void testParsingDefaultPeriod_propertyPresent2() throws IOException {
        mockHostProperty("11.0.0.1", "1234");
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}");

        assertEquals("redis://11.0.0.1:1234", config.getAddress());
    }

    @Test
    public void testParsingDefaultPeriod_envMissing() throws IOException {
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}");

        assertEquals("redis://127.0.0.1:6379", config.getAddress());
    }
    
    private SingleServerConfig mkConfig(String authorityValue) throws IOException {
        String config = "singleServerConfig:\n  address: redis://" + authorityValue;
        return new ConfigSupport().fromYAML(config, Config.class).getSingleServerConfig();
    }
    
    private void mockHostEnv(String host, String port) {
        new MockUp<System>() {
            @Mock
            String getenv(Invocation inv, String name) {
                switch (name) {
                    case "REDIS_HOST":
                        return host;
                    case "REDIS_PORT":
                        return port;
                    default:
                        return inv.proceed(name);
                }
            }
        };
    }

    private void mockHostProperty(String host, String port) {
        MockUp<System> m = new MockUp<System>() {
            @Mock
            String getProperty(Invocation inv, String name, String def) {
                switch (name) {
                    case "REDIS_HOST":
                        return host;
                    case "REDIS_PORT":
                        return port;
                    default:
                        return inv.proceed(name, def);
                }
            }
        };
    }

}
