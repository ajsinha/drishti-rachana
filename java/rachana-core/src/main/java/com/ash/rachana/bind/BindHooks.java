/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.internal.CoreText;
import com.ash.rachana.model.PanelKind;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import com.ash.rachana.model.Ordered;

/**
 * What a host plugs into the binder (split step A13): links ({@link LinkResolver}), the panel kinds a profile adds
 * ({@link PanelBinder} per kind), how a field name reads as a label, and the limits. {@link #core()} is Core alone: no kinds, no
 * profile panels, plain labels, default limits. The sources ({@link TableProvider}) and the viewer ({@link Masker}) are handed to the
 * parts that read them ({@link MapSupport}, {@link com.ash.rachana.control.ControlResolver}, {@link com.ash.rachana.wire.ScreenWire}).
 */
public record BindHooks(LinkResolver links, Map<PanelKind, PanelBinder> panels, UnaryOperator<String> labels, PanelLimits limits,
        PivotLimits pivot) {

    public BindHooks {
        links = links == null ? LinkResolver.NONE : links;
        panels = panels == null ? Map.of() : Ordered.copy(panels);
        labels = labels == null ? BindHooks::plainLabel : labels;
        limits = limits == null ? PanelLimits.defaults() : limits;
        pivot = pivot == null ? PivotLimits.defaults() : pivot;
    }

    /** Core alone. */
    public static BindHooks core() {
        return new BindHooks(null, null, null, null, null);
    }

    public BindHooks withLinks(LinkResolver next) {
        return new BindHooks(next, panels, labels, limits, pivot);
    }

    public BindHooks withPanel(PanelKind kind, PanelBinder binder) {
        Map<PanelKind, PanelBinder> next = new java.util.EnumMap<>(PanelKind.class);
        next.putAll(panels);
        next.put(kind, binder);
        return new BindHooks(links, next, labels, limits, pivot);
    }

    public BindHooks withLabels(UnaryOperator<String> next) {
        return new BindHooks(links, panels, next, limits, pivot);
    }

    public BindHooks withLimits(PanelLimits next) {
        return new BindHooks(links, panels, labels, next, pivot);
    }

    public BindHooks withPivot(PivotLimits next) {
        return new BindHooks(links, panels, labels, limits, next);
    }

    /**
     * A field name as a label without a host's label table or acronyms: words split at {@code _}, {@code -} and lower-to-upper
     * changes, an UPPER_SNAKE name read in lower case, the first word capitalised ({@code TRADE_DATE} and {@code tradeDate} both read
     * "Trade date").
     */
    public static String plainLabel(String name) {
        String[] words = words(name).split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i == 0 && !w.isEmpty()) {
                w = (char) CoreText.upper(w.charAt(0)) + w.substring(1);
            }
            out.append(i == 0 ? "" : " ").append(w);
        }
        return out.toString();
    }

    private static String words(String name) {
        if (upperSnake(name)) {
            name = CoreText.lower(name);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-') {
                sb.append(' ');
            } else if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(name.charAt(i - 1))) {
                sb.append(' ').append((char) CoreText.lower(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim().replaceAll(" +", " ");
    }

    private static boolean upperSnake(String name) {
        int letters = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLowerCase(c)) {
                return false;
            }
            letters += Character.isUpperCase(c) ? 1 : 0;
        }
        return letters > 1;
    }
}
