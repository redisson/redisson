package org.redisson.spring.data.connection;

import org.junit.Test;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
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

}
