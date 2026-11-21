/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.table;

import com.ash.rachana.SutraException;
import com.ash.rachana.SutraProblem;
import com.ash.rachana.el.ElCompiler;
import com.ash.rachana.el.ElException;
import com.ash.rachana.el.EvalContext;
import com.ash.rachana.el.Expr;
import com.ash.rachana.model.Form;
import com.ash.rachana.model.SourceLocation;
import com.ash.rachana.parse.PNode;
import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.parse.YamlSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A live table's declaration (docs/design/LIVE_TABLES.md section 2): one {@code <name>.live.yaml}, a Sutra-family document
 * ({@code rachana: 1}, {@code live-table: <name>}) that names its snapshot source and live stream, its key, version and change columns,
 * its columns (the Data Protocol's fields plus {@code width}, {@code fmt} and {@code heavy}), its own order, grouping and subtotals,
 * {@code narrow}, {@code retention}, the tray and, for a blotter, {@code edit:} (a Core form). Checked at load with every problem reported
 * at once ({@link SutraException}); a declaration with problems is never half-loaded. {@link #schema} answers its Data Protocol schema
 * document (section 4). Immutable. The same code runs on the JVM and, compiled, in JavaScript (Studio, {@code rachana lint}).
 *
 * <p>Codes: {@code RCH-2009} language version, {@code 2010} missing key, {@code 2011} unknown key, {@code 2012} wrong shape, {@code 2020}
 * bad name or value, {@code 2023} unknown type, {@code 2024} a column twice, {@code 2033} YAML a declaration may not contain,
 * {@code 2058} the edit form, {@code 2060} a profile's key without that profile, {@code 2071} unknown column, {@code 2072} no key,
 * {@code 2101} the condition does not compile, and the live-table family {@code 2080} (edit needs a key and a version), {@code 2081} a
 * column that cannot be edited, {@code 2082} a heavy column where a list needs it, {@code 2083} retention.
 */
public final class LiveTableSpec {

    /** The file suffix of a declaration. */
    public static final String SUFFIX = ".live.yaml";
    /** The key that names a declaration's table. */
    public static final String KEY = "live-table";
    /** The keys Core reads. */
    public static final Set<String> CORE_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(List.of("rachana", KEY, "title", "snapshot",
            "stream", "where", "key", "version", "changed-by", "changed-at", "columns", "sort", "group-by", "subtotals", "narrow", "retention",
            "tray", "edit")));
    /** The keys a host's profile may add, at the top and in {@code edit}; without that profile each is a problem. */
    public static final Set<String> PROFILE_KEYS = Set.of("kind", "query", "links", "edit.roles", "edit.save", "edit.fields.editable-by");
    /** The column fields a declaration may write. */
    public static final Set<String> COLUMN_KEYS = Set.of("name", "label", "type", "width", "fmt", "heavy", "items", "nullable");
    /** How a change's reason is asked for. */
    public static final List<String> REASONS = List.of("none", "optional", "required");

    public static final String EDIT_NEEDS = "RCH-2080";
    public static final String NOT_EDITABLE = "RCH-2081";
    public static final String HEAVY = "RCH-2082";
    public static final String RETENTION = "RCH-2083";

    private static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9_-]*(\\.[a-z0-9_-]+)*$");
    private static final Pattern DURATION = Pattern.compile("^\\s*(\\d+)\\s*(ms|s|m|h|d)\\s*$");

    /** One column: the Data Protocol's fields plus how a grid shows it. */
    public record Column(String name, String label, String type, Integer width, String fmt, boolean heavy, String items, boolean nullable) {}

    /** The live side's retention: rows older than {@code ttlMs} by {@code on} (null: arrival), beyond {@code maxRows} the oldest. */
    public record Retention(long ttlMs, String on, int maxRows) {

        public boolean hasTtl() {
            return ttlMs > 0;
        }

        public boolean hasMaxRows() {
            return maxRows > 0;
        }
    }

    private final String file;
    private final String name;
    private final String title;
    private final String snapshot;
    private final String stream;
    private final String where;
    private final Map<String, Object> whereTree;
    private final List<String> key;
    private final String version;
    private final String changedBy;
    private final String changedAt;
    private final List<Column> columns;
    private final List<Map<String, Object>> sort;
    private final List<String> groupBy;
    private final List<String> subtotals;
    private final List<String> narrow;
    private final Retention retention;
    private final boolean tray;
    private final Map<String, Object> edit;
    private final String reason;
    private final Map<String, Object> profile;

    private LiveTableSpec(Reader r) {
        file = r.file;
        name = r.name;
        title = r.title;
        snapshot = r.snapshot;
        stream = r.stream;
        where = r.where;
        whereTree = r.whereTree;
        key = List.copyOf(r.key);
        version = r.version;
        changedBy = r.changedBy;
        changedAt = r.changedAt;
        columns = List.copyOf(r.columns);
        sort = List.copyOf(r.sort);
        groupBy = List.copyOf(r.groupBy);
        subtotals = List.copyOf(r.subtotals);
        narrow = List.copyOf(r.narrow);
        retention = r.retention;
        tray = r.tray;
        edit = r.edit == null ? null : Collections.unmodifiableMap(r.edit);
        reason = r.reason;
        profile = Collections.unmodifiableMap(r.profile);
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    /** {@link #read(String, String, Set, Predicate)} with no profile keys and formats not checked. */
    public static LiveTableSpec read(String yaml, String file) {
        return read(yaml, file, Set.of(), null);
    }

    /**
     * Reads and checks a declaration.
     *
     * @param yaml the file's text
     * @param file its name, for problem locations
     * @param profileKeys the {@link #PROFILE_KEYS} the host's profile reads (empty: Core alone)
     * @param formats whether a format name exists (null: not checked)
     * @throws SutraException listing every problem, in the order of their positions
     */
    public static LiveTableSpec read(String yaml, String file, Set<String> profileKeys, Predicate<String> formats) {
        List<PositionalYamlReader.Issue> issues = new ArrayList<>();
        PNode root;
        try {
            root = new PositionalYamlReader().read(yaml, issues);
        } catch (YamlSyntaxException e) {
            throw new SutraException(List.of(new SutraProblem("RCH-2001", "YAML syntax: " + e.getMessage(),
                    new SourceLocation(file, e.line(), e.column()))));
        }
        Reader r = new Reader(file, profileKeys, formats);
        issues.forEach(i -> r.problems.add(new SutraProblem("RCH-2033", i.message(), new SourceLocation(file, i.line(), i.column()))));
        r.read(root, true);
        try {
            return r.done();
        } catch (SutraException e) {                                    // SPEC 6.1: each problem with its path and range
            throw new SutraException(com.ash.rachana.parse.ProblemPlaces.of(yaml, file).place(e.problems()));
        }
    }

    /**
     * A declaration made in code (a host's default declaration, {@code LiveTables.declare}): the same keys and checks as a file, without
     * {@code rachana: 1}; problems carry no position.
     */
    public static LiveTableSpec of(Map<String, Object> doc, Set<String> profileKeys, Predicate<String> formats) {
        Reader r = new Reader("<code>", profileKeys, formats);
        r.read(node(doc), false);
        return r.done();
    }

    @SuppressWarnings("unchecked")
    private static PNode node(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, PNode> out = new LinkedHashMap<>();
            ((Map<String, Object>) m).forEach((k, x) -> out.put(k, node(x)));
            return new PNode(out, 0, 0);
        }
        if (v instanceof List<?> l) {
            List<PNode> out = new ArrayList<>();
            l.forEach(x -> out.add(node(x)));
            return new PNode(out, 0, 0);
        }
        return new PNode(v, 0, 0);
    }

    /** The checker: one per read. */
    private static final class Reader {
        final String file;
        final Set<String> profileKeys;
        final Predicate<String> formats;
        final List<SutraProblem> problems = new ArrayList<>();
        String name;
        String title;
        String snapshot;
        String stream;
        String where;
        Map<String, Object> whereTree;
        final List<String> key = new ArrayList<>();
        String version;
        String changedBy;
        String changedAt;
        final List<Column> columns = new ArrayList<>();
        final List<Map<String, Object>> sort = new ArrayList<>();
        final List<String> groupBy = new ArrayList<>();
        final List<String> subtotals = new ArrayList<>();
        final List<String> narrow = new ArrayList<>();
        Retention retention;
        boolean tray;
        Map<String, Object> edit;
        String reason = "none";
        final Map<String, Object> profile = new LinkedHashMap<>();
        final Map<String, PNode> named = new LinkedHashMap<>();       // "<where>|<column>" -> the node that names it, checked last

        Reader(String file, Set<String> profileKeys, Predicate<String> formats) {
            this.file = file;
            this.profileKeys = profileKeys == null ? Set.of() : profileKeys;
            this.formats = formats;
        }

        void problem(String code, String message, PNode at) {
            problems.add(new SutraProblem(code, message, at == null ? new SourceLocation(file, 0, 0) : new SourceLocation(file, at.line(), at.column())));
        }

        LiveTableSpec done() {
            if (!problems.isEmpty()) {
                List<SutraProblem> all = new ArrayList<>(problems);
                all.sort(Comparator.comparingInt((SutraProblem p) -> p.location().line()).thenComparingInt(p -> p.location().column()));
                throw new SutraException(all);
            }
            return new LiveTableSpec(this);
        }

        void read(PNode root, boolean fromFile) {
            if (!root.isMap()) {
                problem("RCH-2012", "a live table's declaration is a mapping (rachana: 1, live-table: <name>, ...)", root);
                return;
            }
            Map<String, PNode> m = root.map();
            if (fromFile) {
                PNode lang = m.get("rachana");
                if (lang == null) {
                    problem("RCH-2009", "missing 'rachana: 1' (the Rachana language version) at the top", root);
                } else if (!"1".equals(String.valueOf(lang.value()))) {
                    problem("RCH-2009", "'rachana: " + lang.value() + "' is not a language version this server reads (it reads 1)", lang);
                }
            }
            for (Map.Entry<String, PNode> e : m.entrySet()) {
                String k = e.getKey();
                if (CORE_KEYS.contains(k) || !fromFile && k.equals("rachana")) {
                    continue;
                }
                if (PROFILE_KEYS.contains(k)) {
                    if (profileKeys.contains(k)) {
                        profile.put(k, e.getValue().plain());
                    } else {
                        problem("RCH-2060", "'" + k + "' comes with a profile that brings it; this host has none", e.getValue());
                    }
                } else {
                    problem("RCH-2011", "unknown key '" + k + "' in a live table's declaration (it reads " + String.join(", ", CORE_KEYS) + ")",
                            e.getValue());
                }
            }
            name = text(m, KEY, root, true);
            if (name != null && (!NAME.matcher(name).matches() || name.length() > 128)) {
                problem("RCH-2020", "'" + name + "' is not a table name (lower case letters, digits, '_' and '-', '.' between parts, at most 128)",
                        m.get(KEY));
            }
            title = text(m, "title", root, false);
            snapshot = text(m, "snapshot", root, true);
            stream = text(m, "stream", root, false);
            readKey(m.get("key"), root);
            version = column(m, "version");
            changedBy = column(m, "changed-by");
            changedAt = column(m, "changed-at");
            readColumns(m.get("columns"), root);
            readSort(m.get("sort"));
            names(m.get("group-by"), "group-by", groupBy);
            names(m.get("subtotals"), "subtotals", subtotals);
            readNarrow(m.get("narrow"));
            readRetention(m.get("retention"));
            PNode t = m.get("tray");
            if (t != null && !(t.value() instanceof Boolean)) {
                problem("RCH-2012", "'tray' is true or false", t);
            }
            tray = t != null && Boolean.TRUE.equals(t.value());
            readEdit(m.get("edit"));
            readWhere(m.get("where"));
            checkNames();
        }

        String text(Map<String, PNode> m, String k, PNode at, boolean required) {
            PNode n = m.get(k);
            if (n == null || n.value() == null) {
                if (required) {
                    problem("RCH-2010", "missing '" + k + "'", at);
                }
                return null;
            }
            if (!n.isScalar() || String.valueOf(n.value()).isBlank()) {
                problem("RCH-2012", "'" + k + "' is a text", n);
                return null;
            }
            return String.valueOf(n.value());
        }

        String column(Map<String, PNode> m, String k) {
            String c = text(m, k, null, false);
            if (c != null) {
                named.put(k + "|" + c, m.get(k));
            }
            return c;
        }

        void readKey(PNode n, PNode root) {
            if (n == null || n.value() == null) {
                problem("RCH-2072", "a live table needs a key: 'key: <column>' or a list of columns (rows merge by it)", root);
                return;
            }
            names(n, "key", key);
            if (key.isEmpty()) {
                problem("RCH-2072", "a live table needs a key: 'key: <column>' or a list of columns (rows merge by it)", n);
            }
        }

        void names(PNode n, String what, List<String> out) {
            if (n == null || n.value() == null) {
                return;
            }
            List<PNode> items = n.isList() ? n.list() : List.of(n);
            for (PNode i : items) {
                if (!i.isScalar() || i.value() == null || String.valueOf(i.value()).isBlank()) {
                    problem("RCH-2012", "'" + what + "' is a column name or a list of them", i);
                } else {
                    String c = String.valueOf(i.value());
                    out.add(c);
                    named.put(what + "|" + c, i);
                }
            }
        }

        void readColumns(PNode n, PNode root) {
            if (n == null || !n.isList() || n.list().isEmpty()) {
                problem(n == null ? "RCH-2010" : "RCH-2012", n == null ? "missing 'columns'" : "'columns' is a list of { name, label?, type?, width?, fmt?, heavy? }",
                        n == null ? root : n);
                return;
            }
            Set<String> seen = new LinkedHashSet<>();
            for (PNode c : n.list()) {
                if (!c.isMap() || !(c.map().get("name") != null && c.map().get("name").isScalar() && c.map().get("name").value() != null)) {
                    problem("RCH-2012", "a column is { name, label?, type?, width?, fmt?, heavy?, items?, nullable? }", c);
                    continue;
                }
                Map<String, PNode> cm = c.map();
                for (Map.Entry<String, PNode> e : cm.entrySet()) {
                    if (!COLUMN_KEYS.contains(e.getKey())) {
                        problem("RCH-2011", "unknown column field '" + e.getKey() + "' (a column has " + String.join(", ", COLUMN_KEYS) + ")", e.getValue());
                    }
                }
                String cn = String.valueOf(cm.get("name").value());
                if (!seen.add(cn)) {
                    problem("RCH-2024", "the column '" + cn + "' is declared twice", cm.get("name"));
                    continue;
                }
                String type = cm.get("type") == null ? "string" : String.valueOf(cm.get("type").value());
                if (!TableSchema.TYPES.contains(type)) {
                    problem("RCH-2023", "'" + type + "' is not a column type (" + String.join(", ", new java.util.TreeSet<>(TableSchema.TYPES)) + ")",
                            cm.get("type"));
                }
                Integer width = null;
                if (cm.get("width") != null) {
                    if (whole(cm.get("width").value()) && ((Number) cm.get("width").value()).longValue() > 0
                            && ((Number) cm.get("width").value()).longValue() <= 2000) {
                        width = ((Number) cm.get("width").value()).intValue();
                    } else {
                        problem("RCH-2020", "a column's 'width' is a whole number of pixels from 1 to 2000", cm.get("width"));
                    }
                }
                String fmt = cm.get("fmt") == null ? null : String.valueOf(cm.get("fmt").value());
                if (fmt != null && formats != null && !formats.test(fmt)) {
                    problem("RCH-2020", "unknown format '" + fmt + "'", cm.get("fmt"));
                }
                boolean heavy = flag(cm.get("heavy"), "heavy");
                boolean nullable = cm.get("nullable") == null || flag(cm.get("nullable"), "nullable");
                String items = cm.get("items") == null ? null : String.valueOf(cm.get("items").value());
                if (items != null && (!"list".equals(type) || !TableSchema.TYPES.contains(items))) {
                    problem("RCH-2023", "'items' names the type of a list column's items", cm.get("items"));
                }
                String label = cm.get("label") == null ? null : String.valueOf(cm.get("label").value());
                columns.add(new Column(cn, label, type, width, fmt, heavy, items, nullable));
            }
        }

        boolean flag(PNode n, String what) {
            if (n == null) {
                return false;
            }
            if (!(n.value() instanceof Boolean b)) {
                problem("RCH-2012", "'" + what + "' is true or false", n);
                return false;
            }
            return b;
        }

        void readSort(PNode n) {
            if (n == null || n.value() == null) {
                return;
            }
            for (PNode s : n.isList() ? n.list() : List.of(n)) {
                if (!s.isMap() || s.map().get("col") == null || !s.map().get("col").isScalar()) {
                    problem("RCH-2012", "'sort' is { col, dir: asc|desc } or a list of them", s);
                    continue;
                }
                String col = String.valueOf(s.map().get("col").value());
                PNode dir = s.map().get("dir");
                String d = dir == null ? "asc" : String.valueOf(dir.value());
                if (!d.equals("asc") && !d.equals("desc")) {
                    problem("RCH-2020", "a sort's 'dir' is asc or desc, not '" + d + "'", dir);
                }
                for (String k : s.map().keySet()) {
                    if (!k.equals("col") && !k.equals("dir")) {
                        problem("RCH-2011", "unknown sort field '" + k + "' (a sort is { col, dir })", s.map().get(k));
                    }
                }
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("col", col);
                e.put("dir", d);
                sort.add(e);
                named.put("sort|" + col, s.map().get("col"));
            }
        }

        void readNarrow(PNode n) {
            if (n == null || n.value() == null) {
                return;
            }
            if (!n.isMap() || n.map().get("any-of") == null || n.map().size() != 1) {
                problem("RCH-2012", "'narrow' is { any-of: [<column>, ...] }: a list must filter on one of them", n);
                return;
            }
            names(n.map().get("any-of"), "narrow", narrow);
        }

        void readRetention(PNode n) {
            if (n == null || n.value() == null) {
                return;
            }
            if (!n.isMap()) {
                problem(RETENTION, "'retention' is { ttl?: <duration>, on?: <column>, max-rows?: <n> }", n);
                return;
            }
            Map<String, PNode> m = n.map();
            long ttl = 0;
            int max = 0;
            String on = null;
            for (Map.Entry<String, PNode> e : m.entrySet()) {
                switch (e.getKey()) {
                    case "ttl" -> {
                        ttl = durationMs(e.getValue().value());
                        if (ttl <= 0) {
                            problem(RETENTION, "'retention.ttl' is a duration such as 30s, 10m, 2h or 1d", e.getValue());
                        }
                    }
                    case "max-rows" -> {
                        if (whole(e.getValue().value()) && ((Number) e.getValue().value()).longValue() > 0
                                && ((Number) e.getValue().value()).longValue() <= Integer.MAX_VALUE) {
                            max = ((Number) e.getValue().value()).intValue();
                        } else {
                            problem(RETENTION, "'retention.max-rows' is a whole number above 0", e.getValue());
                        }
                    }
                    case "on" -> {
                        on = String.valueOf(e.getValue().value());
                        named.put("retention.on|" + on, e.getValue());
                    }
                    default -> problem("RCH-2011", "unknown retention field '" + e.getKey() + "' (ttl, on, max-rows)", e.getValue());
                }
            }
            if (!m.containsKey("ttl") && !m.containsKey("max-rows")) {
                problem(RETENTION, "'retention' says ttl, max-rows or both", n);
            }
            retention = new Retention(ttl, on, max);
        }

        @SuppressWarnings("unchecked")
        void readEdit(PNode n) {
            if (n == null || n.value() == null) {
                return;
            }
            Object raw = n.plain();
            if (!(raw instanceof Map<?, ?> rm)) {
                problem("RCH-2058", "'edit' is a form: { edit: cells|beside, fields: [...], reason?: none|optional|required }", n);
                return;
            }
            Map<String, Object> form = new LinkedHashMap<>((Map<String, Object>) rm);
            Object why = form.remove("reason");
            if (why != null) {
                if (!REASONS.contains(String.valueOf(why))) {
                    problem("RCH-2058", "'edit.reason' is none, optional or required, not '" + why + "'", n.map().get("reason"));
                } else {
                    reason = String.valueOf(why);
                }
            }
            for (String p : List.of("roles", "save")) {
                if (form.containsKey(p) && !profileKeys.contains("edit." + p)) {
                    problem("RCH-2058", "'edit." + p + "' comes with a profile that brings it; this host has none", n.map().get(p));
                    form.remove(p);
                }
            }
            if (form.get("fields") instanceof List<?> fl && !profileKeys.contains("edit.fields.editable-by")) {
                for (Object f : fl) {
                    if (f instanceof Map<?, ?> fm && fm.containsKey("editable-by")) {
                        problem("RCH-2058", "'editable-by' comes with a profile that brings it; this host has none", n);
                        break;
                    }
                }
            }
            if (!form.containsKey("edit") && !form.containsKey("new")) {
                form.put("edit", "cells");
            }
            Map<String, Object> checked = new LinkedHashMap<>(form);       // the record is identified by the table's key, not a field
            if (!checked.containsKey("key") && checked.get("fields") instanceof List<?> fl0) {
                List<Object> withKey = new ArrayList<>(fl0);
                withKey.add(Map.of("id", "row_key", "type", "text", "readonly", true));
                checked.put("fields", withKey);
                checked.put("key", "row_key");
            }
            List<String> why2 = Form.problems(checked);
            why2.forEach(w -> problem("RCH-2058", w, n));
            edit = form;
            if (version == null || key.isEmpty()) {
                problem(EDIT_NEEDS, "a table with 'edit' needs a key and a 'version' column (a change names the version it was made on)", n);
            }
            if (form.get("fields") instanceof List<?> fl) {
                PNode fieldsNode = n.isMap() ? n.map().get("fields") : n;
                int i = 0;
                for (Object f : fl) {
                    PNode at = fieldsNode != null && fieldsNode.isList() && i < fieldsNode.list().size() ? fieldsNode.list().get(i) : n;
                    i++;
                    if (!(f instanceof Map<?, ?> fm) || !(fm.get("bind") instanceof String bind)) {
                        problem(NOT_EDITABLE, "an edit field binds a column: 'bind: <column>'", at);
                        continue;
                    }
                    if (key.contains(bind) || bind.equals(version) || bind.equals(changedBy) || bind.equals(changedAt)) {
                        problem(NOT_EDITABLE, "the column '" + bind + "' cannot be edited: it is the key, the version, changed-by or changed-at", at);
                    }
                    named.put("edit|" + bind, at.isMap() && at.map().get("bind") != null ? at.map().get("bind") : at);
                }
            }
        }

        void readWhere(PNode n) {
            if (n == null || n.value() == null) {
                return;
            }
            if (!n.isScalar()) {
                problem("RCH-2012", "'where' is a Rachana-EL condition over each row ('@.STATUS != \"CANCELLED\"')", n);
                return;
            }
            where = String.valueOf(n.value());
            try {
                Expr e = new ElCompiler(16).compile(where);
                whereTree = FilterTree.of(e, EvalContext.of(com.ash.rachana.api.Node.of(Map.of()), null));
                List<String> cols = FilterTree.columns(e);
                if (cols != null) {
                    cols.forEach(c -> named.putIfAbsent("where|" + c, n));
                }
            } catch (ElException e) {
                problem("RCH-2101", "expression '" + where + "': " + e.getMessage(), n);
            }
        }

        void checkNames() {
            Set<String> have = new LinkedHashSet<>();
            columns.forEach(c -> have.add(c.name()));
            if (columns.isEmpty()) {
                return;                                              // the missing columns are the problem already said
            }
            for (Map.Entry<String, PNode> e : named.entrySet()) {
                String where = e.getKey().substring(0, e.getKey().indexOf('|'));
                String col = e.getKey().substring(e.getKey().indexOf('|') + 1);
                Column c = columns.stream().filter(x -> x.name().equals(col)).findFirst().orElse(null);
                if (c == null) {
                    problem("RCH-2071", "'" + where + "' names the column '" + col + "', which is not in 'columns'"
                            + nearest(col, have), e.getValue());
                } else if (c.heavy() && !where.equals("edit")) {
                    problem(HEAVY, "the column '" + col + "' is heavy (never in a list): it cannot be the table's " + where, e.getValue());
                }
            }
        }
    }

    private static String nearest(String col, Set<String> have) {
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (String h : have) {
            int d = distance(col.toLowerCase(java.util.Locale.ROOT), h.toLowerCase(java.util.Locale.ROOT));
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best != null && bestD <= Math.max(2, col.length() / 3) ? " (did you mean '" + best + "'?)" : "";
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    private static boolean whole(Object v) {
        return v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof java.math.BigInteger;
    }

    /** A duration's milliseconds ({@code 500ms}, {@code 30s}, {@code 10m}, {@code 2h}, {@code 1d}; a bare number is seconds); 0 when not one. */
    public static long durationMs(Object v) {
        if (v instanceof Number n) {
            return whole(n) && n.longValue() > 0 ? n.longValue() * 1000L : 0;
        }
        Matcher m = DURATION.matcher(String.valueOf(v));
        if (!m.matches()) {
            return 0;
        }
        long x = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> x;
            case "s" -> x * 1000L;
            case "m" -> x * 60_000L;
            case "h" -> x * 3_600_000L;
            default -> x * 86_400_000L;
        };
    }

    // ---- the model -------------------------------------------------------------------------------------------------

    public String file() {
        return file;
    }

    /** The table's name in the Data Protocol. */
    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    /** The snapshot source the host resolves by name. */
    public String snapshot() {
        return snapshot;
    }

    /** The live stream the host resolves by name; null: a static table. */
    public String stream() {
        return stream;
    }

    /** The row condition as written (Rachana-EL), or null. */
    public String where() {
        return where;
    }

    /** The row condition as a filter tree (PROTOCOL 7), or null when there is none or it cannot be pushed down to a source. */
    public Map<String, Object> whereTree() {
        return whereTree;
    }

    public List<String> key() {
        return key;
    }

    /** The record version column, or null (the latest arrival wins). */
    public String version() {
        return version;
    }

    public String changedBy() {
        return changedBy;
    }

    public String changedAt() {
        return changedAt;
    }

    public List<Column> columns() {
        return columns;
    }

    /** The column of that name, or null. */
    public Column column(String name) {
        for (Column c : columns) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** The names of the columns a list page carries (every column but the heavy ones). */
    public List<String> listColumns() {
        List<String> out = new ArrayList<>();
        columns.forEach(c -> {
            if (!c.heavy()) {
                out.add(c.name());
            }
        });
        return out;
    }

    /** The table's own order: {@code {col, dir}} entries (empty: by key). */
    public List<Map<String, Object>> sort() {
        return sort;
    }

    public List<String> groupBy() {
        return groupBy;
    }

    public List<String> subtotals() {
        return subtotals;
    }

    /** The columns a list must filter on one of (empty: a bare list is allowed). */
    public List<String> narrow() {
        return narrow;
    }

    /** The live side's retention, or null. */
    public Retention retention() {
        return retention;
    }

    /** Whether the reader's own pending changes are served as {@code <name>.changes}. */
    public boolean tray() {
        return tray;
    }

    /** The edit form (a Core form, {@code reason} taken out), or null: a read-only table. */
    public Map<String, Object> edit() {
        return edit;
    }

    /** How a change's reason is asked for: none, optional or required. */
    public String reason() {
        return reason;
    }

    /** The profile keys' values as written ({@code kind}, {@code query}, {@code links}), by key. */
    public Map<String, Object> profile() {
        return profile;
    }

    /** The columns an edit may set (the edit fields' binds), in order; empty for a read-only table. */
    public List<String> editableColumns() {
        List<String> out = new ArrayList<>();
        if (edit != null && edit.get("fields") instanceof List<?> fl) {
            for (Object f : fl) {
                if (f instanceof Map<?, ?> fm && fm.get("bind") instanceof String b) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    /**
     * True when the table's own order is newest first: its first sort is descending by {@code changed-at} or the version, so a new or
     * changed row takes the first place (a window with {@code top} is sent it to insert on top).
     */
    public boolean newestFirst() {
        if (sort.isEmpty()) {
            return false;
        }
        Object col = sort.get(0).get("col");
        return "desc".equals(sort.get(0).get("dir")) && (col.equals(changedAt) || col.equals(version));
    }

    /**
     * The table's Data Protocol schema document (PROTOCOL 3, with LIVE_TABLES section 4's fields): {@code name}, {@code title},
     * {@code key}, {@code columns} (with {@code label}, {@code heavy} and {@code items} where given), {@code can}, {@code rowVersion},
     * {@code changedBy}, {@code changedAt}, {@code narrow: {anyOf}}, {@code view: {sort, groupBy, subtotals, widths}}. The engine adds
     * {@code live}, {@code capped}, the reader's masks and, for a reader who may edit, {@code write}.
     */
    public Map<String, Object> schema() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        if (title != null) {
            m.put("title", title);
        }
        m.put("key", key);
        List<Object> cols = new ArrayList<>();
        for (Column c : columns) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("name", c.name());
            cm.put("type", c.type());
            if (c.label() != null) {
                cm.put("label", c.label());
            }
            if (!c.nullable()) {
                cm.put("nullable", false);
            }
            if (c.items() != null) {
                cm.put("items", c.items());
            }
            if (c.fmt() != null) {
                cm.put("fmt", c.fmt());
            }
            if (c.heavy()) {
                cm.put("heavy", true);
            }
            cols.add(cm);
        }
        m.put("columns", cols);
        List<String> can = new ArrayList<>(List.of("page", "sort", "filter", "count", "group", "offset", "changes", "changes.filter",
                "changes.window", "replay", "arrow"));
        if (!narrow.isEmpty()) {
            can.add("narrow");
        }
        m.put("can", can);
        if (version != null) {
            m.put("rowVersion", version);
        }
        if (changedBy != null) {
            m.put("changedBy", changedBy);
        }
        if (changedAt != null) {
            m.put("changedAt", changedAt);
        }
        if (!narrow.isEmpty()) {
            m.put("narrow", Map.of("anyOf", narrow));
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sort", sort);
        if (!groupBy.isEmpty()) {
            view.put("groupBy", groupBy);
        }
        if (!subtotals.isEmpty()) {
            view.put("subtotals", subtotals);
        }
        Map<String, Object> widths = new LinkedHashMap<>();
        columns.forEach(c -> {
            if (c.width() != null) {
                widths.put(c.name(), (long) c.width());
            }
        });
        if (!widths.isEmpty()) {
            view.put("widths", widths);
        }
        m.put("view", view);
        return m;
    }
}
