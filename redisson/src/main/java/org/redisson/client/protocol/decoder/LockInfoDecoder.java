package org.redisson.client.protocol.decoder;

import io.micrometer.core.instrument.util.StringUtils;
import org.redisson.api.LockInfo;
import org.redisson.client.handler.State;

import java.util.List;

/**
 * @author Sergey Kurenchuk
 */
public class LockInfoDecoder implements MultiDecoder<LockInfo>{

    @Override
    public LockInfo decode(List<Object> parts, State state) {
        assert parts.size() == 2;
        String lockOwnerInfo = parts.get(0).toString();
        String expiredInMillis = parts.get(1).toString();
        assert !StringUtils.isBlank(lockOwnerInfo);
        String[] split = lockOwnerInfo.split(":");
        assert split.length == 2;
        String connectionId = split[0];
        Long threadOwnerId = Long.valueOf(split[1]);
        Long expired = Long.valueOf(expiredInMillis);
        return new LockInfo(connectionId, threadOwnerId, expired);
    }
}
