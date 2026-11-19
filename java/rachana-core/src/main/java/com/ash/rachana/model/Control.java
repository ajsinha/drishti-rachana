/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.List;
import java.util.Locale;

/**
 * A control of a Sutra: a dropdown, a date range, a search box. Its value is read in Rachana-EL as {@code ctl.<id>}, in the
 * {@code rows}, {@code where}, {@code limit} and {@code title} of panels and in strip bindings. Controls only choose; they never write.
 *
 * @param id the name in {@code ctl.<id>} (a letter, then letters, digits and underscores)
 * @param kind how it is drawn and what its value is
 * @param label the text beside it (the id when absent)
 * @param choices a fixed list of options (empty when the options are read from the data or a lookup kind)
 * @param from an expression over the record that lists the options ({@code $.legs[*].id}, or one over {@code ctl.<parent>})
 * @param lookup the entity kind whose ids are the options, or null
 * @param defaultValue the value to start from, as written: a value, {@code first}, {@code all}, {@code today}, a relative range
 * @param remember keep the reader's last choice for this Sutra ({@code remember: user}); false is {@code none}
 * @param required the reader cannot clear it
 * @param placement where it sits
 * @param panel the panel whose header holds it when {@code placement} is {@link Placement#HEADER}
 * @param parent the control a cascading control depends on, or null
 * @param min the lower bound of a number range (a number or an expression over the record), or null
 * @param max the upper bound, or null
 * @param step the slider's step: a number or an expression over the record, or null (1)
 * @param range a slider with two handles whose value is {@code from..to}
 * @param location where it is written
 */
public record Control(String id, ControlKind kind, String label, List<Choice> choices, String from, String lookup, String defaultValue,
        boolean remember, boolean required, Placement placement, String panel, String parent, String min, String max,
        String step, boolean range, SourceLocation location) {

    /** The most options a control offers; more are cut and the control says so. */
    public static final int MAX_OPTIONS = 500;

    /** Where a control sits on the page. */
    public enum Placement {
        /** Under the title, with the strip. */
        STRIP,
        /** In the header of one panel. */
        HEADER,
        /** In a bar at the side of the page. */
        SIDE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One fixed option: the value that is set and the text shown. */
    public record Choice(String value, String label) {}

    public Control {
        choices = List.copyOf(choices);
    }

    /** The text beside the control. */
    public String display() {
        return label == null || label.isBlank() ? id : label;
    }
}
