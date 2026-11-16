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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Provides message acknowledgment operations for message processing.
 *
 * @author Nikita Koksharov
 *
 */
public interface AcknowledgmentRx {

    /**
     * Acknowledges the successful processing of a message.
     *
     * @param args acknowledgment arguments
     * @return list of acknowledged message IDs
     */
    Single<List<String>> acknowledge(MessageAckArgs args);


    /**
     * Explicitly marks a message as failed or rejected.
     *
     * @param args arguments specifying the message to negatively acknowledge
     * @return list of negatively acknowledged message IDs
     */
    Single<List<String>> negativeAcknowledge(MessageNegativeAckArgs args);

    /**
     * Updates visibility by message ID.
     *
     * @param args visibility arguments
     * @return <code>true</code> if visibility has been updated.
     *         <code>false</code> if message id doesn't belong to the owner or doesn't exist.
     */
    Mono<Boolean> updateVisibility(VisibilityArgs args);

}
