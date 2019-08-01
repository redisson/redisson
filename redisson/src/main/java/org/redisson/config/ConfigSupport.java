/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.redisson.api.RedissonNodeInitializer;
import org.redisson.client.codec.Codec;
import org.redisson.cluster.ClusterConnectionManager;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.ReplicatedConnectionManager;
import org.redisson.connection.SentinelConnectionManager;
import org.redisson.connection.SingleConnectionManager;
import org.redisson.connection.balancer.LoadBalancer;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ConfigSupport {
    
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
    @JsonFilter("classFilter")
    public static class ClassMixIn {

    }

    @JsonIgnoreProperties({"clusterConfig", "sentinelConfig"})
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

    private ObjectMapper jsonMapper = createMapper(null, null);
    private ObjectMapper yamlMapper = createMapper(new YAMLFactory(), null);
    
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
        Pattern pattern = Pattern.compile("\\$\\{(\\w+(:-.+?)?)\\}");
        Matcher m = pattern.matcher(content);
        while (m.find()) {
            String[] parts = m.group(1).split(":-");
            String v = System.getenv(parts[0]);
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
        jsonMapper = createMapper(null, classLoader);
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
        yamlMapper = createMapper(new YAMLFactory(), classLoader);
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
        UUID id = UUID.randomUUID();
        
        if (configCopy.getMasterSlaveServersConfig() != null) {
            validate(configCopy.getMasterSlaveServersConfig());
            return new MasterSlaveConnectionManager(configCopy.getMasterSlaveServersConfig(), configCopy, id);
        } else if (configCopy.getSingleServerConfig() != null) {
            validate(configCopy.getSingleServerConfig());
            return new SingleConnectionManager(configCopy.getSingleServerConfig(), configCopy, id);
        } else if (configCopy.getSentinelServersConfig() != null) {
            validate(configCopy.getSentinelServersConfig());
            return new SentinelConnectionManager(configCopy.getSentinelServersConfig(), configCopy, id);
        } else if (configCopy.getClusterServersConfig() != null) {
            validate(configCopy.getClusterServersConfig());
            return new ClusterConnectionManager(configCopy.getClusterServersConfig(), configCopy, id);
        } else if (configCopy.getReplicatedServersConfig() != null) {
            validate(configCopy.getReplicatedServersConfig());
            return new ReplicatedConnectionManager(configCopy.getReplicatedServersConfig(), configCopy, id);
        } else if (configCopy.getConnectionManager() != null) {
            return configCopy.getConnectionManager();
        }else {
            throw new IllegalArgumentException("server(s) address(es) not defined!");
        }
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
        mapper.addMixIn(ReferenceCodecProvider.class, ClassMixIn.class);
        mapper.addMixIn(AddressResolverGroupFactory.class, ClassMixIn.class);
        mapper.addMixIn(Codec.class, ClassMixIn.class);
        mapper.addMixIn(RedissonNodeInitializer.class, ClassMixIn.class);
        mapper.addMixIn(LoadBalancer.class, ClassMixIn.class);
        
        FilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter("classFilter", SimpleBeanPropertyFilter.filterOutAllExcept());
        mapper.setFilterProvider(filterProvider);
        mapper.setSerializationInclusion(Include.NON_NULL);

        if (classLoader != null) {
            TypeFactory tf = TypeFactory.defaultInstance()
                    .withClassLoader(classLoader);
            mapper.setTypeFactory(tf);
        }
        
        return mapper;
    }

}
