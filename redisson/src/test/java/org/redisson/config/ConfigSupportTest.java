package org.redisson.config;

import mockit.Mock;
import mockit.MockUp;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigSupportTest {

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingLiteral(String format) throws IOException {
        mockHostEnv("1.1.1.1");
        SingleServerConfig config = mkConfig("127.0.0.1", format);
        
        assertEquals("redis://127.0.0.1", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingEnv(String format) throws IOException {
        mockHostEnv("1.1.1.1");
        SingleServerConfig config = mkConfig("${REDIS_URI}", format);
        
        assertEquals("redis://1.1.1.1", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingProperty(String format) throws IOException {
        mockHostProperty("1.1.1.1");
        SingleServerConfig config = mkConfig("${REDIS_URI}", format);

        assertEquals("redis://1.1.1.1", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingEnv2(String format) throws IOException {
        mockHostPortEnv("1.1.1.1", "6379");
        SingleServerConfig config = mkConfig("${REDIS_HOST}:${REDIS_PORT}", format);

        assertEquals("redis://1.1.1.1:6379", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingProperty2(String format) throws IOException {
        mockHostPortProperty("1.1.1.1", "6379");
        SingleServerConfig config = mkConfig("${REDIS_HOST}:${REDIS_PORT}", format);

        assertEquals("redis://1.1.1.1:6379", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingEnv_envMissing(String format) throws IOException {
        mockHostEnv(null);
        mockHostProperty(null);
        final SingleServerConfig config = mkConfig("${REDIS_URI}", format);

        assertEquals("redis://${REDIS_URI}", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefault_envPresent(String format) throws IOException {
        mockHostEnv("11.0.0.1");
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}", format);
        
        assertEquals("redis://11.0.0.1", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefault_propertyPresent(String format) throws IOException {
        mockHostProperty("11.0.0.1");
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}", format);

        assertEquals("redis://11.0.0.1", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefault_envPresent2(String format) throws IOException {
        mockHostPortEnv("11.0.0.1", "1234");
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}", format);

        assertEquals("redis://11.0.0.1:1234", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefault_propertyPresent2(String format) throws IOException {
        mockHostPortProperty("11.0.0.1", "1234");
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}", format);

        assertEquals("redis://11.0.0.1:1234", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefault_envMissing(String format) throws IOException {
        mockHostEnv(null);
        mockHostProperty(null);
        SingleServerConfig config = mkConfig("${REDIS_URI:-10.0.0.1}", format);
        
        assertEquals("redis://10.0.0.1", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefault_envMissing2(String format) throws IOException {
        mockHostPortEnv(null, null);
        mockHostPortProperty(null, null);
        SingleServerConfig config = mkConfig("${REDIS_HOST:-127.0.0.1}:${REDIS_PORT:-6379}", format);

        assertEquals("redis://127.0.0.1:6379", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefaultPeriod_propertyPresent2(String format) throws IOException {
        mockHostPortPropertyPeriod("11.0.0.1", "1234");
        SingleServerConfig config = mkConfig("${REDIS.HOST:-127.0.0.1}:${REDIS.PORT:-6379}", format);

        assertEquals("redis://11.0.0.1:1234", config.getAddress());
    }

    @ParameterizedTest
    @MethodSource("formats")
    public void testParsingDefaultPeriod_envMissing(String format) throws IOException {
        mockHostPortProperty(null, null);
        SingleServerConfig config = mkConfig("${REDIS.HOST:-127.0.0.1}:${REDIS.PORT:-6379}", format);

        assertEquals("redis://127.0.0.1:6379", config.getAddress());
    }
    
    private SingleServerConfig mkConfig(String authorityValue, String format) throws IOException {
        switch (format) {
            case "yaml": {
                String config = "singleServerConfig:\n  address: redis://" + authorityValue;
                return new ConfigSupport().fromYAML(config, Config.class).getSingleServerConfig();
            }

            case "toml": {
                String config = "singleServerConfig.address = 'redis://" + authorityValue + "'";
                return new ConfigSupport().fromTOML(config, Config.class).getSingleServerConfig();
            }

            default: {
                throw new UnsupportedEncodingException("format");
            }
        }
    }
    
    private void mockHostEnv(String value) {
        new MockUp<System>() {
            @Mock
            String getenv(String name) {
                return value;
            }
        };
    }

    private void mockHostProperty(String value) {
        new MockUp<System>() {
            @Mock
            String getProperty(String name) {
                return value;
            }
        };
    }
    
    private void mockHostPortEnv(String host, String port) {
        new MockUp<System>() {
            @Mock
            String getenv(String name) {
                switch (name) {
                    case "REDIS_HOST":
                        return host;
                    case "REDIS_PORT":
                        return port;
                    default:
                        return null;
                }
            }
        };
    }

    private void mockHostPortProperty(String host, String port) {
        new MockUp<System>() {
            @Mock
            String getProperty(String name) {
                switch (name) {
                    case "REDIS_HOST":
                        return host;
                    case "REDIS_PORT":
                        return port;
                    default:
                        return null;
                }
            }
        };
    }

    private void mockHostPortPropertyPeriod(String host, String port) {
        new MockUp<System>() {
            @Mock
            String getProperty(String name) {
                switch (name) {
                    case "REDIS.HOST":
                        return host;
                    case "REDIS.PORT":
                        return port;
                    default:
                        return null;
                }
            }
        };
    }

    public static Iterable<Object[]> formats() {
        return Arrays.asList(new Object[][] {
          {"yaml"},
          {"toml"}
        });
    }

}
