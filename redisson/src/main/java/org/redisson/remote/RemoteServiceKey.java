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
package org.redisson.remote;

import java.util.Arrays;
import java.util.Objects;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceKey {

    private final Class<?> serviceInterface;
    private final String methodName;
    private final long[] signature;

    public RemoteServiceKey(Class<?> serviceInterface, String method, long[] signature) {
        super();
        this.serviceInterface = serviceInterface;
        this.methodName = method;
        this.signature = signature;
    }
    
    public String getMethodName() {
        return methodName;
    }

    public long[] getSignature() {
        return signature;
    }
    
    public Class<?> getServiceInterface() {
        return serviceInterface;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemoteServiceKey that = (RemoteServiceKey) o;
        return Objects.equals(serviceInterface, that.serviceInterface)
                    && Objects.equals(methodName, that.methodName)
                        && Objects.deepEquals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInterface, methodName, Arrays.hashCode(signature));
    }
}
