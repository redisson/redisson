package org.redisson.config;

import mockit.Mock;
import mockit.MockUp;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class ConfigSupportTest {
    
    @Test
    public void testParsingLiteral() throws IOException {
        mockHostEnv("1.1.1.1");
        SingleServerConfig config = mkConfig("127.0.0.1");
        
        assertEquals("redis://127.0.0.1", config.getAddress());
    }
    
    @Test
    public void testParsingEnv() throws IOException {
        mockHostEnv("1.1.1.1");
        SingleServerConfig config = mkConfig("${REDIS_URI}");
        
        assertEquals("redis://1.1.1.1", config.getAddress());
    }
    
    @Test
    public void testParsingEnv_envMissing() throws IOException {
        mockHostEnv(null);
        final SingleServerConfig config = mkConfig("${REDIS_URI}");

        assertEquals("redis://${REDIS_URI}", config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envPresent() throws IOException {
        mockHostEnv("11.0.0.1");
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}");
        
        assertEquals("redis://11.0.0.1", config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envMissing() throws IOException {
        mockHostEnv(null);
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}");
        
        assertEquals("redis://10.0.0.1", config.getAddress());
    }
    
    private SingleServerConfig mkConfig(String authorityValue) throws IOException {
        String config = "singleServerConfig:\n  address: redis://" + authorityValue;
        return new ConfigSupport().fromYAML(config, Config.class).getSingleServerConfig();
    }
    
    private void mockHostEnv(String value) {
        new MockUp<System>() {
            @Mock
            String getenv(String name) {
                return value;
            }
        };
    }
    
}
