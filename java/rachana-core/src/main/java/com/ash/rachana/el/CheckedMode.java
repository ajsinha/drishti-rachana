/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.RachanaException;
import com.ash.rachana.RachanaCode;
import com.ash.rachana.el.check.Checker;
import com.ash.rachana.el.check.Fields;
import com.ash.rachana.el.check.Problem;
import com.ash.rachana.el.check.Scope;
import com.ash.rachana.el.check.Spelling;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rachana's checked mode, from text (docs/architecture/ARCHITECTURE.md §28 2.1): the one parser, then the {@link Checker} with the types, masks and
 * measures of a table or model. Wherever the types are known (a virtual table's source contract, a model's tables, a pivot over a typed
 * source) an expression is read here; a Sutra over untyped documents stays unchecked ({@link ElCompiler}). The same text means the same
 * thing in both: checked mode only refuses more, with DEL's codes ({@code RCH-9127}, {@code RCH-9129}, {@code RCH-9152},
 * {@code RCH-9161}).
 */
public final class CheckedMode {

    private CheckedMode() {}

    /** A parsed expression with where each node was written. */
    public record Parsed(String text, Expr expr, Map<Expr, int[]> spans) {
        /** Where {@code e} was written ({start, end}); the whole text when not recorded. */
        public int[] span(Expr e) {
            int[] s = spans.get(e);
            return s == null ? new int[] {0, text.length()} : s;
        }
    }

    /** Parses with spans (a syntax error is {@code RCH-2101}, as everywhere in Rachana). */
    public static Parsed parse(String text) {
        Parser.Parsed p = Parser.parseWithSpans(text, ElLimits.DEFAULTS);
        return new Parsed(text, p.expr(), p.spans());
    }

    /** Parses with spans at a language level ({@code 1.0}, {@code 1.1}). */
    public static Parsed parse(String text, String level) {
        Parser.Parsed p = Parser.parseWithSpans(text, ElLimits.DEFAULTS, FunctionTable.core(), !"1.0".equals(level));
        return new Parsed(text, p.expr(), p.spans());
    }

    /** Parses and checks; a text that does not parse is one {@code RCH-9127} problem at the place it stops. */
    public static Checker.Checked check(String text, Scope scope, Fields fields) {
        return check(text, scope, fields, "1.0");
    }

    /** {@link #check(String, Scope, Fields)} at a language level ({@code 1.0}, {@code 1.1}). */
    public static Checker.Checked check(String text, Scope scope, Fields fields, String level) {
        Parsed p;
        try {
            p = parse(text, level);
        } catch (ElException e) {
            int at = Math.max(0, e.position());
            return new Checker.Checked(com.ash.rachana.el.check.ElType.ANY,
                    List.of(new Problem(Problem.LANGUAGE, Problem.ERROR, e.getMessage(), null, at, Math.min(text.length(), at + 1))),
                    Set.of(), Set.of(), Set.of());
        }
        return Checker.check(com.ash.rachana.el.check.Signatures.get(), p.expr(), p.spans(), text, scope, fields, Spelling.RACHANA,
                !"1.0".equals(level));
    }

    /**
     * The checked expression, or a refusal: the first error's code ({@code RCH-9127} type or scope, {@code RCH-9129} not yet,
     * {@code RCH-9152} unknown name, {@code RCH-9161} masked field filtered) and its words.
     */
    public static Expr require(String text, Scope scope, Fields fields) {
        Checker.Checked c = check(text, scope, fields);
        Problem p = c.firstError().orElse(null);
        if (p != null) {
            throw new RachanaException(code(p.code()), p.message() + (p.fix() == null ? "" : " (" + p.fix() + ")"));
        }
        return parse(text).expr();
    }

    static RachanaCode code(String code) {
        return switch (code) {
            case Problem.LATER -> RachanaCode.LATER_WAVE;
            case Problem.NAME -> RachanaCode.NAME_UNKNOWN;
            case Problem.MASKED -> RachanaCode.MASKED_FIELD;
            default -> RachanaCode.EXPR_INVALID;
        };
    }
}
