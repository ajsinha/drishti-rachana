/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.hostcompat;

/** The type of a {@link DataNode}. Shape fingerprints are built from these. */
public enum NodeType {
    /** A JSON object. */
    OBJECT('o'),
    /** A JSON array. */
    ARRAY('a'),
    /** A string. */
    STRING('s'),
    /** A number. */
    NUMBER('n'),
    /** A boolean. */
    BOOLEAN('b'),
    /** The JSON null. */
    NULL('z'),
    /** Nothing there: navigation to a field or index that does not exist. */
    MISSING('m');

    private final char code;

    NodeType(char code) {
        this.code = code;
    }

    /** A one-character code used in shape signatures. */
    public char code() {
        return code;
    }

    /** True for a string, number, boolean or null. */
    public boolean isScalar() {
        return this == STRING || this == NUMBER || this == BOOLEAN || this == NULL;
    }
}
