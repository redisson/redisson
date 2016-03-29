/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

public class RemoteServiceRequest {

    private String requestId;
    private String methodName;
    private Object[] args;
    private long ackTimeout;
    private long responseTimeout;
    private long date;
    
    
    public RemoteServiceRequest() {
    }
    
    public RemoteServiceRequest(String requestId, String methodName, Object[] args, long ackTimeout, long responseTimeout, long date) {
        super();
        this.requestId = requestId;
        this.methodName = methodName;
        this.args = args;
        this.ackTimeout = ackTimeout;
        this.responseTimeout = responseTimeout;
        this.date = date;
    }
    
    public long getResponseTimeout() {
        return responseTimeout;
    }
    
    public long getDate() {
        return date;
    }
    
    public long getAckTimeout() {
        return ackTimeout;
    }
    
    public String getRequestId() {
        return requestId;
    }

    public Object[] getArgs() {
        return args;
    }
    
    public String getMethodName() {
        return methodName;
    }

    @Override
    public String toString() {
        return "RemoteServiceRequest [requestId=" + requestId + ", methodName=" + methodName + ", args="
                + Arrays.toString(args) + ", ackTimeout=" + ackTimeout + ", date=" + date + "]";
    }

}
