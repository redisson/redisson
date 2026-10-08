/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.spring.starter;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.config.*;
import org.redisson.misc.RedisURI;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring configuration used with Spring Boot 4.0+
 *
 * @author Nikita Koksharov
 *
 */
@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass({Redisson.class, RedisOperations.class, DataRedisAutoConfiguration.class})
@EnableConfigurationProperties({RedissonProperties.class, DataRedisProperties.class})
public class RedissonAutoConfigurationV4 {

    public static final String[] EMPTY = {};

    /**
     * Enables merging of Spring Boot properties into the config defined by
     * <code>spring.redis.redisson.config</code>. Disabled by default.
     */
    private static final String CONFIG_MERGE_PROPERTY = "spring.redis.redisson.merge";

    private static final List<String> SERVER_CONFIGS = Arrays.asList("singleServerConfig", "sentinelServersConfig",
            "clusterServersConfig", "masterSlaveServersConfig", "replicatedServersConfig");

    @Autowired(required = false)
    private List<RedissonAutoConfigurationCustomizer> redissonAutoConfigurationCustomizers;

    @Autowired
    private RedissonProperties redissonProperties;

    @Autowired
    private DataRedisProperties redisProperties;

    @Autowired
    private ApplicationContext ctx;

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
        return new RedissonConnectionFactory(redisson);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean(RedissonReactiveClient.class)
    public RedissonReactiveClient redissonReactive(RedissonClient redisson) {
        return redisson.reactive();
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean(RedissonRxClient.class)
    public RedissonRxClient redissonRxJava(RedissonClient redisson) {
        return redisson.rxJava();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redisson() throws IOException {
        Config config;
        String prefix = getPrefix();

        String username = redisProperties.getUsername();
        int database = redisProperties.getDatabase();
        String password = redisProperties.getPassword();
        String clientName = redisProperties.getClientName();

        boolean isSentinel = false;
        boolean isCluster = false;

        ObjectProvider<DataRedisConnectionDetails> provider = ctx.getBeanProvider(DataRedisConnectionDetails.class);
        DataRedisConnectionDetails connectionDetails = provider.getIfAvailable();
        if (connectionDetails != null) {
            password = connectionDetails.getPassword();
            username = connectionDetails.getUsername();
            isSentinel = connectionDetails.getSentinel() != null;
            isCluster = connectionDetails.getCluster() != null;
        }

        if (redissonProperties.getConfig() != null) {
            config = parseConfig(redissonProperties.getConfig(), prefix, username, password, database, clientName,
                                    connectionDetails, isSentinel, isCluster);
        } else if (redissonProperties.getFile() != null) {
            try (InputStream is = getConfigStream()) {
                config = Config.fromYAML(is);
            }
        } else if (redisProperties.getSentinel() != null || isSentinel) {
            config = buildSentinelConfig(prefix, username, password, database, clientName, connectionDetails);
        } else if (redisProperties.getCluster() != null || isCluster) {
            config = buildClusterConfig(prefix, username, password, clientName, connectionDetails);
        } else {
            config = buildSingleServerConfig(prefix, username, password, database, clientName, connectionDetails);
        }

        if (redissonAutoConfigurationCustomizers != null) {
            for (RedissonAutoConfigurationCustomizer customizer : redissonAutoConfigurationCustomizers) {
                customizer.customize(config);
            }
        }
        return Redisson.create(config);
    }

    /**
     * Redisson-specific settings defined in the YAML config are applied on top of the Redis settings
     * taken from Spring Boot properties, so that the same settings aren't duplicated in both places.
     * Settings explicitly defined in the YAML config take precedence.
     * Merging is applied only when <code>spring.redis.redisson.merge</code> is set to <code>true</code>.
     *
     * @param yaml config in YAML format
     * @return config object
     */
    @SuppressWarnings("unchecked")
    private Config parseConfig(String yaml, String prefix, String username, String password, int database,
                               String clientName, DataRedisConnectionDetails connectionDetails,
                               boolean isSentinel, boolean isCluster) {
        if (!isMergeEnabled()) {
            return Config.fromYAML(yaml);
        }

        ConfigSupport support = new ConfigSupport();
        Map<String, Object> redissonSettings = support.fromYAML(yaml, Map.class);
        if (redissonSettings == null) {
            return Config.fromYAML(yaml);
        }

        Map<String, Object> springSettings = buildSpringSettings(prefix, username, password, database, clientName,
                                                                 connectionDetails, isSentinel, isCluster);

        // a server config declared in the YAML config takes precedence,
        // so the other server configs derived from Spring properties are removed
        if (SERVER_CONFIGS.stream().anyMatch(redissonSettings::containsKey)) {
            springSettings.keySet().removeIf(name -> SERVER_CONFIGS.contains(name)
                    && !redissonSettings.containsKey(name));
        }

        Config result = Config.fromYAML(new Yaml().dump(merge(springSettings, redissonSettings)));
        // any ssl setting declared in the YAML config takes precedence over the Spring SSL bundle
        if (redissonSettings.keySet().stream().noneMatch(name -> name.startsWith("ssl"))) {
            initSSL(result);
        }
        return result;
    }

    private boolean isMergeEnabled() {
        return ctx.getEnvironment().getProperty(CONFIG_MERGE_PROPERTY, Boolean.class, false);
    }

    private Map<String, Object> buildSpringSettings(String prefix, String username, String password, int database,
                                                    String clientName, DataRedisConnectionDetails connectionDetails,
                                                    boolean isSentinel, boolean isCluster) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("username", username);
        settings.put("password", password);

        if (redisProperties.getSentinel() != null || isSentinel) {
            SentinelServersConfig c = buildSentinelConfig(prefix, username, password, database, clientName,
                                                            connectionDetails).useSentinelServers();
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("masterName", c.getMasterName());
            server.put("sentinelAddresses", c.getSentinelAddresses());
            server.put("sentinelUsername", c.getSentinelUsername());
            server.put("sentinelPassword", c.getSentinelPassword());
            server.put("database", c.getDatabase());
            putBaseSettings(server, c);
            settings.put("sentinelServersConfig", server);
        } else if (redisProperties.getCluster() != null || isCluster) {
            ClusterServersConfig c = buildClusterConfig(prefix, username, password, clientName,
                                                        connectionDetails).useClusterServers();
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("nodeAddresses", c.getNodeAddresses());
            putBaseSettings(server, c);
            settings.put("clusterServersConfig", server);
        } else {
            SingleServerConfig c = buildSingleServerConfig(prefix, username, password, database, clientName,
                                                            connectionDetails).useSingleServer();
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("address", c.getAddress());
            server.put("database", c.getDatabase());
            putBaseSettings(server, c);
            settings.put("singleServerConfig", server);
        }
        return settings;
    }

    private void putBaseSettings(Map<String, Object> server, BaseConfig<?> config) {
        server.put("clientName", config.getClientName());
        server.put("connectTimeout", config.getConnectTimeout());
        server.put("timeout", config.getTimeout());
    }

    private Map<String, Object> merge(Map<String, Object> springSettings, Map<String, Object> redissonSettings) {
        Map<String, Object> result = new LinkedHashMap<>(springSettings);
        for (Map.Entry<String, Object> entry : redissonSettings.entrySet()) {
            Object springV = result.get(entry.getKey());
            Object redissonV = entry.getValue();
            if (springV instanceof Map && redissonV instanceof Map) {
                result.put(entry.getKey(), merge(asMap(springV), asMap(redissonV)));
            } else if (redissonV != null) {
                result.put(entry.getKey(), redissonV);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Config buildSentinelConfig(String prefix, String username, String password, int database,
                                       String clientName, DataRedisConnectionDetails connectionDetails) {
        String[] nodes;
        String sentinelMaster;
        String sentinelUsername = null;
        String sentinelPassword = null;

        if (connectionDetails != null && connectionDetails.getSentinel() != null) {
            DataRedisConnectionDetails.Sentinel sentinel = connectionDetails.getSentinel();
            database = sentinel.getDatabase();
            sentinelMaster = sentinel.getMaster();
            nodes = convertNodes(prefix, sentinel.getNodes());
            sentinelUsername = sentinel.getUsername();
            sentinelPassword = sentinel.getPassword();
        } else {
            DataRedisProperties.Sentinel sentinel = redisProperties.getSentinel();
            if (sentinel != null) {
                nodes = convert(prefix, sentinel.getNodes());
                sentinelMaster = sentinel.getMaster();
            } else {
                nodes = EMPTY;
                sentinelMaster = null;
            }
        }

        Config config = new Config()
                .setUsername(username)
                .setPassword(password);

        SentinelServersConfig c = config.useSentinelServers()
                .setMasterName(sentinelMaster)
                .addSentinelAddress(nodes)
                .setSentinelPassword(sentinelPassword)
                .setSentinelUsername(sentinelUsername)
                .setDatabase(database)
                .setClientName(clientName);

        setTimeouts(c);
        initSSL(config);
        return config;
    }

    private Config buildClusterConfig(String prefix, String username, String password,
                                      String clientName, DataRedisConnectionDetails connectionDetails) {
        String[] nodes;

        if (connectionDetails != null && connectionDetails.getCluster() != null) {
            nodes = convertNodes(prefix, connectionDetails.getCluster().getNodes());
        } else {
            nodes = convert(prefix, redisProperties.getCluster().getNodes());
        }

        Config config = new Config()
                .setUsername(username)
                .setPassword(password);

        ClusterServersConfig c = config.useClusterServers()
                .addNodeAddress(nodes)
                .setClientName(clientName);

        setTimeouts(c);
        initSSL(config);
        return config;
    }

    private Config buildSingleServerConfig(String prefix, String username, String password, int database,
                                           String clientName, DataRedisConnectionDetails connectionDetails) {
        String singleAddr;

        if (connectionDetails != null && connectionDetails.getStandalone() != null) {
            DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
            database = standalone.getDatabase();
            singleAddr = prefix + standalone.getHost() + ":" + standalone.getPort();
        } else {
            singleAddr = prefix + redisProperties.getHost() + ":" + redisProperties.getPort();
        }

        Config config = new Config()
                .setUsername(username)
                .setPassword(password);

        SingleServerConfig c = config.useSingleServer()
                .setAddress(singleAddr)
                .setDatabase(database)
                .setClientName(clientName);

        setTimeouts(c);
        initSSL(config);
        return config;
    }

    private void setTimeouts(BaseConfig c) {
        if (redisProperties.getConnectTimeout() != null) {
            c.setConnectTimeout((int) redisProperties.getConnectTimeout().toMillis());
        }
        if (redisProperties.getTimeout() != null) {
            c.setTimeout((int) redisProperties.getTimeout().toMillis());
        }
    }

    private void initSSL(Config config) {
        DataRedisProperties.Ssl ssl = redisProperties.getSsl();
        if (ssl.getBundle() == null) {
            return;
        }

        ObjectProvider<SslBundles> provider = ctx.getBeanProvider(SslBundles.class);
        SslBundles bundles = provider.getIfAvailable();
        if (bundles == null) {
            return;
        }

        SslBundle bundle = bundles.getBundle(ssl.getBundle());
        config.setSslCiphers(bundle.getOptions().getCiphers());
        config.setSslProtocols(bundle.getOptions().getEnabledProtocols());
        config.setSslTrustManagerFactory(bundle.getManagers().getTrustManagerFactory());
        config.setSslKeyManagerFactory(bundle.getManagers().getKeyManagerFactory());
    }

    private String getPrefix() {
        DataRedisProperties.Ssl ssl = redisProperties.getSsl();
        if (ssl.isEnabled()) {
            return RedisURI.REDIS_SSL_PROTOCOL;
        }
        return RedisURI.REDIS_PROTOCOL;
    }

    @SuppressWarnings("IllegalCatch")
    private String[] convertNodes(String prefix, List<?> nodesObject) {
        List<String> nodes = new ArrayList<>(nodesObject.size());
        try {
            // fixes JDK 8 record type compilation error
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            for (Object node : nodesObject) {
                MethodType hostType = MethodType.methodType(String.class);
                MethodHandle hostHandle = lookup.findVirtual(node.getClass(), "host", hostType);
                String host = (String) hostHandle.invoke(node);

                MethodType portType = MethodType.methodType(int.class);
                MethodHandle portHandle = lookup.findVirtual(node.getClass(), "port", portType);
                int port = (int) portHandle.invoke(node);

                nodes.add(prefix + host + ":" + port);
            }
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to convert nodes", e);
        }
        return nodes.toArray(new String[0]);
    }

    private String[] convert(String prefix, List<String> nodesObject) {
        if (nodesObject == null) {
            return EMPTY;
        }
        return nodesObject.stream()
                .map(node -> {
                    if (RedisURI.isValid(node)) {
                        return node;
                    }
                    return prefix + node;
                })
                .toArray(String[]::new);
    }

    private InputStream getConfigStream() throws IOException {
        Resource resource = ctx.getResource(redissonProperties.getFile());
        return resource.getInputStream();
    }
}