/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ash.rachana.api.Node;
import com.ash.rachana.format.Formats;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The business-day calendars are a hook per instance: Core's own answer unless a profile gives its resolver. */
class CalendarsTest {

    private static Object eval(String e) {
        return eval(new ElCompiler(), e);
    }

    private static Object eval(ElCompiler c, String e) {
        return Values.simplify(c.compile(e).eval(EvalContext.of(Node.of(java.util.Map.of()), Formats.defaults())));
    }

    @Test
    void coreCalendarsByDefault() {
        assertThat(eval("is_business_day('2026-12-25', 'USNY')")).isEqualTo(false);
        assertThat(eval("is_business_day('2026-12-24', 'USNY')")).isEqualTo(true);
        assertThatThrownBy(() -> eval("is_business_day('2026-12-24', 'XXXX')")).hasMessageContaining("unknown calendar XXXX");
    }

    @Test
    void aHostsResolverAnswersInstead() {
        Calendars firm = Calendars.of(spec -> new Calendars.Calendar() {
            public String name() { return spec; }
            public boolean isBusinessDay(LocalDate d) { return d.getDayOfWeek() != DayOfWeek.FRIDAY; }
            public LocalDate previous(LocalDate d) { LocalDate p = d.minusDays(1); return isBusinessDay(p) ? p : p.minusDays(1); }
            public LocalDate next(LocalDate d) { LocalDate n = d.plusDays(1); return isBusinessDay(n) ? n : n.plusDays(1); }
        });
        ElCompiler c = new ElCompiler(100, ElLimits.DEFAULTS, FunctionTable.of(java.util.List.of(), firm, null));
        assertThat(eval(c, "is_business_day('2026-12-25', 'FIRM')")).isEqualTo(false);   // a Friday
        assertThat(eval(c, "is_business_day('2026-12-26', 'FIRM')")).isEqualTo(true);    // a Saturday, a business day for this firm
        assertThatThrownBy(() -> eval("is_business_day('2026-12-24', 'FIRM')")).hasMessageContaining("unknown calendar FIRM");  // per instance
    }
}
