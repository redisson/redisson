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

import org.apache.catalina.Manager;
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

    private static final String ALREADY_FILTERED_NOTE = UpdateValve.class.getName() + ".ALREADY_FILTERED_NOTE";
    
    public UpdateValve() {
        super(true);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        //check if we already filtered/processed this request 
        if (request.getNote(ALREADY_FILTERED_NOTE) == null) {
            request.setNote(ALREADY_FILTERED_NOTE, Boolean.TRUE);
            try {
                getNext().invoke(request, response);
            } finally {
                request.removeNote(ALREADY_FILTERED_NOTE);
                final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try {
                    ClassLoader applicationClassLoader = request.getContext().getLoader().getClassLoader();
                    Thread.currentThread().setContextClassLoader(applicationClassLoader);
                    Manager manager = request.getContext().getManager();
                    ((RedissonSessionManager)manager).store(request.getSession(false));
                } finally {
                    Thread.currentThread().setContextClassLoader(classLoader);
                }
            }
        } else {
            getNext().invoke(request, response);
        }
    }

}
