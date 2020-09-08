/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;

/**
 * Redisson Valve object for Apache Tomcat
 * 
 * @author Nikita Koksharov
 *
 */
public class UsageValve extends ValveBase {

    private static final String ALREADY_FILTERED_NOTE = UsageValve.class.getName() + ".ALREADY_FILTERED_NOTE";

    public UsageValve() {
        super(true);
    }

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        if (getNext() == null) {
            return;
        }

        //check if we already filtered/processed this request
        if (request.getNote(ALREADY_FILTERED_NOTE) == null) {
            request.setNote(ALREADY_FILTERED_NOTE, Boolean.TRUE);
            RedissonSession s = null;
            try {
                getRedissonSession(request).ifPresent(RedissonSession::startUsage);
                getNext().invoke(request, response);
            } finally {
                request.removeNote(ALREADY_FILTERED_NOTE);
                getRedissonSession(request).ifPresent(RedissonSession::endUsage);
            }
        } else {
            getNext().invoke(request, response);
        }
    }

    private Optional<RedissonSession> getRedissonSession(Request request) throws IOException {
        HttpSession session = request.getSession(false);
        if(session != null) {
            return Optional.ofNullable((RedissonSession)request.getContext().getManager().findSession(session.getId()));
        } else {
            return Optional.empty();
        }
    }

}
