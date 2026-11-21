/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A table from a JDBC query. The query is the configuration's text, fixed at start: one {@code select} (or {@code with … select}), its
 * values written as {@code :name} parameters and bound from the table's {@code params} through a prepared statement; no SQL text is ever
 * built from a request. The connection is read-only unless the configuration says {@code read-only: false}, auto-commit off and rolled back
 * after each read, with the table's timeout and {@code max-rows}. Drivers are the operator's jars ({@code drivers:}), loaded in their own
 * class loader; nothing is bundled. Errors name the table and the SQL state, never the password or row values.
 */
final class JdbcSource extends TableSource {

    /** The JDBC drivers the server can use: the configured jars' and any on the class path. */
    static final class Drivers {
        private final List<Driver> all = new ArrayList<>();

        Drivers(List<Path> jars) {
            ClassLoader parent = JdbcSource.class.getClassLoader();
            ClassLoader loader = parent;
            if (!jars.isEmpty()) {
                List<URL> urls = new ArrayList<>();
                for (Path j : jars) {
                    try {
                        urls.add(j.toUri().toURL());
                    } catch (MalformedURLException e) {
                        throw new IllegalArgumentException("driver " + j + " is not a file address");
                    }
                }
                loader = new URLClassLoader("rachana-jdbc-drivers", urls.toArray(URL[]::new), parent);
            }
            ServiceLoader.load(Driver.class, loader).stream().forEach(p -> {
                try {
                    all.add(p.get());
                } catch (java.util.ServiceConfigurationError e) {
                    // a driver that cannot start is not offered
                }
            });
        }

        /** The driver that takes this address. */
        Driver driver(String url) throws SQLException {
            for (Driver d : all) {
                if (d.acceptsURL(url)) {
                    return d;
                }
            }
            throw new SQLException("no JDBC driver for this address: name the driver's jar under drivers:", "08001");
        }
    }

    /** The query with {@code ?} in place of each {@code :name}, and the names in their order. */
    record Prepared(String sql, List<String> names) {}

    private final Drivers drivers;
    private final Prepared prepared;

    JdbcSource(ServerConfig.Table t, Drivers drivers) {
        super(t);
        this.drivers = drivers;
        this.prepared = prepare(t.query());
    }

    /**
     * Checks a configured query: one statement, a {@code select} or {@code with}, every {@code :name} given in {@code params} and every
     * param used.
     */
    static void check(String query, Set<String> params, Consumer<String> problem) {
        String body = stripComments(query).trim();
        String lower = body.toLowerCase(Locale.ROOT);
        while (lower.startsWith("(")) {
            lower = lower.substring(1).trim();
        }
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            problem.accept("a single select (or with … select): a table is read, never written");
        }
        String noTrail = body.endsWith(";") ? body.substring(0, body.length() - 1) : body;
        if (outsideQuotes(noTrail).contains(";")) {
            problem.accept("one statement only (no ';' inside)");
        }
        Prepared p;
        try {
            p = prepare(query);
        } catch (IllegalArgumentException e) {
            problem.accept(e.getMessage());
            return;
        }
        Set<String> used = new LinkedHashSet<>(p.names());
        for (String n : used) {
            if (!params.contains(n)) {
                problem.accept(":" + n + " has no value in params");
            }
        }
        for (String n : params) {
            if (!used.contains(n)) {
                problem.accept("params." + n + " is not used by the query (:" + n + ")");
            }
        }
    }

    /** {@code :name} (outside quotes and comments, not {@code ::}) becomes {@code ?}. */
    static Prepared prepare(String query) {
        String q = stripComments(query).trim();
        if (q.endsWith(";")) {
            q = q.substring(0, q.length() - 1);
        }
        StringBuilder out = new StringBuilder();
        List<String> names = new ArrayList<>();
        char quote = 0;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                out.append(c);
            } else if (c == ':' && i + 1 < q.length() && q.charAt(i + 1) == ':') {
                out.append("::");
                i++;
            } else if (c == ':' && i + 1 < q.length() && Character.isLetter(q.charAt(i + 1))) {
                int j = i + 1;
                while (j < q.length() && (Character.isLetterOrDigit(q.charAt(j)) || q.charAt(j) == '_')) {
                    j++;
                }
                names.add(q.substring(i + 1, j));
                out.append('?');
                i = j - 1;
            } else {
                out.append(c);
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("an unclosed quote");
        }
        return new Prepared(out.toString(), List.copyOf(names));
    }

    private static String stripComments(String q) {
        return q.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
    }

    private static String outsideQuotes(String q) {
        return q.replaceAll("'(?:[^']|'')*'", "''").replaceAll("\"[^\"]*\"", "\"\"");
    }

    @Override
    List<Map<String, Object>> load() throws Exception {
        return query(prepared.sql(), List.of());
    }

    /**
     * The table's condition pushed into the query ({@link FilterSql}: bound values, column names from the table's own allow-list). A
     * condition that does not translate, or a database that refuses the wrapped query, reads every row, as before.
     */
    @Override
    List<Map<String, Object>> load(Object filter) throws Exception {
        Map<String, String> t = types();
        FilterSql.Where w = t == null ? null : FilterSql.of(filter, t);
        if (w == null) {
            return load();
        }
        try {
            return query("select * from (" + prepared.sql() + ") rachana_t where " + w.sql(), w.params());
        } catch (ReadFailed e) {
            return load();
        }
    }

    private List<Map<String, Object>> query(String sql, List<Object> extra) throws Exception {
        ServerConfig.Jdbc j = table.jdbc();
        Properties props = new Properties();
        if (j.user() != null) {
            props.setProperty("user", j.user());
        }
        String pw = password(j);
        if (pw != null) {
            props.setProperty("password", pw);
        }
        try (Connection c = drivers.driver(j.url()).connect(j.url(), props)) {
            if (c == null) {
                throw new ReadFailed(table.name() + ": the driver refused the address");
            }
            c.setReadOnly(j.readOnly());
            c.setAutoCommit(false);
            try (PreparedStatement st = c.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                st.setQueryTimeout((int) Math.max(1, table.timeoutMs() / 1000));
                st.setMaxRows(table.maxRows() + 1);
                for (int i = 0; i < prepared.names().size(); i++) {
                    st.setObject(i + 1, table.params().get(prepared.names().get(i)));
                }
                for (int i = 0; i < extra.size(); i++) {
                    st.setObject(prepared.names().size() + i + 1, extra.get(i));
                }
                List<Map<String, Object>> out = new ArrayList<>();
                try (ResultSet rs = st.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    String[] labels = new String[n];
                    for (int i = 0; i < n; i++) {
                        labels[i] = md.getColumnLabel(i + 1);
                    }
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        for (int i = 0; i < n; i++) {
                            r.put(labels[i], rs.getObject(i + 1));
                        }
                        out.add(r);
                    }
                }
                return out;
            } finally {
                c.rollback();
            }
        } catch (SQLException e) {
            throw new ReadFailed(table.name() + ": the query failed (SQL state " + e.getSQLState() + ", " + e.getClass().getSimpleName() + ")");
        }
    }

    /** The password, from the environment or a file; null when none is configured. */
    private static String password(ServerConfig.Jdbc j) throws ReadFailed {
        if (j.passwordEnv() != null) {
            String v = System.getenv(j.passwordEnv());
            if (v == null) {
                throw new ReadFailed("the environment variable " + j.passwordEnv() + " (the password) is not set");
            }
            return v;
        }
        if (j.passwordFile() != null) {
            try {
                return Files.readString(j.passwordFile(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                throw new ReadFailed("the password file " + j.passwordFile() + " could not be read");
            }
        }
        return null;
    }
}
