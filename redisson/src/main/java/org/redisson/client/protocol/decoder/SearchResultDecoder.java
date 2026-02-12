/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.client.protocol.decoder;

import org.redisson.api.search.query.Document;
import org.redisson.api.search.query.SearchResult;
import org.redisson.client.handler.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SearchResultDecoder implements MultiDecoder<Object> {

    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return new SearchResult(0, Collections.emptyList());
        }

        Long total = (Long) parts.get(0);
        List<Document> docs = new ArrayList<>();
        if (total > 0) {
            for (int i = 1; i < parts.size(); i++) {
                String id = (String) parts.get(i);
                if ((i + 1) < parts.size() && parts.get(i + 1) instanceof Map) {
                    Map<String, Object> attrs = (Map<String, Object>) parts.get(++i);
                    docs.add(new Document(id, attrs));
                } else {
                    docs.add(new Document(id));
                }
            }
        }

        return new SearchResult(total, docs);
    }

}
