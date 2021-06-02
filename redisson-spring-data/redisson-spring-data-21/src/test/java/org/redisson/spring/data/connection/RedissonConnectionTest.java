package org.redisson.spring.data.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.redisson.api.RBitSet;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;

import java.util.ArrayList;
import java.util.List;

public class RedissonConnectionTest extends BaseConnectionTest {

    @Test
    public void testBitField() {
        BitFieldSubCommands c = BitFieldSubCommands.create();
        c = c.set(BitFieldSubCommands.BitFieldType.INT_8).valueAt(1).to(120);
        List<Long> list = connection.bitField("testUnsigned".getBytes(), c);
        assertThat(list).containsExactly(0L);

        BitFieldSubCommands c2 = BitFieldSubCommands.create();
        c2 = c2.incr(BitFieldSubCommands.BitFieldType.INT_8).valueAt(1).by(1);
        List<Long> list2 = connection.bitField("testUnsigned".getBytes(), c2);
        assertThat(list2).containsExactly(121L);

        BitFieldSubCommands c3 = BitFieldSubCommands.create();
        c3 = c3.get(BitFieldSubCommands.BitFieldType.INT_8).valueAt(1);
        List<Long> list3 = connection.bitField("testUnsigned".getBytes(), c3);
        assertThat(list3).containsExactly(121L);
    }

    @Test
    public void testEcho() {
        assertThat(connection.echo("test".getBytes())).isEqualTo("test".getBytes());
    }

    @Test
    public void testSetGet() {
        connection.set("key".getBytes(), "value".getBytes());
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testSetExpiration() {
        assertThat(connection.set("key".getBytes(), "value".getBytes(), Expiration.milliseconds(111122), SetOption.SET_IF_ABSENT)).isTrue();
        assertThat(connection.get("key".getBytes())).isEqualTo("value".getBytes());
    }
    
    @Test
    public void testHSetGet() {
        assertThat(connection.hSet("key".getBytes(), "field".getBytes(), "value".getBytes())).isTrue();
        assertThat(connection.hGet("key".getBytes(), "field".getBytes())).isEqualTo("value".getBytes());
    }

    @Test
    public void testZScan() {
        connection.zAdd("key".getBytes(), 1, "value1".getBytes());
        connection.zAdd("key".getBytes(), 2, "value2".getBytes());

        Cursor<RedisZSetCommands.Tuple> t = connection.zScan("key".getBytes(), ScanOptions.scanOptions().build());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value1".getBytes());
        assertThat(t.hasNext()).isTrue();
        assertThat(t.next().getValue()).isEqualTo("value2".getBytes());
    }

    
}
