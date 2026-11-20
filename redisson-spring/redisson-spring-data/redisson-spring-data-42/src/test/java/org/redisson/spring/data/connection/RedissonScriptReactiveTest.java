package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonScriptReactiveTest extends BaseConnectionTest {

    @Test
    public void testEval() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection cc = factory.getReactiveConnection();

        String s = "local ret = {}" +
                "local mysqlKeys = {}" +
                "table.insert(ret, 'test1')" +
                "table.insert(ret, 'test2')" +
                "table.insert(ret, 'test3')" +
                "table.insert(ret, mysqlKeys)" +
                "return ret";
        Flux<List<Object>> ss = cc.scriptingCommands().eval(ByteBuffer.wrap(s.getBytes()), ReturnType.MULTI, 0);
        List<Object> r = ss.blockFirst();
        assertThat(r.get(2)).isEqualTo(ByteBuffer.wrap("test3".getBytes()));
        assertThat((List) r.get(3)).isEmpty();
    }

    @Test
    public void testEvalListWithNilAndNestedLists() {
        RedissonConnectionFactory factory = new RedissonConnectionFactory(redisson);
        ReactiveRedisConnection cc = factory.getReactiveConnection();

        String s = "return {redis.call('get', 'missing'), 1, 'a', {'b', {'c'}, redis.call('get', 'missing')}}";
        List<Object> r = cc.scriptingCommands().<List<Object>>eval(ByteBuffer.wrap(s.getBytes()), ReturnType.MULTI, 0).blockFirst();
        assertThat(r).containsExactly(null, 1L, ByteBuffer.wrap("a".getBytes()),
                Arrays.asList(ByteBuffer.wrap("b".getBytes()), Arrays.asList(ByteBuffer.wrap("c".getBytes())), null));

        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        RedisScript<List> popAndLength = RedisScript.of("return {redis.call('rpop', KEYS[1]), redis.call('llen', KEYS[1])}", List.class);
        assertThat(template.execute(popAndLength, Collections.singletonList("missing")).blockFirst()).containsExactly(null, 0L);
    }

}
