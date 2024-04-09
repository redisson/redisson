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
package org.redisson;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class VoidSlotCallback implements SlotCallback<Void, Void> {

    private final AtomicBoolean r = new AtomicBoolean();

    private final Object[] params;

    public VoidSlotCallback() {
        this(null);
    }

    public VoidSlotCallback(Object[] params) {
        this.params = params;
    }

    @Override
    public void onSlotResult(List<Object> keys, Void res) {
    }

    @Override
    public Void onFinish() {
        return null;
    }

    @Override
    public Object[] createParams(List<Object> params) {
        if (this.params != null) {
            return this.params;
        }
        return SlotCallback.super.createParams(params);
    }
}
