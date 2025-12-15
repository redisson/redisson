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
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
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
 * Spring configuration used with Spring Boot 4.0+
 *
 * @author Nikita Koksharov
 *
 */
@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass({Redisson.class, RedisOperations.class, DataRedisAutoConfiguration.class})
@EnableConfigurationProperties({RedissonProperties.class, DataRedisProperties.class})
public class RedissonAutoConfigurationV4 {

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

    @SuppressWarnings("MethodLength")
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redisson() throws IOException {
        Config config;
        Method clusterMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getCluster");
        Method usernameMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getUsername");
        Method timeoutMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getTimeout");
        Method connectTimeoutMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getConnectTimeout");
        Method clientNameMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getClientName");

        Object timeoutValue = ReflectionUtils.invokeMethod(timeoutMethod, redisProperties);
        String prefix = getPrefix();

        String username = null;
        int database = redisProperties.getDatabase();
        String password = redisProperties.getPassword();
        boolean isSentinel = false;
        boolean isCluster = false;

        ObjectProvider<DataRedisConnectionDetails> provider = ctx.getBeanProvider(DataRedisConnectionDetails.class);
        DataRedisConnectionDetails b = provider.getIfAvailable();
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
            config = Config.fromYAML(redissonProperties.getConfig());
        } else if (redissonProperties.getFile() != null) {
            InputStream is = getConfigStream();
            config = Config.fromYAML(is);
        } else if (redisProperties.getSentinel() != null || isSentinel) {
            String[] nodes = {};
            String sentinelMaster = null;

            if (redisProperties.getSentinel() != null) {
                Method nodesMethod = ReflectionUtils.findMethod(DataRedisProperties.Sentinel.class, "getNodes");
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

            if (b != null && b.getSentinel() != null) {
                database = b.getSentinel().getDatabase();
                sentinelMaster = b.getSentinel().getMaster();
                nodes = convertNodes(prefix, (List<Object>) (Object) b.getSentinel().getNodes());
                sentinelUsername = b.getSentinel().getUsername();
                sentinelPassword = b.getSentinel().getPassword();
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

            if (b != null && b.getCluster() != null) {
                nodes = convertNodes(prefix, (List<Object>) (Object) b.getCluster().getNodes());
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

            if (b != null && b.getStandalone() != null) {
                database = b.getStandalone().getDatabase();
                singleAddr = prefix + b.getStandalone().getHost() + ":" + b.getStandalone().getPort();
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
        Method getSSLMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getSsl");
        if (getSSLMethod == null) {
            return;
        }

        DataRedisProperties.Ssl ssl = redisProperties.getSsl();
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
        Method isSSLMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "isSsl");
        Method getSSLMethod = ReflectionUtils.findMethod(DataRedisProperties.class, "getSsl");
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
