/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.connection.balancer.LoadBalancer;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class ConfigSupport {

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
    @JsonFilter("classFilter")
    public static class ClassMixIn {

    }

    public abstract static class SingleSeverConfigMixIn {

        @JsonProperty
        List<URI> address;

        @JsonIgnore
        abstract SingleServerConfig setAddress(String address);

        @JsonIgnore
        abstract URI getAddress();

        @JsonIgnore
        abstract void setAddress(URI address);

    }

    public abstract static class MasterSlaveServersConfigMixIn {

        @JsonProperty
        List<URI> masterAddress;

        @JsonIgnore
        abstract MasterSlaveServersConfig setMasterAddress(String masterAddress);

        @JsonIgnore
        abstract URI getMasterAddress();

        @JsonIgnore
        abstract void setMasterAddress(URI masterAddress);

    }

    @JsonIgnoreProperties("clusterConfig")
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
        ElasticacheServersConfig elasticacheServersConfig;

    }

    private final ObjectMapper jsonMapper = createMapper(null);
    private final ObjectMapper yamlMapper = createMapper(new YAMLFactory());

    public Config fromJSON(String content) throws IOException {
        return jsonMapper.readValue(content, Config.class);
    }

    public Config fromJSON(File file) throws IOException {
        return jsonMapper.readValue(file, Config.class);
    }

    public Config fromJSON(URL url) throws IOException {
        return jsonMapper.readValue(url, Config.class);
    }

    public Config fromJSON(Reader reader) throws IOException {
        return jsonMapper.readValue(reader, Config.class);
    }

    public Config fromJSON(InputStream inputStream) throws IOException {
        return jsonMapper.readValue(inputStream, Config.class);
    }

    public String toJSON(Config config) throws IOException {
        return jsonMapper.writeValueAsString(config);
    }

    public Config fromYAML(String content) throws IOException {
        return yamlMapper.readValue(content, Config.class);
    }

    public Config fromYAML(File file) throws IOException {
        return yamlMapper.readValue(file, Config.class);
    }

    public Config fromYAML(URL url) throws IOException {
        return yamlMapper.readValue(url, Config.class);
    }

    public Config fromYAML(Reader reader) throws IOException {
        return yamlMapper.readValue(reader, Config.class);
    }

    public Config fromYAML(InputStream inputStream) throws IOException {
        return yamlMapper.readValue(inputStream, Config.class);
    }

    public String toYAML(Config config) throws IOException {
        return yamlMapper.writeValueAsString(config);
    }


    private ObjectMapper createMapper(JsonFactory mapping) {
        ObjectMapper mapper = new ObjectMapper(mapping);
        mapper.addMixIn(MasterSlaveServersConfig.class, MasterSlaveServersConfigMixIn.class);
        mapper.addMixIn(SingleServerConfig.class, SingleSeverConfigMixIn.class);
        mapper.addMixIn(Config.class, ConfigMixIn.class);
        mapper.addMixIn(Codec.class, ClassMixIn.class);
        mapper.addMixIn(LoadBalancer.class, ClassMixIn.class);
        FilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter("classFilter", SimpleBeanPropertyFilter.filterOutAllExcept());
        mapper.setFilterProvider(filterProvider);
        mapper.setSerializationInclusion(Include.NON_NULL);
        return mapper;
    }

}
