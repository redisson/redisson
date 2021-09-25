/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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

import io.micrometer.core.instrument.util.StringUtils;
import org.redisson.api.LockInfo;
import org.redisson.client.handler.State;

import java.util.List;

/**
 * @author Sergey Kurenchuk
 */
public class LockInfoDecoder implements MultiDecoder<LockInfo> {

    private static class FreeLockInfo extends LockInfo {

        FreeLockInfo() {
            super(null, null, null);
        }

        @Override
        public boolean isLocked() {
            return false;
        }
    }

    @Override
    public LockInfo decode(List<Object> parts, State state) {
        if (parts.size() != 2) {
            return new FreeLockInfo();
        }
        String lockOwnerInfo = parts.get(0).toString();
        String expiredInMillis = parts.get(1).toString();
        if (StringUtils.isBlank(lockOwnerInfo)) {
            return new FreeLockInfo();
        }
        String[] split = lockOwnerInfo.split(":");
        if (split.length != 2) {
            return new FreeLockInfo();
        }
        String connectionId = split[0];
        Long threadOwnerId = Long.valueOf(split[1]);
        Long expired = Long.valueOf(expiredInMillis);
        return new LockInfo(connectionId, threadOwnerId, expired);
    }
}