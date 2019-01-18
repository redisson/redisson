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
package org.redisson.api;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary stream holder. Maximum size of stream is limited by available memory of Redis master node.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RBinaryStream extends RBucket<byte[]> {

    /**
     * Returns inputStream which reads binary stream.
     * This stream isn't thread-safe.
     * 
     * @return stream
     */
    InputStream getInputStream();

    /**
     * Returns outputStream which writes binary stream.
     * This stream isn't thread-safe.
     * 
     * @return stream
     */
    OutputStream getOutputStream();
    
}
