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
package org.redisson.api.stream;

import org.redisson.api.StreamMessageId;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class StreamCreateGroupParams implements StreamCreateGroupArgs {

    private final String name;
    private boolean makeStream;
    private int entriesRead;
    private StreamMessageId id = StreamMessageId.NEWEST;

    public StreamCreateGroupParams(String value) {
        this.name = value;
    }

    @Override
    public StreamCreateGroupArgs entriesRead(int amount) {
        this.entriesRead = amount;
        return this;
    }

    @Override
    public StreamCreateGroupArgs makeStream() {
        this.makeStream = true;
        return this;
    }

    @Override
    public StreamCreateGroupArgs id(StreamMessageId id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public boolean isMakeStream() {
        return makeStream;
    }

    public int getEntriesRead() {
        return entriesRead;
    }

    public StreamMessageId getId() {
        return id;
    }
}
