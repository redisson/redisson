/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.executor;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CronExpressionEx extends CronExpression {

    private static final ThreadLocal<NavigableSet<Integer>> YEARS_FIELD = new ThreadLocal<>();

    private final NavigableSet<Integer> years;

    public CronExpressionEx(String expr) {
        super(parseYear(expr));
        years = YEARS_FIELD.get();
        YEARS_FIELD.remove();
    }

    public CronExpressionEx(String expr, boolean withSeconds) {
        super(parseYear(expr), withSeconds);
        years = YEARS_FIELD.get();
        YEARS_FIELD.remove();
    }

    @Override
    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime) {
        if (years != null) {
            afterTime = afterTime.withYear(years.ceiling(afterTime.getYear()));
        }
        return super.nextTimeAfter(afterTime);
    }

    private static String parseYear(String expr) {
        String[] parts = expr.split("\\s+");
        if (parts.length == 7) {
            String year = parts[6];
            String[] years = year.split(",");
            if (years.length > 1) {
                NavigableSet<Integer> yy = new TreeSet<>(Arrays.stream(years).map(y -> Integer.valueOf(y))
                                                    .collect(Collectors.toList()));
                YEARS_FIELD.set(yy);
            }
            String[] yearsRange = year.split("-");
            if (yearsRange.length > 1) {
                NavigableSet<Integer> yy = new TreeSet<>(IntStream.rangeClosed(Integer.valueOf(yearsRange[0]), Integer.valueOf(yearsRange[1]))
                                                    .boxed().collect(Collectors.toList()));
                YEARS_FIELD.set(yy);
            }
            return expr.replace(year, "").trim();
        }
        return expr;
    }
}
