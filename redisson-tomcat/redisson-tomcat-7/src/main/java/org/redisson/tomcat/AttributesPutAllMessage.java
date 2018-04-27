/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.tomcat;

import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AttributesPutAllMessage extends AttributeMessage {

    private Map<String, Object> attrs;
    
    public AttributesPutAllMessage() {
    }

    public AttributesPutAllMessage(String sessionId, Map<String, Object> attrs) {
        super(sessionId);
        this.attrs = attrs;
    }
    
    public Map<String, Object> getAttrs() {
        return attrs;
    }

}
