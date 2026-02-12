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
package org.redisson.transaction.operation.bucket;

import org.redisson.api.RBuckets;
import org.redisson.api.bucket.SetArgs;
import org.redisson.client.codec.Codec;

/**
 *
 * @author seakider
 *
 */
public class BucketsSetIfAllKeysExistOperation extends BucketsSetOperation {

    public BucketsSetIfAllKeysExistOperation(Codec codec, SetArgs setArgs, String transactionId) {
        super(codec, setArgs, transactionId);
    }

    @Override
    protected void commit(RBuckets bucket, SetArgs setArgs) {
        bucket.setIfAllKeysExistAsync(setArgs);
    }
}
