/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.client.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author seakider
 *
 */
public enum StreamEntryStatus {

    SUCCESS(1),

    ID_NOT_FOUND(-1),

    HAS_PENDING_REFERENCES(2);

    private static final Logger log = LoggerFactory.getLogger(StreamEntryStatus.class);
    private final int status;

    StreamEntryStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static StreamEntryStatus valueOfStatus(int code) {
        for (StreamEntryStatus value : StreamEntryStatus.values()) {
            if (code == value.status)
                return value;
        }

        log.error("unknown status:{}", code);
        return null;
    }
}
