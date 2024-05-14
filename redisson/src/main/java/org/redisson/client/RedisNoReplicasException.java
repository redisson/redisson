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
package org.redisson.client;

/**
 * This error occurs when Redis a redis master has insufficient slaves to handle a request.
 * *
 */
public class RedisNoReplicasException extends RedisRetryException {

    private static final long serialVersionUID = -5658453331593029252L;

    public RedisNoReplicasException(String message) {
        super(message);
    }
}
