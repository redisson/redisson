package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.redisson.api.RExecutorService;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.redisson.api.WorkerOptions;
import org.redisson.api.annotation.RInject;
import org.redisson.api.mapreduce.RCollator;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.api.mapreduce.RCollectionMapper;
import org.redisson.api.mapreduce.RCollector;
import org.redisson.api.mapreduce.RReducer;

@RunWith(Parameterized.class)
public class RedissonCollectionMapReduceTest extends BaseTest {
    
    public static class WordMapper implements RCollectionMapper<String, String, Integer> {

        @Override
        public void map(String value, RCollector<String, Integer> collector) {
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
               Integer i = (Integer) iter.next();
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
    
    @Parameterized.Parameters(name = "{index} - {0}")
    public static Iterable<Object[]> mapClasses() {
        return Arrays.asList(new Object[][]{
            {RList.class}, {RQueue.class}
        });
    }

    @Parameterized.Parameter(0)
    public Class<?> mapClass;

    
    @Before
    public void beforeTest() {
        redisson.getExecutorService(RExecutorService.MAPREDUCE_NAME).registerWorkers(WorkerOptions.defaults().workers(3));
    }
    
    @Test
    public void test() {
        RList<String> list = getCollection();
        
        list.add("Alice was beginning to get very tired"); 
        list.add("of sitting by her sister on the bank and");
        list.add("of having nothing to do once or twice she");
        list.add("had peeped into the book her sister was reading");
        list.add("but it had no pictures or conversations in it");
        list.add("and what is the use of a book");
        list.add("thought Alice without pictures or conversation");
        
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
        
        RCollectionMapReduce<String, String, Integer> mapReduce = list.<String, Integer>mapReduce().mapper(new WordMapper()).reducer(new WordReducer());
        assertThat(mapReduce.execute()).isEqualTo(result);
        Integer count = mapReduce.execute(new WordCollator());
        assertThat(count).isEqualTo(57);
        
        mapReduce.execute("resultMap");
        RMap<Object, Object> resultMap = redisson.getMap("resultMap");
        assertThat(resultMap).isEqualTo(result);
        resultMap.delete();
    }
    
    public static class WordMapperInject implements RCollectionMapper<String, String, Integer> {

        @RInject
        private RedissonClient redisson;
        
        @Override
        public void map(String value, RCollector<String, Integer> collector) {
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
               Integer i = (Integer) iter.next();
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

    @Test
    public void testInjector() {
        RList<String> list = getCollection();

        list.add("Alice was beginning to get very tired"); 
        
        RCollectionMapReduce<String, String, Integer> mapReduce = list.<String, Integer>mapReduce().mapper(new WordMapperInject()).reducer(new WordReducerInject());
        
        mapReduce.execute();
        assertThat(redisson.getAtomicLong("test").get()).isEqualTo(8);

        mapReduce.execute(new WordCollatorInject());
        assertThat(redisson.getAtomicLong("test").get()).isEqualTo(16 + 1);
    }

    private RList<String> getCollection() {
        RList<String> list = null;
        if (RList.class.isAssignableFrom(mapClass)) {
            list = redisson.getList("list");
        } else if (RQueue.class.isAssignableFrom(mapClass)) {
            list = (RList<String>) redisson.<String>getQueue("queue");
        }
        return list;
    }

}
