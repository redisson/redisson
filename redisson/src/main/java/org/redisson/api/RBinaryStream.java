/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * Binary stream holder stores a sequence of bytes.
 * Maximum size of stream is limited to 512Mb.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RBinaryStream extends RBucket<byte[]> {

    /**
     * Returns async channel object which allows to write and read binary stream.
     * This object isn't thread-safe.
     *
     * @return channel object
     */
    AsynchronousByteChannel getAsynchronousChannel();

    /**
     * Returns channel object which allows to write and read binary stream.
     * This object isn't thread-safe.
     *
     * @return channel object
     */
    SeekableByteChannel getChannel();

    /**
     * Returns inputStream object which allows to read binary stream.
     * This object isn't thread-safe.
     * 
     * @return stream object
     */
    InputStream getInputStream();

    /**
     * Returns outputStream object which allows to write binary stream.
     * This object isn't thread-safe.
     * 
     * @return stream object
     */
    OutputStream getOutputStream();
    
}
