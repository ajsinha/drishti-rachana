/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.design.ops;

import com.ash.rachana.internal.NumberText;
import com.ash.rachana.model.Area;
import com.ash.rachana.model.Panel;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.model.PanelOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.ash.rachana.model.Ordered;

/** The checks every operation shares, against {@link PanelKind}, {@link PanelOptions} and the Sutra's panel keys. Stateless. */
final class OpChecks {

    /** Keys any panel takes besides its kind's options (the parser's own list, without {@code id} and {@code kind}). */
    static final Set<String> COMMON = Ordered.set("title", "key", "code", "area", "infer", "columns", "body", "panels", "description", Panel.SPAN, Panel.HEIGHT);
    static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

    private OpChecks() {}

    static PanelKind kind(String text) {
        return PanelKind.parse(text).orElseThrow(() -> new OpException(OpException.NOT_ACCEPTED, "unknown panel kind '" + text
                + "'; expected one of " + String.join(", ", Arrays.stream(PanelKind.values()).filter(k -> k.at("1.0")).map(PanelKind::id).toList())));
    }

    static void option(PanelKind kind, String option, Object value) {
        if (option == null || option.isBlank()) {
            throw new OpException(OpException.MALFORMED, "an option name is needed");
        }
        if ("id".equals(option) || "kind".equals(option)) {
            throw new OpException(OpException.NOT_ACCEPTED, "'" + option + "' cannot be set on a panel: remove it and add another");
        }
        if (!COMMON.contains(option) && !kind.accepts(option)) {
            List<String> ok = new ArrayList<>(kind.required());
            ok.addAll(kind.optional());
            ok.sort(null);                                   // the kind's sets have no order of their own: name them sorted, every run alike
            throw new OpException(OpException.NOT_ACCEPTED, "option '" + option + "' is not valid for '" + kind.id() + "' panels; expected one of "
                    + String.join(", ", ok) + " or " + String.join(", ", COMMON.stream().sorted().toList()));
        }
        if ((value instanceof Map<?, ?> || value instanceof List<?>) && !PanelOptions.takesContainer(kind, option)) {
            throw new OpException(OpException.BAD_VALUE, "option '" + option + "' of '" + kind.id() + "' panels takes one value (text, number or true/false), not a "
                    + (value instanceof Map<?, ?> ? "mapping" : "list"));
        }
        if (value != null && !COMMON.contains(option)) {
            PanelOptions.problem(kind, option, value).ifPresent(why -> {
                throw new OpException(OpException.BAD_VALUE, why);
            });
        }
        if (Panel.SPAN.equals(option) && value != null) {
            range(option, value, Panel.MAX_SPAN);
        }
        if (Panel.HEIGHT.equals(option) && value != null) {
            range(option, value, Panel.MAX_HEIGHT);
        }
    }

    static void range(String what, Object value, int max) {
        if (!(value instanceof Long n) || n < 1 || n > max) {
            throw new OpException(OpException.BAD_VALUE, what + " must be a whole number from 1 to " + max + ", not '" + value + "'");
        }
    }

    static Area area(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "main" -> Area.MAIN;
            case "right", "side" -> Area.RIGHT;
            default -> throw new OpException(OpException.BAD_VALUE, "area must be main or right, not '" + text + "'");
        };
    }

    /** JSON numbers as Long or Double, containers copied (the model's own types), so values check as the parser's do. */
    static Object plain(Object v) {
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return ((Number) v).longValue();
        }
        if (v instanceof Float) {
            return NumberText.toDouble((Number) v);
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> out.put(String.valueOf(k), plain(x)));
            return out;
        }
        if (v instanceof List<?> l) {
            return l.stream().map(OpChecks::plain).toList();
        }
        return v;
    }
}
