/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.live.spi.Change;
import com.ash.rachana.live.spi.ChangeWriter;
import com.ash.rachana.table.LiveTableSpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The server's {@link ChangeWriter} over JDBC (LIVE_TABLES 10, L10): a change to a live table whose snapshot is a JDBC table with
 * {@code write:} is applied in one transaction with its id recorded in the journal table ({@code write.journal-table}, a table the
 * operator creates with a {@code change_id} primary key): an id recorded before is already applied (exactly once after an outbox
 * resend). An update or a remove runs the version guard again ({@code ... where <key> = ? and <version> = ?}); a row that changed at the
 * source refuses the change ({@link ChangeWriter.Refused}). Statements are bound, names are checked identifiers; nothing is concatenated
 * from a request.
 */
final class JdbcWriter implements ChangeWriter {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63})?$");

    /** Where one live table's changes go. */
    record Target(LiveTableSpec spec, ServerConfig.Table table, ServerConfig.TableWrite write) {}

    private final Map<String, Target> targets = new ConcurrentHashMap<>();
    private final JdbcSource.Drivers drivers;

    JdbcWriter(JdbcSource.Drivers drivers) {
        this.drivers = drivers;
    }

    /** Whether a name is a plain SQL identifier (optionally schema-qualified). */
    static boolean name(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    void target(Target t) {
        targets.put(t.spec().name(), t);
    }

    @Override
    public Object write(Change ch) throws Exception {
        Target t = targets.get(ch.table());
        if (t == null) {
            throw new Refused(ch.table() + " has no writer");
        }
        LiveTableSpec spec = t.spec();
        ServerConfig.Jdbc j = t.table().jdbc();
        Properties props = new Properties();
        if (j.user() != null) {
            props.setProperty("user", j.user());
        }
        String pw = JdbcSource.password(j);
        if (pw != null) {
            props.setProperty("password", pw);
        }
        try (Connection c = drivers.driver(j.url()).connect(j.url(), props)) {
            if (c == null) {
                throw new SQLException(t.table().name() + ": the driver refused the address");
            }
            c.setAutoCommit(false);
            try {
                try (PreparedStatement st = c.prepareStatement("insert into " + t.write().journalTable() + " (change_id, changed_at) values (?, ?)")) {
                    st.setString(1, ch.id());
                    st.setTimestamp(2, ch.at() == null ? null : Timestamp.from(ch.at()));
                    st.executeUpdate();
                } catch (SQLException dup) {
                    if (dup.getSQLState() != null && dup.getSQLState().startsWith("23")) {
                        c.rollback();
                        return null;                                     // applied before: the same change again
                    }
                    throw dup;
                }
                String table = t.write().table();
                String v = spec.version();
                List<Object> args = new ArrayList<>();
                StringBuilder sql = new StringBuilder();
                Object next = ch.basedOn() instanceof Number n ? (Object) (n.longValue() + 1) : null;
                switch (ch.op()) {
                    case "update" -> {
                        sql.append("update ").append(table).append(" set ");
                        for (Map.Entry<String, Object> e : ch.set().entrySet()) {
                            sql.append(col(e.getKey())).append(" = ?, ");
                            args.add(e.getValue());
                        }
                        if (spec.changedBy() != null) {
                            sql.append(col(spec.changedBy())).append(" = ?, ");
                            args.add(ch.by());
                        }
                        if (spec.changedAt() != null) {
                            sql.append(col(spec.changedAt())).append(" = ?, ");
                            args.add(Timestamp.from(ch.at()));
                        }
                        sql.append(col(v)).append(" = ").append(col(v)).append(" + 1");
                        where(sql, args, spec, ch, true);
                    }
                    case "remove" -> {
                        sql.append("delete from ").append(table);
                        where(sql, args, spec, ch, true);
                        next = null;
                    }
                    case "add" -> {
                        List<String> cols = new ArrayList<>(spec.key());
                        args.addAll(ch.key());
                        ch.set().forEach((k, val) -> {
                            cols.add(k);
                            args.add(val);
                        });
                        cols.add(v);
                        args.add(1L);
                        if (spec.changedBy() != null) {
                            cols.add(spec.changedBy());
                            args.add(ch.by());
                        }
                        if (spec.changedAt() != null) {
                            cols.add(spec.changedAt());
                            args.add(Timestamp.from(ch.at()));
                        }
                        sql.append("insert into ").append(table).append(" (")
                                .append(String.join(", ", cols.stream().map(JdbcWriter::col).toList())).append(") values (")
                                .append(String.join(", ", java.util.Collections.nCopies(cols.size(), "?"))).append(")");
                        next = 1L;
                    }
                    default -> throw new Refused("no operation '" + ch.op() + "'");
                }
                int n;
                try (PreparedStatement st = c.prepareStatement(sql.toString())) {
                    st.setQueryTimeout((int) Math.max(1, t.table().timeoutMs() / 1000));
                    for (int i = 0; i < args.size(); i++) {
                        st.setObject(i + 1, args.get(i));
                    }
                    n = st.executeUpdate();
                } catch (SQLException e) {
                    c.rollback();
                    if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                        throw new Refused("the source refused it: " + (ch.op().equals("add") ? "the row exists" : "a constraint"));
                    }
                    throw e;
                }
                if (n != 1) {
                    c.rollback();
                    throw new Refused("the row changed at the source since version " + ch.basedOn());
                }
                c.commit();
                return next;
            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // the connection is gone; the transaction with it
                }
                throw e;
            }
        }
    }

    private static void where(StringBuilder sql, List<Object> args, LiveTableSpec spec, Change ch, boolean version) {
        sql.append(" where ");
        for (int i = 0; i < spec.key().size(); i++) {
            sql.append(col(spec.key().get(i))).append(" = ? and ");
            args.add(ch.key().get(i));
        }
        sql.append(col(spec.version())).append(" = ?");
        args.add(ch.basedOn());
    }

    private static String col(String name) {
        if (!name(name)) {
            throw new IllegalArgumentException("not a plain column name: " + name);
        }
        return name;
    }
}
