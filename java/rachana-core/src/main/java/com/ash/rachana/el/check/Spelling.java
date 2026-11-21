/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el.check;

import java.util.Map;
import com.ash.rachana.model.Ordered;

/**
 * How the checker words a problem's fix: in Rachana's own spelling, or in DEL's while DEL is still read (its checker calls this one
 * until the migration of docs/architecture/ARCHITECTURE.md §28 part B deletes it). The rules are the same; only the words a person is told to write
 * differ.
 */
public enum Spelling {
    RACHANA("true or false", "!= null", "== null", " = ", "c ? a : b", "x > 0 || x != null", Map.of()),
    DEL("True or False", "is not None", "is None", "=", "a if c else b", "x > 0 or x is not None",
            Ordered.map("starts_with", "startswith", "ends_with", "endswith", "latest", "last", "earliest", "first"));

    private final String booleans;
    private final String notNull;
    private final String isNull;
    private final String keyword;
    private final String conditional;
    private final String conditionExample;
    private final Map<String, String> names;

    Spelling(String booleans, String notNull, String isNull, String keyword, String conditional, String conditionExample,
            Map<String, String> names) {
        this.booleans = booleans;
        this.notNull = notNull;
        this.isNull = isNull;
        this.keyword = keyword;
        this.conditional = conditional;
        this.conditionExample = conditionExample;
        this.names = names;
    }

    /** {@code true or false}. */
    public String booleans() {
        return booleans;
    }

    /** {@code x != null}'s operator. */
    public String notNull() {
        return notNull;
    }

    /** {@code x == null}'s operator. */
    public String isNull() {
        return isNull;
    }

    /** A named argument as written: {@code where = ...} or DEL's {@code where=...}. */
    public String named(String name) {
        return name + keyword;
    }

    /** The conditional as written. */
    public String conditional() {
        return conditional;
    }

    /** An example condition. */
    public String conditionExample() {
        return conditionExample;
    }

    /** A function's name as this spelling writes it. */
    public String name(String rachana) {
        return names.getOrDefault(rachana, rachana);
    }
}
