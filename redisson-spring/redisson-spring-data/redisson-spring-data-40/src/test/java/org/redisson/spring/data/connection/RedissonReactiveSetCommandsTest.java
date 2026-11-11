package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveSetCommands;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReactiveSetCommandsTest extends BaseConnectionTest {

    @Test
    public void testSScanWithBinaryPattern() {
        byte[] binaryMember = {(byte) 0xFF, 'm'};
        connection.sAdd("sscan-binary".getBytes(), binaryMember, "other".getBytes());

        ReactiveSetCommands setCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().setCommands();

        assertThat(setCommands.sScan(ByteBuffer.wrap("sscan-binary".getBytes()),
                ScanOptions.scanOptions().match(new byte[] {(byte) 0xFF, '*'}).build()).collectList().block())
                .containsExactly(ByteBuffer.wrap(binaryMember));
    }

    @Test
    public void testSMIsMember() {
        connection.sAdd("smismember".getBytes(), "a".getBytes(), "b".getBytes());

        ReactiveSetCommands setCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().setCommands();

        assertThat(setCommands.sMIsMember(ByteBuffer.wrap("smismember".getBytes()),
                Arrays.asList(ByteBuffer.wrap("b".getBytes()), ByteBuffer.wrap("x".getBytes()), ByteBuffer.wrap("a".getBytes()))).block())
                .containsExactly(true, false, true);
    }

    @Test
    public void testSInterCard() {
        connection.sAdd("set1".getBytes(), "a".getBytes(), "b".getBytes(), "c".getBytes());
        connection.sAdd("set2".getBytes(), "b".getBytes(), "c".getBytes(), "d".getBytes());
        connection.sAdd("set3".getBytes(), "c".getBytes(), "x".getBytes());

        ReactiveSetCommands setCommands = new RedissonConnectionFactory(redisson).getReactiveConnection().setCommands();

        assertThat(setCommands.sInterCard(Arrays.asList(ByteBuffer.wrap("set1".getBytes()),
                ByteBuffer.wrap("set2".getBytes()))).block()).isEqualTo(2);
        assertThat(setCommands.sInterCard(Arrays.asList(ByteBuffer.wrap("set1".getBytes()),
                ByteBuffer.wrap("set2".getBytes()), ByteBuffer.wrap("set3".getBytes()))).block()).isEqualTo(1);
    }

}
