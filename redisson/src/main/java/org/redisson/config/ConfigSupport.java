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
package org.redisson.config;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.netty.channel.EventLoopGroup;
import org.redisson.api.NameMapper;
import org.redisson.api.NatMapper;
import org.redisson.api.RedissonNodeInitializer;
import org.redisson.client.FailedNodeDetector;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.cluster.ClusterConnectionManager;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.connection.*;
import org.redisson.connection.balancer.LoadBalancer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ConfigSupport {

    @JsonIgnoreType
    public static class IgnoreMixIn {

    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
    @JsonFilter("classFilter")
    public static class ClassMixIn {

    }

    @JsonIgnoreProperties({"slaveNotUsed"})
    public static class ConfigPropsMixIn {

    }

    @JsonIgnoreProperties({"clusterConfig", "sentinelConfig", "singleConfig"})
    public static class ConfigMixIn {

        @JsonProperty
        SentinelServersConfig sentinelServersConfig;

        @JsonProperty
        MasterSlaveServersConfig masterSlaveServersConfig;

        @JsonProperty
        SingleServerConfig singleServerConfig;

        @JsonProperty
        ClusterServersConfig clusterServersConfig;

        @JsonProperty
        ReplicatedServersConfig replicatedServersConfig;

    }

    protected final ObjectMapper jsonMapper = createMapper(null, null);
    protected final ObjectMapper yamlMapper = createMapper(new YAMLFactory(), null);
    
    private String resolveEnvParams(Readable in) {
        Scanner s = new Scanner(in).useDelimiter("\\A");
        try {
            if (s.hasNext()) {
                return resolveEnvParams(s.next());
            }
            return "";
        } finally {
            s.close();
        }
    }
    
    private String resolveEnvParams(String content) {
        Pattern pattern = Pattern.compile("\\$\\{([\\w\\.]+(:-.+?)?)\\}");
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            String[] parts = m.group(1).split(":-");
            String v = System.getenv(parts[0]);
            v = System.getProperty(parts[0], v);
            if (v != null) {
                content = content.replace(m.group(), v);
            } else if (parts.length == 2) {
                content = content.replace(m.group(), parts[1]);
            }
        }
        return content;
    }
    
    public <T> T fromJSON(String content, Class<T> configType) throws IOException {
        content = resolveEnvParams(content);
        return jsonMapper.readValue(content, configType);
    }

    public <T> T fromJSON(File file, Class<T> configType) throws IOException {
        return fromJSON(file, configType, null);
    }
    
    public <T> T fromJSON(File file, Class<T> configType, ClassLoader classLoader) throws IOException {
        ObjectMapper jsonMapper = createMapper(null, classLoader);
        String content = resolveEnvParams(new FileReader(file));
        return jsonMapper.readValue(content, configType);
    }

    public <T> T fromJSON(URL url, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(url.openStream()));
        return jsonMapper.readValue(content, configType);
    }

    public <T> T fromJSON(Reader reader, Class<T> configType) throws IOException {
        String content = resolveEnvParams(reader);
        return jsonMapper.readValue(content, configType);
    }

    public <T> T fromJSON(InputStream inputStream, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(inputStream));
        return jsonMapper.readValue(content, configType);
    }

    public String toJSON(Config config) throws IOException {
        return jsonMapper.writeValueAsString(config);
    }

    public <T> T fromYAML(String content, Class<T> configType) throws IOException {
        content = resolveEnvParams(content);
        return yamlMapper.readValue(content, configType);
    }

    public <T> T fromYAML(File file, Class<T> configType) throws IOException {
        return fromYAML(file, configType, null);
    }
    
    public <T> T fromYAML(File file, Class<T> configType, ClassLoader classLoader) throws IOException {
        ObjectMapper yamlMapper = createMapper(new YAMLFactory(), classLoader);
        String content = resolveEnvParams(new FileReader(file));
        return yamlMapper.readValue(content, configType);
    }

    public <T> T fromYAML(URL url, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(url.openStream()));
        return yamlMapper.readValue(content, configType);
    }

    public <T> T fromYAML(Reader reader, Class<T> configType) throws IOException {
        String content = resolveEnvParams(reader);
        return yamlMapper.readValue(content, configType);
    }

    public <T> T fromYAML(InputStream inputStream, Class<T> configType) throws IOException {
        String content = resolveEnvParams(new InputStreamReader(inputStream));
        return yamlMapper.readValue(content, configType);
    }

    public String toYAML(Config config) throws IOException {
        return yamlMapper.writeValueAsString(config);
    }

    public static ConnectionManager createConnectionManager(Config configCopy) {
        ConnectionManager cm = null;
        if (configCopy.getMasterSlaveServersConfig() != null) {
            validate(configCopy.getMasterSlaveServersConfig());
            cm = new MasterSlaveConnectionManager(configCopy.getMasterSlaveServersConfig(), configCopy);
        } else if (configCopy.getSingleServerConfig() != null) {
            validate(configCopy.getSingleServerConfig());
            cm = new SingleConnectionManager(configCopy.getSingleServerConfig(), configCopy);
        } else if (configCopy.getSentinelServersConfig() != null) {
            validate(configCopy.getSentinelServersConfig());
            cm = new SentinelConnectionManager(configCopy.getSentinelServersConfig(), configCopy);
        } else if (configCopy.getClusterServersConfig() != null) {
            validate(configCopy.getClusterServersConfig());
            cm = new ClusterConnectionManager(configCopy.getClusterServersConfig(), configCopy);
        } else if (configCopy.getReplicatedServersConfig() != null) {
            validate(configCopy.getReplicatedServersConfig());
            cm = new ReplicatedConnectionManager(configCopy.getReplicatedServersConfig(), configCopy);
        } else if (configCopy.getConnectionManager() != null) {
            cm = configCopy.getConnectionManager();
        }

        if (cm == null) {
            throw new IllegalArgumentException("server(s) address(es) not defined!");
        }
        if (!configCopy.isLazyInitialization()) {
            cm.connect();
        }
        return cm;
    }

    private static void validate(SingleServerConfig config) {
        if (config.getConnectionPoolSize() < config.getConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("connectionPoolSize can't be lower than connectionMinimumIdleSize");
        }
    }
    
    private static void validate(BaseMasterSlaveServersConfig<?> config) {
        if (config.getSlaveConnectionPoolSize() < config.getSlaveConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("slaveConnectionPoolSize can't be lower than slaveConnectionMinimumIdleSize");
        }
        if (config.getMasterConnectionPoolSize() < config.getMasterConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("masterConnectionPoolSize can't be lower than masterConnectionMinimumIdleSize");
        }
        if (config.getSubscriptionConnectionPoolSize() < config.getSubscriptionConnectionMinimumIdleSize()) {
            throw new IllegalArgumentException("slaveSubscriptionConnectionMinimumIdleSize can't be lower than slaveSubscriptionConnectionPoolSize");
        }
    }

    private ObjectMapper createMapper(JsonFactory mapping, ClassLoader classLoader) {
        ObjectMapper mapper = new ObjectMapper(mapping);
        
        mapper.addMixIn(Config.class, ConfigMixIn.class);
        mapper.addMixIn(BaseMasterSlaveServersConfig.class, ConfigPropsMixIn.class);
        mapper.addMixIn(ReferenceCodecProvider.class, ClassMixIn.class);
        mapper.addMixIn(AddressResolverGroupFactory.class, ClassMixIn.class);
        mapper.addMixIn(Codec.class, ClassMixIn.class);
        mapper.addMixIn(RedissonNodeInitializer.class, ClassMixIn.class);
        mapper.addMixIn(LoadBalancer.class, ClassMixIn.class);
        mapper.addMixIn(NatMapper.class, ClassMixIn.class);
        mapper.addMixIn(NameMapper.class, ClassMixIn.class);
        mapper.addMixIn(NettyHook.class, ClassMixIn.class);
        mapper.addMixIn(CredentialsResolver.class, ClassMixIn.class);
        mapper.addMixIn(EventLoopGroup.class, ClassMixIn.class);
        mapper.addMixIn(ConnectionListener.class, ClassMixIn.class);
        mapper.addMixIn(ExecutorService.class, ClassMixIn.class);
        mapper.addMixIn(KeyManagerFactory.class, IgnoreMixIn.class);
        mapper.addMixIn(TrustManagerFactory.class, IgnoreMixIn.class);
        mapper.addMixIn(CommandMapper.class, ClassMixIn.class);
        mapper.addMixIn(FailedNodeDetector.class, ClassMixIn.class);

        FilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter("classFilter", SimpleBeanPropertyFilter.filterOutAllExcept());
        mapper.setFilterProvider(filterProvider);
        mapper.setSerializationInclusion(Include.NON_NULL);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        if (classLoader != null) {
            TypeFactory tf = TypeFactory.defaultInstance()
                    .withClassLoader(classLoader);
            mapper.setTypeFactory(tf);
        }
        
        return mapper;
    }

}
