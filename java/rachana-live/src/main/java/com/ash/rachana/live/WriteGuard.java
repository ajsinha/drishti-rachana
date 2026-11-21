/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.live;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.live.spi.Authoriser;
import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeSink;
import com.ash.rachana.live.spi.LiveStream;
import com.ash.rachana.live.spi.SnapshotSource;
import com.ash.rachana.model.Form;
import com.ash.rachana.table.KeyedRows;
import com.ash.rachana.table.LiveTableSpec;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The guarded edit path of the live tables (LIVE_TABLES 1.6, 5; PROTOCOL 16 Writes). A write is passed to the host's {@link ChangeSink}
 * only when all four hold: the declaration edits every column it sets, the table's switch is on, a sink is bound, and the authoriser
 * lets this reader edit this row and these columns. In order: the message's shape ({@code 422}), a table without writes ({@code 405}),
 * the switch, the reader (a read refusal as for reads), the columns ({@code 403}), the row (outside the reader's scope or absent:
 * {@code 404}), the host's {@code mayEdit} ({@code 403}), the declaration's form rules run by Core's form checker ({@code 422} naming each
 * column and rule), the version guard ({@code 409} naming the current version, who and when). Every refusal is audited; a repeated change
 * id answers its first answer. The tray of a table ({@code <name>.changes}) is a live table over the sink's pending changes, filtered
 * to the caller's own.
 */
final class WriteGuard {

    static final String TRAY = ".changes";
    /** The tray's one column an approver sets: {@code released} or {@code rejected}. */
    static final String STATE = "state";
    private static final List<String> DECISIONS = List.of("released", "rejected");
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int ANSWERS = 10_000;

    private final LiveTables tables;
    private volatile ChangeSink sink;
    private final Set<String> off = ConcurrentHashMap.newKeySet();
    private final Map<String, Form> forms = new ConcurrentHashMap<>();
    private final Map<String, TrayStream> trays = new ConcurrentHashMap<>();
    /** Answers given, by change id (for a sink that keeps none): {@code [by, answer]}. */
    private final Map<String, Object[]> answers = java.util.Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) {
            return size() > ANSWERS;
        }
    });

    WriteGuard(LiveTables tables) {
        this.tables = tables;
    }

    void sink(ChangeSink s) {
        sink = s;
        if (s != null) {
            s.watch(this::changed);
        }
    }

    ChangeSink sink() {
        return sink;
    }

    void edits(String table, boolean on) {
        if (on) {
            off.remove(table);
        } else {
            off.add(table);
        }
    }

    boolean on(String table) {
        return !off.contains(table);
    }

    /** Whether a table takes writes at all ({@code can: write}): it has {@code edit:} and a sink is bound. */
    boolean writable(LiveTableSpec spec) {
        return spec.edit() != null && sink != null;
    }

    /** Whether the trays take decisions: a sink is bound and it may hold changes. */
    boolean decides() {
        ChangeSink s = sink;
        return s != null && s.holds();
    }

    /** The schema's {@code write} for a reader: the columns they may set with their field rules, and the reason, or null. */
    @SuppressWarnings("unchecked")
    Map<String, Object> schemaWrite(LiveTableSpec spec, LiveTables.Reader r) {
        if (tables.isTray(spec.name())) {                                // an approver decides the column state
            if (!decides() || !r.editable().contains(STATE)) {
                return null;
            }
            Map<String, Object> rules = new LinkedHashMap<>();
            rules.put("type", "enum");
            rules.put("options", DECISIONS);
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("columns", Map.of(STATE, rules));
            w.put("reason", "optional");
            return w;
        }
        if (!writable(spec) || !on(spec.name())) {
            return null;
        }
        Map<String, Object> cols = new LinkedHashMap<>();
        for (Object f : (List<Object>) spec.edit().get("fields")) {
            Map<String, Object> fm = (Map<String, Object>) f;
            String bind = String.valueOf(fm.get("bind"));
            if (r.editable().contains(bind) && !r.schema().column(bind).masked()) {
                Map<String, Object> rules = new LinkedHashMap<>(fm);
                rules.remove("bind");
                rules.remove("editable-by");
                cols.put(bind, rules);
            }
        }
        if (cols.isEmpty()) {
            return null;
        }
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("columns", cols);
        w.put("reason", spec.reason() == null ? "none" : spec.reason());
        return w;
    }

    // ---- the write --------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    Map<String, Object> write(String name, Object body, Object caller) {
        if (name.endsWith(TRAY) && tables.isTray(name)) {
            if (!decides()) {
                throw ProtocolError.of("rdp.write.unsupported", 405, name + " is read only: it lists pending changes", "table", name);
            }
            return decide(name, body, caller);
        }
        LiveTableSpec spec = tables.spec(name);
        Map<String, Object> m = shape(name, body);
        String id = (String) m.get("id");
        String op = (String) m.get("op");
        if (!writable(spec)) {
            throw ProtocolError.of("rdp.write.unsupported", 405, name + " takes no writes", "table", name);
        }
        LiveTables.Reader r = tables.reader(caller, name);              // a read refusal answers as for a read
        Authoriser a = tables.authoriserOrNull();
        String who = a == null ? null : a.who(caller);
        List<Object> key = (List<Object>) m.get("key");
        Map<String, Object> set = (Map<String, Object>) m.getOrDefault("set", Map.of());
        if (!on(name)) {
            throw refused(caller, name, id, key, set, "edits of " + name + " are switched off on this host");
        }
        if (who == null) {
            throw refused(caller, name, id, key, set, "the caller has no name to write as");
        }
        ChangeSink s = sink;
        String madeBy = s.madeBy(id);
        Object[] seen = answers.get(id);
        if (madeBy == null && seen != null) {
            madeBy = (String) seen[0];
        }
        if (madeBy != null) {                                           // a repeat: the first answer again (only to its maker)
            if (!madeBy.equals(who)) {
                throw refused(caller, name, id, key, set, "the change id '" + id + "' is another's");
            }
            ChangeSink.Outcome o = s.prior(id);
            return o != null ? answer(id, o) : (Map<String, Object>) seen[1];
        }
        List<String> declared = spec.editableColumns();
        if (op.equals("add") && !spec.edit().containsKey("new")) {
            throw refused(caller, name, id, key, set, name + "'s declaration adds no rows");
        }
        if (op.equals("remove") && r.editable().stream().noneMatch(declared::contains)) {
            throw refused(caller, name, id, key, set, "you may edit no column of " + name);
        }
        for (String c : set.keySet()) {
            if (!declared.contains(c)) {
                throw refused(caller, name, id, key, set, "the column '" + c + "' is not editable in " + name);
            }
            if (!r.editable().contains(c) || r.schema().column(c) != null && r.schema().column(c).masked()) {
                throw refused(caller, name, id, key, set, "you may not edit '" + c + "'" + (r.readOnlyReason() == null ? "" : ": " + r.readOnlyReason()));
            }
        }
        LiveTable t = tables.opened(name);
        Map<String, Object> row;
        try {
            row = t.row(key);
        } catch (ProtocolError e) {
            throw e;
        } catch (Exception e) {
            throw ProtocolError.of("rdp.unavailable", 503, name + "'s row could not be read", "table", name, "retry", true);
        }
        if (row != null && !r.scope().test(row)) {
            tables.event(caller, name, "write-refused", "the row is outside the reader's scope", Map.of("change", id));
            throw ProtocolError.of("rdp.table.unknown", 404, "no row " + key + " in " + name, "table", name);
        }
        if (op.equals("add") ? row != null : row == null) {
            if (row == null) {
                tables.event(caller, name, "write-refused", "no such row", Map.of("change", id));
                throw ProtocolError.of("rdp.table.unknown", 404, "no row " + key + " in " + name, "table", name);
            }
            throw conflict(caller, spec, r, id, row, "a row with the key " + key + " exists");
        }
        Change change = new Change(id, name, op, key, m.get("basedOn"), set, (Map<String, Object>) m.get("from"),
                (String) m.get("reason"), who, tables.clock().instant());
        if (a.mayEdit(caller, name, change.toMap(), row == null ? Map.of() : r.shown().apply(row)) instanceof Authoriser.Decision.Refuse no) {
            throw refused(caller, name, id, key, set, no.reason());
        }
        check(spec, op, set, row, change.reason());
        if (!op.equals("add") && KeyedRows.versionOrder(row.get(spec.version()), m.get("basedOn")) != 0) {
            throw conflict(caller, spec, r, id, row, "the row changed since version " + m.get("basedOn"));
        }
        ChangeSink.Outcome o;
        try {
            o = s.accept(change);
        } catch (Exception e) {
            throw ProtocolError.of("rdp.unavailable", 503, "the change could not be taken: " + e.getClass().getSimpleName(), "table", name,
                    "retry", true);
        }
        Map<String, Object> ans = answer(id, o);
        answers.put(id, new Object[] {who, ans});
        Map<String, Object> ev = audit(id, set, row, r);
        tables.event(caller, name, "write-submitted", null, ev);
        String follow = switch (o.state()) {
            case APPLIED -> "write-applied";
            case HELD -> "write-held";
            case REFUSED -> "write-refused";
            default -> null;
        };
        if (follow != null) {
            tables.event(caller, name, follow, o.message(), ev);
        }
        return ans;
    }

    /** The write message's shape (PROTOCOL 16, bridge.json change): id, op, key in key order, basedOn, set. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> shape(String name, Object body) {
        LiveTableSpec spec = tables.spec(name);
        List<Map<String, Object>> bad = new ArrayList<>();
        if (!(body instanceof Map<?, ?> b)) {
            throw invalid(name, List.of(issue(null, "shape", "a write is an object: { id, op, key, basedOn, set }")));
        }
        Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) b);
        if (!(m.get("id") instanceof String id) || !ID.matcher(id).matches()) {
            bad.add(issue(null, "id", "id is the change's own id: 1 to 64 letters, digits, '-' or '_' (a ULID)"));
        }
        Object op = m.getOrDefault("op", "update");
        if (!List.of("update", "add", "remove").contains(op)) {
            bad.add(issue(null, "op", "op is update, add or remove"));
        }
        m.put("op", op);
        Object k = m.get("key");
        List<Object> key = k instanceof List<?> l ? new ArrayList<>(l) : k == null ? null : new ArrayList<>(java.util.Collections.singletonList(k));
        if (key == null || key.size() != spec.key().size() || key.contains(null)) {
            bad.add(issue(null, "key", "key is the row's key values in key order " + spec.key()));
        }
        m.put("key", key);
        if (m.get("set") != null && !(m.get("set") instanceof Map)) {
            bad.add(issue(null, "set", "set is an object: { <column>: <value> }"));
        } else if (!"remove".equals(op) && (m.get("set") == null || ((Map<?, ?>) m.get("set")).isEmpty())) {
            bad.add(issue(null, "set", "set names the columns the change sets"));
        } else if ("remove".equals(op) && m.get("set") instanceof Map<?, ?> sm && !sm.isEmpty()) {
            bad.add(issue(null, "set", "a remove sets no column"));
        }
        if (!"add".equals(op) && m.get("basedOn") == null) {
            bad.add(issue(null, "basedOn", "basedOn is the row's version the change was made on"));
        }
        if (m.get("from") != null && !(m.get("from") instanceof Map)) {
            bad.add(issue(null, "from", "from is an object: { <column>: <value seen> }"));
        }
        if (m.get("reason") != null && !(m.get("reason") instanceof String)) {
            bad.add(issue(null, "reason", "reason is text"));
        }
        if (!bad.isEmpty()) {
            throw invalid(name, bad);
        }
        return m;
    }

    /** The declaration's form rules over the change, by Core's form checker (the code the page ran). */
    @SuppressWarnings("unchecked")
    private void check(LiveTableSpec spec, String op, Map<String, Object> set, Map<String, Object> row, String reason) {
        List<Map<String, Object>> bad = new ArrayList<>();
        if ("required".equals(spec.reason()) && (reason == null || reason.isBlank())) {
            bad.add(issue(null, "reason", "a reason is required"));
        }
        if (!op.equals("remove")) {
            Form form = forms.computeIfAbsent(spec.name(), n -> form(spec.edit()));
            Map<String, String> idOf = new LinkedHashMap<>();
            Map<String, String> bindOf = new LinkedHashMap<>();
            for (Object f : (List<Object>) spec.edit().get("fields")) {
                Map<String, Object> fm = (Map<String, Object>) f;
                idOf.put(String.valueOf(fm.get("bind")), String.valueOf(fm.get("id")));
                bindOf.put(String.valueOf(fm.get("id")), String.valueOf(fm.get("bind")));
            }
            Map<String, Object> values = new LinkedHashMap<>();
            Set<String> checked = new LinkedHashSet<>();
            idOf.forEach((bind, id) -> {
                if (set.containsKey(bind)) {
                    values.put(id, set.get(bind));
                    checked.add(id);
                } else if (row != null) {
                    values.put(id, row.get(bind));
                }
            });
            if (op.equals("add")) {
                checked.addAll(idOf.values());
            }
            for (Form.Issue i : form.check(values, checked, LocalDate.now(tables.clock()))) {
                if (i.error()) {
                    String col = i.field() == null ? null : bindOf.get(i.field());
                    Object v = i.field() == null ? null : values.get(i.field());
                    bad.add(issue(col, i.field() == null || !checked.contains(i.field()) ? "rule" : v == null || v.toString().isBlank() ? "required"
                            : "value", i.message()));
                }
            }
        }
        if (!bad.isEmpty()) {
            throw invalid(spec.name(), bad);
        }
    }

    @SuppressWarnings("unchecked")
    private static Form form(Map<String, Object> edit) {
        Map<String, Object> checked = new LinkedHashMap<>(edit);              // as LiveTableSpec checked it: the row's key is not a field
        if (!checked.containsKey("key") && checked.get("fields") instanceof List<?> fl) {
            List<Object> withKey = new ArrayList<>(fl);
            withKey.add(Map.of("id", "row_key", "type", "text", "readonly", true));
            checked.put("fields", withKey);
            checked.put("key", "row_key");
        }
        return Form.fromOption(checked, false).orElseThrow(() -> new IllegalStateException("the declaration's edit form was checked at load"));
    }

    private static Map<String, Object> issue(String column, String rule, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("column", column);
        m.put("rule", rule);
        m.put("message", message);
        return m;
    }

    private static ProtocolError invalid(String name, List<Map<String, Object>> fields) {
        return ProtocolError.of("rdp.write.invalid", 422, "the change breaks " + fields.size() + (fields.size() == 1 ? " rule" : " rules") + ": "
                + fields.get(0).get("message"), "table", name, "fields", fields);
    }

    private ProtocolError refused(Object caller, String name, String id, List<Object> key, Map<String, Object> set, String why) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("change", id);
        ev.put("key", key);
        ev.put("columns", new ArrayList<>(set.keySet()));
        tables.event(caller, name, "write-refused", why, ev);
        return ProtocolError.of("rdp.write.forbidden", 403, why, "table", name);
    }

    private ProtocolError conflict(Object caller, LiveTableSpec spec, LiveTables.Reader r, String id, Map<String, Object> row, String why) {
        Map<String, Object> cur = new LinkedHashMap<>();
        cur.put("version", row.get(spec.version()));
        for (String[] c : new String[][] {{"by", spec.changedBy()}, {"at", spec.changedAt()}}) {
            if (c[1] != null && r.schema().column(c[1]) != null && !r.schema().column(c[1]).masked()) {
                cur.put(c[0], row.get(c[1]));
            }
        }
        tables.event(caller, spec.name(), "write-conflicted", why, Map.of("change", id, "current", cur));
        return ProtocolError.of("rdp.write.conflict", 409, why + ": it is at version " + cur.get("version")
                + (cur.get("by") == null ? "" : ", changed by " + cur.get("by")) + (cur.get("at") == null ? "" : " at " + cur.get("at")),
                "table", spec.name(), "current", cur);
    }

    /** The audit fields of a change: the columns set, their values before and after (never a value masked for the caller). */
    private static Map<String, Object> audit(String id, Map<String, Object> set, Map<String, Object> row, LiveTables.Reader r) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("change", id);
        ev.put("columns", new ArrayList<>(set.keySet()));
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        set.forEach((c, v) -> {
            if (r.schema().column(c) == null || !r.schema().column(c).masked()) {
                from.put(c, row == null ? null : row.get(c));
                to.put(c, v);
            }
        });
        ev.put("from", from);
        ev.put("to", to);
        return ev;
    }

    private static Map<String, Object> answer(String id, ChangeSink.Outcome o) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("change", id);
        a.put("state", o.state().wire());
        a.put("message", o.message() == null ? o.state().wire() : o.message());
        if (o.version() != null) {
            a.put("version", o.version());
        }
        return a;
    }

    // ---- the approvers' decisions ----------------------------------------------------------------------------------------

    /**
     * An approver's decision written to a tray (PROTOCOL 16 Writes, four eyes): {@code {id, op: update, key: [<change>], basedOn: held,
     * set: {state: released | rejected}, reason?}}. In order: the shape ({@code 422}), the reader (refusals as for reads), the approve
     * right ({@code 403}, audited), the change (not listed for this reader: {@code 404}), its state (no longer held: {@code 409} naming its
     * state, maker and time), the host's {@code mayDecide} ({@code 403}, audited), a rejection's reason ({@code 422}). Then the sink's
     * {@code release} or {@code reject} (audited {@code write-released} or {@code write-rejected} by the sink); answered
     * {@code {change: <decision id>, state: applied, message}}. A repeated decision id answers its first answer, only to its maker.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decide(String tray, Object body, Object caller) {
        String base = tables.trayBase(tray);
        List<Map<String, Object>> bad = new ArrayList<>();
        if (!(body instanceof Map<?, ?> b)) {
            throw invalid(tray, List.of(issue(null, "shape", "a decision is an object: { id, key: [<change>], basedOn: held, set: { state } }")));
        }
        Map<String, Object> m = (Map<String, Object>) b;
        if (!(m.get("id") instanceof String id0) || !ID.matcher(id0).matches()) {
            bad.add(issue(null, "id", "id is the decision's own id: 1 to 64 letters, digits, '-' or '_' (a ULID)"));
        }
        if (!"update".equals(m.getOrDefault("op", "update"))) {
            bad.add(issue(null, "op", "a decision is an update of the change's state"));
        }
        Object k = m.get("key");
        List<?> key = k instanceof List<?> l ? l : k == null ? null : List.of(k);
        if (key == null || key.size() != 1 || !(key.get(0) instanceof String)) {
            bad.add(issue(null, "key", "key is the change's id: [<change>]"));
        }
        if (!"held".equals(m.get("basedOn"))) {
            bad.add(issue(null, "basedOn", "basedOn is the change's state when it was decided: held"));
        }
        Object to = m.get("set") instanceof Map<?, ?> sm && sm.size() == 1 ? sm.get(STATE) : null;
        if (!DECISIONS.contains(to)) {
            bad.add(issue(STATE, "value", "set is { state: released } or { state: rejected }"));
        }
        if (m.get("reason") != null && !(m.get("reason") instanceof String)) {
            bad.add(issue(null, "reason", "reason is text"));
        }
        if (!bad.isEmpty()) {
            throw invalid(tray, bad);
        }
        String id = (String) m.get("id");
        String changeId = (String) key.get(0);
        String reason = (String) m.get("reason");
        LiveTables.Reader r = tables.reader(caller, tray);            // a read refusal answers as for a read
        Authoriser a = tables.authoriserOrNull();
        String who = a.who(caller);
        Object[] seen = answers.get(id);
        if (seen != null) {                                             // a repeat: the first answer again (only to its maker)
            if (!who.equals(seen[0])) {
                throw refused(caller, tray, id, List.of(changeId), Map.of(STATE, to), "the decision id '" + id + "' is another's");
            }
            return (Map<String, Object>) seen[1];
        }
        if (!a.approves(caller, base)) {
            throw refused(caller, tray, id, List.of(changeId), Map.of(STATE, to), "you may not decide the changes of " + base);
        }
        ChangeSink s = sink;
        ChangeSink.Entry e = s.pending(base).stream().filter(x -> x.change().id().equals(changeId)).findFirst().orElse(null);
        Map<String, Object> asHeld = e == null ? null : trayRow(e);
        if (asHeld != null) {
            asHeld.put(STATE, "held");                                  // listed for this approver while it was held
        }
        if (e == null || !r.scope().test(asHeld)) {
            tables.event(caller, tray, "write-refused", "no such change for this reader", Map.of("change", id));
            throw ProtocolError.of("rdp.table.unknown", 404, "no change " + changeId + " in " + tray, "table", tray);
        }
        if (e.state() != ChangeSink.State.HELD) {
            Map<String, Object> cur = new LinkedHashMap<>();
            cur.put("version", e.state().wire());
            cur.put("by", e.change().by());
            cur.put("at", e.at() == null ? null : e.at().toString());
            tables.event(caller, tray, "write-conflicted", "the change is " + e.state().wire(), Map.of("change", id, "current", cur));
            throw ProtocolError.of("rdp.write.conflict", 409, "the change " + changeId + " is " + e.state().wire() + ", not held", "table", tray,
                    "current", cur);
        }
        if (a.mayDecide(caller, base, e.change().toMap()) instanceof Authoriser.Decision.Refuse no) {
            throw refused(caller, tray, id, List.of(changeId), Map.of(STATE, to), no.reason());
        }
        if ("rejected".equals(to) && (reason == null || reason.isBlank())) {
            throw invalid(tray, List.of(issue(null, "reason", "a rejection gives its reason")));
        }
        ChangeSink.Outcome o = "released".equals(to) ? s.release(changeId, who) : s.reject(changeId, who, reason);
        Map<String, Object> ans = new LinkedHashMap<>();
        ans.put("change", id);
        ans.put("state", ChangeSink.State.APPLIED.wire());
        ans.put("message", changeId + " " + to + (o == null || o.message() == null ? "" : ": " + o.message()));
        answers.put(id, new Object[] {who, ans});
        return ans;
    }

    // ---- host decisions ------------------------------------------------------------------------------------------------

    ChangeSink.Outcome release(String table, String id, String by) {
        return bound().release(id, by);
    }

    ChangeSink.Outcome reject(String table, String id, String by, String reason) {
        return bound().reject(id, by, reason);
    }

    private ChangeSink bound() {
        ChangeSink s = sink;
        if (s == null) {
            throw new IllegalStateException("no change sink is bound");
        }
        return s;
    }

    /** A sink's entry moved: the audit event, and the tray's patch. */
    private void changed(ChangeSink.Entry e, String event, String decidedBy) {
        if (event != null) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("change", e.change().id());
            ev.put("key", e.change().key());
            ev.put("columns", new ArrayList<>(e.change().set().keySet()));
            if (decidedBy != null) {
                ev.put("decidedBy", decidedBy);
            }
            tables.event(e.change().by(), e.change().table(), event, e.message(), ev);
        }
        TrayStream ts = trays.get(e.change().table() + TRAY);
        if (ts != null) {
            ts.send(e);
        }
    }

    // ---- the tray ----------------------------------------------------------------------------------------------------

    /** The tray's declaration for a table with edits: {@code <name>.changes}, key {@code change}, newest first. */
    static LiveTableSpec traySpec(LiveTableSpec base) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("rachana", 1L);
        doc.put("live-table", base.name() + TRAY);
        doc.put("title", (base.title() == null ? base.name() : base.title()) + ": pending changes");
        doc.put("snapshot", base.name() + TRAY);
        doc.put("stream", base.name() + TRAY);
        doc.put("key", "change");
        doc.put("columns", List.of(col("change", "string"), col("state", "string"), col("key", "list"), col("set", "object"),
                col("by", "string"), col("at", "timestamp"), col("message", "string"), col("op", "string"), col("summary", "string")));
        doc.put("sort", Map.of("col", "at", "dir", "desc"));
        return LiveTableSpec.of(doc, Set.of(), null);
    }

    private static Map<String, Object> col(String name, String type) {
        return Map.of("name", name, "type", type);
    }

    static Map<String, Object> trayRow(ChangeSink.Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("change", e.change().id());
        m.put("state", e.state().wire());
        m.put("key", e.change().key());
        m.put("set", e.change().set());
        m.put("by", e.change().by());
        m.put("at", e.at() == null ? null : e.at().toString());
        m.put("message", e.message());
        m.put("op", e.change().op());
        m.put("summary", summary(e.change().op(), e.change().key(), e.change().set()));
        return m;
    }

    /** A change in words for the tray: the row's key, then what it does ({@code a: qty = 9, status = DONE}); a masked value is not shown. */
    static String summary(String op, List<Object> key, Map<String, Object> set) {
        StringBuilder b = new StringBuilder();
        key.forEach(k -> b.append(b.isEmpty() ? "" : ", ").append(k));
        b.append(": ");
        if ("remove".equals(op)) {
            return b.append("remove").toString();
        }
        if ("add".equals(op)) {
            b.append("add ");
        }
        int n = 0;
        for (Map.Entry<String, Object> e : set.entrySet()) {
            Object v = e.getValue();
            b.append(n++ == 0 ? "" : ", ").append(e.getKey()).append(" = ")
                    .append(v instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("$masked")) ? "(masked)" : String.valueOf(v));
        }
        return b.toString();
    }

    /** The tray's source: the sink's pending changes of the base table, read whole. */
    SnapshotSource traySource(String base) {
        return new SnapshotSource() {
            @Override
            public Tier tier() {
                return Tier.WHOLE;
            }

            @Override
            public void read(Object filter, Consumer<Map<String, Object>> rows) {
                ChangeSink s = sink;
                if (s != null) {
                    s.pending(base).forEach(e -> rows.accept(trayRow(e)));
                }
            }
        };
    }

    LiveStream trayStream(String tray) {
        return trays.computeIfAbsent(tray, k -> new TrayStream());
    }

    /** The tray's stream: an entry still to do is an upsert, a finished one a delete. */
    static final class TrayStream implements LiveStream {
        private volatile Listener listener;

        @Override
        public AutoCloseable subscribe(Listener l) {
            listener = l;
            return () -> listener = null;
        }

        void send(ChangeSink.Entry e) {
            Listener l = listener;
            if (l == null) {
                return;
            }
            if (e.state().terminal()) {
                l.delete(List.of(e.change().id()), null);
            } else {
                l.upsert(trayRow(e), null);
            }
        }
    }
}
