/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import com.ash.rachana.el.FormRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One expression of a form's rules, hints and {@code =} defaults (RACHANA_REFERENCE.md 10). Since split step A7b it is Rachana-EL,
 * read by Rachana-EL's parser and evaluated with the {@code form} profile's meaning ({@link FormRules}: exact decimals, {@code +}
 * joining a text, empty values, three-valued {@code and}/{@code or}, a rule that cannot be told yet holds). Evaluated on the server only:
 * the console's forms send the draft to the host's form check as people type and show the answer.
 */
public final class FormExpr {

    /** The most characters of one expression. */
    public static final int MAX_LENGTH = FormRules.MAX_LENGTH;

    private final FormRules.Compiled compiled;

    private FormExpr(FormRules.Compiled compiled) {
        this.compiled = compiled;
    }

    /** Parses {@code text}; an {@link IllegalArgumentException} says what is wrong, in words. */
    public static FormExpr parse(String text) {
        return new FormExpr(FormRules.compile(text));
    }

    public String text() {
        return compiled.text();
    }

    /** The names it reads: {@code notional}, {@code counterparty} for {@code counterparty.LIMIT}. */
    public Set<String> names() {
        Set<String> out = new LinkedHashSet<>();
        compiled.names(out);
        return out;
    }

    /** Its value over the form's values (a lookup's value is a map of what it carries); null when it cannot be told. */
    public Object eval(Map<String, Object> values, LocalDate today) {
        return compiled.eval(values, today);
    }

    /** Whether a rule holds: true, or empty (it cannot be told yet). */
    public boolean holds(Map<String, Object> values, LocalDate today) {
        return FormRules.holds(eval(values, today));
    }

    /** A number when it reads as one (a Number, or text like 25,000,000), else null. */
    public static BigDecimal number(Object v) {
        return FormRules.number(v);
    }
}
