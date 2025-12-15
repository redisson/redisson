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

/**
 * Arguments object for {@link org.redisson.api.RStream#createGroup(StreamCreateGroupArgs)} method.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamCreateGroupArgs {

    /**
     * Defines entries_read argument
     *
     * @param amount entries_read argument
     * @return arguments object
     */
    StreamCreateGroupArgs entriesRead(int amount);

    /**
     * Defines whether a stream should be created if it doesn't exist.
     *
     * @return arguments object
     */
    StreamCreateGroupArgs makeStream();

    /**
     * Defines Stream Message ID.
     * Only new messages after defined stream <code>id</code> will
     * be available for consumers of this group.
     * <p>
     * {@link StreamMessageId#NEWEST} is used for messages arrived since the moment of group creation
     * <p>
     * {@link StreamMessageId#ALL} is used for all messages added before and after the moment of group creation
     *
     * @param id Stream Message ID
     * @return arguments object
     */
    StreamCreateGroupArgs id(StreamMessageId id);

    /**
     * Defines name of group.
     * Only new messages will be available for consumers of this group.
     *
     * @param value name of group
     * @return arguments object
     */
    static StreamCreateGroupArgs name(String value) {
        return new StreamCreateGroupParams(value);
    }

}
