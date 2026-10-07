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
package org.redisson.api.tsnative;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TSMultiGetParams implements TSMultiGetArgs {

    private final String[] filters;
    private boolean latest;
    private boolean withLabels;
    private String[] selectedLabels;

    TSMultiGetParams(String[] filters) {
        this.filters = filters;
    }

    public String[] getFilters() {
        return filters;
    }

    public boolean isLatest() {
        return latest;
    }

    public boolean isWithLabels() {
        return withLabels;
    }

    public String[] getSelectedLabels() {
        return selectedLabels;
    }

    @Override
    public TSMultiGetArgs latest() {
        this.latest = true;
        return this;
    }

    @Override
    public TSMultiGetArgs withLabels() {
        this.withLabels = true;
        return this;
    }

    @Override
    public TSMultiGetArgs selectedLabels(String... labels) {
        this.selectedLabels = labels;
        return this;
    }

}
