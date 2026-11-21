/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.bind;

import com.ash.rachana.view.PanelData;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.Values;
import com.ash.rachana.model.Panel;

/**
 * Binds a {@code map} panel: which country is highlighted (or whose regions are drawn), read from the document; what a click
 * opens and how the countries are shaded come from {@link MapSupport}, read across a kind before the panels are bound. The
 * shapes are the console's (vendored), so the record carries codes and figures, never geometry.
 */
final class MapBinder {

    private final ElCompiler el;

    MapBinder(ElCompiler el) {
        this.el = el;
    }

    PanelData map(Panel p, BindContext c) {
        boolean region = p.option("region").isPresent();
        String highlight = text(p, "highlight", c);
        String country = region ? text(p, "region", c) : null;
        MapSupport.Lookup found = c.maps().get(p.id());
        return new PanelData.GeoMap(region ? "region" : "world", highlight, country, found == null ? null : found.link(),
                found == null ? null : found.shade(), p.option("unit").orElse(null));
    }

    /** The text an option's expression gives for this document; null when the document lacks it or it is masked (a masked place is never named). */
    private String text(Panel p, String option, BindContext c) {
        return p.option(option).map(expr -> {
            try {
                Object v = el.compile(expr).eval(c.eval());
                return Values.isNull(v) || Values.masked(v) ? null : Values.text(v);
            } catch (RuntimeException e) {
                return null;
            }
        }).filter(s -> !s.isBlank()).orElse(null);
    }
}
