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
package org.redisson.api.bloomfilter;

/**
 * BloomFilter ScanDump Info for BF.SCANDUMP
 * if returned iterator is 0 and data is empty, iteration is complete
 *
 * @author Su Ko
 *
 */
public class BloomFilterScanDumpInfo {
    private final long iterator;
    private final byte[] data;

    public BloomFilterScanDumpInfo(long iterator, byte[] data) {
        this.iterator = iterator;
        this.data = data;
    }

    public long getIterator() {
        return iterator;
    }

    public byte[] getData() {
        return data;
    }
}
