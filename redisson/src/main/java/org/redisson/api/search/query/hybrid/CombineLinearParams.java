/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.api.search.query.hybrid;

/**
 * Implementation of Linear combine configuration.
 *
 * @author Nikita Koksharov
 */
public final class CombineLinearParams implements CombineLinearStep {

    private Double alpha;
    private Double beta;
    private Integer window;
    private String scoreAlias;

    CombineLinearParams() {
    }

    @Override
    public CombineLinearStep alpha(double alpha) {
        this.alpha = alpha;
        return this;
    }

    @Override
    public CombineLinearStep beta(double beta) {
        this.beta = beta;
        return this;
    }

    @Override
    public CombineLinearStep window(int window) {
        this.window = window;
        return this;
    }

//    @Override
//    public CombineLinearStep scoreAlias(String alias) {
//        this.scoreAlias = alias;
//        return this;
//    }

    public Double getAlpha() {
        return alpha;
    }

    public Double getBeta() {
        return beta;
    }

    public Integer getWindow() {
        return window;
    }

    public String getScoreAlias() {
        return scoreAlias;
    }
}