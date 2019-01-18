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
package org.redisson.executor.params;

import java.io.Serializable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class TaskParameters implements Serializable {

    private static final long serialVersionUID = -5662511632962297898L;
    
    private String className;
    private byte[] classBody;
    private byte[] lambdaBody;
    private byte[] state;
    private String requestId;

    public TaskParameters() {
    }
    
    public TaskParameters(String className, byte[] classBody, byte[] lambdaBody, byte[] state) {
        super();
        this.className = className;
        this.classBody = classBody;
        this.state = state;
        this.lambdaBody = lambdaBody;
    }
    
    public byte[] getLambdaBody() {
        return lambdaBody;
    }
    public void setLambdaBody(byte[] lambdaBody) {
        this.lambdaBody = lambdaBody;
    }

    public String getClassName() {
        return className;
    }
    public void setClassName(String className) {
        this.className = className;
    }
    
    public byte[] getClassBody() {
        return classBody;
    }
    public void setClassBody(byte[] classBody) {
        this.classBody = classBody;
    }
    
    public byte[] getState() {
        return state;
    }
    public void setState(byte[] state) {
        this.state = state;
    }
    
    public String getRequestId() {
        return requestId;
    }
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
}
