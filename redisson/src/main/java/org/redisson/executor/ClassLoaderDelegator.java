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
package org.redisson.executor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClassLoaderDelegator extends ClassLoader {

    private final ThreadLocal<ClassLoader> threadLocalClassLoader = new ThreadLocal<ClassLoader>();

    public void setCurrentClassLoader(ClassLoader classLoader) {
        threadLocalClassLoader.set(classLoader);
    }
    
    public int hashCode() {
        return threadLocalClassLoader.get().hashCode();
    }

    public boolean equals(Object obj) {
        return threadLocalClassLoader.get().equals(obj);
    }

    public String toString() {
        return threadLocalClassLoader.get().toString();
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return threadLocalClassLoader.get().loadClass(name);
    }

    public URL getResource(String name) {
        return threadLocalClassLoader.get().getResource(name);
    }

    public Enumeration<URL> getResources(String name) throws IOException {
        return threadLocalClassLoader.get().getResources(name);
    }

    public InputStream getResourceAsStream(String name) {
        return threadLocalClassLoader.get().getResourceAsStream(name);
    }

    public void setDefaultAssertionStatus(boolean enabled) {
        threadLocalClassLoader.get().setDefaultAssertionStatus(enabled);
    }

    public void setPackageAssertionStatus(String packageName, boolean enabled) {
        threadLocalClassLoader.get().setPackageAssertionStatus(packageName, enabled);
    }

    public void setClassAssertionStatus(String className, boolean enabled) {
        threadLocalClassLoader.get().setClassAssertionStatus(className, enabled);
    }

    public void clearAssertionStatus() {
        threadLocalClassLoader.get().clearAssertionStatus();
    }

    public void clearCurrentClassLoader() {
        threadLocalClassLoader.remove();
    }
    
}
