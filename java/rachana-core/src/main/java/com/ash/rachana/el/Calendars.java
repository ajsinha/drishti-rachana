/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The business-day calendars the date functions use ({@code is_business_day}, {@code shift_date} by business days,
 * {@code business_days_between}, {@code intck}): a hook. Core's own calendars ({@link BusinessCalendar}: weekends and the public
 * holidays of USNY, GBLO, EUTA, JPTO, CATO, and joint calendars such as {@code USNY+GBLO}) answer unless a host installs its own
 * {@link Resolver}, which may know more centres or a firm's own holidays.
 */
public final class Calendars {

    /** A business-day calendar. Immutable and safe for many threads. */
    public interface Calendar {
        /** The calendar's name, as written ({@code USNY+GBLO}). */
        String name();

        /** True on a business day. */
        boolean isBusinessDay(LocalDate d);

        /** The business day before {@code d}. */
        LocalDate previous(LocalDate d);

        /** The business day after {@code d}. */
        LocalDate next(LocalDate d);
    }

    /** Turns a calendar's name into the calendar; an {@link IllegalArgumentException} with words for a name it does not know. */
    @FunctionalInterface
    public interface Resolver {
        Calendar resolve(String spec);
    }

    /** Core's own calendars. */
    public static final Resolver CORE = BusinessCalendar::of;

    private static final int MAX_HELD = 64;
    private static final ConcurrentHashMap<String, Calendar> HELD = new ConcurrentHashMap<>();
    private static volatile Resolver resolver = CORE;

    private Calendars() {
    }

    /** Installs a host's resolver ({@code null}: Core's own again); calendars already resolved are forgotten. */
    public static void use(Resolver r) {
        resolver = r == null ? CORE : r;
        HELD.clear();
    }

    /** The calendar of a name in upper case, resolved once and held (holidays are worked out once per year per calendar). */
    static Calendar held(String key) {
        Calendar cal = HELD.get(key);
        if (cal == null) {
            cal = resolver.resolve(key);
            if (HELD.size() < MAX_HELD) {
                HELD.put(key, cal);
            }
        }
        return cal;
    }

    /** Resolves without holding: the checker's question whether a written name is a calendar. */
    static Calendar resolve(String spec) {
        return resolver.resolve(spec);
    }
}
