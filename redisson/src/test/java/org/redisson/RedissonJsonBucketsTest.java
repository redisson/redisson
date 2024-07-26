package org.redisson;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.redisson.api.RJsonBuckets;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.codec.JacksonCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.redisson.RedissonJsonBucketTest.NestedType;
import static org.redisson.RedissonJsonBucketTest.TestType;

public class RedissonJsonBucketsTest extends DockerRedisStackTest {
    
    @Test
    public void testSetAndGet() {
        RJsonBuckets buckets = redisson.getJsonBuckets(new JacksonCodec<>(TestType.class));
        Map<String, TestType> map = new HashMap<>();
        IntStream.range(0, 1000).forEach(i -> {
            TestType testType = new TestType();
            testType.setName("name" + i);
            map.put(testType.getName(), testType);
        });
        buckets.set(map);
        
        Map<String, TestType> stringObjectMap = buckets.get(map.keySet().toArray(new String[]{}));
        assertThat(stringObjectMap.size()).isEqualTo(1000);
    }
    
    @Test
    public void testGetWithPath() {
        RJsonBuckets buckets = redisson.getJsonBuckets(new JacksonCodec<>(TestType.class));
        Map<String, TestType> map = new HashMap<>();
        IntStream.range(0, 1000).forEach(i -> {
            TestType testType = new TestType();
            testType.setName("name" + i);
            NestedType nestedType = new NestedType();
            nestedType.setValue(i);
            testType.setType(nestedType);
            map.put(testType.getName(), testType);
        });
        buckets.set(map);
        
        Map<String, List<Integer>> result = buckets.get(new JacksonCodec<>(new TypeReference<List<Integer>>() {}), "$.type.value", map.keySet().toArray(new String[]{}));
        assertThat(result.size()).isEqualTo(1000);
    }
    
    @Test
    public void testSetWithPath() {
        RJsonBuckets buckets = redisson.getJsonBuckets(new JacksonCodec<>(TestType.class));
        Map<String, Object> map = new HashMap<>();
        IntStream.range(0, 1000).forEach(i -> {
            TestType testType = new TestType();
            testType.setName("name" + i);
            NestedType nestedType = new NestedType();
            nestedType.setValue(i);
            testType.setType(nestedType);
            map.put(testType.getName(), testType);
        });
        buckets.set(map);
        
        map.clear();
        IntStream.range(0, 1000).forEach(i -> {
            map.put("name" + i, i + 1);
        });
        buckets.set(new IntegerCodec(), "$.type.value", map);
    }
}
