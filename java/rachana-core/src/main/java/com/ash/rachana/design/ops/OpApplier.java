/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies a list of {@link Op operations} to the text of a Sutra, one after the other. After each one the result is parsed
 * as a Sutra; an operation that fails, or whose result is not valid, is skipped with a located {@link OpProblem} (its index
 * in the list) and the text stays as it was, so the output is never a corrupted Sutra. The text of a valid input keeps its
 * comments, quoting and key order wherever an operation does not touch it. Stateless and thread-safe.
 */
public final class OpApplier {

    private final com.ash.rachana.parse.SutraParser parser;

    /** An applier of Core's grammar alone: {@code setMatch} is refused (the {@code match} key is a profile's). */
    public OpApplier() {
        this(new com.ash.rachana.parse.SutraParser());
    }

    /** An applier whose results must be valid Sutras of this parser's grammar ({@link com.ash.rachana.Rachana#parser()}). */
    public OpApplier(com.ash.rachana.parse.SutraParser parser) {
        this.parser = java.util.Objects.requireNonNull(parser, "a parser");
    }

    /**
     * @param yaml the Sutra text; it may be invalid only when the first operation is a {@link Text} that replaces it
     * @param ops the operations, in order
     */
    public OpResult apply(String yaml, List<? extends Op> ops) {
        String text = yaml == null ? "" : yaml;
        List<OpProblem> problems = new ArrayList<>();
        int applied = 0;
        for (int i = 0; i < ops.size(); i++) {
            Op op = ops.get(i);
            try {
                SutraDoc doc = new SutraDoc(text, parser);
                if (op instanceof SetMatch && !parser.grammar().hasKey("match")) {
                    throw new OpException(OpException.NOT_ACCEPTED, "setMatch sets the 'match' key, which comes with a profile; this grammar has none");
                }
                op.applyTo(doc);
                String next = doc.text();
                doc.sutra();                                     // the result must be a valid Sutra
                text = next;
                applied++;
            } catch (OpException e) {
                problems.add(new OpProblem(i, op.name(), e.code(), e.getMessage(), e.line()));
            } catch (SutraException e) {
                SutraProblem first = e.problems().get(0);
                String more = e.problems().size() > 1 ? " (and " + (e.problems().size() - 1) + " more)" : "";
                int line = first.location() == null ? 0 : first.location().line();
                problems.add(new OpProblem(i, op.name(), first.code(), first.message() + more, line));
            } catch (RuntimeException e) {
                problems.add(new OpProblem(i, op.name(), OpException.UNEDITABLE, "cannot apply: " + e.getMessage(), 0));
            }
        }
        return new OpResult(text, List.copyOf(problems), applied);
    }
}
