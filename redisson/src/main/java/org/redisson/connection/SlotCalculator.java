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
package org.redisson.connection;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

final class SlotCalculator {

    private SlotCalculator() {
    }

    static int calcSlot(byte[] key) {
        if (key == null) {
            return 0;
        }

        int start = indexOf(key, (byte) '{');

        if (start != -1) {
            int end = indexOf(key, (byte) '}');

            if (end != -1 && start + 1 < end) {
                key = Arrays.copyOfRange(key, start + 1, end);
            }
        }

        return CRC16.crc16(key) % MasterSlaveConnectionManager.MAX_SLOT;
    }

    static int calcSlot(ByteBuf key) {
        if (key == null) {
            return 0;
        }

        int start = key.indexOf(key.readerIndex(), key.readerIndex() + key.readableBytes(), (byte) '{');

        if (start != -1) {
            int end = key.indexOf(start + 1, key.readerIndex() + key.readableBytes(), (byte) '}');

            if (end != -1 && start + 1 < end) {
                key = key.slice(start + 1, end - start - 1);
            }
        }

        return CRC16.crc16(key) % MasterSlaveConnectionManager.MAX_SLOT;
    }

    static int calcSlot(String key) {
        if (key == null) {
            return 0;
        }

        int start = key.indexOf('{');

        if (start != -1) {
            int end = key.indexOf('}');

            if (end != -1 && start + 1 < end) {
                key = key.substring(start + 1, end);
            }
        }

        return CRC16.crc16(key.getBytes()) % MasterSlaveConnectionManager.MAX_SLOT;
    }

    private static int indexOf(byte[] array, byte element) {
        for (int i = 0; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }

        return -1;
    }

}
