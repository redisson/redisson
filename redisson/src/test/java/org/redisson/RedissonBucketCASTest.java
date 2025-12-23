package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.bucket.CompareAndSetArgs;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonBucketCASTest extends RedisDockerTest {

    @Test
    public void testCompareAndSetArgsExpected() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpected");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expected(null).set("value1"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value1");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expected(null).set("value2"))).isFalse();
        assertThat(bucket.get()).isEqualTo("value1");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected("value1").set("value2"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value2");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected("wrongValue").set("value3"))).isFalse();
        assertThat(bucket.get()).isEqualTo("value2");
    }

    @Test
    public void testCompareAndSetArgsUnexpected() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsUnexpected");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.unexpected("anyValue").set("value1"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value1");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.unexpected("otherValue").set("value2"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value2");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.unexpected("value2").set("value3"))).isFalse();
        assertThat(bucket.get()).isEqualTo("value2");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>unexpected(null).set("value4"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value4");
    }

    @Test
    public void testCompareAndSetArgsExpectedWithTTL() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedWithTTL");

        bucket.set("initial");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected("initial")
                .set("newValue")
                .timeToLive(Duration.ofMillis(500)))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
        assertThat(bucket.isExists()).isFalse();
    }

    @Test
    public void testCompareAndSetArgsExpectedWithExpireAt() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedWithExpireAt");

        bucket.set("initial");

        Instant expireTime = Instant.now().plusMillis(500);
        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected("initial")
                .set("newValue")
                .expireAt(expireTime))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
        assertThat(bucket.isExists()).isFalse();
    }

    @Test
    public void testCompareAndSetArgsUnexpectedWithTTL() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsUnexpectedWithTTL");

        bucket.set("initial");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.unexpected("otherValue")
                .set("newValue")
                .timeToLive(Duration.ofMillis(500)))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
    }

    @Test
    public void testCompareAndSetArgsUnexpectedWithExpireAt() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsUnexpectedWithExpireAt");

        bucket.set("initial");

        Instant expireTime = Instant.now().plusMillis(500);
        assertThat(bucket.compareAndSet(CompareAndSetArgs.unexpected("otherValue")
                .set("newValue")
                .expireAt(expireTime))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
    }

    @Test
    public void testCompareAndSetArgsExpectedWithListType() {
        RBucket<List<String>> bucket = redisson.getBucket("testCompareAndSetArgsExpectedList");

        List<String> list1 = Arrays.asList("a", "b", "c");
        List<String> list2 = Arrays.asList("d", "e", "f");
        List<String> list3 = Arrays.asList("g", "h", "i");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<List<String>>expected(null).set(list1))).isTrue();
        assertThat(bucket.get()).isEqualTo(list1);

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected(list1).set(list2))).isTrue();
        assertThat(bucket.get()).isEqualTo(list2);

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected(list1).set(list3))).isFalse();
        assertThat(bucket.get()).isEqualTo(list2);
    }

    @Test
    public void testCompareAndSetArgsAsync() throws Exception {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsAsync");

        RFuture<Boolean> future1 = bucket.compareAndSetAsync(CompareAndSetArgs.<String>expected(null).set("value1"));
        assertThat(future1.get()).isTrue();
        assertThat(bucket.get()).isEqualTo("value1");

        RFuture<Boolean> future2 = bucket.compareAndSetAsync(CompareAndSetArgs.expected("value1").set("value2"));
        assertThat(future2.get()).isTrue();
        assertThat(bucket.get()).isEqualTo("value2");

        RFuture<Boolean> future3 = bucket.compareAndSetAsync(CompareAndSetArgs.unexpected("otherValue").set("value3"));
        assertThat(future3.get()).isTrue();
        assertThat(bucket.get()).isEqualTo("value3");
    }

    @Test
    public void testCompareAndSetArgsExpectedNullToNull() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedNullToNull");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expected(null).set("value1"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value1");

        bucket.delete();

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expected(null).set("value2"))).isTrue();
        assertThat(bucket.get()).isEqualTo("value2");
    }

    @Test
    public void testCompareAndSetArgsUnexpectedNullOnNonExistent() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsUnexpectedNullOnNonExistent");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>unexpected(null).set("value1"))).isFalse();
        assertThat(bucket.isExists()).isFalse();
    }

    @Test
    public void testCompareAndSetArgsExpectedWithTTLOnNonExistent() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedWithTTLOnNonExistent");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expected(null)
                .set("newValue")
                .timeToLive(Duration.ofMillis(500)))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
    }

    @Test
    public void testCompareAndSetArgsChaining() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsChaining");

        CompareAndSetArgs<String> args = CompareAndSetArgs.<String>expected(null)
                .set("value1")
                .timeToLive(Duration.ofHours(1));

        assertThat(bucket.compareAndSet(args)).isTrue();
        assertThat(bucket.get()).isEqualTo("value1");
        assertThat(bucket.remainTimeToLive()).isGreaterThan(0);
    }

    @Test
    public void testCompareAndSetArgsMultipleOperations() {
        RBucket<Integer> bucket = redisson.getBucket("testCompareAndSetArgsMultipleOperations");

        bucket.set(0);

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected(0).set(1))).isTrue();
        assertThat(bucket.get()).isEqualTo(1);

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected(1).set(2))).isTrue();
        assertThat(bucket.get()).isEqualTo(2);

        assertThat(bucket.compareAndSet(CompareAndSetArgs.expected(1).set(3))).isFalse();
        assertThat(bucket.get()).isEqualTo(2);
    }

    @Test
    public void testCompareAndSetArgsExpectedDigest() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedDigest");

        bucket.set("Hello world");
        String digest = bucket.getDigest();

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expectedDigest(digest)
                .set("newValue"))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expectedDigest(digest)
                .set("anotherValue"))).isFalse();
        assertThat(bucket.get()).isEqualTo("newValue");
    }

    @Test
    public void testCompareAndSetArgsUnexpectedDigest() {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsUnexpectedDigest");

        bucket.set("Hello world");

        String wrongDigest = "0000000000000000";

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>unexpectedDigest(wrongDigest)
                .set("newValue"))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");
    }

    @Test
    public void testCompareAndSetArgsExpectedDigestWithTTL() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedDigestWithTTL");

        bucket.set("Hello world");
        String digest = bucket.getDigest();

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expectedDigest(digest)
                .set("newValue")
                .timeToLive(Duration.ofMillis(500)))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
    }

    @Test
    public void testCompareAndSetArgsExpectedDigestWithExpireAt() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsExpectedDigestWithExpireAt");

        bucket.set("Hello world");
        String digest = bucket.getDigest();

        Instant expireTime = Instant.now().plusMillis(500);
        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>expectedDigest(digest)
                .set("newValue")
                .expireAt(expireTime))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
    }

    @Test
    public void testCompareAndSetArgsUnexpectedDigestWithTTL() throws InterruptedException {
        RBucket<String> bucket = redisson.getBucket("testCompareAndSetArgsUnexpectedDigestWithTTL");

        bucket.set("Hello world");
        String wrongDigest = "0000000000000000";

        assertThat(bucket.compareAndSet(CompareAndSetArgs.<String>unexpectedDigest(wrongDigest)
                .set("newValue")
                .timeToLive(Duration.ofMillis(500)))).isTrue();
        assertThat(bucket.get()).isEqualTo("newValue");

        Thread.sleep(600);
        assertThat(bucket.get()).isNull();
    }
}
