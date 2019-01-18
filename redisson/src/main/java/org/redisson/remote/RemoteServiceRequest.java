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
import java.util.Arrays;
import java.util.List;

import org.redisson.api.RemoteInvocationOptions;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RemoteServiceRequest implements Serializable {

    private static final long serialVersionUID = -1711385312384040075L;
    
    private String id;
    private String executorId;
    private String methodName;
    private List<String> signatures;
    private Object[] args;
    private RemoteInvocationOptions options;
    private long date;
    
    
    public RemoteServiceRequest() {
    }
    
    public RemoteServiceRequest(String id) {
        this.id = id;
    }
    
    public RemoteServiceRequest(String executorId, String id, String methodName, List<String> signatures, Object[] args, RemoteInvocationOptions options, long date) {
        super();
        this.id = id;
        this.executorId = executorId;
        this.methodName = methodName;
        this.signatures = signatures;
        this.args = args;
        this.options = options;
        this.date = date;
    }
    
    public long getDate() {
        return date;
    }
    
    public String getExecutorId() {
        return executorId;
    }
    
    public String getId() {
        return id;
    }

    public Object[] getArgs() {
        return args;
    }

    public List<String> getSignatures() {
        return signatures;
    }

    public RemoteInvocationOptions getOptions() {
        return options;
    }

    public String getMethodName() {
        return methodName;
    }

    @Override
    public String toString() {
        return "RemoteServiceRequest [requestId=" + id + ", methodName=" + methodName + ", signatures=["
                + Arrays.toString(signatures.toArray()) + "], args="
                + Arrays.toString(args) + ", options=" + options + ", date=" + date + "]";
    }

}
