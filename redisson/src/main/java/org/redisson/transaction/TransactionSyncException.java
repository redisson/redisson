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
package org.redisson.transaction;

/**
 * Thrown when transaction has been successfully committed to master node(s),
 * but specified amount of replicas failed to acknowledge the synchronization within timeout.
 *
 * @author Nikita Koksharov
 * @author Ronit Gupta
 *
 */
public class TransactionSyncException extends TransactionException {

    private static final long serialVersionUID = 5128892147392819421L;

    public TransactionSyncException(String message) {
        super(message);
    }

    public TransactionSyncException(String message, Throwable cause) {
        super(message, cause);
    }

}
