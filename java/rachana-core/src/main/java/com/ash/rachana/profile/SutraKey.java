/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

import com.ash.rachana.parse.PNode;
import java.util.List;
import java.util.Map;

/**
 * A top-level key a profile adds to the Sutra grammar (docs/design/CORE_AND_PROFILES.md section 3.2): its name, how it is read, the
 * panels it brings and its JSON Schema fragment. What it reads is kept in {@link com.ash.rachana.model.Sutra#extensions()} under its
 * name. A profile cannot add a key Core reads.
 */
public interface SutraKey {

    /** The key, as written at the top of a Sutra ({@code match}). */
    String name();

    /**
     * Reads the key.
     *
     * @param node its value, or null when the Sutra does not write it
     * @param context where to report problems, the file and its folder
     * @return the value kept on the Sutra, or null for none
     */
    Object read(PNode node, Context context);

    /** Panels the key's value brings to the Sutra (written as {@code use:} items), after its own. None by default. */
    default List<PNode> panels(Object value) {
        return List.of();
    }

    /** Whether a Sutra must write it (the schema lists it as required). */
    default boolean required() {
        return false;
    }

    /** The key it follows in the schema's properties, or null to come last. */
    default String after() {
        return null;
    }

    /** Its JSON Schema fragment. */
    Map<String, Object> schema();

    /** What a key's reader is given. */
    interface Context {

        /** The file read, for problem locations. */
        String file();

        /** The folder the Sutra was read from (the parser's {@code domain} argument), or null. */
        String folder();

        /** The Sutra's root mapping (where a missing key is reported). */
        PNode root();

        /** Reports a problem at a node. */
        void problem(String code, String message, PNode at);
    }
}
