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

import java.util.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SearchResultDecoderV2 implements MultiDecoder<Object> {

    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return new SearchResult(0, Collections.emptyList());
        }

        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                m.put(parts.get(i-1).toString(), parts.get(i));
            }
        }

        List<Document> docs = new ArrayList<>();
        List<Map<String, Object>> results = (List<Map<String, Object>>) m.get("results");
        for (Map<String, Object> result : results) {
            String id = (String) result.get("id");
            Map<String, Object> attrs = (Map<String, Object>) result.get("extra_attributes");
            docs.add(new Document(id, attrs));
        }
        Long total = (Long) m.get("total_results");
        return new SearchResult(total, docs);
    }

}
