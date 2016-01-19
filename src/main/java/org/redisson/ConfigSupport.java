package org.redisson;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.connection.balancer.LoadBalancer;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

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

    ObjectMapper mapper = createMapper();

    public Config fromJSON(String content) throws IOException {
        return mapper.readValue(content, Config.class);
    }

    public String toJSON(Config config) throws IOException {
        return mapper.writeValueAsString(config);
    }

    private ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
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
