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
package org.redisson.codec;

import java.io.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CustomObjectInputStream extends ObjectInputStream {

    private final ClassLoader classLoader;
    private Set<String> allowedClasses;

    public CustomObjectInputStream(ClassLoader classLoader, InputStream in, Set<String> allowedClasses) throws IOException {
        super(in);
        this.classLoader = classLoader;
        this.allowedClasses = allowedClasses;
    }

    public CustomObjectInputStream(ClassLoader classLoader, InputStream in) throws IOException {
        super(in);
        this.classLoader = classLoader;
    }
    
    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        try {
            String name = desc.getName();
            if (allowedClasses != null && !allowedClasses.contains(name)) {
                throw new InvalidClassException("Class " + name + " isn't allowed");
            }
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            return super.resolveClass(desc);
        }
    }
    
    @Override
    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
        List<Class<?>> loadedClasses = new ArrayList<Class<?>>(interfaces.length);
        
        for (String name : interfaces) {
            Class<?> clazz = Class.forName(name, false, classLoader);
            loadedClasses.add(clazz);
        }
        
        return Proxy.getProxyClass(classLoader, loadedClasses.toArray(new Class[0]));
    }
    
}
