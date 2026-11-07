package org.redisson.client.protocol.decoder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ContainsDecoderTest {

    @Test
    void shouldNotThrowWhenCallerModifiesKeysListBeforeDecode() {
        List<String> keys = new ArrayList<>(Arrays.asList("lock1", "lock2"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(keys);

        // Simulate LockTask.renew() completion mutating the keys list before or during decode
        keys.clear();

        assertThatCode(() -> {
            List<String> result = decoder.decode(Arrays.asList(1L, 0L), null);
            assertThat(result).containsExactly("lock1");
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeCorrectKeys() {
        List<String> keys = Arrays.asList("a", "b", "c");
        ContainsDecoder<String> decoder = new ContainsDecoder<>(keys);

        List<String> result = decoder.decode(Arrays.asList(1L, 0L, 1L), null);
        assertThat(result).containsExactly("a", "c");
    }
}
