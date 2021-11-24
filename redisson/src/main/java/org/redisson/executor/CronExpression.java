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
/**
 * Copyright (C) 2012- Frode Carlsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Note: rewritten to standard Java 8 DateTime by zemiak (c) 2016
 */
package org.redisson.executor;

import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This provides cron support for java8 using java-time.
 * <P>
 *
 * Parser for unix-like cron expressions: Cron expressions allow specifying combinations of criteria for time
 * such as: &quot;Each Monday-Friday at 08:00&quot; or &quot;Every last friday of the month at 01:30&quot;
 * <p>
 * A cron expressions consists of 5 or 6 mandatory fields (seconds may be omitted) separated by space. <br>
 * These are:
 *
 * <table cellspacing="8">
 * <tr>
 * <th align="left">Field</th>
 * <th align="left">&nbsp;</th>
 * <th align="left">Allowable values</th>
 * <th align="left">&nbsp;</th>
 * <th align="left">Special Characters</th>
 * </tr>
 * <tr>
 * <td align="left"><code>Seconds (may be omitted)</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>0-59</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Minutes</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>0-59</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Hours</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>0-23</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Day of month</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>1-31</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>, - * ? / L W</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Month</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>1-12 or JAN-DEC (note: english abbreviations)</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td align="left"><code>Day of week</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>1-7 or MON-SUN (note: english abbreviations)</code></td>
 * <td align="left">&nbsp;</th>
 * <td align="left"><code>, - * ? / L #</code></td>
 * </tr>
 * </table>
 *
 * <P>
 * '*' Can be used in all fields and means 'for all values'. E.g. &quot;*&quot; in minutes, means 'for all minutes'
 * <P>
 * '?' Can be used in Day-of-month and Day-of-week fields. Used to signify 'no special value'. It is used when one want
 * to specify something for one of those two fields, but not the other.
 * <P>
 * '-' Used to specify a time interval. E.g. &quot;10-12&quot; in Hours field means 'for hours 10, 11 and 12'
 * <P>
 * ',' Used to specify multiple values for a field. E.g. &quot;MON,WED,FRI&quot; in Day-of-week field means &quot;for
 * monday, wednesday and friday&quot;
 * <P>
 * '/' Used to specify increments. E.g. &quot;0/15&quot; in Seconds field means &quot;for seconds 0, 15, 30, ad
 * 45&quot;. And &quot;5/15&quot; in seconds field means &quot;for seconds 5, 20, 35, and 50&quot;. If '*' s specified
 * before '/' it is the same as saying it starts at 0. For every field there's a list of values that can be turned on or
 * off. For Seconds and Minutes these range from 0-59. For Hours from 0 to 23, For Day-of-month it's 1 to 31, For Months
 * 1 to 12. &quot;/&quot; character helsp turn some of these values back on. Thus &quot;7/6&quot; in Months field
 * specify just Month 7. It doesn't turn on every 6 month following, since cron fields never roll over
 * <P>
 * 'L' Can be used on Day-of-month and Day-of-week fields. It signifies last day of the set of allowed values. In
 * Day-of-month field it's the last day of the month (e.g.. 31 jan, 28 feb (29 in leap years), 31 march, etc.). In
 * Day-of-week field it's Sunday. If there's a prefix, this will be subtracted (5L in Day-of-month means 5 days before
 * last day of Month: 26 jan, 23 feb, etc.)
 * <P>
 * 'W' Can be specified in Day-of-Month field. It specifies closest weekday (monday-friday). Holidays are not accounted
 * for. &quot;15W&quot; in Day-of-Month field means 'closest weekday to 15 i in given month'. If the 15th is a Saturday,
 * it gives Friday. If 15th is a Sunday, the it gives following Monday.
 * <P>
 * '#' Can be used in Day-of-Week field. For example: &quot;5#3&quot; means 'third friday in month' (day 5 = friday, #3
 * - the third). If the day does not exist (e.g. &quot;5#5&quot; - 5th friday of month) and there aren't 5 fridays in
 * the month, then it won't match until the next month with 5 fridays.
 * <P>
 * <b>Case-sensitive</b> No fields are case-sensitive
 * <P>
 * <b>Dependencies between fields</b> Fields are always evaluated independently, but the expression doesn't match until
 * the constraints of each field are met. Overlap of intervals are not allowed. That is: for
 * Day-of-week field &quot;FRI-MON&quot; is invalid,but &quot;FRI-SUN,MON&quot; is valid
 *
 */
@SuppressWarnings({"AvoidInlineConditionals", "MultipleVariableDeclarations", "InnerAssignment", "UnnecessaryParentheses"})
public class CronExpression {

    enum CronFieldType {
        SECOND(0, 59, null) {
            @Override
            int getValue(ZonedDateTime dateTime) {
                return dateTime.getSecond();
            }

            @Override
            ZonedDateTime setValue(ZonedDateTime dateTime, int value) {
                return dateTime.withSecond(value).withNano(0);
            }

            @Override
            ZonedDateTime overflow(ZonedDateTime dateTime) {
                return dateTime.plusMinutes(1).withSecond(0).withNano(0);
            }
        },
        MINUTE(0, 59, null) {
            @Override
            int getValue(ZonedDateTime dateTime) {
                return dateTime.getMinute();
            }

            @Override
            ZonedDateTime setValue(ZonedDateTime dateTime, int value) {
                return dateTime.withMinute(value).withSecond(0).withNano(0);
            }

            @Override
            ZonedDateTime overflow(ZonedDateTime dateTime) {
                return dateTime.plusHours(1).withMinute(0).withSecond(0).withNano(0);
            }
        },
        HOUR(0, 23, null) {
            @Override
            int getValue(ZonedDateTime dateTime) {
                return dateTime.getHour();
            }

            @Override
            ZonedDateTime setValue(ZonedDateTime dateTime, int value) {
                return dateTime.withHour(value).withMinute(0).withSecond(0).withNano(0);
            }

            @Override
            ZonedDateTime overflow(ZonedDateTime dateTime) {
                return dateTime.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            }
        },
        DAY_OF_MONTH(1, 31, null) {
            @Override
            int getValue(ZonedDateTime dateTime) {
                return dateTime.getDayOfMonth();
            }

            @Override
            ZonedDateTime setValue(ZonedDateTime dateTime, int value) {
                return dateTime.withDayOfMonth(value).withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            @Override
            ZonedDateTime overflow(ZonedDateTime dateTime) {
                return dateTime.plusMonths(1).withDayOfMonth(0).withHour(0).withMinute(0).withSecond(0).withNano(0);
            }
        },
        MONTH(1, 12,
                Arrays.asList("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")) {
            @Override
            int getValue(ZonedDateTime dateTime) {
                return dateTime.getMonthValue();
            }

            @Override
            ZonedDateTime setValue(ZonedDateTime dateTime, int value) {
                return dateTime.withMonth(value).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            }

            @Override
            ZonedDateTime overflow(ZonedDateTime dateTime) {
                return dateTime.plusYears(1).withMonth(1).withHour(0).withDayOfMonth(1).withMinute(0).withSecond(0).withNano(0);
            }
        },
        DAY_OF_WEEK(1, 7, Arrays.asList("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")) {
            @Override
            int getValue(ZonedDateTime dateTime) {
                return dateTime.getDayOfWeek().getValue();
            }

            @Override
            ZonedDateTime setValue(ZonedDateTime dateTime, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            ZonedDateTime overflow(ZonedDateTime dateTime) {
                throw new UnsupportedOperationException();
            }
        };

        final int from, to;
        final List<String> names;

        CronFieldType(int from, int to, List<String> names) {
            this.from = from;
            this.to = to;
            this.names = names;
        }

        /**
         * @param dateTime {@link ZonedDateTime} instance
         * @return The field time or date value from {@code dateTime}
         */
        abstract int getValue(ZonedDateTime dateTime);

        /**
         * @param dateTime Initial {@link ZonedDateTime} instance to use
         * @param value    to set for this field in {@code dateTime}
         * @return {@link ZonedDateTime} with {@code value} set for this field and all smaller fields cleared
         */
        abstract ZonedDateTime setValue(ZonedDateTime dateTime, int value);

        /**
         * Handle when this field overflows and the next higher field should be incremented
         *
         * @param dateTime Initial {@link ZonedDateTime} instance to use
         * @return {@link ZonedDateTime} with the next greater field incremented and all smaller fields cleared
         */
        abstract ZonedDateTime overflow(ZonedDateTime dateTime);
    }

    private final String expr;
    private final SimpleField secondField;
    private final SimpleField minuteField;
    private final SimpleField hourField;
    private final DayOfWeekField dayOfWeekField;
    private final SimpleField monthField;
    private final DayOfMonthField dayOfMonthField;

    public CronExpression(final String expr) {
        this(expr, true);
    }

    public CronExpression(final String expr, final boolean withSeconds) {
        if (expr == null) {
            throw new IllegalArgumentException("expr is null"); //$NON-NLS-1$
        }

        this.expr = expr;

        final int expectedParts = withSeconds ? 6 : 5;
        final String[] parts = expr.split("\\s+"); //$NON-NLS-1$
        if (parts.length != expectedParts) {
            throw new IllegalArgumentException(String.format("Invalid cron expression [%s], expected %s field, got %s", expr, expectedParts, parts.length));
        }

        int ix = withSeconds ? 1 : 0;
        this.secondField = new SimpleField(CronFieldType.SECOND, withSeconds ? parts[0] : "0");
        this.minuteField = new SimpleField(CronFieldType.MINUTE, parts[ix++]);
        this.hourField = new SimpleField(CronFieldType.HOUR, parts[ix++]);
        this.dayOfMonthField = new DayOfMonthField(parts[ix++]);
        this.monthField = new SimpleField(CronFieldType.MONTH, parts[ix++]);
        this.dayOfWeekField = new DayOfWeekField(parts[ix++]);
    }

    public static CronExpression create(final String expr) {
        return new CronExpression(expr, true);
    }

    public static CronExpression createWithoutSeconds(final String expr) {
        return new CronExpression(expr, false);
    }

    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime) {
        // will search for the next time within the next 4 years. If there is no
        // time matching, an InvalidArgumentException will be thrown (it is very
        // likely that the cron expression is invalid, like the February 30th).
        return nextTimeAfter(afterTime, afterTime.plusYears(4));
    }

    public LocalDateTime nextLocalDateTimeAfter(LocalDateTime dateTime) {
        return nextTimeAfter(ZonedDateTime.of(dateTime, ZoneId.systemDefault())).toLocalDateTime();
    }

    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime, long durationInMillis) {
        // will search for the next time within the next durationInMillis
        // millisecond. Be aware that the duration is specified in millis,
        // but in fact the limit is checked on a day-to-day basis.
        return nextTimeAfter(afterTime, afterTime.plus(Duration.ofMillis(durationInMillis)));
    }

    public ZonedDateTime nextTimeAfter(ZonedDateTime afterTime, ZonedDateTime dateTimeBarrier) {
        ZonedDateTime[] nextDateTime = { afterTime.plusSeconds(1).withNano(0) };

        while (true) {
            checkIfDateTimeBarrierIsReached(nextDateTime[0], dateTimeBarrier);
            if (!monthField.nextMatch(nextDateTime)) {
                continue;
            }
            if (!findDay(nextDateTime, dateTimeBarrier)) {
                continue;
            }
            if (!hourField.nextMatch(nextDateTime)) {
                continue;
            }
            if (!minuteField.nextMatch(nextDateTime)) {
                continue;
            }
            if (!secondField.nextMatch(nextDateTime)) {
                continue;
            }

            checkIfDateTimeBarrierIsReached(nextDateTime[0], dateTimeBarrier);
            return nextDateTime[0];
        }
    }

    /**
     * Find the next match for the day field.
     * <p>
     * This is handled different than all other fields because there are two ways to describe the day and it is easier
     * to handle them together in the same method.
     *
     * @param dateTime        Initial {@link ZonedDateTime} instance to start from
     * @param dateTimeBarrier At which point stop searching for next execution time
     * @return {@code true} if a match was found for this field or {@code false} if the field overflowed
     * @see {@link SimpleField#nextMatch(ZonedDateTime[])}
     */
    private boolean findDay(ZonedDateTime[] dateTime, ZonedDateTime dateTimeBarrier) {
        int month = dateTime[0].getMonthValue();

        while (!(dayOfMonthField.matches(dateTime[0].toLocalDate())
                && dayOfWeekField.matches(dateTime[0].toLocalDate()))) {
            dateTime[0] = dateTime[0].plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            if (dateTime[0].getMonthValue() != month) {
                return false;
            }
        }
        return true;
    }

    private static void checkIfDateTimeBarrierIsReached(ZonedDateTime nextTime, ZonedDateTime dateTimeBarrier) {
        if (nextTime.isAfter(dateTimeBarrier)) {
            throw new IllegalArgumentException("No next execution time could be determined that is before the limit of " + dateTimeBarrier);
        }
    }

    public String getExpr() {
        return expr;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + expr + ">";
    }

    static class FieldPart implements Comparable<FieldPart> {
        private int from = -1, to = -1, increment = -1;
        private String modifier, incrementModifier;

        @Override
        public int compareTo(FieldPart o) {
            return Integer.compare(from, o.from);
        }
    }

    abstract static class BasicField {
        private static final Pattern CRON_FIELD_REGEXP = Pattern
                .compile("(?:                                             # start of group 1\n"
                                + "   (?:(?<all>\\*)|(?<ignore>\\?)|(?<last>L))  # global flag (L, ?, *)\n"
                                + " | (?<start>[0-9]{1,2}|[a-z]{3,3})              # or start number or symbol\n"
                                + "      (?:                                        # start of group 2\n"
                                + "         (?<mod>L|W)                             # modifier (L,W)\n"
                                + "       | -(?<end>[0-9]{1,2}|[a-z]{3,3})        # or end nummer or symbol (in range)\n"
                                + "      )?                                         # end of group 2\n"
                                + ")                                              # end of group 1\n"
                                + "(?:(?<incmod>/|\\#)(?<inc>[0-9]{1,7}))?        # increment and increment modifier (/ or \\#)\n",
                        Pattern.CASE_INSENSITIVE | Pattern.COMMENTS);

        final CronFieldType fieldType;
        final List<FieldPart> parts = new ArrayList<>();

        private BasicField(CronFieldType fieldType, String fieldExpr) {
            this.fieldType = fieldType;
            parse(fieldExpr);
        }

        private void parse(String fieldExpr) { // NOSONAR
            String[] rangeParts = fieldExpr.split(",");
            for (String rangePart : rangeParts) {
                Matcher m = CRON_FIELD_REGEXP.matcher(rangePart);
                if (!m.matches()) {
                    throw new IllegalArgumentException("Invalid cron field '" + rangePart + "' for field [" + fieldType + "]");
                }
                String startNummer = m.group("start");
                String modifier = m.group("mod");
                String sluttNummer = m.group("end");
                String incrementModifier = m.group("incmod");
                String increment = m.group("inc");

                FieldPart part = new FieldPart();
                part.increment = 999;
                if (startNummer != null) {
                    part.from = mapValue(startNummer);
                    part.modifier = modifier;
                    if (sluttNummer != null) {
                        part.to = mapValue(sluttNummer);
                        part.increment = 1;
                    } else if (increment != null) {
                        part.to = fieldType.to;
                    } else {
                        part.to = part.from;
                    }
                } else if (m.group("all") != null) {
                    part.from = fieldType.from;
                    part.to = fieldType.to;
                    part.increment = 1;
                } else if (m.group("ignore") != null) {
                    part.modifier = m.group("ignore");
                } else if (m.group("last") != null) {
                    part.modifier = m.group("last");
                } else {
                    throw new IllegalArgumentException("Invalid cron part: " + rangePart);
                }

                if (increment != null) {
                    part.incrementModifier = incrementModifier;
                    part.increment = Integer.parseInt(increment);
                }

                validateRange(part);
                validatePart(part);
                parts.add(part);
            }

            Collections.sort(parts);
        }

        protected void validatePart(FieldPart part) {
            if (part.modifier != null) {
                throw new IllegalArgumentException(String.format("Invalid modifier [%s]", part.modifier));
            } else if (part.incrementModifier != null && !"/".equals(part.incrementModifier)) {
                throw new IllegalArgumentException(String.format("Invalid increment modifier [%s]", part.incrementModifier));
            }
        }

        private void validateRange(FieldPart part) {
            if ((part.from != -1 && part.from < fieldType.from) || (part.to != -1 && part.to > fieldType.to)) {
                throw new IllegalArgumentException(String.format("Invalid interval [%s-%s], must be %s<=_<=%s", part.from, part.to, fieldType.from,
                        fieldType.to));
            } else if (part.from != -1 && part.to != -1 && part.from > part.to) {
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid interval [%s-%s].  Rolling periods are not supported (ex. 5-1, only 1-5) since this won't give a deterministic result. Must be %s<=_<=%s",
                                part.from, part.to, fieldType.from, fieldType.to));
            }
        }

        protected int mapValue(String value) {
            int idx;
            if (fieldType.names != null && (idx = fieldType.names.indexOf(value.toUpperCase(Locale.getDefault()))) >= 0) {
                return idx + fieldType.from;
            }
            return Integer.parseInt(value);
        }

        protected boolean matches(int val, FieldPart part) {
            if (val >= part.from && val <= part.to && (val - part.from) % part.increment == 0) {
                return true;
            }
            return false;
        }

        protected int nextMatch(int val, FieldPart part) {
            if (val > part.to) {
                return -1;
            }
            int nextPotential = Math.max(val, part.from);
            if (part.increment == 1 || nextPotential == part.from) {
                return nextPotential;
            }

            int remainder = ((nextPotential - part.from) % part.increment);
            if (remainder != 0) {
                nextPotential += part.increment - remainder;
            }

            return nextPotential <= part.to ? nextPotential : -1;
        }
    }

    static class SimpleField extends BasicField {
        SimpleField(CronFieldType fieldType, String fieldExpr) {
            super(fieldType, fieldExpr);
        }

        public boolean matches(int val) {
            if (val >= fieldType.from && val <= fieldType.to) {
                for (FieldPart part : parts) {
                    if (matches(val, part)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Find the next match for this field. If a match cannot be found force an overflow and increase the next
         * greatest field.
         *
         * @param dateTime {@link ZonedDateTime} array so the reference can be modified
         * @return {@code true} if a match was found for this field or {@code false} if the field overflowed
         */
        protected boolean nextMatch(ZonedDateTime[] dateTime) {
            int value = fieldType.getValue(dateTime[0]);

            for (FieldPart part : parts) {
                int nextMatch = nextMatch(value, part);
                if (nextMatch > -1) {
                    if (nextMatch != value) {
                        dateTime[0] = fieldType.setValue(dateTime[0], nextMatch);
                    }
                    return true;
                }
            }

            dateTime[0] = fieldType.overflow(dateTime[0]);
            return false;
        }
    }

    static class DayOfWeekField extends BasicField {

        DayOfWeekField(String fieldExpr) {
            super(CronFieldType.DAY_OF_WEEK, fieldExpr);
        }

        boolean matches(LocalDate dato) {
            for (FieldPart part : parts) {
                if ("L".equals(part.modifier)) {
                    YearMonth ym = YearMonth.of(dato.getYear(), dato.getMonth().getValue());
                    return dato.getDayOfWeek() == DayOfWeek.of(part.from) && dato.getDayOfMonth() > (ym.lengthOfMonth() - 7);
                } else if ("#".equals(part.incrementModifier)) {
                    if (dato.getDayOfWeek() == DayOfWeek.of(part.from)) {
                        int num = dato.getDayOfMonth() / 7;
                        return part.increment == (dato.getDayOfMonth() % 7 == 0 ? num : num + 1);
                    }
                    return false;
                } else if (matches(dato.getDayOfWeek().getValue(), part)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected int mapValue(String value) {
            // Use 1-7 for weedays, but 0 will also represent sunday (linux practice)
            return "0".equals(value) ? 7 : super.mapValue(value);
        }

        @Override
        protected boolean matches(int val, FieldPart part) {
            return "?".equals(part.modifier) || super.matches(val, part);
        }

        @Override
        protected void validatePart(FieldPart part) {
            if (part.modifier != null && Arrays.asList("L", "?").indexOf(part.modifier) == -1) {
                throw new IllegalArgumentException(String.format("Invalid modifier [%s]", part.modifier));
            } else if (part.incrementModifier != null && Arrays.asList("/", "#").indexOf(part.incrementModifier) == -1) {
                throw new IllegalArgumentException(String.format("Invalid increment modifier [%s]", part.incrementModifier));
            }
        }
    }

    static class DayOfMonthField extends BasicField {
        DayOfMonthField(String fieldExpr) {
            super(CronFieldType.DAY_OF_MONTH, fieldExpr);
        }

        boolean matches(LocalDate dato) {
            for (FieldPart part : parts) {
                if ("L".equals(part.modifier)) {
                    YearMonth ym = YearMonth.of(dato.getYear(), dato.getMonth().getValue());
                    return dato.getDayOfMonth() == (ym.lengthOfMonth() - (part.from == -1 ? 0 : part.from));
                } else if ("W".equals(part.modifier)) {
                    if (dato.getDayOfWeek().getValue() <= 5) {
                        if (dato.getDayOfMonth() == part.from) {
                            return true;
                        } else if (dato.getDayOfWeek().getValue() == 5) {
                            return dato.plusDays(1).getDayOfMonth() == part.from;
                        } else if (dato.getDayOfWeek().getValue() == 1) {
                            return dato.minusDays(1).getDayOfMonth() == part.from;
                        }
                    }
                } else if (matches(dato.getDayOfMonth(), part)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void validatePart(FieldPart part) {
            if (part.modifier != null && Arrays.asList("L", "W", "?").indexOf(part.modifier) == -1) {
                throw new IllegalArgumentException(String.format("Invalid modifier [%s]", part.modifier));
            } else if (part.incrementModifier != null && !"/".equals(part.incrementModifier)) {
                throw new IllegalArgumentException(String.format("Invalid increment modifier [%s]", part.incrementModifier));
            }
        }

        @Override
        protected boolean matches(int val, FieldPart part) {
            return "?".equals(part.modifier) || super.matches(val, part);
        }
    }
}