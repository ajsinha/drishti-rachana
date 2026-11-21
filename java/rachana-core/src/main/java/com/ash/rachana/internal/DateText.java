/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.time.LocalDate;

/**
 * Core's English date text: month and day names from Core's own table (never the runtime's locale data), and the date pattern
 * of a {@code date} format (SPEC formats.yaml), printed by Core rather than {@code java.time.format}. Static, thread-safe.
 *
 * <p>The pattern letters are those of {@code DateTimeFormatter} that a date has: {@code y} (one, three or more: the year from 1 to 9999,
 * at least that many digits; two: the last two digits), {@code M} (one: the month number; two: zero-padded; three: the short
 * English name; four or more: the full name), {@code d} (one or two: the day of the month), {@code E} (one to three: the short
 * day name; four or more: the full name); text in single quotes is printed as written ({@code ''} is a quote); every other
 * character that is not an ASCII letter is printed as is. A pattern with any other ASCII letter is not one Core prints: the
 * caller shows the date as it came. For these patterns the output equals {@code DateTimeFormatter.ofPattern(p, Locale.ENGLISH)}
 * ({@code DateTextTest}).
 */
public final class DateText {

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December"};
    private static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private DateText() {
    }

    /** The English month name, 1 to 12; {@code shortName} gives the first three letters. */
    public static String month(int month, boolean shortName) {
        String m = MONTHS[month - 1];
        return shortName ? m.substring(0, 3) : m;
    }

    /** The English day name, 1 (Monday) to 7 (Sunday); {@code shortName} gives the first three letters. */
    public static String day(int isoDay, boolean shortName) {
        String d = DAYS[isoDay - 1];
        return shortName ? d.substring(0, 3) : d;
    }

    /** The date printed by the pattern; null when the pattern has a letter Core does not print (see the class comment). */
    public static String format(LocalDate date, String pattern) {
        StringBuilder b = new StringBuilder();
        int n = pattern.length();
        for (int i = 0; i < n; ) {
            char ch = pattern.charAt(i);
            if (ch == '\'') {
                int j = i + 1;
                if (j < n && pattern.charAt(j) == '\'') {
                    b.append('\'');
                    i += 2;
                    continue;
                }
                while (j < n) {
                    if (pattern.charAt(j) == '\'') {
                        if (j + 1 < n && pattern.charAt(j + 1) == '\'') {
                            b.append('\'');
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    b.append(pattern.charAt(j++));
                }
                if (j >= n) {
                    return null;                                       // an unclosed quote
                }
                i = j + 1;
                continue;
            }
            if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z')) {
                if (ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '#') {
                    return null;                                       // optional sections and reserved characters
                }
                b.append(ch);
                i++;
                continue;
            }
            int run = 1;
            while (i + run < n && pattern.charAt(i + run) == ch) {
                run++;
            }
            switch (ch) {
                case 'y' -> {
                    int y = date.getYear();
                    if (y < 1 || y > 9999) {
                        return null;                                   // eras and signed years: the date as it came
                    }
                    b.append(run == 2 ? NumberText.pad(y % 100, 2) : NumberText.pad(y, run));
                }
                case 'M' -> {
                    if (run > 5) {
                        return null;
                    }
                    int m = date.getMonthValue();
                    b.append(run == 1 ? Integer.toString(m) : run == 2 ? NumberText.pad(m, 2)
                            : run == 3 ? month(m, true) : run == 4 ? month(m, false) : month(m, false).substring(0, 1));
                }
                case 'd' -> {
                    if (run > 2) {
                        return null;
                    }
                    b.append(run == 1 ? Integer.toString(date.getDayOfMonth()) : NumberText.pad(date.getDayOfMonth(), 2));
                }
                case 'E' -> {
                    if (run > 5) {
                        return null;
                    }
                    int d = date.getDayOfWeek().getValue();
                    b.append(run <= 3 ? day(d, true) : run == 4 ? day(d, false) : day(d, false).substring(0, 1));
                }
                default -> {
                    return null;
                }
            }
            i += run;
        }
        return b.toString();
    }
}
