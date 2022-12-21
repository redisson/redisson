package org.redisson.spring.data.connection;

import org.junit.Test;
import org.springframework.data.redis.connection.stream.*;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nikita Koksharov
 */
public class RedissonStreamTest extends BaseConnectionTest {

    @Test
    public void testPending() {
        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup", ReadOffset.latest(), true);

        PendingMessages p = connection.streamCommands().xPending("test".getBytes(), Consumer.from("testGroup", "test1"));
        assertThat(p.size()).isEqualTo(0);

        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        List<ByteRecord> l = connection.streamCommands().xReadGroup(Consumer.from("testGroup", "test1"), StreamOffset.create("test".getBytes(), ReadOffset.from(">")));
        assertThat(l.size()).isEqualTo(3);

        PendingMessages p2 = connection.streamCommands().xPending("test".getBytes(), Consumer.from("testGroup", "test1"));
        assertThat(p2.size()).isEqualTo(3);
    }

    @Test
    public void testGroups() {
        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup", ReadOffset.latest(), true);
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        StreamInfo.XInfoGroups groups = connection.streamCommands().xInfoGroups("test".getBytes());
        assertThat(groups.size()).isEqualTo(1);
        assertThat(groups.get(0).groupName()).isEqualTo("testGroup");
        assertThat(groups.get(0).pendingCount()).isEqualTo(0);
        assertThat(groups.get(0).consumerCount()).isEqualTo(0);
        assertThat(groups.get(0).lastDeliveredId()).isEqualTo("0-0");
    }

    @Test
    public void testConsumers() {
        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup", ReadOffset.latest(), true);
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        connection.streamCommands().xGroupCreate("test".getBytes(), "testGroup2", ReadOffset.latest(), true);
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("1".getBytes(), "1".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("2".getBytes(), "2".getBytes()));
        connection.streamCommands().xAdd("test".getBytes(), Collections.singletonMap("3".getBytes(), "3".getBytes()));

        List<ByteRecord> list = connection.streamCommands().xReadGroup(Consumer.from("testGroup", "consumer1"),
                                    StreamOffset.create("test".getBytes(), ReadOffset.lastConsumed()));
        assertThat(list.size()).isEqualTo(6);

        StreamInfo.XInfoStream info = connection.streamCommands().xInfo("test".getBytes());
        assertThat(info.streamLength()).isEqualTo(6);

        StreamInfo.XInfoConsumers s1 = connection.streamCommands().xInfoConsumers("test".getBytes(), "testGroup");
        assertThat(s1.getConsumerCount()).isEqualTo(1);
        assertThat(s1.get(0).consumerName()).isEqualTo("consumer1");
        assertThat(s1.get(0).pendingCount()).isEqualTo(6);
        assertThat(s1.get(0).idleTimeMs()).isLessThan(100L);
        assertThat(s1.get(0).groupName()).isEqualTo("testGroup");
    }

}
