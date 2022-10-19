/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Result object for autoClaim request.
 *
 * @see RStream#fastAutoClaim(String, String, long, TimeUnit, StreamMessageId, int)
 * @see RStreamAsync#fastAutoClaimAsync(String, String, long, TimeUnit, StreamMessageId, int)
 * 
 * @author Nikita Koksharov
 *
 */
public class FastAutoClaimResult implements Serializable {

    private static final long serialVersionUID = -5525031552305408248L;

    private StreamMessageId nextId;
    private List<StreamMessageId> ids;

    public FastAutoClaimResult() {
    }

    public FastAutoClaimResult(StreamMessageId nextId, List<StreamMessageId> ids) {
        super();
        this.nextId = nextId;
        this.ids = ids;
    }

    public StreamMessageId getNextId() {
        return nextId;
    }

    public List<StreamMessageId> getIds() {
        return ids;
    }
}
