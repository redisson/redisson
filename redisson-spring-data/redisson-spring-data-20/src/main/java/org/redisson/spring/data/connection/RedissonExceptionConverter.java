/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.spring.data.connection;

import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisRedirectException;
import org.redisson.client.RedisTimeoutException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.ClusterRedirectException;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * Converts Redisson exceptions to Spring compatible
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExceptionConverter implements Converter<Exception, DataAccessException> {

    @Override
    public DataAccessException convert(Exception source) {
        if (source instanceof RedisConnectionException) {
            return new RedisConnectionFailureException(source.getMessage(), source);
        }
        if (source instanceof RedisRedirectException) {
            RedisRedirectException ex = (RedisRedirectException) source;
            return new ClusterRedirectException(ex.getSlot(), ex.getUrl().getHost(), ex.getUrl().getPort(), source);
        }

        if (source instanceof RedisException) {
            return new InvalidDataAccessApiUsageException(source.getMessage(), source);
        }
        
        if (source instanceof DataAccessException) {
            return (DataAccessException) source;
        }
        
        if (source instanceof RedisTimeoutException) {
            return new QueryTimeoutException(source.getMessage(), source);
        }

        return null;
    }

}
