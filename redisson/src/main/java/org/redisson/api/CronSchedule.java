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
package org.redisson.api;

import org.redisson.executor.CronExpression;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Cron expression object used in {@link RScheduledExecutorService}.
 * Fully compatible with quartz cron expression.
 * 
 * @see RScheduledExecutorService#schedule(Runnable, CronSchedule)
 * 
 * @author Nikita Koksharov
 *
 */
public final class CronSchedule {

    private final CronExpression expression;
    private final ZoneId zoneId;

    CronSchedule(CronExpression expression, ZoneId zoneId) {
        super();
        this.expression = expression;
        this.zoneId = zoneId;
    }

    /**
     * Creates cron expression object with defined expression string
     * 
     * @param expression of cron
     * @return object
     * @throws IllegalArgumentException
     *             wrapping a ParseException if the expression is invalid
     */
    public static CronSchedule of(String expression) {
        return of(expression, ZoneId.systemDefault());
    }

    /**
     * Creates cron expression object with defined expression string and time-zone ID
     *
     * @param expression of cron
     * @param zoneId id of zone
     * @return object
     * @throws IllegalArgumentException
     *             wrapping a ParseException if the expression is invalid
     */
    public static CronSchedule of(String expression, ZoneId zoneId) {
        CronExpression ce = new CronExpression(expression);
        ce.setTimeZone(TimeZone.getTimeZone(zoneId));
        return new CronSchedule(ce, zoneId);
    }

    /**
     * Creates cron expression which schedule task execution
     * every day at the given time 
     * 
     * @param hour of schedule
     * @param minute of schedule
     * @return object
     * @throws IllegalArgumentException
     *             wrapping a ParseException if the expression is invalid
     */
    public static CronSchedule dailyAtHourAndMinute(int hour, int minute) {
        String expression = String.format("0 %d %d ? * *", minute, hour);
        return of(expression);
    }

    /**
     * Creates cron expression which schedule task execution
     * every day at the given time in specified time-zone ID
     *
     * @param hour of schedule
     * @param minute of schedule
     * @param zoneId id of zone
     * @return object
     * @throws IllegalArgumentException
     *             wrapping a ParseException if the expression is invalid
     */
    public static CronSchedule dailyAtHourAndMinute(int hour, int minute, ZoneId zoneId) {
        String expression = String.format("0 %d %d ? * *", minute, hour);
        return of(expression, zoneId);
    }

    /**
     * Creates cron expression which schedule task execution
     * every given days of the week at the given time.
     * Use Calendar object constants to define day.
     * 
     * @param hour of schedule
     * @param minute of schedule
     * @param daysOfWeek - Calendar object constants
     * @return object
     */
    public static CronSchedule weeklyOnDayAndHourAndMinute(int hour, int minute, Integer... daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.length == 0) {
            throw new IllegalArgumentException("You must specify at least one day of week.");
        }

        String expression = String.format("0 %d %d ? * %d", minute, hour, daysOfWeek[0]);
        for (int i = 1; i < daysOfWeek.length; i++) {
            expression = expression + "," + daysOfWeek[i];
        }

        return of(expression);
    }

    /**
     * Creates cron expression which schedule task execution
     * every given days of the week at the given time in specified time-zone ID.
     * Use Calendar object constants to define day.
     *
     * @param hour of schedule
     * @param minute of schedule
     * @param zoneId id of zone
     * @param daysOfWeek - Calendar object constants
     * @return object
     */
    public static CronSchedule weeklyOnDayAndHourAndMinute(int hour, int minute, ZoneId zoneId, Integer... daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.length == 0) {
            throw new IllegalArgumentException("You must specify at least one day of week.");
        }

        String expression = String.format("0 %d %d ? * %d", minute, hour, daysOfWeek[0]);
        for (int i = 1; i < daysOfWeek.length; i++) {
            expression = expression + "," + daysOfWeek[i];
        }

        return of(expression, zoneId);
    }

    /**
     * Creates cron expression which schedule task execution
     * every given day of the month at the given time
     * 
     * @param hour of schedule
     * @param minute of schedule
     * @param dayOfMonth of schedule
     * @return object
     */
    public static CronSchedule monthlyOnDayAndHourAndMinute(int dayOfMonth, int hour, int minute) {
        String expression = String.format("0 %d %d %d * ?", minute, hour, dayOfMonth);
        return of(expression);
    }

    /**
     * Creates cron expression which schedule task execution
     * every given day of the month at the given time in specified time-zone ID.
     *
     * @param hour of schedule
     * @param minute of schedule
     * @param dayOfMonth of schedule
     * @param zoneId id of zone
     * @return object
     */
    public static CronSchedule monthlyOnDayAndHourAndMinute(int dayOfMonth, int hour, int minute, ZoneId zoneId) {
        String expression = String.format("0 %d %d %d * ?", minute, hour, dayOfMonth);
        return of(expression, zoneId);
    }

    public CronExpression getExpression() {
        return expression;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}

