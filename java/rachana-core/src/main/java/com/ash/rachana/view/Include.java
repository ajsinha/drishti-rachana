/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a field of a ViewModel record is written (split step A13; it replaces the host's JSON library annotation, with the same
 * meaning): on a record, the rule of all its fields; on a field, that field's own. A field with no rule is always written.
 * {@link ViewJson} writes by it, {@link ViewModelSchema} reads it to say what is required and what may be null.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface Include {

    Rule value();

    /** Which values are left out. */
    enum Rule {
        /** None. */
        ALWAYS,
        /** Null. */
        NON_NULL,
        /** Null, and an empty text, list or map. */
        NON_EMPTY,
        /** As {@link #NON_EMPTY}, and {@code false} and zero. */
        NON_DEFAULT
    }
}
