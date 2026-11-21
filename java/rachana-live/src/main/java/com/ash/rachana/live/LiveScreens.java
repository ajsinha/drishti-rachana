/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveTableSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ash.rachana.model.Ordered;

/**
 * The screen a declaration gives (LIVE_TABLES 2.2, LT-12): a Sutra that calls the library fragment {@code core/live-table@1} with the
 * declaration's columns (heavy ones left out), its order and its edit mode, and, for a table with edits and a tray,
 * {@code core/held-changes@1} over {@code <name>.changes}. No screen is hand-built; the Sutra is plain text the host serves or saves.
 * Values are written as JSON flow values (YAML 1.2 reads them as written).
 */
final class LiveScreens {

    /** The data entry of the table and of its tray in the Sutra. */
    static final String ROWS = "live";
    static final String PENDING = "pending";

    private LiveScreens() {}

    static String screen(LiveTableSpec spec) {
        String title = spec.title() == null ? spec.name() : spec.title();
        StringBuilder b = new StringBuilder();
        b.append("rachana: ").append(com.ash.rachana.profile.Grammar.DEFAULT_LEVEL).append("\n");   // SPEC 2.1: its level
        b.append("sutra: ").append(Json.write(sutraName(spec.name()))).append('\n');
        b.append("version: 1\n");
        b.append("libraries: [core@1]\n");
        b.append("title: { id: ").append(Json.write("'" + title.replace("\\", "\\\\").replace("'", "\\'") + "'")).append(" }\n");
        b.append("data:\n");
        b.append("  ").append(ROWS).append(": { table: ").append(Json.write(spec.name())).append(", key: ")
                .append(Json.write(spec.key().size() == 1 ? spec.key().get(0) : spec.key())).append(", live: true }\n");
        boolean tray = spec.edit() != null && spec.tray();
        if (tray) {
            b.append("  ").append(PENDING).append(": { table: ").append(Json.write(spec.name() + WriteGuard.TRAY))
                    .append(", key: change, live: true }\n");
        }
        b.append("panels:\n");
        b.append("  - use: core/live-table@1\n");
        b.append("    with:\n");
        b.append("      title: ").append(Json.write(title)).append('\n');
        b.append("      rows: $.").append(ROWS).append('\n');
        Map<String, Object> order = order(spec);
        b.append("      order: ").append(Json.write(order.get("col"))).append('\n');
        if ("asc".equals(order.get("dir"))) {
            b.append("      direction: asc\n");
        }
        if (spec.edit() != null) {
            b.append("      edit: ").append("beside".equals(spec.edit().get("edit")) ? "beside" : "cells").append('\n');
        }
        b.append("      columns:\n");
        for (LiveTableSpec.Column c : spec.columns()) {
            if (c.heavy()) {
                continue;                                             // never in a list page (LIVE_TABLES 2.1): read by key
            }
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("label", c.label() == null ? c.name() : c.label());
            col.put("bind", c.name().matches("[A-Za-z_][A-Za-z0-9_]*") ? "@." + c.name() : "@['" + c.name().replace("'", "\\'") + "']");
            if (c.fmt() != null) {
                col.put("fmt", c.fmt());
            }
            b.append("        - ").append(flow(col)).append('\n');
        }
        if (tray) {
            b.append("  - use: core/held-changes@1\n");
            b.append("    with:\n");
            b.append("      changes: $.").append(PENDING).append('\n');
        }
        return b.toString();
    }

    /** The table's own order (its first {@code sort} entry), else its version newest first, else its first key column. */
    private static Map<String, Object> order(LiveTableSpec spec) {
        if (!spec.sort().isEmpty()) {
            Map<String, Object> s = spec.sort().get(0);
            return Ordered.map("col", String.valueOf(s.get("col")), "dir", "asc".equals(s.get("dir")) ? "asc" : "desc");
        }
        if (spec.version() != null) {
            return Ordered.map("col", spec.version(), "dir", "desc");
        }
        return Ordered.map("col", spec.key().get(0), "dir", "asc");
    }

    /** A Sutra's name from a table's: letters, digits and '-' ({@code trade.changes} gives {@code trade-changes-live}). */
    private static String sutraName(String table) {
        String s = table.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return (s.isEmpty() || !Character.isLetter(s.charAt(0)) ? "t-" + s : s) + "-live";
    }

    /** One mapping as a YAML flow mapping of JSON values. */
    private static String flow(Map<String, Object> m) {
        List<String> parts = new ArrayList<>();
        m.forEach((k, v) -> parts.add(k + ": " + Json.write(v)));
        return "{ " + String.join(", ", parts) + " }";
    }
}
