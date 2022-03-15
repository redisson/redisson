package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.FunctionLibrary;
import org.redisson.api.FunctionStats;
import org.redisson.api.RFunction;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonFunctionTest extends BaseTest {

    @Test
    public void testEmpty() {
        RFunction f = redisson.getFunction();
        f.flush();
        assertThat(f.dump()).isNotEmpty();
        assertThat(f.list()).isEmpty();
        assertThat(f.list("test")).isEmpty();
    }

    @Test
    public void testStats() {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("lib", "redis.register_function('myfun', function(keys, args) for i = 1, 8829381983, 1 do end return args[1] end)" +
                "redis.register_function('myfun2', function(keys, args) return 'test' end)" +
                "redis.register_function('myfun3', function(keys, args) return 123 end)");
        f.callAsync(RFunction.Mode.READ, "myfun", RFunction.ReturnType.VALUE, Collections.emptyList(), "test");
        FunctionStats stats = f.stats();
        FunctionStats.RunningFunction func = stats.getRunningFunction();
        assertThat(func.getName()).isEqualTo("myfun");
        FunctionStats.Engine engine = stats.getEngines().get("LUA");
        assertThat(engine.getLibraries()).isEqualTo(1);
        assertThat(engine.getFunctions()).isEqualTo(3);

        f.kill();
        FunctionStats stats2 = f.stats();
        assertThat(stats2.getRunningFunction()).isNull();
    }

    @Test
    public void testCall() {
        RFunction f = redisson.getFunction();
        f.flush();
        f.load("lib", "redis.register_function('myfun', function(keys, args) return args[1] end)" +
                                        "redis.register_function('myfun2', function(keys, args) return 'test' end)" +
                                        "redis.register_function('myfun3', function(keys, args) return 123 end)");
        String s = f.call(RFunction.Mode.READ, "myfun", RFunction.ReturnType.VALUE, Collections.emptyList(), "test");
        assertThat(s).isEqualTo("test");

        RFunction f2 = redisson.getFunction(StringCodec.INSTANCE);
        String s2 = f2.call(RFunction.Mode.READ, "myfun2", RFunction.ReturnType.STRING, Collections.emptyList());
        assertThat(s2).isEqualTo("test");

        RFunction f3 = redisson.getFunction(LongCodec.INSTANCE);
        Long s3 = f3.call(RFunction.Mode.READ, "myfun3", RFunction.ReturnType.LONG, Collections.emptyList());
        assertThat(s3).isEqualTo(123L);
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


