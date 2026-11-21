/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.parse;

import com.ash.rachana.SutraProblem;
import com.ash.rachana.model.DataEntry;
import com.ash.rachana.model.SourceLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a Sutra's {@code data:} block (SPEC 2.4, decision D10; language level 1.1). Codes: {@code RCH-2073} an entry that does not have
 * exactly one of {@code table}, {@code url}, {@code rows}, {@code record}, or a bad name; {@code RCH-2072} a live entry without a key;
 * {@code RCH-2011} an unknown key in an entry; {@code RCH-2012} a value of the wrong type.
 */
final class DataReader {

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final Pattern TABLE = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");
    private static final List<String> FORMS = List.of("table", "url", "rows", "record");
    private static final List<String> COMMON = List.of("key", "fields", "as", "where", "limit", "refresh", "live", "mode", "format", "path");
    private static final Set<String> FORMATS = Set.of("json", "csv", "ndjson", "arrow");
    private static final Set<String> MODES = Set.of("auto", "client", "server");

    private final String file;
    private final List<SutraProblem> problems;

    DataReader(String file, List<SutraProblem> problems) {
        this.file = file;
        this.problems = problems;
    }

    /** The entries in the order written; empty when there is no block or it cannot be read. */
    List<DataEntry> read(PNode n) {
        List<DataEntry> out = new ArrayList<>();
        if (n == null || n.value() == null) {
            return out;
        }
        if (!n.isMap()) {
            problem("RCH-2012", "'data' must be a mapping of name to source ({ table }, { url }, { rows }, { record } or an address)", n);
            return out;
        }
        n.map().forEach((name, v) -> {
            DataEntry e = entry(name, v);
            if (e != null) {
                out.add(e);
            }
        });
        return out;
    }

    private DataEntry entry(String name, PNode n) {
        if (!NAME.matcher(name).matches()) {
            problem("RCH-2073", "data name '" + name + "' must be a name read as $." + name + ": letters, digits and _, not starting with a digit", n);
            return null;
        }
        SourceLocation at = new SourceLocation(file, n.line(), n.column());
        if (n.isScalar() && n.value() instanceof String s) {                   // text: shorthand for { url }
            return new DataEntry(name, DataEntry.Form.URL, null, s, null, null, null, null, null, null, null, null, null, false, null, null, at);
        }
        if (!n.isMap()) {
            problem("RCH-2073", "data '" + name + "' must be an address or a mapping with one of table, url, rows, record", n);
            return null;
        }
        Map<String, PNode> m = n.map();
        int before = problems.size();
        m.forEach((k, v) -> {
            if (!FORMS.contains(k) && !COMMON.contains(k)) {
                problem("RCH-2011", "unknown key '" + k + "' in data '" + name + "'; expected one of " + String.join(", ", FORMS) + ", then "
                        + String.join(", ", COMMON), v);
            }
        });
        List<String> forms = FORMS.stream().filter(m::containsKey).toList();
        if (forms.size() != 1) {
            problem("RCH-2073", "data '" + name + "' must have exactly one of table, url, rows, record" + (forms.isEmpty() ? "" : "; it has "
                    + String.join(", ", forms)), n);
            return null;
        }
        String table = null, url = null;
        List<Object> rows = null;
        Map<String, Object> record = null;
        DataEntry.Form form = switch (forms.get(0)) { case "table" -> DataEntry.Form.TABLE; case "url" -> DataEntry.Form.URL; case "rows" -> DataEntry.Form.ROWS; default -> DataEntry.Form.RECORD; };
        PNode src = m.get(forms.get(0));
        switch (form) {
            case TABLE -> {
                table = src.value() instanceof String s ? s : null;
                if (table == null || table.length() > 128 || !TABLE.matcher(table).matches()) {
                    problem("RCH-2073", "table of data '" + name + "' must be a table name (letters, digits, _ and -, dots between parts, at most 128)", src);
                }
            }
            case URL -> {
                url = src.value() instanceof String s && !s.isBlank() ? s : null;
                if (url == null) {
                    problem("RCH-2012", "url of data '" + name + "' must be an address (text)", src);
                }
            }
            case ROWS -> {
                if (!src.isList() || !src.list().stream().allMatch(PNode::isMap)) {
                    problem("RCH-2012", "rows of data '" + name + "' must be a list of mappings", src);
                } else {
                    rows = new ArrayList<>();
                    for (PNode r : src.list()) {
                        rows.add(r.plain());
                    }
                }
            }
            case RECORD -> {
                if (!src.isMap()) {
                    problem("RCH-2012", "record of data '" + name + "' must be a mapping", src);
                } else {
                    record = plainMap(src);
                }
            }
        }
        List<String> key = key(m.get("key"), name);
        String as = choice(m.get("as"), Set.of("rows", "record"), "as", name);
        String mode = choice(m.get("mode"), MODES, "mode", name);
        String format = choice(m.get("format"), FORMATS, "format", name);
        String where = text(m.get("where"), "where", name);
        String path = text(m.get("path"), "path", name);
        Integer limit = null;
        PNode l = m.get("limit");
        if (l != null) {
            if (l.value() instanceof Long x && x > 0 && x <= Integer.MAX_VALUE) {
                limit = x.intValue();
            } else {
                problem("RCH-2012", "limit of data '" + name + "' must be a whole number above 0", l);
            }
        }
        Double refresh = null;
        PNode r = m.get("refresh");
        if (r != null) {
            Double seconds = r.value() instanceof Long x ? Double.valueOf(x) : r.value() instanceof Double x ? x : null;
            if (seconds != null && seconds > 0) {
                refresh = seconds;
            } else {
                problem("RCH-2012", "refresh of data '" + name + "' must be a number of seconds above 0", r);
            }
        }
        boolean live = false;
        PNode lv = m.get("live");
        if (lv != null) {
            if (lv.value() instanceof Boolean b) {
                live = b;
            } else {
                problem("RCH-2012", "live of data '" + name + "' must be true or false", lv);
            }
        }
        if (live && key.isEmpty()) {
            problem("RCH-2072", "data '" + name + "' is live but has no key: a live table is patched by key (add key: [field, ...])", lv);
        }
        if (live && form != DataEntry.Form.TABLE) {
            problem("RCH-2073", "only a table can be live: data '" + name + "' is " + forms.get(0), lv);
        }
        Map<String, Object> fields = null;
        PNode f = m.get("fields");
        if (f != null) {
            if (!f.isMap()) {
                problem("RCH-2012", "fields of data '" + name + "' must be a mapping of field name to { type, label?, fmt? }", f);
            } else {
                fields = plainMap(f);
            }
        }
        if (problems.size() > before) {
            return null;
        }
        return new DataEntry(name, form, table, url, format, path, rows, record, key, as, where, limit, refresh, live, mode, fields, at);
    }

    private List<String> key(PNode k, String name) {
        List<String> out = new ArrayList<>();
        if (k == null) {
            return out;
        }
        if (k.value() instanceof String s) {
            out.add(s);
        } else if (k.isList() && k.list().stream().allMatch(x -> x.value() instanceof String)) {
            k.list().forEach(x -> out.add((String) x.value()));
        } else {
            problem("RCH-2012", "key of data '" + name + "' must be a field name or a list of field names", k);
        }
        return out;
    }

    private String choice(PNode n, Set<String> allowed, String what, String name) {
        if (n == null) {
            return null;
        }
        if (n.value() instanceof String s && allowed.contains(s)) {
            return s;
        }
        problem("RCH-2012", what + " of data '" + name + "' must be one of " + String.join(", ", new java.util.TreeSet<>(allowed)), n);
        return null;
    }

    private String text(PNode n, String what, String name) {
        if (n == null) {
            return null;
        }
        if (n.value() instanceof String s) {
            return s;
        }
        problem("RCH-2012", what + " of data '" + name + "' must be text", n);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> plainMap(PNode n) {
        Object p = n.plain();
        return p instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    private void problem(String code, String message, PNode at) {
        problems.add(new SutraProblem(code, message, new SourceLocation(file, at == null ? 0 : at.line(), at == null ? 0 : at.column())));
    }
}
