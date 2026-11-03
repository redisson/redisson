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

import org.redisson.api.search.query.ScoreExplanation;
import org.redisson.api.search.query.hybrid.HybridSearchResult;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.misc.Tuple;

import java.util.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class HybridSearchResultDecoder implements MultiDecoder<Object> {

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size, List<Object> parts) {
        if (state.getLevel() == 2) {
            if (paramNum % 2 != 0) {
                return codec.getMapValueDecoder();
            }
            return codec.getMapKeyDecoder();
        }
        if (state.getLevel() > 2) {
            return StringCodec.INSTANCE.getValueDecoder();
        }
        return MultiDecoder.super.getDecoder(codec, paramNum, state, size, parts);
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        if (state.getLevel() == 2) {
            List<Object> score = null;
            int start = 0;
            if (parts.size() > 1 && "score".equals(parts.get(0)) && parts.get(1) instanceof List) {
                score = (List<Object>) parts.get(1);
                start = 2;
            }
            Map<Object, Object> fields = MultiDecoder.newLinkedHashMap((parts.size() - start) / 2);
            for (int i = start + 1; i < parts.size(); i += 2) {
                fields.put(parts.get(i - 1), parts.get(i));
            }
            return new Tuple<Map, List>(fields, score);
        }
        if (state.getLevel() > 0) {
            return parts;
        }

        if (parts.isEmpty()) {
            return new HybridSearchResult(0, Collections.emptyList());
        }

        Long total = (Long) parts.get(1);
        List<Tuple> rows = Collections.emptyList();
        if (total > 0) {
            rows = (List<Tuple>) parts.get(3);
        }

        List<Map<String, String>> docs = new ArrayList<>(rows.size());
        List<ScoreExplanation> explanations = new ArrayList<>(rows.size());
        boolean explained = false;
        for (Tuple<Map, List> row : rows) {
            docs.add(row.getT1());
            if (row.getT2() == null) {
                explanations.add(null);
            } else {
                explained = true;
                explanations.add(toExplanation(row.getT2().get(1)));
            }
        }
        if (!explained) {
            explanations = Collections.emptyList();
        }

        return new HybridSearchResult(total, docs, explanations);
    }

    private static ScoreExplanation toExplanation(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof List) {
            List<Object> pair = (List<Object>) node;
            List<Object> children = (List<Object>) pair.get(1);
            List<ScoreExplanation> details = new ArrayList<>(children.size());
            for (Object child : children) {
                details.add(toExplanation(child));
            }
            return new ScoreExplanation((String) pair.get(0), details);
        }
        return new ScoreExplanation((String) node, Collections.emptyList());
    }

}
