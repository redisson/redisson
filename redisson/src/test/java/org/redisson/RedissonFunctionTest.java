package org.redisson;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonFunctionTest extends RedisDockerTest {

    @Test
    public void testEmpty() {
        RFunction f = redisson.getFunction();
        f.flush();
        assertThat(f.dump()).isNotEmpty();
        assertThat(f.list()).isEmpty();
        assertThat(f.list("test")).isEmpty();
    }

    @Test
    public void testStats() throws InterruptedException {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("lib", "redis.register_function('myfun', function(keys, args) for i = 1, 3329381988, 1 do end return args[1] end)" +
                "redis.register_function('myfun2', function(keys, args) return 'test' end)" +
                "redis.register_function('myfun3', function(keys, args) return 123 end)");
        f.callAsync(FunctionMode.WRITE, "myfun", FunctionResult.VALUE, Collections.emptyList(), "test");

        FunctionStats stats = f.stats();

        FunctionStats.RunningFunction func = stats.getRunningFunction();
        assertThat(func.getName()).isEqualTo("myfun");

        FunctionStats.Engine engine = stats.getEngines().get("LUA");
        assertThat(engine.getLibraries()).isEqualTo(1);
        assertThat(engine.getFunctions()).isEqualTo(3);

        Thread.sleep(4000);
    }

    @Test
    public void testCluster() {
        testInCluster(r -> {
            Map<String, Object> testMap = new HashMap<>();
            testMap.put("a", "b");
            testMap.put("c", "d");
            testMap.put("e", "f");
            testMap.put("g", "h");
            testMap.put("i", "j");
            testMap.put("k", "l");

            RFunction f = r.getFunction();
            f.flush();
            f.load("lib", "redis.register_function{function_name='myfun', callback=function(keys, args) return args[1] end, flags={ 'no-writes' }}");

            // waiting for the function replication to all nodes
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            RBatch batch = r.createBatch();
            RFunctionAsync function = batch.getFunction();
            for (Map.Entry<String, Object> property : testMap.entrySet()) {
                List<Object> key = Collections.singletonList(property.getKey());
                function.callAsync(
                        FunctionMode.READ,
                        "myfun",
                        FunctionResult.VALUE,
                        key,
                        property.getValue());
            }
            List<String> results = (List<String>) batch.execute().getResponses();
            assertThat(results).containsExactly("b", "d", "f", "h", "j", "l");
        });
    }

    @Test
    public void testCall() {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("lib", "redis.register_function{function_name='myfun', callback=function(keys, args) return args[1] end, flags={ 'no-writes' }}" +
                                        "redis.register_function{function_name='myfun2', callback=function(keys, args) return 'test' end, flags={ 'no-writes' }}" +
                                        "redis.register_function('myfun3', function(keys, args) return 123 end)");
        String s = f.call(FunctionMode.READ, "myfun", FunctionResult.VALUE, Collections.emptyList(), "test");
        assertThat(s).isEqualTo("test");

        RFunction f2 = redisson.getFunction(StringCodec.INSTANCE);
        String s2 = f2.call(FunctionMode.READ, "myfun2", FunctionResult.STRING, Collections.emptyList());
        assertThat(s2).isEqualTo("test");

        RFunction f3 = redisson.getFunction(LongCodec.INSTANCE);
        Long s3 = f3.call(FunctionMode.WRITE, "myfun3", FunctionResult.LONG, Collections.emptyList());
        assertThat(s3).isEqualTo(123L);

        f.loadAndReplace("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)" +
                "redis.register_function('myfun2', function(keys, args) return 'test2' end)" +
                "redis.register_function('myfun3', function(keys, args) return 123 end)");

        RFunction f4 = redisson.getFunction(StringCodec.INSTANCE);
        String s4 = f4.call(FunctionMode.WRITE, "myfun2", FunctionResult.STRING, Collections.emptyList());
        assertThat(s4).isEqualTo("test2");

    }

    @Test
    public void testKeysLoadAsExpected() {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("lib", "redis.register_function{function_name='myfun', callback=function(keys, args) return keys[1] end, flags={ 'no-writes' }}" +
                        "redis.register_function('myfun2', function(keys, args) return args[1] end)");
        String s = f.call(FunctionMode.READ, "myfun", FunctionResult.STRING, Arrays.asList("testKey"), "arg1");
        assertThat(s).isEqualTo("testKey");

        RFunction f2 = redisson.getFunction(StringCodec.INSTANCE);
        String s2 = f2.call(FunctionMode.WRITE, "myfun2", FunctionResult.STRING, Arrays.asList("testKey1", "testKey2"), "arg1");
        assertThat(s2).isEqualTo("arg1");

        String s3 = f.call(FunctionMode.WRITE, "myfun2", FunctionResult.VALUE, Arrays.asList("testKey"), "argv1");
        assertThat(s3).isEqualTo("argv1");
    }

    @Test
    public void testList() {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)" +
                                       "redis.register_function{function_name='myfun2', callback=function(keys, args) return args[1] end, flags={ 'no-writes' }}");

        List<FunctionLibrary> data = f.list();
        FunctionLibrary fl = data.get(0);
        assertThat(fl.getName()).isEqualTo("lib");
        FunctionLibrary.Function f2 = fl.getFunctions().stream().filter(e -> e.getName().equals("myfun2")).findFirst().get();
        assertThat(f2.getFlags()).containsExactly(FunctionLibrary.Flag.NO_WRITES);
    }

    @Test
    public void testListPattern() {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("alib", "redis.register_function('myfun', function(keys, args) return args[1] end)");
        f.load("lib2", "redis.register_function('myfun2', function(keys, args) return args[1] end)");

        List<FunctionLibrary> data = f.list("ali*");
        FunctionLibrary fl = data.get(0);
        assertThat(data).hasSize(1);
        assertThat(fl.getName()).isEqualTo("alib");

        List<FunctionLibrary> data1 = f.list("ali2*");
        assertThat(data1).isEmpty();
    }



}


