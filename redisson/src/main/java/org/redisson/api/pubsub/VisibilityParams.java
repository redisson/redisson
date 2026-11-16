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

import org.redisson.api.BaseSyncParams;

import java.time.Duration;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class VisibilityParams extends BaseSyncParams<VisibilityArgs> implements VisibilityArgs, VisibilityStepArgs {

    private final String id;
    private Duration visibility;

    public VisibilityParams(String id) {
        Objects.requireNonNull(id);
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Duration getVisibility() {
        return visibility;
    }

    @Override
    public VisibilityArgs visibility(Duration value) {
        visibility = value;
        return this;
    }
}
