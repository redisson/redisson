/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

    private CronExpression expression;
    
    CronSchedule(CronExpression expression) {
        super();
        this.expression = expression;
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
        return new CronSchedule(new CronExpression(expression));
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
    
    public CronExpression getExpression() {
        return expression;
    }
    
}
