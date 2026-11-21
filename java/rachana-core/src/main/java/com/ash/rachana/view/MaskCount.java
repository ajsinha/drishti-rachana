/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import com.ash.rachana.api.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Counts, in what a view shows, the values a caller's field masks replaced ({@link Node#MASK}): the number behind
 * {@code provenance.masked}, and which panels hold some. Counted on the built view (the text a person would read), so it holds
 * for every panel kind without each binder reporting its own.
 */
public final class MaskCount {


    /** How many masked values, and which panels hold some. */
    public record Result(int count, List<String> panels) {}

    private MaskCount() {}

    /** Counts over the strip cells, the title and every panel's content. */
    public static Result of(ViewModel.TitleView title, List<ViewModel.Cell> strip, List<ViewModel.PanelView> panels) {
        int total = 0;
        if (title != null) {
            total += text(title.pill()) + text(title.id()) + (title.with() == null ? 0 : text(title.with().text()));
        }
        for (ViewModel.Cell c : strip) {
            total += text(c.text());
        }
        List<String> withMasks = new ArrayList<>();
        for (ViewModel.PanelView p : panels) {
            int n = p.data() == null ? 0 : count(ViewJson.tree(p.data()));
            if (n > 0) {
                total += n;
                withMasks.add(p.id());
            }
        }
        return new Result(total, withMasks);
    }

    private static int text(String s) {
        return s != null && s.contains(Node.MASK) ? 1 : 0;
    }

    private static int count(Object n) {
        if (n instanceof String s) {
            return s.contains(Node.MASK) ? 1 : 0;
        }
        int sum = 0;
        if (n instanceof Map<?, ?> m) {
            for (Object child : m.values()) {
                sum += count(child);
            }
        } else if (n instanceof List<?> l) {
            for (Object child : l) {
                sum += count(child);
            }
        }
        return sum;
    }
}
