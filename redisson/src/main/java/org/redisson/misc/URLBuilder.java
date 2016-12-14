/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.misc;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class URLBuilder {

    private static volatile boolean init = false; 
    
    static {
        init();
    }
    
    public static void init() {
        if (init) {
            return;
        }
        init = true;
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if ("redis".equals(protocol)) {
                    return new URLStreamHandler() {
                        @Override
                        protected URLConnection openConnection(URL u) throws IOException {
                            throw new UnsupportedOperationException();
                        };
                        
                        @Override
                        protected boolean equals(URL u1, URL u2) {
                            return u1.toString().equals(u2.toString());
                        }
                        
                        @Override
                        protected int hashCode(URL u) {
                            return u.toString().hashCode();
                        }
                        
                    };
                }
                return null;
            }
        });
    }
    
    public static URL create(String url) {
        try {
            String[] parts = url.split(":");
            if (parts.length-1 >= 3) {
                String port = parts[parts.length-1];
                String newPort = port.split("[^\\d]")[0];
                String host = url.replace(":" + port, "");
                return new URL("redis://[" + host + "]:" + newPort);
            } else {
                String port = parts[parts.length-1];
                String newPort = port.split("[^\\d]")[0];
                String host = url.replace(":" + port, "");
                return new URL("redis://" + host + ":" + newPort);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
