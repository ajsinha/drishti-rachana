/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.control;

import com.ash.rachana.bind.AsOf;
import com.ash.rachana.el.BusinessCalendar;
import java.time.LocalDate;

/**
 * The host hook for business dates (split step A13): the business date a request reads (relative control values such as
 * {@code last 5 business days} are read from it) and the calendar that says which days are business days.
 */
public interface BusinessDays {

    /** The business date of a request's point in time (the current business date when it names none). */
    LocalDate today(AsOf asOf);

    /** The business calendar. */
    BusinessCalendar calendar();

    /** A fixed date and calendar (tests, a host with one calendar). */
    static BusinessDays of(LocalDate current, BusinessCalendar calendar) {
        return new BusinessDays() {
            @Override
            public LocalDate today(AsOf asOf) {
                return asOf == null ? current : asOf.dateOr(current);
            }

            @Override
            public BusinessCalendar calendar() {
                return calendar;
            }
        };
    }
}
