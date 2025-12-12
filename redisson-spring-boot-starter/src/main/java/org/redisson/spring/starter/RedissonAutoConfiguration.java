/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties.Sentinel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Spring configuration used with Spring Boot 2.6 and lower
 *
 * @author Nikita Koksharov
 * @author Nikos Kakavas (https://github.com/nikakis)
 * @author AnJia (https://anjia0532.github.io/)
 *
 */
@Configuration
@ConditionalOnClass({Redisson.class, RedisOperations.class})
@ConditionalOnMissingClass("org.springframework.boot.autoconfigure.AutoConfiguration")
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties({RedissonProperties.class, RedisProperties.class})
public class RedissonAutoConfiguration {

    @Autowired(required = false)
    private List<RedissonAutoConfigurationCustomizer> redissonAutoConfigurationCustomizers;

    @Autowired
    private RedissonProperties redissonProperties;

    @Autowired
    private RedisProperties redisProperties;

    @Autowired
    private ApplicationContext ctx;

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<Object, Object>();
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

    private boolean hasConnectionDetails() {
        try {
            Class.forName("org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @SuppressWarnings("MethodLength")
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redisson() throws IOException {
        Config config;
        Method clusterMethod = ReflectionUtils.findMethod(RedisProperties.class, "getCluster");
        Method usernameMethod = ReflectionUtils.findMethod(RedisProperties.class, "getUsername");
        Method timeoutMethod = ReflectionUtils.findMethod(RedisProperties.class, "getTimeout");
        Method connectTimeoutMethod = ReflectionUtils.findMethod(RedisProperties.class, "getConnectTimeout");
        Method clientNameMethod = ReflectionUtils.findMethod(RedisProperties.class, "getClientName");

        Object timeoutValue = ReflectionUtils.invokeMethod(timeoutMethod, redisProperties);
        String prefix = getPrefix();

        String username = null;
        int database = redisProperties.getDatabase();
        String password = redisProperties.getPassword();
        boolean isSentinel = false;
        boolean isCluster = false;
        if (hasConnectionDetails()) {
            ObjectProvider<RedisConnectionDetails> provider = ctx.getBeanProvider(RedisConnectionDetails.class);
            RedisConnectionDetails b = provider.getIfAvailable();
            if (b != null) {
                password = b.getPassword();
                username = b.getUsername();

                if (b.getSentinel() != null) {
                    isSentinel = true;
                }
                if (b.getCluster() != null) {
                    isCluster = true;
                }
            }
        }

        Integer timeout = null;
        if (timeoutValue instanceof Duration) {
            timeout = (int) ((Duration) timeoutValue).toMillis();
        } else if (timeoutValue != null){
            timeout = (Integer) timeoutValue;
        }

        Integer connectTimeout = null;
        if (connectTimeoutMethod != null) {
            Object connectTimeoutValue = ReflectionUtils.invokeMethod(connectTimeoutMethod, redisProperties);
            if (connectTimeoutValue != null) {
                connectTimeout = (int) ((Duration) connectTimeoutValue).toMillis();
            }
        } else {
            connectTimeout = timeout;
        }

        String clientName = null;
        if (clientNameMethod != null) {
            clientName = (String) ReflectionUtils.invokeMethod(clientNameMethod, redisProperties);
        }

        if (usernameMethod != null) {
            username = (String) ReflectionUtils.invokeMethod(usernameMethod, redisProperties);
        }

        if (redissonProperties.getConfig() != null) {
            try {
                config = Config.fromYAML(redissonProperties.getConfig());
            } catch (IOException e) {
                try {
                    config = Config.fromJSON(redissonProperties.getConfig());
                } catch (IOException e1) {
                    e1.addSuppressed(e);
                    throw new IllegalArgumentException("Can't parse config", e1);
                }
            }
        } else if (redissonProperties.getFile() != null && !redissonProperties.getFile().isEmpty()) {
            try {
                InputStream is = getConfigStream();
                config = Config.fromYAML(is);
            } catch (IOException e) {
                // trying next format
                try {
                    InputStream is = getConfigStream();
                    config = Config.fromJSON(is);
                } catch (IOException e1) {
                    e1.addSuppressed(e);
                    throw new IllegalArgumentException("Can't parse config", e1);
                }
            }
        } else if (redisProperties.getSentinel() != null || isSentinel) {
            String[] nodes = {};
            String sentinelMaster = null;

            if (redisProperties.getSentinel() != null) {
                Method nodesMethod = ReflectionUtils.findMethod(Sentinel.class, "getNodes");
                Object nodesValue = ReflectionUtils.invokeMethod(nodesMethod, redisProperties.getSentinel());
                if (nodesValue instanceof String) {
                    nodes = convert(prefix, Arrays.asList(((String) nodesValue).split(",")));
                } else {
                    nodes = convert(prefix, (List<String>) nodesValue);
                }
                sentinelMaster = redisProperties.getSentinel().getMaster();
            }


            String sentinelUsername = null;
            String sentinelPassword = null;
            if (hasConnectionDetails()) {
                ObjectProvider<RedisConnectionDetails> provider = ctx.getBeanProvider(RedisConnectionDetails.class);
                RedisConnectionDetails b = provider.getIfAvailable();
                if (b != null && b.getSentinel() != null) {
                    database = b.getSentinel().getDatabase();
                    sentinelMaster = b.getSentinel().getMaster();
                    nodes = convertNodes(prefix, (List<Object>) (Object) b.getSentinel().getNodes());
                    sentinelUsername = b.getSentinel().getUsername();
                    sentinelPassword = b.getSentinel().getPassword();
                }
            }

            config = new Config()
                    .setUsername(username)
                    .setPassword(password);

            SentinelServersConfig c = config.useSentinelServers()
                    .setMasterName(sentinelMaster)
                    .addSentinelAddress(nodes)
                    .setSentinelPassword(sentinelPassword)
                    .setSentinelUsername(sentinelUsername)
                    .setDatabase(database)
                    .setClientName(clientName);
            if (connectTimeout != null) {
                c.setConnectTimeout(connectTimeout);
            }
            if (connectTimeoutMethod != null && timeout != null) {
                c.setTimeout(timeout);
            }
            initSSL(config);
        } else if ((clusterMethod != null && ReflectionUtils.invokeMethod(clusterMethod, redisProperties) != null)
                    || isCluster) {

            String[] nodes = {};
            if (clusterMethod != null && ReflectionUtils.invokeMethod(clusterMethod, redisProperties) != null) {
                Object clusterObject = ReflectionUtils.invokeMethod(clusterMethod, redisProperties);
                Method nodesMethod = ReflectionUtils.findMethod(clusterObject.getClass(), "getNodes");
                List<String> nodesObject = (List) ReflectionUtils.invokeMethod(nodesMethod, clusterObject);

                nodes = convert(prefix, nodesObject);
            }

            if (hasConnectionDetails()) {
                ObjectProvider<RedisConnectionDetails> provider = ctx.getBeanProvider(RedisConnectionDetails.class);
                RedisConnectionDetails b = provider.getIfAvailable();
                if (b != null && b.getCluster() != null) {
                    nodes = convertNodes(prefix, (List<Object>) (Object) b.getCluster().getNodes());
                }
            }

            config = new Config()
                    .setUsername(username)
                    .setPassword(password);
            ClusterServersConfig c = config.useClusterServers()
                    .addNodeAddress(nodes)
                    .setClientName(clientName);
            if (connectTimeout != null) {
                c.setConnectTimeout(connectTimeout);
            }
            if (connectTimeoutMethod != null && timeout != null) {
                c.setTimeout(timeout);
            }
            initSSL(config);
        } else {
            config = new Config()
                    .setUsername(username)
                    .setPassword(password);

            String singleAddr = prefix + redisProperties.getHost() + ":" + redisProperties.getPort();

            if (hasConnectionDetails()) {
                ObjectProvider<RedisConnectionDetails> provider = ctx.getBeanProvider(RedisConnectionDetails.class);
                RedisConnectionDetails b = provider.getIfAvailable();
                if (b != null && b.getStandalone() != null) {
                    database = b.getStandalone().getDatabase();
                    singleAddr = prefix + b.getStandalone().getHost() + ":" + b.getStandalone().getPort();
                }
            }

            SingleServerConfig c = config.useSingleServer()
                    .setAddress(singleAddr)
                    .setDatabase(database)
                    .setClientName(clientName);
            if (connectTimeout != null) {
                c.setConnectTimeout(connectTimeout);
            }
            if (connectTimeoutMethod != null && timeout != null) {
                c.setTimeout(timeout);
            }
            initSSL(config);
        }
        if (redissonAutoConfigurationCustomizers != null) {
            for (RedissonAutoConfigurationCustomizer customizer : redissonAutoConfigurationCustomizers) {
                customizer.customize(config);
            }
        }
        return Redisson.create(config);
    }

    private void initSSL(Config config) {
        Method getSSLMethod = ReflectionUtils.findMethod(RedisProperties.class, "getSsl");
        if (getSSLMethod == null) {
            return;
        }

        RedisProperties.Ssl ssl = redisProperties.getSsl();
        if (ssl.getBundle() == null) {
            return;
        }

        ObjectProvider<SslBundles> provider = ctx.getBeanProvider(SslBundles.class);
        SslBundles bundles = provider.getIfAvailable();
        if (bundles == null) {
            return;
        }
        SslBundle b = bundles.getBundle(ssl.getBundle());
        if (b == null) {
            return;
        }
        config.setSslCiphers(b.getOptions().getCiphers());
        config.setSslProtocols(b.getOptions().getEnabledProtocols());
        config.setSslTrustManagerFactory(b.getManagers().getTrustManagerFactory());
        config.setSslKeyManagerFactory(b.getManagers().getKeyManagerFactory());
    }

    private String getPrefix() {
        String prefix = RedisURI.REDIS_PROTOCOL;
        Method isSSLMethod = ReflectionUtils.findMethod(RedisProperties.class, "isSsl");
        Method getSSLMethod = ReflectionUtils.findMethod(RedisProperties.class, "getSsl");
        if (isSSLMethod != null) {
            if ((Boolean) ReflectionUtils.invokeMethod(isSSLMethod, redisProperties)) {
                prefix = RedisURI.REDIS_SSL_PROTOCOL;
            }
        } else if (getSSLMethod != null) {
            Object ss = ReflectionUtils.invokeMethod(getSSLMethod, redisProperties);
            if (ss != null) {
                Method isEnabledMethod = ReflectionUtils.findMethod(ss.getClass(), "isEnabled");
                Boolean enabled = (Boolean) ReflectionUtils.invokeMethod(isEnabledMethod, ss);
                if (enabled) {
                    prefix = RedisURI.REDIS_SSL_PROTOCOL;
                }
            }
        }
        return prefix;
    }

    private String[] convertNodes(String prefix, List<Object> nodesObject) {
        List<String> nodes = new ArrayList<>(nodesObject.size());
        for (Object node : nodesObject) {
            Field hostField = ReflectionUtils.findField(node.getClass(), "host");
            Field portField = ReflectionUtils.findField(node.getClass(), "port");
            ReflectionUtils.makeAccessible(hostField);
            ReflectionUtils.makeAccessible(portField);
            String host = (String) ReflectionUtils.getField(hostField, node);
            int port = (int) ReflectionUtils.getField(portField, node);
            nodes.add(prefix + host + ":" + port);
        }
        return nodes.toArray(new String[0]);
    }

    private String[] convert(String prefix, List<String> nodesObject) {
        List<String> nodes = new ArrayList<>(nodesObject.size());
        for (String node : nodesObject) {
            if (!RedisURI.isValid(node)) {
                nodes.add(prefix + node);
            } else {
                nodes.add(node);
            }
        }
        return nodes.toArray(new String[0]);
    }

    private InputStream getConfigStream() throws IOException {
        Resource resource = ctx.getResource(redissonProperties.getFile());
        return resource.getInputStream();
    }


}
