/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.api;

import com.ash.rachana.api.internal.Nodes;
import java.util.List;
import java.util.Map;

/**
 * A value as Rachana reads it: an object, a list, a scalar, null or missing. The evaluator, the formats and the checker read only
 * this interface, so a host passes its own document type without copying it (it implements {@code Node}); {@link #of(Object)} makes
 * Rachana's own nodes from plain Java values.
 *
 * <p>Navigation never throws: a field or index that is not there gives {@link #missing()}. A masked value ({@link #isMasked()})
 * stands for the whole field: anything read under it is masked too.
 */
public interface Node {

    /** What a node is. */
    enum Kind {
        /** An object: fields by name, in the order the source gave them. */
        OBJECT,
        /** A list. */
        ARRAY,
        /** A text. */
        STRING,
        /** A number. */
        NUMBER,
        /** A boolean. */
        BOOLEAN,
        /** The JSON null. */
        NULL,
        /** Nothing there: the result of reading a field or index that does not exist. */
        MISSING;

        /** True for a text, number, boolean or null. */
        public boolean isScalar() {
            return this == STRING || this == NUMBER || this == BOOLEAN || this == NULL;
        }
    }

    /** The text a masked value reads. */
    String MASK = "•••";

    /** What this node is. */
    Kind kind();

    /** A field of an object; {@link #missing()} for any other node or an absent field. */
    default Node get(String field) {
        return missing();
    }

    /** An element of a list (negative counts from the end); {@link #missing()} for any other node or an index out of range. */
    default Node get(int index) {
        return missing();
    }

    /** The number of fields or elements; zero for a scalar. */
    default int size() {
        return 0;
    }

    /** True for the absent node. */
    default boolean isMissing() {
        return kind() == Kind.MISSING;
    }

    /** True for a JSON null and for the absent node. */
    default boolean isNull() {
        return kind() == Kind.NULL || kind() == Kind.MISSING;
    }

    /** True for a list. */
    default boolean isArray() {
        return kind() == Kind.ARRAY;
    }

    /** True for the masked value. */
    default boolean isMasked() {
        return false;
    }

    /** A scalar's value as text; empty for containers and the absent node. */
    default String asText() {
        return "";
    }

    /** A scalar's value as a double; NaN when it is not a number. */
    default double asDouble() {
        return Double.NaN;
    }

    /** A scalar's value as a boolean ({@code true}, or the text {@code "true"} in any case); false otherwise. */
    default boolean asBoolean() {
        return false;
    }

    /** A scalar's Java value ({@code String}, {@code Number}, {@code Boolean}); null for null, containers and the absent node. */
    default Object value() {
        return null;
    }

    /** A list's elements; empty for any other node. */
    default List<? extends Node> elements() {
        return List.of();
    }

    /** An object's fields in order; empty for any other node. */
    default Map<String, ? extends Node> fields() {
        return Map.of();
    }

    /** The plain Java value: {@code Map}, {@code List}, {@code String}, {@code Number}, {@code Boolean} or null. */
    Object unwrap();

    /** Follows a dotted path with optional indexes, {@code legs[0].cashflows}; a leading {@code $.} is ignored. */
    default Node at(String path) {
        return Nodes.at(this, path);
    }

    /** The absent node. */
    static Node missing() {
        return Nodes.MISSING;
    }

    /** The JSON null. */
    static Node nullValue() {
        return Nodes.NULL;
    }

    /** The masked value: what a field the reader may not see reads. */
    static Node masked() {
        return Nodes.MASKED;
    }

    /** A tree of a plain Java value (maps, lists, texts, numbers, booleans; anything else as its text); a node is returned as it is. */
    static Node of(Object value) {
        return Nodes.of(value);
    }

    /** A list of the nodes given. */
    static Node list(List<? extends Node> elements) {
        return new Nodes.Arr(List.<Node>copyOf(elements));
    }
}
