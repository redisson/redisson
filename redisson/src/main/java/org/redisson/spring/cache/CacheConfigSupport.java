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
package org.redisson.spring.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.redisson.api.map.event.MapEntryListener;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CacheConfigSupport {

    ObjectMapper jsonMapper = createMapper(null);
    ObjectMapper yamlMapper = createMapper(new YAMLFactory());
    
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class")
    public static class ClassMixIn {
    }
    
    private ObjectMapper createMapper(JsonFactory mapping) {
        ObjectMapper mapper = new ObjectMapper(mapping);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.addMixIn(MapEntryListener.class, ClassMixIn.class);
        return mapper;
    }
    
    public Map<String, CacheConfig> fromJSON(String content) throws IOException {
        return jsonMapper.readValue(content, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(File file) throws IOException {
        return jsonMapper.readValue(file, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(URL url) throws IOException {
        return jsonMapper.readValue(url, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(Reader reader) throws IOException {
        return jsonMapper.readValue(reader, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromJSON(InputStream inputStream) throws IOException {
        return jsonMapper.readValue(inputStream, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public String toJSON(Map<String, ? extends CacheConfig> configs) throws IOException {
        return jsonMapper.writeValueAsString(configs);
    }

    public Map<String, CacheConfig> fromYAML(String content) throws IOException {
        return yamlMapper.readValue(content, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromYAML(File file) throws IOException {
        return yamlMapper.readValue(file, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromYAML(URL url) throws IOException {
        return yamlMapper.readValue(url, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromYAML(Reader reader) throws IOException {
        return yamlMapper.readValue(reader, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public Map<String, CacheConfig> fromYAML(InputStream inputStream) throws IOException {
        return yamlMapper.readValue(inputStream, new TypeReference<Map<String, CacheConfig>>() {});
    }

    public String toYAML(Map<String, ? extends CacheConfig> configs) throws IOException {
        return yamlMapper.writeValueAsString(configs);
    }

    /**
     * Convert a map of Spring {@link RedisCacheConfiguration} to a map of Redisson {@link CacheConfig}.
     *
     * <p>For each entry:
     * - resolve key prefix and TTL from the {@code RedisCacheConfiguration},
     * - set them on a new {@code CacheConfig} instance,
     *
     * @param configMap map of cache name -> RedisCacheConfiguration (may be null or empty)
     * @return map of cache name -> CacheConfig; empty map if input is null or empty
     */
    public  Map<String, ? extends CacheConfig> fromConfig(Map<String, RedisCacheConfiguration> configMap) {
        if (Objects.isNull(configMap) || configMap.isEmpty()) return Collections.emptyMap();

        Map<String, CacheConfig> redissonCacheConfigMap = new HashMap<>();

        for (Map.Entry<String, RedisCacheConfiguration> configEntry : configMap.entrySet()) {
            CacheConfig cacheConfig = new CacheConfig();

            RedisCacheConfiguration configValue = configEntry.getValue();
            String prefix = resolvePrefix(configValue);
            Duration ttl = resolveTtl(configValue);

            cacheConfig.setKeyPrefix(prefix);
            if (Objects.nonNull(ttl)){
                cacheConfig.setTTL(ttl.toMillis());
            }

            redissonCacheConfigMap.put(configEntry.getKey(),cacheConfig);
        }
        return redissonCacheConfigMap;
    }

    /**
     * Resolve the cache entry Time-To-Live (TTL) from the given {@link RedisCacheConfiguration}.
     *
     * <p>This method uses reflection to handle different versions of Spring Data Redis where
     * {@code getTtl()} or {@code getTtlFunction()} may be available.</p>
     *
     * <ul>
     *   <li>If {@code getTtl()} exists, it directly returns the configured {@link Duration}.</li>
     *   <li>If {@code getTtlFunction()} exists, it invokes the underlying
     *       {@code RedisCacheWriter.TtlFunction#getTimeToLive(Object)} method to determine TTL.</li>
     * </ul>
     *
     * @param config the RedisCacheConfiguration to resolve TTL from
     * @return the resolved {@link Duration} value, or {@code null} if none is defined
     * @throws IllegalArgumentException if {@code TtlFunction} exists but does not define a valid {@code getTimeToLive(Object)} method
     */
    private Duration resolveTtl(RedisCacheConfiguration config) {
        Method getTtl = ReflectionUtils.findMethod(RedisCacheConfiguration.class, "getTtl");
        Method getTtlFunction = ReflectionUtils.findMethod(RedisCacheConfiguration.class, "getTtlFunction");
        if(Objects.isNull(getTtl) && Objects.isNull(getTtlFunction)) return null;
        if (Objects.nonNull(getTtl))  {
            /// @see org.springframework.data.redis.cache.RedisCacheConfiguration#getTtl
            return (Duration) ReflectionUtils.invokeMethod(getTtl, config);
        };

        /// @see org.springframework.data.redis.cache.RedisCacheWriter.TtlFunction#getTimeToLive
        Object ttlFun = ReflectionUtils.invokeMethod(getTtlFunction, config);
        if (Objects.isNull(ttlFun)) return null;
        /// @see org.springframework.data.redis.cache.RedisCacheWriter.TtlFunction#getTimeToLive
        Method getTimeToLive = ReflectionUtils.findMethod(ttlFun.getClass(), "getTimeToLive", Object.class);
        if (getTimeToLive == null) {
            throw new IllegalArgumentException("RedisCacheWriter.TtlFunction instance doesn't have getTimeToLive method.");
        }
        return (Duration) ReflectionUtils.invokeMethod(getTimeToLive, ttlFun, Objects.class, null);
    }

    /**
     * Resolve the cache key prefix from {@link RedisCacheConfiguration}.
     * <p>
     * This method uses reflection to access the {@code getKeyPrefix} method
     * from {@link RedisCacheConfiguration}, because the API may not expose it directly.
     * It then invokes the method to retrieve the prefix and normalizes it
     * by removing any trailing double colons "::".
     *
     * @param config the Redis cache configuration
     * @return the resolved key prefix without trailing "::", or {@code null} if unavailable
     */
    private String resolvePrefix(RedisCacheConfiguration config){
        /// @see org.springframework.data.redis.cache.RedisCacheConfiguration#getKeyPrefix
        Method getKeyPrefix = ReflectionUtils.findMethod(RedisCacheConfiguration.class, "getKeyPrefix");
        if (Objects.isNull(getKeyPrefix)) return null;
        /// @see org.springframework.data.redis.cache.CacheKeyPrefix
        Object cacheKeyPrefixInstance = ReflectionUtils.invokeMethod(getKeyPrefix, config);
        if (Objects.isNull(cacheKeyPrefixInstance)) return null;
        /// @see org.springframework.data.redis.cache.CacheKeyPrefix#compute

        Method compute = ReflectionUtils.findMethod(cacheKeyPrefixInstance.getClass(), "compute", String.class);
        if (Objects.isNull(compute)){
            throw new IllegalArgumentException("CacheKeyPrefix instance doesn't have compute method.");
        }
        ReflectionUtils.makeAccessible(compute);
        String keyPrefix = (String) ReflectionUtils.invokeMethod(compute, cacheKeyPrefixInstance,"");
        if (Objects.isNull(keyPrefix)) return null;
        // Remove trailing "::" (double colon) if present
        String prefix = keyPrefix.replaceFirst(":{2}$", "");
        return prefix.isEmpty() ? null : prefix;
    }
}
