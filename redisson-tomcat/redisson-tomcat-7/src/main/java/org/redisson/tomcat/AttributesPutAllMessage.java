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
package org.redisson.tomcat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AttributesPutAllMessage extends AttributeMessage {

    private Map<String, byte[]> attrs;
    
    public AttributesPutAllMessage() {
    }

    public AttributesPutAllMessage(String nodeId, String sessionId, Map<String, Object> attrs) throws IOException {
        super(nodeId, sessionId);
        if (attrs != null) {
        	this.attrs = new HashMap<String, byte[]>();
        	for (Entry<String, Object> entry: attrs.entrySet()) {
            	this.attrs.put(entry.getKey(), toByteArray(entry.getValue()));
        	}
        } else {
        	this.attrs = null;
        }
    }

    public Map<String, Object> getAttrs(ClassLoader classLoader) throws IOException, ClassNotFoundException {
    	if (attrs == null) {
    		return null;
    	}
    	Map<String, Object> result = new HashMap<String, Object>();
    	for (Entry<String, byte[]> entry: attrs.entrySet()) {
    		result.put(entry.getKey(), toObject(classLoader, entry.getValue()));
    	}
        return result;
    }

}
