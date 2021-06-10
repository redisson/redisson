/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.api.redisnode.RedisNodes;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

@Path("/quarkus-redisson-client")
public class QuarkusRedissonClientResource {

    @Inject
    RedissonClient redisson;

    @GET
    @Path("/map")
    public String map() {
        RMap<String, Integer> m = redisson.getMap("test");
        m.put("1", 2);
        return m.get("1").toString();
    }

    @GET
    @Path("/pingAll")
    public String pingAll() {
        redisson.getRedisNodes(RedisNodes.SINGLE).pingAll();
        return "OK";
    }

    @GET
    @Path("/executeTask")
    public String executeTask() throws ExecutionException, InterruptedException {
        RScheduledExecutorService t = redisson.getExecutorService("test");
        t.registerWorkers(WorkerOptions.defaults());

        RExecutorFuture<String> r = t.submit(new Task());
        return r.get();
    }

}
