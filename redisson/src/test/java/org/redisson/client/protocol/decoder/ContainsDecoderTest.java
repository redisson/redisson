/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.client.protocol.decoder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ContainsDecoder}, focused on the mutable-argument race that
 * used to break lock watchdog renewal.
 *
 * @author Nikita Koksharov
 */
public class ContainsDecoderTest {

    /**
     * A plain, non-concurrent scenario must keep working: every key reported as
     * present by Redis is mapped back to its argument.
     */
    @Test
    public void testDecodeAllPresent() {
        List<String> args = new ArrayList<>(Arrays.asList("k1", "k2", "k3"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(args);

        List<Object> parts = new ArrayList<>(Arrays.asList(1L, 1L, 1L));

        assertEquals(Arrays.asList("k1", "k2", "k3"), decoder.decode(parts, null));
    }

    /**
     * Only the arguments whose reply element is {@code 1} are returned.
     */
    @Test
    public void testDecodePartiallyPresent() {
        List<String> args = new ArrayList<>(Arrays.asList("k1", "k2", "k3"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(args);

        List<Object> parts = new ArrayList<>(Arrays.asList(1L, 0L, 1L));

        assertEquals(Arrays.asList("k1", "k3"), decoder.decode(parts, null));
    }

    /**
     * An empty reply is tolerated and yields an empty result.
     */
    @Test
    public void testDecodeEmptyParts() {
        ContainsDecoder<String> decoder = new ContainsDecoder<>(new ArrayList<>());

        assertEquals(new ArrayList<>(), decoder.decode(new ArrayList<>(), null));
    }

    /**
     * The decoder must not observe later mutations of the collection it was
     * created with. {@code LockTask.renew()} and {@code ReadLockTask.renew()}
     * pass the same {@code keys} list that the completion callback shrinks via
     * {@code keys.removeAll(existingNames)}; since the reply may be decoded after
     * that callback runs, the decoder used to read a shortened list and threw
     * {@code IndexOutOfBoundsException}.
     */
    @Test
    public void testDecodeIsolatedFromArgumentMutation() {
        List<String> args = new ArrayList<>(Arrays.asList("k1", "k2"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(args);

        // Simulates keys.removeAll(existingNames) in the completion callback
        // running before the reply is decoded.
        args.clear();

        List<Object> parts = new ArrayList<>(Arrays.asList(1L, 1L));

        assertEquals(Arrays.asList("k1", "k2"), decoder.decode(parts, null));
    }

    /**
     * Same race, but the argument list is only shortened instead of cleared,
     * which is what produced "Index 1 out of bounds for length 1" in production.
     */
    @Test
    public void testDecodeIsolatedFromPartialArgumentRemoval() {
        List<String> args = new ArrayList<>(Arrays.asList("k1", "k2", "k3"));
        ContainsDecoder<String> decoder = new ContainsDecoder<>(args);

        args.removeAll(Arrays.asList("k2", "k3"));

        List<Object> parts = new ArrayList<>(Arrays.asList(1L, 1L, 1L));

        assertEquals(Arrays.asList("k1", "k2", "k3"), decoder.decode(parts, null));
    }

    /**
     * Decoding from a concurrent thread while the caller mutates its own list
     * must not surface an IndexOutOfBoundsException.
     */
    @Test
    public void testDecodeConcurrentWithArgumentMutation() throws InterruptedException {
        List<String> args = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            args.add("k" + i);
        }
        ContainsDecoder<String> decoder = new ContainsDecoder<>(args);

        List<Object> parts = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            parts.add(1L);
        }

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread mutator = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                args.remove(args.size() - 1);
            }
        });
        mutator.start();

        try {
            List<String> decoded = decoder.decode(parts, null);
            assertEquals(1000, decoded.size());
        } catch (Throwable t) {
            failure.set(t);
        }

        mutator.join();

        if (failure.get() != null) {
            throw new AssertionError("decode threw " + failure.get(), failure.get());
        }
    }

}
