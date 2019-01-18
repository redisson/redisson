/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
public enum SslProvider {

    /**
     * Use JDK default implementation to handle SSL connection
     */
    JDK,
    
    /**
     * Use OpenSSL-based implementation to handle SSL connection.
     * <code>netty-tcnative</code> lib is required to be in classpath.
     */
    OPENSSL
    
}
