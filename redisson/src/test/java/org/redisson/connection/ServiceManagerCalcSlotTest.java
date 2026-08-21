package org.redisson.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the slot-calculation logic in {@link ServiceManager#calcSlot}.
 *
 * <p>A single {@link ServiceManager} instance is shared across all tests
 * (created once in {@code @BeforeAll}) with {@code clusterDetected = true}
 * so that {@link ServiceManager#calcSlot} exercises the real CRC16 path.
 */
class ServiceManagerCalcSlotTest {

    private static ServiceManager serviceManager;

    @BeforeAll
    static void setUp() {
        MasterSlaveServersConfig config = new MasterSlaveServersConfig();
        serviceManager = new ServiceManager(config, new Config());
        serviceManager.setClusterDetected(true);
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        serviceManager.getTimer().stop();
        serviceManager.getGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync();
        serviceManager.getExecutor().shutdown();
    }

    // -------------------------------------------------------------------------
    // Null safety -- hashSlot handles null; calcSlot delegates when cluster is on
    // -------------------------------------------------------------------------

    @Test
    void calcSlotNullStringReturnsZero() {
        assertThat(serviceManager.calcSlot((String) null)).isEqualTo(0);
    }

    @Test
    void calcSlotNullByteArrayReturnsZero() {
        assertThat(serviceManager.calcSlot((byte[]) null)).isEqualTo(0);
    }

    @Test
    void calcSlotNullByteBufReturnsZero() {
        assertThat(serviceManager.calcSlot((ByteBuf) null)).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Plain keys (no hash tag)
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "plain key \"{0}\" -> slot {1}")
    @CsvSource({
        "foo,   12182",
        "bar,    5061",
        "key:1,  6657",
    })
    void calcSlotPlainKeyReturnsCorrectSlot(String key, int expectedSlot) {
        assertThat(serviceManager.calcSlot(key)).isEqualTo(expectedSlot);
    }

    // -------------------------------------------------------------------------
    // Hash-tag extraction: slot must equal slot of the tag content alone
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" should hash same as \"{1}\" -> slot {2}")
    @CsvSource({
        "{foo},                   foo,                12182",
        "{foo}.bar,               foo,                12182",
        "foo.{bar}.baz,           bar,                5061",
        "{key}:1,                 key,                12539",
        "{key}:2,                 key,                12539",
    })
    void calcSlotHashTagUsesTagContentForSlot(String keyWithTag, String tagContent, int expectedSlot) {
        assertThat(serviceManager.calcSlot(keyWithTag))
                .as("key '%s' should hash same as tag content '%s'", keyWithTag, tagContent)
                .isEqualTo(expectedSlot);
        assertThat(serviceManager.calcSlot(tagContent))
                .as("tag content '%s' alone should produce same slot", tagContent)
                .isEqualTo(expectedSlot);
    }

    // -------------------------------------------------------------------------
    // Hash-tag edge cases: tag must NOT be extracted
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "edge case \"{0}\" -> whole-key slot {1}")
    @CsvSource({
        "{},          15257",
        "{}.empty,     5271",
        "{,            4092",
        "},           12090",
        "{foo,        13308",
    })
    void calcSlotInvalidHashTagHashesWholeKey(String key, int expectedSlot) {
        assertThat(serviceManager.calcSlot(key)).isEqualTo(expectedSlot);
    }

    // -------------------------------------------------------------------------
    // Multiple brace pairs: only the FIRST valid tag is used
    // -------------------------------------------------------------------------

    @Test
    void calcSlotMultipleBracePairsOnlyFirstTagUsed() {
        // 'a{b}c{d}e' -- first tag is 'b' (slot 3300), second tag 'd' is ignored
        int slotOfB = serviceManager.calcSlot("b");
        assertThat(serviceManager.calcSlot("a{b}c{d}e")).isEqualTo(slotOfB);
        assertThat(serviceManager.calcSlot("a{b}c{d}e")).isEqualTo(3300);
    }

    // -------------------------------------------------------------------------
    // Consistency: all three overloads must return the same slot for the same key
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "overload consistency for \"{0}\"")
    @CsvSource({
        "foo",
        "{foo}.bar",
        "foo.{bar}.baz",
        "{}",
        "{",
        "{key}:1",
    })
    void calcSlotAllOverloadsReturnSameSlot(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);

        try {
            int slotFromString    = serviceManager.calcSlot(key);
            int slotFromByteArray = serviceManager.calcSlot(bytes);
            int slotFromByteBuf   = serviceManager.calcSlot(buf);
            assertThat(slotFromByteArray)
                    .as("byte[] overload must match String overload for key '%s'", key)
                    .isEqualTo(slotFromString);
            assertThat(slotFromByteBuf)
                    .as("ByteBuf overload must match String overload for key '%s'", key)
                    .isEqualTo(slotFromString);
        } finally {
            buf.release();
        }
    }

    // -------------------------------------------------------------------------
    // Slot range: result must always be in [0, MAX_SLOT)
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "slot for \"{0}\" is within valid range")
    @CsvSource({
        "foo",
        "{foo}.bar",
        "a{b}c{d}e",
        "{}",
        "anylongkeywithoutatag",
    })
    void calcSlotResultIsWithinValidSlotRange(String key) {
        int slot = serviceManager.calcSlot(key);
        assertThat(slot)
                .as("slot for '%s' must be in [0, %d)", key, MasterSlaveConnectionManager.MAX_SLOT)
                .isGreaterThanOrEqualTo(0)
                .isLessThan(MasterSlaveConnectionManager.MAX_SLOT);
    }

    // -------------------------------------------------------------------------
    // ByteBuf: reader index must not advance (non-destructive read)
    // -------------------------------------------------------------------------

    @Test
    void calcSlotByteBufDoesNotAdvanceReaderIndex() {
        byte[] bytes = "foo.{bar}.baz".getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        int readerIndexBefore = buf.readerIndex();

        try {
            serviceManager.calcSlot(buf);
            assertThat(buf.readerIndex())
                    .as("calcSlot must not advance the ByteBuf readerIndex")
                    .isEqualTo(readerIndexBefore);
        } finally {
            buf.release();
        }
    }

}
