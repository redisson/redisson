package org.redisson.client.protocol.decoder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainsDecoderTest {

    @Test
    public void testArgsMutationDoesNotAffectDecoding() {
        List<String> keys = new ArrayList<>(Arrays.asList("key1", "key2", "key3"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(keys);

        // Mutate original keys list as occurs in completion callbacks
        keys.clear();

        List<Object> parts = Arrays.asList(1L, 0L, 1L);
        List<String> result = decoder.decode(parts, null);

        assertThat(result).containsExactly("key1", "key3");
    }

    @Test
    public void testBoundsSafetyWhenPartsExceedArgs() {
        List<String> keys = new ArrayList<>(Arrays.asList("key1"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(keys);

        // More parts than args
        List<Object> parts = Arrays.asList(1L, 1L, 1L);
        List<String> result = decoder.decode(parts, null);

        assertThat(result).containsExactly("key1");
    }
}
