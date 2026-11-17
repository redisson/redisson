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
package org.redisson.api.pubsub;

import org.redisson.api.RFuture;

import java.util.List;

/**
 * Provides message acknowledgment operations for message processing.
 *
 * @author Nikita Koksharov
 *
 */
public interface AcknowledgmentAsync {

    /**
     * Acknowledges the successful processing of a message.
     * <p>
     * Message owner isn't checked.
     *
     * @param args acknowledgment arguments
     * @return list of acknowledged message IDs
     */
    RFuture<List<String>> acknowledgeAsync(MessageAckArgs args);


    /**
     * Explicitly marks a message as failed or rejected.
     * <p>
     * Message owner isn't checked.
     *
     * @param args arguments specifying the message to negatively acknowledge
     * @return list of negatively acknowledged message IDs
     */
    RFuture<List<String>> negativeAcknowledgeAsync(MessageNegativeAckArgs args);

    /**
     * Updates visibility by message ID.
     * <p>
     * Message owner is checked.
     *
     * @param args visibility arguments
     * @return <code>true</code> if visibility has been updated.
     *         <code>false</code> if message id doesn't belong to the owner or doesn't exist.
     */
    RFuture<Boolean> updateVisibilityAsync(VisibilityArgs args);

}
