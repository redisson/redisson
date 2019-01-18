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

import java.io.Serializable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceResponse implements RRemoteServiceResponse, Serializable {

    private static final long serialVersionUID = -1958922748139674253L;
    
    private Object result;
    private Throwable error;
    private String id;
    
    public RemoteServiceResponse() {
    }
    
    public RemoteServiceResponse(String id, Object result) {
        this.result = result;
        this.id = id;
    }

    public RemoteServiceResponse(String id, Throwable error) {
        this.error = error;
        this.id = id;
    }
    
    @Override
    public String getId() {
        return id;
    }

    public Throwable getError() {
        return error;
    }
    
    public Object getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "RemoteServiceResponse [result=" + result + ", error=" + error + "]";
    }
    
}
