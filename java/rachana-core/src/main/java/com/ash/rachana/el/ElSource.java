/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.el;

import com.ash.rachana.el.check.Signatures;
import com.ash.rachana.internal.CoreText;
import com.ash.rachana.internal.NumberText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rachana-EL as written, for tools that read and rewrite expressions without running them (the {@code rachana upgrade} of
 * rachana-tools): the tokens and the parse tree of an expression with where each part was written, and the facts in which language
 * level 1.0 and later levels differ (DECISIONS EL1-1 to EL1-5, SPEC 4.10 rule 3, 4.13, 4.16, 4.17). Every answer is the compiler's
 * own (the lexer, the parser, the catalogue, the 1.1 function table), so a tool cannot disagree with it. Stateless, thread-safe.
 */
public final class ElSource {

    private ElSource() {}

    /** One token: its kind (a name of the lexer's types: {@code IDENT}, {@code NUMBER}, {@code STRING}, {@code OP} ...), its text, where. */
    public record Tok(String type, String text, int start, int end) {}

    /**
     * The tokens of {@code src} as level 1.0 or 1.1 reads them, the end of input left out.
     *
     * @throws ElException when the text does not split into tokens
     */
    public static List<Tok> tokens(String src, boolean level11) {
        List<Tok> out = new ArrayList<>();
        for (Token t : Lexer.tokens(src, level11)) {
            if (t.type() != Token.Type.EOF) {
                int end = t.type() == Token.Type.STRING ? closing(src, t.pos()) : t.pos() + t.length();
                out.add(new Tok(t.type().name(), t.text(), t.pos(), end));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** Where a quoted text that starts at {@code at} ends (after its closing quote). */
    private static int closing(String src, int at) {
        char q = src.charAt(at);
        int j = at + 1;
        while (j < src.length() && src.charAt(j) != q) {
            j += src.charAt(j) == '\\' ? 2 : 1;
        }
        return Math.min(src.length(), j + 1);
    }

    /** An expression's tree and where each node was written ({start, end} offsets into the source). */
    public record Parsed(Expr expr, Map<Expr, int[]> spans) {}

    /**
     * {@code src} parsed against {@code table} at level 1.0 or 1.1, with the place of each node.
     *
     * @throws ElException when it does not compile at that level
     */
    public static Parsed parse(String src, FunctionTable table, boolean level11) {
        Parser.Parsed p = Parser.parseWithSpans(src, ElLimits.DEFAULTS, table == null ? FunctionTable.core() : table, level11);
        return new Parsed(p.expr(), Collections.unmodifiableMap(p.spans()));
    }

    /**
     * The name a named argument of {@code fn}, written {@code name} at level 1.0, has at level 1.1 for the same parameter (SPEC 4.10
     * rule 3: from 1.1 a Core function's arguments are named by the function catalogue); {@code name} itself when the two agree; null
     * when level 1.1 cannot give the same parameter by name (a repeatable one, or a keyword-only argument level 1.0 kept by name).
     */
    public static String namedArgumentAtLevel11(String fn, String name) {
        List<CoreParams.Param> core = CoreParams.of(fn);
        if (core == null) {
            return name;                                         // level 1.1 binds as 1.0 does (a profile's function)
        }
        Signatures.Signature sig = Signatures.get().function(Functions.oneName(fn)).orElse(null);
        if (sig == null) {
            return null;
        }
        Signatures.Param param = sig.param(name).orElse(null);
        if (param == null) {
            return null;
        }
        boolean rachana = "R".equals(sig.runs());
        if (param.keywordOnly() && !rachana) {
            return null;
        }
        List<Signatures.Param> positional = rachana ? sig.params() : sig.params().stream().filter(x -> !x.keywordOnly()).toList();
        int place = positional.indexOf(param);
        if (place < 0 || place >= core.size() || core.get(place).repeatable()) {
            return null;
        }
        return core.get(place).name();
    }

    /** The functions whose meaning level 1.1 changes for the same call (Level11's table and its forms): {@code range}, the code-point ones, {@code sum} ... */
    public static Set<String> changedAtLevel11() {
        Set<String> out = new TreeSet<>(Level11.names());
        out.add("sort");
        return Collections.unmodifiableSet(out);
    }

    /** Whether a later level of Rachana reserves {@code name} (reserved.yaml's names: a call at 1.1 is refused unless a table has it). */
    public static boolean reserved(String name) {
        return ReservedNames.contains(name);
    }

    /** Whether {@code table} has a function of this name (a profile's included). */
    public static boolean known(FunctionTable table, String name) {
        return (table == null ? FunctionTable.core() : table).arityOf(name) != null;
    }

    /**
     * Whether an operator reads {@code text} as a number at level 1.0 (Java's grammar: hexadecimal, {@code Infinity}, {@code NaN},
     * {@code d}/{@code f} suffixes) or at 1.1 and later (the literal grammar, SPEC 4.16 rule 1; DECISIONS EL1-5).
     */
    public static boolean readsAsNumber(String text, boolean level11) {
        if (level11) {
            Object v = Exact.read(text);
            return !(v instanceof Double d && d.isNaN());
        }
        if (!Values.couldBeNumber(text)) {
            return false;
        }
        try {
            NumberText.parse(CoreText.strip(text));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
