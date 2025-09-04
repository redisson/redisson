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
package org.redisson.api.stream;

import org.redisson.api.StreamMessageId;

/**
 *
 * @author seakider
 *
 */
public class StreamAckParams extends BaseReferencesParams<StreamAckArgs> implements StreamAckArgs, StreamMessageIdArgs {
    private final String groupName;
    private StreamMessageId[] ids;

    public StreamAckParams(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public StreamAckArgs ids(StreamMessageId... ids) {
        this.ids = ids;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public StreamMessageId[] getIds() {
        return ids;
    }
}
