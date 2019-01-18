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
package org.redisson.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceKey {

    private final Class<?> serviceInterface;
    private final String methodName;
    private final List<String> signatures;

    public RemoteServiceKey(Class<?> serviceInterface, String method, List<String> signatures) {
        super();
        this.serviceInterface = serviceInterface;
        this.methodName = method;
        this.signatures = Collections.unmodifiableList(signatures);
    }
    
    public String getMethodName() {
        return methodName;
    }

    public List<String> getSignatures() {
        return signatures;
    }

    public Class<?> getServiceInterface() {
        return serviceInterface;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
        result = prime * result + ((signatures == null) ? 0 : signatures.hashCode());
        result = prime * result + ((serviceInterface == null) ? 0 : serviceInterface.getName().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RemoteServiceKey other = (RemoteServiceKey) obj;
        if (methodName == null) {
            if (other.methodName != null)
                return false;
        } else if (!methodName.equals(other.methodName)) {
            return false;
        } else if (!signatures.equals(other.signatures)) {
            return false;
        } if (serviceInterface == null) {
            if (other.serviceInterface != null)
                return false;
        } else if (!serviceInterface.equals(other.serviceInterface))
            return false;
        return true;
    }
    
}
