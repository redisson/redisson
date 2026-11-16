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

import org.redisson.api.SyncArgs;

import java.time.Duration;

/**
 * Interface defining visibility parameters.
 *
 * @author Nikita Koksharov
 *
 */
public interface VisibilityStepArgs {

    /**
     * Sets the visibility timeout for retrieved messages.
     * <p>
     * The visibility timeout specifies how long a message will be hidden from other consumers
     * after it has been retrieved but before it has been acknowledged or negatively acknowledged. This prevents other
     * consumers from processing the same message while it's being handled.
     *
     * @param value the duration for which retrieved messages should remain invisible to other consumers
     * @return arguments object
     */
    VisibilityArgs visibility(Duration value);


}
