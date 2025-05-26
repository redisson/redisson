package org.redisson;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.api.RScript.Mode;
import org.redisson.client.RedisException;
import org.redisson.client.RedisNoScriptException;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.CommandMapper;
import org.redisson.config.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonScriptTest extends RedisDockerTest {

    @Test
    public void testCommandMapping() {
        String script = "local value = redis.call('cmd1', KEYS[1], ARGV[1]); "
                + "if value ~= false then "
                    + "redis.call('cmd2', KEYS[1], ARGV[1], ARGV[2]); "
                    + "redis.call('cmd2', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return value; "
                + "end; "
                + "return nil; ";

        Config cfg = createConfig();
        cfg.useSingleServer().setCommandMapper(new CommandMapper() {
            @Override
            public String map(String name) {
                if (name.equalsIgnoreCase("cmd1")) {
                    return "hget";
                }
                if (name.equalsIgnoreCase("cmd2")) {
                    return "hset";
                }
                return name;
            }
        });

        RedissonClient r = Redisson.create(cfg);
        RMap<Object, Object> m = r.getMap("test");
        m.fastPut("key1", "value");

        String res = r.getScript().eval(Mode.READ_WRITE,
                script,
                RScript.ReturnType.VALUE, Arrays.asList("test"), "key1", "value3");
        assertThat(res).isEqualTo("value");

        assertThat(m.get("key1")).isEqualTo("value3");
    }

    @Test
    public void testMulti() {
        RLexSortedSet idx2 = redisson.getLexSortedSet("ABCD17436");
        
        Long l = Long.valueOf("1506524856000");
        for (int i = 0; i < 100; i++) {
            String s = "DENY" + "\t" + "TESTREDISSON" + "\t"
                    + Long.valueOf(l) + "\t" + "helloworld_hongqin";
            idx2.add(s);
            l = l + 1;
        }

        String max = "'[DENY" + "\t" + "TESTREDISSON" + "\t" + "1506524856099'";
        String min = "'[DENY" + "\t" + "TESTREDISSON" + "\t" + "1506524856000'";
         String luaScript1= "local d = {}; d[1] = redis.call('zrevrangebylex','ABCD17436'," +max+","+min+",'LIMIT',0,5); ";
         luaScript1=  luaScript1 + " d[2] = redis.call('zrevrangebylex','ABCD17436'," +max+","+min+",'LIMIT',0,15); ";
         luaScript1=  luaScript1 + " d[3] = redis.call('zrevrangebylex','ABCD17436'," +max+","+min+",'LIMIT',0,25); ";
         luaScript1 = luaScript1 + " return d;";
     
         List<List<Object>> objs = redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_ONLY,
                luaScript1,
                RScript.ReturnType.MULTI, Collections.emptyList());            
        
        assertThat(objs).hasSize(3);
        assertThat(objs.get(0)).hasSize(5);
        assertThat(objs.get(1)).hasSize(15);
        assertThat(objs.get(2)).hasSize(25);
    }

    @Test
    public void testString() {
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        String res = script.eval(RScript.Mode.READ_ONLY, "return 'hello'", RScript.ReturnType.STATUS);
        assertThat(res).isEqualTo("hello");
    }

    @Test
    public void testEval() {
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        List<Object> res = script.eval(RScript.Mode.READ_ONLY, "return {'1','2','3.3333','foo',nil,'bar'}", RScript.ReturnType.MULTI, Collections.emptyList());
        assertThat(res).containsExactly("1", "2", "3.3333", "foo");
    }

    @Test
    public void testEvalAsync() {
        RScript script = redisson.getScript(StringCodec.INSTANCE);
        RFuture<List<Object>> res = script.evalAsync(RScript.Mode.READ_ONLY, "return {'1','2','3.3333','foo',nil,'bar'}", RScript.ReturnType.MULTI, Collections.emptyList());
        assertThat(res.toCompletableFuture().join()).containsExactly("1", "2", "3.3333", "foo");
    }

    @Test
    public void testEvalResultMapping() {
        testInCluster(redissonClient -> {
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);
            Long res = script.eval(RScript.Mode.READ_ONLY, "return 1;", RScript.ReturnType.INTEGER,
                    integers -> integers.stream().mapToLong(r -> r).sum());
            assertThat(res).isEqualTo(3);
        });
    }

    @Test
    public void testScriptEncoding() {
        RScript script = redisson.getScript();
        String value = "test";
        script.eval(RScript.Mode.READ_WRITE, "redis.call('set', KEYS[1], ARGV[1])", RScript.ReturnType.VALUE, Arrays.asList("foo"), value);

        String val = script.eval(RScript.Mode.READ_WRITE, "return redis.call('get', KEYS[1])", RScript.ReturnType.VALUE, Arrays.asList("foo"));
        Assertions.assertEquals(value, val);
    }

    @Test
    public void testScriptExists() {
        RScript s = redisson.getScript();
        String r = s.scriptLoad("return redis.call('get', 'foo')");
        Assertions.assertEquals("282297a0228f48cd3fc6a55de6316f31422f5d17", r);

        List<Boolean> r1 = s.scriptExists(r);
        Assertions.assertEquals(1, r1.size());
        Assertions.assertTrue(r1.get(0));

        s.scriptFlush();

        List<Boolean> r2 = s.scriptExists(r);
        Assertions.assertEquals(1, r2.size());
        Assertions.assertFalse(r2.get(0));
    }

    @Test
    public void testScriptFlush() {
        redisson.getBucket("foo").set("bar");
        String r = redisson.getScript().scriptLoad("return redis.call('get', 'foo')");
        Assertions.assertEquals("282297a0228f48cd3fc6a55de6316f31422f5d17", r);
        String r1 = redisson.getScript().evalSha(Mode.READ_ONLY, "282297a0228f48cd3fc6a55de6316f31422f5d17", RScript.ReturnType.VALUE, Collections.emptyList());
        Assertions.assertEquals("bar", r1);
        redisson.getScript().scriptFlush();

        Assertions.assertThrows(RedisNoScriptException.class, () -> {
            redisson.getScript().evalSha(Mode.READ_ONLY, "282297a0228f48cd3fc6a55de6316f31422f5d17", RScript.ReturnType.VALUE, Collections.emptyList());
        });
    }

    @Test
    public void testScriptLoad() {
        redisson.getBucket("foo").set("bar");
        String r = redisson.getScript().scriptLoad("return redis.call('get', 'foo')");
        Assertions.assertEquals("282297a0228f48cd3fc6a55de6316f31422f5d17", r);
        String r1 = redisson.getScript().evalSha(Mode.READ_ONLY, "282297a0228f48cd3fc6a55de6316f31422f5d17", RScript.ReturnType.VALUE, Collections.emptyList());
        Assertions.assertEquals("bar", r1);
    }

    @Test
    public void testScriptLoadAsync() {
        redisson.getBucket("foo").set("bar");
        RFuture<String> r = redisson.getScript().scriptLoadAsync("return redis.call('get', 'foo')");
        Assertions.assertEquals("282297a0228f48cd3fc6a55de6316f31422f5d17", r.toCompletableFuture().join());
        String r1 = redisson.getScript().evalSha(Mode.READ_ONLY, "282297a0228f48cd3fc6a55de6316f31422f5d17", RScript.ReturnType.VALUE, Collections.emptyList());
        Assertions.assertEquals("bar", r1);
    }

    @Test
    public void testEvalSha() {
        RScript s = redisson.getScript();
        String res = s.scriptLoad("return redis.call('get', 'foo')");
        Assertions.assertEquals("282297a0228f48cd3fc6a55de6316f31422f5d17", res);

        redisson.getBucket("foo").set("bar");
        String r1 = s.evalSha(Mode.READ_ONLY, "282297a0228f48cd3fc6a55de6316f31422f5d17", RScript.ReturnType.VALUE, Collections.emptyList());
        Assertions.assertEquals("bar", r1);
    }

    @Test
    public void testEvalshaAsync() {
        RScript s = redisson.getScript();
        String res = s.scriptLoad("return redis.call('get', 'foo')");
        Assertions.assertEquals("282297a0228f48cd3fc6a55de6316f31422f5d17", res);

        redisson.getBucket("foo").set("bar");
        String r = redisson.getScript().eval(Mode.READ_ONLY, "return redis.call('get', 'foo')", RScript.ReturnType.VALUE);
        Assertions.assertEquals("bar", r);
        RFuture<Object> r1 = redisson.getScript().evalShaAsync(Mode.READ_ONLY, "282297a0228f48cd3fc6a55de6316f31422f5d17", RScript.ReturnType.VALUE, Collections.emptyList());
        Assertions.assertEquals("bar", r1.toCompletableFuture().join());
    }


}
