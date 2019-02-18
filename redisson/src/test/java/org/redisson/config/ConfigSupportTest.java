package org.redisson.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigSupportTest {
    
    @Test
    public void testParsingLiteral() throws IOException {
        SingleServerConfig config = mkConfig("127.0.0.1", new HashMap<String, String>() {{
            put("REDIS_URI", "1.1.1.1");
        }});
        
        assertEquals(URI.create("redis://127.0.0.1"), config.getAddress());
    }
    
    @Test
    public void testParsingEnv() throws IOException {
        SingleServerConfig config = mkConfig("${REDIS_URI}", new HashMap<String, String>() {{
            put("REDIS_URI", "1.1.1.1");
        }});
        
        assertEquals(URI.create("redis://1.1.1.1"), config.getAddress());
    }
    
    @Test(expected = InvalidFormatException.class)
    public void testParsingEnv_envMissing() throws IOException {
        mkConfig("${REDIS_URI}", new HashMap<>());
    }
    
    @Test
    public void testParsingDefault_envPresent() throws IOException {
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}", new HashMap<String, String>() {{
            put("REDIS_URI", "11.0.0.1");
        }});
        
        assertEquals(URI.create("redis://11.0.0.1"), config.getAddress());
    }
    
    @Test
    public void testParsingDefault_envMissing() throws IOException {
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}", new HashMap<>());
        
        assertEquals(URI.create("redis://10.0.0.1"), config.getAddress());
    }
    
    private SingleServerConfig mkConfig(String authorityValue, Map<String, String> env) throws IOException {
        String config = "singleServerConfig:\n  address: redis://" + authorityValue;
        return new ConfigSupport(env::get).fromYAML(config, Config.class).getSingleServerConfig();
    }
    
}
