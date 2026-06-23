package org.redisson.connection;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SlotCalculator}.
 *
 * <p>Expected slot values are independently verified against the Redis CRC16 specification
 * (see https://redis.io/docs/reference/cluster-spec/#hash-tags).
 *
 * <p>Key cases covered:
 * <ul>
 *   <li>Plain keys (no braces)</li>
 *   <li>Valid hash tags — slot is derived from the tag content only</li>
 *   <li>Edge cases: empty tag {@code {}}, unclosed brace, close-only brace</li>
 *   <li>Multiple brace pairs — only the first valid tag is used</li>
 *   <li>Consistency across all three overloads: {@code String}, {@code byte[]}, {@code ByteBuf}</li>
 *   <li>Null input → slot 0</li>
 * </ul>
 */
class SlotCalculatorTest {

    // -------------------------------------------------------------------------
    // Null safety
    // -------------------------------------------------------------------------

    @Test
    void calcSlot_nullString_returnsZero() {
        assertThat(SlotCalculator.calcSlot((String) null)).isEqualTo(0);
    }

    @Test
    void calcSlot_nullByteArray_returnsZero() {
        assertThat(SlotCalculator.calcSlot((byte[]) null)).isEqualTo(0);
    }

    @Test
    void calcSlot_nullByteBuf_returnsZero() {
        assertThat(SlotCalculator.calcSlot((ByteBuf) null)).isEqualTo(0);
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
    void calcSlot_plainKey_returnsCorrectSlot(String key, int expectedSlot) {
        assertThat(SlotCalculator.calcSlot(key)).isEqualTo(expectedSlot);
    }

    // -------------------------------------------------------------------------
    // Hash-tag extraction: slot must equal slot of the tag content alone
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" should hash same as \"{1}\" -> slot {2}")
    @CsvSource({
        // key with tag,          tag content alone,  expected slot
        "{foo},                   foo,                12182",
        "{foo}.bar,               foo,                12182",
        "foo.{bar}.baz,           bar,                5061",
        "{key}:1,                 key,                12539",
        "{key}:2,                 key,                12539",  // different suffix, same tag -> same slot
    })
    void calcSlot_hashTag_usesTagContentForSlot(String keyWithTag, String tagContent, int expectedSlot) {
        assertThat(SlotCalculator.calcSlot(keyWithTag))
                .as("key '%s' should hash same as tag content '%s'", keyWithTag, tagContent)
                .isEqualTo(expectedSlot);
        assertThat(SlotCalculator.calcSlot(tagContent))
                .as("tag content '%s' alone should produce same slot", tagContent)
                .isEqualTo(expectedSlot);
    }

    // -------------------------------------------------------------------------
    // Hash-tag edge cases: tag must NOT be extracted
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "edge case \"{0}\" -> whole-key slot {1}")
    @CsvSource({
        // Empty braces: {} -> whole key hashed
        "{},          15257",
        "{}.empty,     5271",
        // Unclosed / reverse braces: whole key hashed
        "{,            4092",
        "},           12090",
        "{foo,        13308",
    })
    void calcSlot_invalidHashTag_hashesWholeKey(String key, int expectedSlot) {
        assertThat(SlotCalculator.calcSlot(key)).isEqualTo(expectedSlot);
    }

    // -------------------------------------------------------------------------
    // Multiple brace pairs: only the FIRST valid tag is used
    // -------------------------------------------------------------------------

    @Test
    void calcSlot_multipleBracePairs_onlyFirstTagUsed() {
        // "a{b}c{d}e" — first tag is "b" (slot 3300), second tag "d" is ignored
        int slotOfB = SlotCalculator.calcSlot("b");
        assertThat(SlotCalculator.calcSlot("a{b}c{d}e")).isEqualTo(slotOfB);
        assertThat(SlotCalculator.calcSlot("a{b}c{d}e")).isEqualTo(3300);
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
    void calcSlot_allOverloads_returnSameSlot(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);

        try {
            int slotFromString    = SlotCalculator.calcSlot(key);
            int slotFromByteArray = SlotCalculator.calcSlot(bytes);
            int slotFromByteBuf   = SlotCalculator.calcSlot(buf);

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
    void calcSlot_result_isWithinValidSlotRange(String key) {
        int slot = SlotCalculator.calcSlot(key);
        assertThat(slot)
                .as("slot for '%s' must be in [0, %d)", key, MasterSlaveConnectionManager.MAX_SLOT)
                .isGreaterThanOrEqualTo(0)
                .isLessThan(MasterSlaveConnectionManager.MAX_SLOT);
    }

    // -------------------------------------------------------------------------
    // ByteBuf: reader index must not advance (non-destructive read)
    // -------------------------------------------------------------------------

    @Test
    void calcSlot_byteBuf_doesNotAdvanceReaderIndex() {
        byte[] bytes = "foo.{bar}.baz".getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        int readerIndexBefore = buf.readerIndex();
        try {
            SlotCalculator.calcSlot(buf);
            assertThat(buf.readerIndex())
                    .as("calcSlot must not advance the ByteBuf readerIndex")
                    .isEqualTo(readerIndexBefore);
        } finally {
            buf.release();
        }
    }

}

