package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.api.*;
import org.redisson.api.annotation.RInject;
import org.redisson.api.mapreduce.*;
import org.redisson.mapreduce.MapReduceTimeoutException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMapReduceTest extends RedisDockerTest {
    
    public static class WordMapper implements RMapper<String, String, String, Integer> {

        @Override
        public void map(String key, String value, RCollector<String, Integer> collector) {
            String[] words = value.split("[^a-zA-Z]");
            for (String word : words) {
                collector.emit(word, 1);
            }
        }
        
    }
    
    public static class WordReducer implements RReducer<String, Integer> {

        @Override
        public Integer reduce(String reducedKey, Iterator<Integer> iter) {
            int sum = 0;
            while (iter.hasNext()) {
               Integer i = iter.next();
               sum += i;
            }
            return sum;
        }
        
    }

    public static class WordCollator implements RCollator<String, Integer, Integer> {

        @Override
        public Integer collate(Map<String, Integer> resultMap) {
            int result = 0;
            for (Integer count : resultMap.values()) {
                result += count;
            }
            return result;
        }
        
    }
    
    public static Iterable<Object[]> mapClasses() {
        return Arrays.asList(new Object[][]{
            {RMap.class}, {RMapCache.class}
        });
    }

    @BeforeEach
    public void beforeTest() {
        redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME).registerWorkers(WorkerOptions.defaults().workers(3));
    }
    
    @ParameterizedTest
    @MethodSource("mapClasses")
    public void testCancel(Class<?> mapClass) throws InterruptedException {
        RMap<String, String> map = getMap(mapClass);
        for (int i = 0; i < 1000; i++) {
            map.put("" + i, "ab cd fjks");
        }
        
        RMapReduce<String, String, String, Integer> mapReduce = map.<String, Integer>mapReduce().mapper(new WordMapper()).reducer(new WordReducer());
        RFuture<Map<String, Integer>> future = mapReduce.executeAsync();
        Thread.sleep(100);
        assertThat(future.cancel(true)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("mapClasses")
    public void testTimeout(Class<?> mapClass) {
        Assertions.assertThrows(MapReduceTimeoutException.class, () -> {
            RMap<String, String> map = getMap(mapClass);
            for (int i = 0; i < 1000; i++) {
                map.put("" + i, "ab cd fjks");
            }

            RMapReduce<String, String, String, Integer> mapReduce = map.<String, Integer>mapReduce()
                    .mapper(new WordMapper())
                    .reducer(new WordReducer())
                    .timeout(1, TimeUnit.SECONDS);

            mapReduce.execute();
        });
    }
    
    @ParameterizedTest
    @MethodSource("mapClasses")
    @Timeout(5)
    public void test(Class<?> mapClass) {
        RMap<String, String> map = getMap(mapClass);
        
        map.put("1", "Alice was beginning to get very tired"); 
        map.put("2", "of sitting by her sister on the bank and");
        map.put("3", "of having nothing to do once or twice she");
        map.put("4", "had peeped into the book her sister was reading");
        map.put("5", "but it had no pictures or conversations in it");
        map.put("6", "and what is the use of a book");
        map.put("7", "thought Alice without pictures or conversation");
        
        Map<String, Integer> result = new HashMap<>();
        result.put("to", 2);
        result.put("Alice", 2);
        result.put("get", 1);
        result.put("beginning", 1);
        result.put("sitting", 1);
        result.put("do", 1);
        result.put("by", 1);
        result.put("or", 3);
        result.put("into", 1);
        result.put("sister", 2);
        result.put("on", 1);
        result.put("a", 1);
        result.put("without", 1);
        result.put("and", 2);
        result.put("once", 1);
        result.put("twice", 1);
        result.put("she", 1);
        result.put("had", 2);
        result.put("reading", 1);
        result.put("but", 1);
        result.put("it", 2);
        result.put("no", 1);
        result.put("in", 1);
        result.put("what", 1);
        result.put("use", 1);
        result.put("thought", 1);
        result.put("conversation", 1);
        result.put("was", 2);
        result.put("very", 1);
        result.put("tired", 1);
        result.put("of", 3);
        result.put("her", 2);
        result.put("the", 3);
        result.put("bank", 1);
        result.put("having", 1);
        result.put("nothing", 1);
        result.put("peeped", 1);
        result.put("book", 2);
        result.put("pictures", 2);
        result.put("conversations", 1);
        result.put("is", 1);
        
        RMapReduce<String, String, String, Integer> mapReduce = map.<String, Integer>mapReduce().mapper(new WordMapper()).reducer(new WordReducer());
        assertThat(mapReduce.execute()).isEqualTo(result);
        Integer count = mapReduce.execute(new WordCollator());
        assertThat(count).isEqualTo(57);
        
        mapReduce.execute("resultMap");
        RMap<Object, Object> resultMap = redisson.getMap("resultMap");
        assertThat(resultMap).isEqualTo(result);
        resultMap.delete();
    }

    private RMap<String, String> getMap(Class<?> mapClass) {
        if (RMapCache.class.isAssignableFrom(mapClass)) {
            return redisson.getMapCache("map");
        }
        return redisson.getMap("map");
    }
    
    public static class WordMapperInject implements RMapper<String, String, String, Integer> {

        @RInject
        private RedissonClient redisson;
        
        @Override
        public void map(String key, String value, RCollector<String, Integer> collector) {
            redisson.getAtomicLong("test").incrementAndGet();
            
            String[] words = value.split("[^a-zA-Z]");
            for (String word : words) {
                collector.emit(word, 1);
            }
        }
        
    }
    
    public static class WordReducerInject implements RReducer<String, Integer> {

        @RInject
        private RedissonClient redisson;

        @Override
        public Integer reduce(String reducedKey, Iterator<Integer> iter) {
            redisson.getAtomicLong("test").incrementAndGet();
            
            int sum = 0;
            while (iter.hasNext()) {
               Integer i = iter.next();
               sum += i;
            }
            return sum;
        }
        
    }

    public static class WordCollatorInject implements RCollator<String, Integer, Integer> {

        @RInject
        private RedissonClient redisson;

        @Override
        public Integer collate(Map<String, Integer> resultMap) {
            redisson.getAtomicLong("test").incrementAndGet();
            
            int result = 0;
            for (Integer count : resultMap.values()) {
                result += count;
            }
            return result;
        }
        
    }

    @ParameterizedTest
    @MethodSource("mapClasses")
    public void testCollatorTimeout(Class<?> mapClass) {
        RMap<String, String> map = getMap(mapClass);
        map.put("1", "Alice was beginning to get very tired");
        
        RMapReduce<String, String, String, Integer> mapReduce = map.<String, Integer>mapReduce()
                                                        .mapper(new WordMapperInject())
                                                        .reducer(new WordReducerInject())
                                                        .timeout(10, TimeUnit.SECONDS);
        
        Integer res = mapReduce.execute(new WordCollatorInject());
        assertThat(res).isEqualTo(7);

    }
    
    @ParameterizedTest
    @MethodSource("mapClasses")
    public void testInjector(Class<?> mapClass) {
        RMap<String, String> map = getMap(mapClass);

        map.put("1", "Alice was beginning to get very tired"); 
        
        RMapReduce<String, String, String, Integer> mapReduce = map.<String, Integer>mapReduce().mapper(new WordMapperInject()).reducer(new WordReducerInject());
        
        mapReduce.execute();
        assertThat(redisson.getAtomicLong("test").get()).isEqualTo(8);

        mapReduce.execute(new WordCollatorInject());
        assertThat(redisson.getAtomicLong("test").get()).isEqualTo(16 + 1);
    }

}
