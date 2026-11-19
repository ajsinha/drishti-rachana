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
 * holidays of USNY, GBLO, EUTA, JPTO, CATO, and joint calendars such as {@code USNY+GBLO}) answer unless a profile gives its own
 * {@link Resolver}, which may know more centres or a firm's own holidays. The hook is per {@link com.ash.rachana.Rachana} instance
 * ({@code Profile.Builder.calendars}); nothing is installed process-wide.
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
    private static final Calendars CORE_CALENDARS = new Calendars(CORE);

    private final Resolver resolver;
    private final ConcurrentHashMap<String, Calendar> held = new ConcurrentHashMap<>();

    private Calendars(Resolver resolver) {
        this.resolver = resolver;
    }

    /** Core's own calendars, shared (they hold no host state). */
    public static Calendars core() {
        return CORE_CALENDARS;
    }

    /**
     * The calendars a host's resolver answers ({@code null}: Core's own). Each {@link com.ash.rachana.Rachana} instance holds its own,
     * so two instances in one JVM never see each other's calendars.
     */
    public static Calendars of(Resolver r) {
        return r == null || r == CORE ? CORE_CALENDARS : new Calendars(r);
    }

    /** The resolver these calendars ask. */
    public Resolver resolver() {
        return resolver;
    }

    /** The calendar of a name in upper case, resolved once and held (holidays are worked out once per year per calendar). */
    Calendar held(String key) {
        Calendar cal = held.get(key);
        if (cal == null) {
            cal = resolver.resolve(key);
            if (held.size() < MAX_HELD) {
                held.put(key, cal);
            }
        }
        return cal;
    }

    /** Resolves without holding: the checker's question whether a written name is a calendar. */
    Calendar resolve(String spec) {
        return resolver.resolve(spec);
    }
}
