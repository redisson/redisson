package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonScriptReactiveTest extends BaseConnectionTest {

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
