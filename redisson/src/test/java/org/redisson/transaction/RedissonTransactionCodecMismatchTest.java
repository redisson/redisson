package org.redisson.transaction;

import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.api.RTransaction;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.JsonJacksonCodec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RTransaction must reject a different codec for an object already created
 * under the same name with another codec, otherwise the wrong codec is used
 * silently and data is corrupted.
 *
 * @see <a href="https://github.com/redisson/redisson/issues/5127">Issue #5127</a>
 */
public class RedissonTransactionCodecMismatchTest extends RedisDockerTest {

    @Test
    public void getBucketCodecMismatchThrows() {
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        transaction.getBucket("bucket1", new StringCodec());

        assertThatThrownBy(() -> transaction.getBucket("bucket1", new JsonJacksonCodec()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getBucketSameCodecDoesNotThrow() {
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        transaction.getBucket("bucket2", new StringCodec());

        assertThatCode(() -> transaction.getBucket("bucket2", new StringCodec()))
                .doesNotThrowAnyException();
    }

    @Test
    public void getSetCodecMismatchThrows() {
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        transaction.getSet("set1", new StringCodec());

        assertThatThrownBy(() -> transaction.getSet("set1", new JsonJacksonCodec()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getMapCodecMismatchThrows() {
        RTransaction transaction = redisson.createTransaction(TransactionOptions.defaults());
        transaction.getMap("map1", new StringCodec());

        assertThatThrownBy(() -> transaction.getMap("map1", new JsonJacksonCodec()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
