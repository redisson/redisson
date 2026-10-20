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

import org.redisson.api.tsnative.TSAggregation;
import org.redisson.api.tsnative.TSChunkInfo;
import org.redisson.api.tsnative.TSDuplicatePolicy;
import org.redisson.api.tsnative.TSEncoding;
import org.redisson.api.tsnative.TSInfo;
import org.redisson.api.tsnative.TSRule;
import org.redisson.client.handler.State;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesInfoDecoder implements MultiDecoder<TSInfo> {

    @Override
    @SuppressWarnings("MethodLength")
    public TSInfo decode(List<Object> parts, State state) {
        long totalSamples = 0;
        long memoryUsage = 0;
        Long firstTimestamp = null;
        Long lastTimestamp = null;
        Duration retentionTime = Duration.ZERO;
        long chunkCount = 0;
        long chunkSize = 0;
        TSEncoding chunkType = null;
        TSDuplicatePolicy duplicatePolicy = null;
        Map<String, String> labels = Collections.emptyMap();
        String sourceKey = null;
        List<TSRule> rules = Collections.emptyList();
        Duration ignoreMaxTimeDiff = Duration.ZERO;
        double ignoreMaxValueDiff = 0;
        String keySelfName = null;
        List<TSChunkInfo> chunks = Collections.emptyList();

        for (int i = 0; i < parts.size() - 1; i += 2) {
            String name = (String) parts.get(i);
            Object value = parts.get(i + 1);
            if (value == null) {
                continue;
            }

            if ("totalSamples".equals(name)) {
                totalSamples = TimeSeriesReplyValues.toLong(value);
            } else if ("memoryUsage".equals(name)) {
                memoryUsage = TimeSeriesReplyValues.toLong(value);
            } else if ("firstTimestamp".equals(name)) {
                firstTimestamp = TimeSeriesReplyValues.toLong(value);
            } else if ("lastTimestamp".equals(name)) {
                lastTimestamp = TimeSeriesReplyValues.toLong(value);
            } else if ("retentionTime".equals(name)) {
                retentionTime = Duration.ofMillis(TimeSeriesReplyValues.toLong(value));
            } else if ("chunkCount".equals(name)) {
                chunkCount = TimeSeriesReplyValues.toLong(value);
            } else if ("chunkSize".equals(name)) {
                chunkSize = TimeSeriesReplyValues.toLong(value);
            } else if ("chunkType".equals(name)) {
                chunkType = TSEncoding.valueOf(value.toString().toUpperCase(Locale.ROOT));
            } else if ("duplicatePolicy".equals(name)) {
                duplicatePolicy = TSDuplicatePolicy.valueOf(value.toString().toUpperCase(Locale.ROOT));
            } else if ("labels".equals(name)) {
                labels = TimeSeriesReplyValues.toLabels((List<Object>) value);
            } else if ("sourceKey".equals(name)) {
                sourceKey = value.toString();
            } else if ("rules".equals(name)) {
                rules = toRules((List<Object>) value);
            } else if ("ignoreMaxTimeDiff".equals(name)) {
                ignoreMaxTimeDiff = Duration.ofMillis(TimeSeriesReplyValues.toLong(value));
            } else if ("ignoreMaxValDiff".equals(name)) {
                ignoreMaxValueDiff = TimeSeriesReplyValues.toDouble(value);
            } else if ("keySelfName".equals(name)) {
                keySelfName = value.toString();
            } else if ("Chunks".equals(name)) {
                chunks = toChunks((List<Object>) value);
            }
        }

        return new TSInfo(totalSamples, memoryUsage, firstTimestamp, lastTimestamp, retentionTime,
                chunkCount, chunkSize, chunkType, duplicatePolicy, labels, sourceKey, rules,
                ignoreMaxTimeDiff, ignoreMaxValueDiff, keySelfName, chunks);
    }

    private static List<TSRule> toRules(List<Object> parts) {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }

        List<TSRule> rules = new ArrayList<>();
        if (parts.get(0) instanceof List) {
            for (Object part : parts) {
                List<Object> rule = (List<Object>) part;
                rules.add(toRule((String) rule.get(0), rule.subList(1, rule.size())));
            }
            return rules;
        }

        for (int i = 0; i < parts.size(); i += 2) {
            rules.add(toRule((String) parts.get(i), (List<Object>) parts.get(i + 1)));
        }
        return rules;
    }

    private static TSRule toRule(String destinationKey, List<Object> rest) {
        Duration bucketDuration = Duration.ofMillis(TimeSeriesReplyValues.toLong(rest.get(0)));
        TSAggregation aggregation = null;
        String token = rest.get(1).toString();
        for (TSAggregation candidate : TSAggregation.values()) {
            if (candidate.getValue().equalsIgnoreCase(token)) {
                aggregation = candidate;
                break;
            }
        }
        return new TSRule(destinationKey, bucketDuration, aggregation, TimeSeriesReplyValues.toLong(rest.get(2)));
    }

    private static List<TSChunkInfo> toChunks(List<Object> parts) {
        List<TSChunkInfo> chunks = new ArrayList<>(parts.size());
        for (Object part : parts) {
            List<Object> chunk = (List<Object>) part;
            long startTimestamp = 0;
            long endTimestamp = 0;
            long samples = 0;
            long size = 0;
            double bytesPerSample = 0;
            for (int i = 0; i < chunk.size() - 1; i += 2) {
                String name = (String) chunk.get(i);
                Object value = chunk.get(i + 1);
                if ("startTimestamp".equals(name)) {
                    startTimestamp = TimeSeriesReplyValues.toLong(value);
                } else if ("endTimestamp".equals(name)) {
                    endTimestamp = TimeSeriesReplyValues.toLong(value);
                } else if ("samples".equals(name)) {
                    samples = TimeSeriesReplyValues.toLong(value);
                } else if ("size".equals(name)) {
                    size = TimeSeriesReplyValues.toLong(value);
                } else if ("bytesPerSample".equals(name)) {
                    bytesPerSample = TimeSeriesReplyValues.toDouble(value);
                }
            }
            chunks.add(new TSChunkInfo(startTimestamp, endTimestamp, samples, size, bytesPerSample));
        }
        return chunks;
    }

}
