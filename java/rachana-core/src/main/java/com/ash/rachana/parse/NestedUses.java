/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A fragment's uses of other fragments (DESIGN.md (libraries) 4.4, 7.2; level 1.2): a use named {@code as: x} inside a fragment used as {@code a}
 * is the instance {@code a-x}. Its ids become {@code a-x-<id>}, its private state {@code a-x.<name>}, its events {@code @a-x.<event>}, so
 * two uses of the outer fragment (or two inner uses of one fragment) never collide. The outer fragment reads the inner use as written
 * ({@code state.x.<output>}, {@code @x.<event>}); only the inner use's outputs may be read ({@code RCH-2117}). Stateless.
 */
final class NestedUses {

    private NestedUses() {}

    /** The {@code as:} of every use among a fragment's panels, at any depth, in order. */
    static Set<String> names(PNode panels) {
        Set<String> out = new LinkedHashSet<>();
        walk(panels, n -> {
            if (n.isMap() && n.map().containsKey("use") && n.map().get("as") != null && n.map().get("as").text() != null) {
                out.add(n.map().get("as").text());
            }
        });
        return out;
    }

    private static Pattern reads(Set<String> names) {
        String alt = String.join("|", names.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?<![A-Za-z0-9_.$@-])(@|state\\.)(" + alt + ")(?=\\.)");
    }

    /** {@code s} without its reads of nested uses ({@code state.x.<name>}: not the screen's state). */
    static String withoutReads(String s, Set<String> names) {
        if (names.isEmpty() || !s.contains("state.")) {
            return s;
        }
        Matcher m = Pattern.compile("(?<![A-Za-z0-9_.$@-])state\\.(" + String.join("|", names.stream().map(Pattern::quote).toList())
                + ")\\.[A-Za-z][A-Za-z0-9_]*").matcher(s);
        StringBuilder sb = new StringBuilder();
        int copied = 0;
        while (m.find()) {
            sb.append(s, copied, m.start());
            copied = m.end();
        }
        return sb.append(s, copied, s.length()).toString();
    }

    /**
     * The fragment's own panels with each nested use renamed for the outer use {@code as}: its {@code as: x} becomes {@code as-x}, and
     * every read of it ({@code @x.}, {@code state.x.}) follows. The user's slot content ({@code own} false) is untouched.
     */
    static List<PNode> prefixed(List<PNode> panels, List<Boolean> own, String as, Set<String> names) {
        if (names.isEmpty()) {
            return panels;
        }
        Pattern p = reads(names);
        List<PNode> out = new ArrayList<>(panels.size());
        for (int i = 0; i < panels.size(); i++) {
            out.add(own.get(i) ? rename(panels.get(i), as, names, p) : panels.get(i));
        }
        return out;
    }

    private static PNode rename(PNode n, String as, Set<String> names, Pattern p) {
        if (n.isMap()) {
            boolean use = n.map().containsKey("use");
            Map<String, PNode> m = new LinkedHashMap<>();
            n.map().forEach((k, v) -> m.put(k, use && "as".equals(k) && names.contains(v.text())
                    ? new PNode(as + "-" + v.text(), v.line(), v.column()) : rename(v, as, names, p)));
            return new PNode(m, n.line(), n.column());
        }
        if (n.isList()) {
            List<PNode> l = new ArrayList<>();
            n.list().forEach(v -> l.add(rename(v, as, names, p)));
            return new PNode(l, n.line(), n.column());
        }
        if (!(n.value() instanceof String s) || s.indexOf('@') < 0 && !s.contains("state.")) {
            return n;
        }
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        int copied = 0;
        while (m.find()) {
            sb.append(s, copied, m.start()).append(m.group(1)).append(as).append('-').append(m.group(2));
            copied = m.end();
        }
        return new PNode(sb.append(s, copied, s.length()).toString(), n.line(), n.column());
    }

    private static final Pattern PRIVATE = Pattern.compile("(?<![A-Za-z0-9_.$@-])state\\.([a-z][a-z0-9-]*)\\.([A-Za-z][A-Za-z0-9_]*)");

    /**
     * What a fragment used as {@code as} (null: a use without one) reads of its nested uses (its own text, before binding): only their outputs, {@code RCH-2117}
     * otherwise. Run once every use is bound ({@code stateful} holds every instance, nested ones under {@code <as>-<x>}).
     */
    static void checkReads(String fragment, PNode panels, String as, Set<String> names, Map<String, TypedFragment.Stateful> stateful, String file,
            List<SutraProblem> out) {
        walk(panels, n -> {
            if (n.value() instanceof String s && s.contains("state.")) {
                Matcher m = PRIVATE.matcher(s);
                while (m.find()) {
                    TypedFragment.Stateful inner = names.contains(m.group(1)) ? stateful.get(as == null ? m.group(1) : as + "-" + m.group(1)) : null;
                    if (inner != null && !inner.outputs().contains(m.group(2))) {
                        out.add(TypedFragment.problem(TypedFragment.SCOPE, "fragment " + fragment + " reads state." + m.group(1) + "." + m.group(2)
                                + ", which is private to its use " + m.group(1) + " of fragment " + inner.fragment() + (inner.outputs().isEmpty()
                                ? " (it publishes no outputs)" : "; it reads that use's outputs: " + String.join(", ", inner.outputs())), file, n));
                    }
                }
            }
        });
    }

    private static void walk(PNode n, java.util.function.Consumer<PNode> f) {
        if (n == null) {
            return;
        }
        f.accept(n);
        if (n.isMap()) {
            n.map().values().forEach(v -> walk(v, f));
        } else if (n.isList()) {
            n.list().forEach(v -> walk(v, f));
        }
    }
}
