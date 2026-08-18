package org.redisson.transaction;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.Encoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonTransactionalMapTest extends RedissonBaseTransactionalMapTest {

    @Override
    protected RMap<String, String> getMap() {
        return redisson.getMap("test");
    }

    @Override
    protected RMap<String, String> getTransactionalMap(RTransaction transaction) {
        return transaction.getMap("test");
    }

    @Test
    public void testContainsValueReleasesEncodedBuffers() {
        TrackingStringCodec codec = new TrackingStringCodec();
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        RMap<String, String> map = transaction.getMap("isequal-leak", codec);

        map.put("k", "v");
        codec.allocated.clear();

        assertThat(map.containsValue("v")).isTrue();
        assertThat(codec.allocated).hasSize(2);
        assertThat(codec.allocated).allSatisfy(buf -> assertThat(buf.refCnt()).isZero());

        transaction.commit();
    }

    static final class TrackingStringCodec extends StringCodec {

        final List<ByteBuf> allocated = new ArrayList<ByteBuf>();

        @Override
        public Encoder getValueEncoder() {
            Encoder delegate = super.getValueEncoder();
            return in -> {
                ByteBuf buf = delegate.encode(in);
                allocated.add(buf);
                return buf;
            };
        }
    }

}
