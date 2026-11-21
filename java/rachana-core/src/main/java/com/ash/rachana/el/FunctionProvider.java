/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import java.util.List;

/**
 * A host's addition to Rachana's one function table, handed to a profile ({@code Profile.functions(...)}): the table of one
 * {@code Rachana} instance is built from Core's own families and the profile's providers, so two instances never share functions.
 * Nothing is found by class-path scanning. A host that supplies aggregates over its own engine (a pivot engine, a database) does it
 * here; where no provider supplies an aggregate Core does not have, the expression compiles and is refused with the reason when it
 * is evaluated.
 *
 * <p>Core depends on no provider; a provider depends on Core. A provider's functions are pure and thread-safe, keep Rachana's
 * rules (an empty argument gives an empty result unless the function says otherwise; a masked value makes the result masked) and
 * never take a name Rachana's own families have.
 */
public interface FunctionProvider {

    /** Who provides the functions, as the catalogue and a refusal name it ({@code the pivot engine (drishti-bicompute)}). */
    String source();

    /** The functions provided, each name once. */
    List<Provided> functions();

    /** The grouping behind {@code group(list, key, [aggregate])}; null when this provider does not group. */
    default Grouping grouping() {
        return null;
    }

    /**
     * One function a provider adds.
     *
     * @param name its one name
     * @param minArgs the fewest arguments
     * @param maxArgs the most arguments
     * @param args its arguments as the catalogue writes them ({@code list, [field]})
     * @param form {@code mergeable} or {@code holistic} for an aggregate, {@code measure} for a figure from another engine, {@code list}
     *     for a function from a list to a list resting on the provider's aggregates ({@code cumsum}, {@code movmean})
     * @param description what it means, one line
     * @param example an example on the demo data
     * @param function the implementation, its arguments evaluated
     */
    record Provided(String name, int minArgs, int maxArgs, String args, String form, String description, String example,
            ElFunction function) {}

    /** The grouping of a list's elements by a key, each group's values aggregated by a provided aggregate. */
    @FunctionalInterface
    interface Grouping {

        /**
         * The groups, in key order (text order, so ISO dates order), at most {@code maxGroups} (the first keys met; later new keys are
         * left out).
         *
         * @param keys the key of each element; a null or blank key leaves the element out
         * @param columns the aggregate's inputs, one column per input, each holding one value per element ({@code sum(@.NOTIONAL)}:
         *     one column; {@code wavg(@.PRICE, @.QUANTITY)}: two); empty for a count of the elements
         * @param aggregate a provided aggregate's name ({@code sum}, {@code avg}, {@code percentile} …), or {@code count}
         * @param options the aggregate's further literal arguments ({@code 0.95} for a percentile)
         * @param maxGroups the most groups returned
         */
        List<Group> group(List<String> keys, List<List<Object>> columns, String aggregate, List<Object> options, int maxGroups);
    }

    /**
     * One group: its key, its aggregated value (null when it has none; masked when a value it reads is masked) and how many elements it
     * holds.
     */
    record Group(String key, Object value, long count) {}
}
