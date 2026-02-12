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
package org.redisson.config;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public enum ReadMode {

    /**
     * Read from slave nodes. Uses MASTER if no SLAVES are available.
     * Node is selected using specified <code>loadBalancer</code> in Redisson configuration.
     */
    SLAVE,

    /**
     * Read from master node
     */
    MASTER,

    /**
     * Read from master and slave nodes.
     * Node is selected using specified <code>loadBalancer</code> in Redisson configuration.
     */
    MASTER_SLAVE,

}
