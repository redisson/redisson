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
package org.redisson.api;

import org.redisson.api.tdigest.TDigestMergeArgs;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive interface for t-digest.
 *
 * @author Nikita Koksharov
 *
 */
public interface RTDigestReactive extends RExpirableReactive {

    /**
     * Initializes a new t-digest sketch using the default compression.
     * <p>
     * Equivalent to {@code TDIGEST.CREATE key}.
     *
     * @return void
     */
    Mono<Void> create();

    /**
     * Initializes a new t-digest sketch with the specified compression.
     * Higher compression trades memory for accuracy.
     * <p>
     * Equivalent to {@code TDIGEST.CREATE key COMPRESSION compression}.
     *
     * @param compression compression factor
     * @return void
     */
    Mono<Void> create(int compression);

    /**
     * Adds a single observation to the sketch.
     * <p>
     * Equivalent to {@code TDIGEST.ADD key value}.
     *
     * @param value observation value
     * @return void
     */
    Mono<Void> add(double value);

    /**
     * Adds one or more observations to the sketch.
     * <p>
     * Equivalent to {@code TDIGEST.ADD key value [value ...]}.
     *
     * @param values observation values
     * @return void
     */
    Mono<Void> add(double... values);

    /**
     * Merges the sketches stored at the specified keys into this sketch.
     * This sketch is the merge destination.
     * <p>
     * Equivalent to {@code TDIGEST.MERGE this numkeys key [key ...]}.
     *
     * @param keys names of the source sketches
     * @return void
     */
    Mono<Void> mergeWith(String... keys);

    /**
     * Merges the source sketches defined by {@code args} into this sketch
     * using the supplied compression and override options.
     * This sketch is the merge destination.
     * <p>
     * Equivalent to {@code TDIGEST.MERGE this numkeys key [key ...] [COMPRESSION c] [OVERRIDE]}.
     *
     * @param args merge arguments
     * @return void
     */
    Mono<Void> mergeWith(TDigestMergeArgs args);

    /**
     * Returns the minimum observation added to the sketch,
     * or {@code NaN} if the sketch is empty.
     * <p>
     * Equivalent to {@code TDIGEST.MIN key}.
     *
     * @return minimum observation
     */
    Mono<Double> getMin();

    /**
     * Returns the maximum observation added to the sketch,
     * or {@code NaN} if the sketch is empty.
     * <p>
     * Equivalent to {@code TDIGEST.MAX key}.
     *
     * @return maximum observation
     */
    Mono<Double> getMax();

    /**
     * Returns, for each input fraction, an estimate of the value
     * below which that fraction of observations fall.
     * <p>
     * Equivalent to {@code TDIGEST.QUANTILE key quantile [quantile ...]}.
     *
     * @param quantiles input fractions between 0 and 1 inclusively
     * @return estimated value per input fraction
     */
    Mono<List<Double>> quantile(double... quantiles);

    /**
     * Returns, for each input value, an estimate of the fraction of
     * observations that are less than or equal to it.
     * <p>
     * Equivalent to {@code TDIGEST.CDF key value [value ...]}.
     *
     * @param values input values
     * @return estimated fraction per input value
     */
    Mono<List<Double>> cumulativeProbability(double... values);

    /**
     * Returns an estimate of the mean value, ignoring observations
     * outside the {@code [lowCutQuantile, highCutQuantile]} range.
     * <p>
     * Equivalent to {@code TDIGEST.TRIMMED_MEAN key lowCutQuantile highCutQuantile}.
     *
     * @param lowCutQuantile low cut fraction between 0 and 1 inclusively
     * @param highCutQuantile high cut fraction between 0 and 1 inclusively
     * @return estimated trimmed mean
     */
    Mono<Double> trimmedMean(double lowCutQuantile, double highCutQuantile);

    /**
     * Returns, for each input value, the estimated rank: the number of
     * observations strictly less than it. Returns {@code -1} for a value
     * smaller than every observation.
     * <p>
     * Equivalent to {@code TDIGEST.RANK key value [value ...]}.
     *
     * @param values input values
     * @return estimated rank per input value
     */
    Mono<List<Long>> rank(double... values);

    /**
     * Returns, for each input value, the estimated reverse rank: the number
     * of observations strictly greater than it. Returns {@code -1} for a
     * value larger than every observation.
     * <p>
     * Equivalent to {@code TDIGEST.REVRANK key value [value ...]}.
     *
     * @param values input values
     * @return estimated reverse rank per input value
     */
    Mono<List<Long>> revRank(double... values);

    /**
     * Returns, for each input rank, an estimate of the value
     * with that rank, counting from the smallest observation.
     * <p>
     * Equivalent to {@code TDIGEST.BYRANK key rank [rank ...]}.
     *
     * @param ranks input ranks
     * @return estimated value per input rank
     */
    Mono<List<Double>> byRank(long... ranks);

    /**
     * Returns, for each input rank, an estimate of the value
     * with that rank, counting from the largest observation.
     * <p>
     * Equivalent to {@code TDIGEST.BYREVRANK key rank [rank ...]}.
     *
     * @param ranks input reverse ranks
     * @return estimated value per input reverse rank
     */
    Mono<List<Double>> byRevRank(long... ranks);

    /**
     * Returns information and statistics about the sketch.
     * <p>
     * Equivalent to {@code TDIGEST.INFO key}.
     *
     * @return sketch information
     */
    Mono<TDigestInfo> getInfo();

    /**
     * Resets the sketch: empties it and re-initializes it.
     * <p>
     * Equivalent to {@code TDIGEST.RESET key}.
     *
     * @return void
     */
    Mono<Void> reset();

}
