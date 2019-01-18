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

import javax.servlet.ServletException;

import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

/**
 * Redisson Valve object for Apache Tomcat
 * 
 * @author Nikita Koksharov
 *
 */
public class UpdateValve extends ValveBase {

    private final RedissonSessionManager manager;
    
    public UpdateValve(RedissonSessionManager manager) {
        super();
        this.manager = manager;
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        String sessionId = request.getRequestedSessionId();
        Session session = request.getContext().getManager().findSession(sessionId);
        if (session != null) {
            if (!session.isValid()) {
                session.expire();
                request.getContext().getManager().remove(session);
            } else {
                manager.add(session);
                session.access();
                session.endAccess();
            }
        }
        
        try {
            getNext().invoke(request, response);
        } finally {
            manager.store(request.getSession(false));
        }
    }

}
