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
/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.redisson.quarkus.client.it;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.redisson.api.*;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.codec.StringCodec;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutionException;

@Path("/quarkus-redisson-client")
public class QuarkusRedissonClientResource {

    @Inject
    RedissonClient redisson;

    @Inject
    CachedService cachedService;

    @GET
    @Path("/cacheResult")
    public Boolean cacheResult() {
        assert redisson.getKeys().count() == 0;

        String val1 = cachedService.cache1("key1");
        String val2 = cachedService.cache1("key1");

        Long val3 = cachedService.cache2(0L);
        Long val4 = cachedService.cache2(0L);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assert redisson.getKeys().count() >= 2;

        return val1.equals(val2) && val3.equals(val4);
    }

}
