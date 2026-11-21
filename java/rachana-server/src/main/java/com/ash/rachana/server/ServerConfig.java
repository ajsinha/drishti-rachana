/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.parse.PositionalYamlReader;
import com.ash.rachana.parse.YamlSyntaxException;
import com.ash.rachana.table.LiveTableSpec;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server's one configuration file ({@code rachana-server.yaml}; every key in {@code rachana-server.example.yaml}), read and checked
 * whole: every problem is reported at once, an unknown key is a problem, and nothing starts on a configuration with problems.
 *
 * <p><b>Paths are explicit</b>: each is absolute, or starts with {@code ${config}} (the folder of this file) or {@code ${project}} (the
 * project folder); a bare relative path is refused, so where a file is read from never depends on the working folder. Resolved paths are
 * printed at start. <b>Secrets</b> are never written in the file: a password or a header value is named by an environment variable
 * ({@code password-env}) or a file ({@code password-file}), and {@link #toString()} of every part leaves them out.
 */
public final class ServerConfig {

    /** The file's {@code rachana-server:} version this reader knows. */
    public static final long VERSION = 1;

    /** A configuration that cannot be used: every problem, each naming its key. */
    public static final class Invalid extends Exception {
        private final List<String> problems;

        public Invalid(List<String> problems) {
            super(String.join("\n", problems), null, false, false);
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    /** How a caller is known: nobody (loopback only), a users file, or a header a trusted reverse proxy sets. */
    public enum AuthMode { NONE, USERS, HEADER }

    /** Where and how the server listens. */
    public record Http(String bind, int port, List<String> origins, boolean cookieSecure, long sessionIdleMs, long sessionMaxMs, int maxBody) {}

    /** The project folder: {@code sutras/}, {@code rachana_libs/}, {@code static/}. */
    public record Project(Path dir, String level, boolean watch) {}

    /** A JDBC connection: never written with its password ({@code toString}). */
    public record Jdbc(String url, String user, String passwordEnv, Path passwordFile, boolean readOnly) {
        @Override
        public String toString() {
            return "jdbc " + Redact.url(url) + (user == null ? "" : " as " + user) + (readOnly ? " (read-only)" : " (read-write)");
        }
    }

    /** A REST JSON source: the address, where the rows are in the answer, and headers (values from the environment). */
    public record Rest(URI url, String path, Map<String, String> headers, Map<String, String> headerEnv) {
        @Override
        public String toString() {
            return "rest " + Redact.url(url.toString()) + (path == null ? "" : " " + path);
        }
    }

    /**
     * One table: exactly one of {@code file}, {@code jdbc} (with {@code query} and its named {@code params}) or {@code rest}; the key; how
     * often it is read again ({@code refreshMs}, 0: never); declared columns (each a map of the protocol's column fields) or none (inferred
     * from the first read).
     */
    public record Table(String name, String title, List<String> key, Path file, Jdbc jdbc, String query, Map<String, Object> params,
            Rest rest, long refreshMs, long timeoutMs, int maxRows, List<Map<String, Object>> columns, String where) {

        public String source() {
            return file != null ? "file " + file : jdbc != null ? jdbc.toString() : String.valueOf(rest);
        }
    }

    /** Who may read a table: the roles ({@code read}; empty: every signed-in caller) and, per column, the roles that see it unmasked. */
    public record TableAccess(List<String> read, Map<String, List<String>> unmask) {}

    /** Sign-in. */
    public record Auth(AuthMode mode, Path users, String userHeader, String rolesHeader, List<String> trustedProxies) {}

    /** Table and Sutra rights; {@code openDefault}: a table or Sutra without an entry is open to every signed-in caller (else refused). */
    public record Access(Map<String, TableAccess> tables, Map<String, List<String>> sutras, boolean openDefault) {}

    private final Path file;
    private final Http http;
    private final Project project;
    private final List<Path> libraries;
    private final Path runtime;
    private final List<Path> drivers;
    private final Map<String, Table> tables;
    private final Auth auth;
    private final Access access;

    private ServerConfig(Path file, Http http, Project project, List<Path> libraries, Path runtime, List<Path> drivers, Map<String, Table> tables,
            Auth auth, Access access) {
        this.file = file;
        this.http = http;
        this.project = project;
        this.libraries = libraries;
        this.runtime = runtime;
        this.drivers = drivers;
        this.tables = tables;
        this.auth = auth;
        this.access = access;
    }

    public Path file() {
        return file;
    }

    public Http http() {
        return http;
    }

    public Project project() {
        return project;
    }

    /** More library folders or {@code .rlib} files, beside the project's {@code rachana_libs/} and the bundled ones. */
    public List<Path> libraries() {
        return libraries;
    }

    /** The runtime bundle to serve instead of the jar's, or null. */
    public Path runtime() {
        return runtime;
    }

    /** JDBC driver jars, loaded in their own class loader. */
    public List<Path> drivers() {
        return drivers;
    }

    public Map<String, Table> tables() {
        return tables;
    }

    public Auth auth() {
        return auth;
    }

    public Access access() {
        return access;
    }

    /** The same configuration on another port (0: any free one). */
    public ServerConfig withPort(int port) {
        Http h = new Http(http.bind(), port, http.origins(), http.cookieSecure(), http.sessionIdleMs(), http.sessionMaxMs(), http.maxBody());
        return new ServerConfig(file, h, project, libraries, runtime, drivers, tables, auth, access);
    }

    /** Reads and checks a configuration file. */
    public static ServerConfig read(Path file) throws IOException, Invalid {
        Path f = file.toAbsolutePath().normalize();
        return parse(Files.readString(f), f);
    }

    /** Reads and checks a configuration's text; {@code file} anchors {@code ${config}}. */
    @SuppressWarnings("unchecked")
    public static ServerConfig parse(String yaml, Path file) throws Invalid {
        Object root;
        try {
            root = new PositionalYamlReader().read(yaml).plain();
        } catch (YamlSyntaxException e) {
            throw new Invalid(List.of("line " + e.line() + ": YAML syntax: " + e.getMessage()));
        }
        Reader r = new Reader(file.toAbsolutePath().normalize().getParent());
        Map<String, Object> m = r.map(root, "");
        r.keys(m, "", "rachana-server", "server", "project", "libraries", "assets", "drivers", "tables", "auth", "access");
        if (!Long.valueOf(VERSION).equals(m.get("rachana-server"))) {
            r.problem("rachana-server: this file starts with 'rachana-server: 1' (the version of its keys)");
        }
        Map<String, Object> s = r.map(m.get("server"), "server");
        r.keys(s, "server", "bind", "port", "origins", "cookie-secure", "session-idle", "session-max", "max-body");
        String bind = r.text(s.getOrDefault("bind", "127.0.0.1"), "server.bind");
        long port = r.number(s.getOrDefault("port", 8080L), "server.port", 0, 65535);
        List<String> origins = new ArrayList<>();
        for (String o : r.texts(s.get("origins"), "server.origins")) {
            URI u = r.uri(o, "server.origins");
            if (u != null && (u.getPath() != null && !u.getPath().isEmpty() || u.getQuery() != null)) {
                r.problem("server.origins: '" + o + "' is an origin (scheme://host[:port]), with no path");
            }
            origins.add(o);
        }
        Http http = new Http(bind, (int) port, List.copyOf(origins), r.bool(s.getOrDefault("cookie-secure", false), "server.cookie-secure"),
                r.duration(s.getOrDefault("session-idle", "30m"), "server.session-idle"),
                r.duration(s.getOrDefault("session-max", "12h"), "server.session-max"),
                (int) r.number(s.getOrDefault("max-body", 1_048_576L), "server.max-body", 1024, 64L * 1024 * 1024));

        Map<String, Object> p = r.map(m.get("project"), "project");
        r.keys(p, "project", "dir", "level", "watch");
        Path projectDir = p.get("dir") == null ? null : r.path(p.get("dir"), "project.dir", false);
        if (p.get("dir") == null) {
            r.problem("project.dir: the project folder is needed (it holds sutras/)");
        }
        r.projectDir = projectDir;
        String level = r.text(p.getOrDefault("level", com.ash.rachana.profile.Grammar.DEFAULT_LEVEL), "project.level");
        if (level != null && !level.matches("1\\.[0-9]+")) {
            r.problem("project.level: a language level such as 1.1 or 1.2, not '" + level + "'");
        }
        Project project = new Project(projectDir, level, r.bool(p.getOrDefault("watch", true), "project.watch"));

        List<Path> libraries = new ArrayList<>();
        for (Object l : r.list(m.get("libraries"), "libraries")) {
            libraries.add(r.path(l, "libraries", true));
        }
        Map<String, Object> a = r.map(m.get("assets"), "assets");
        r.keys(a, "assets", "runtime");
        Path runtime = a.get("runtime") == null ? null : r.path(a.get("runtime"), "assets.runtime", true);
        List<Path> drivers = new ArrayList<>();
        for (Object d : r.list(m.get("drivers"), "drivers")) {
            drivers.add(r.path(d, "drivers", true));
        }

        Map<String, Table> tables = new LinkedHashMap<>();
        Map<String, Object> t = r.map(m.get("tables"), "tables");
        for (Map.Entry<String, Object> e : t.entrySet()) {
            Table tb = r.table(e.getKey(), r.map(e.getValue(), "tables." + e.getKey()));
            if (tb != null) {
                tables.put(e.getKey(), tb);
            }
        }

        Map<String, Object> au = r.map(m.get("auth"), "auth");
        r.keys(au, "auth", "mode", "users", "header");
        String modeText = r.text(au.getOrDefault("mode", "none"), "auth.mode");
        AuthMode mode = switch (modeText == null ? "" : modeText) {
            case "none" -> AuthMode.NONE;
            case "users" -> AuthMode.USERS;
            case "header" -> AuthMode.HEADER;
            default -> {
                r.problem("auth.mode: none, users or header, not '" + modeText + "'");
                yield AuthMode.NONE;
            }
        };
        Path users = au.get("users") == null ? null : r.path(au.get("users"), "auth.users", true);
        if (mode == AuthMode.USERS && users == null) {
            r.problem("auth.users: mode users needs the users file (make entries with: java -jar rachana-server.jar hash-password)");
        }
        Map<String, Object> h = r.map(au.get("header"), "auth.header");
        r.keys(h, "auth.header", "user", "roles", "trusted-proxies");
        List<String> proxies = r.texts(h.get("trusted-proxies"), "auth.header.trusted-proxies");
        if (mode == AuthMode.HEADER && (h.get("user") == null || proxies.isEmpty())) {
            r.problem("auth.header: mode header needs user (the header's name) and trusted-proxies (the addresses allowed to set it)");
        }
        for (String pr : proxies) {
            if (!pr.matches("[0-9.]+|[0-9a-fA-F:]+")) {
                r.problem("auth.header.trusted-proxies: '" + pr + "' is an IP address, not a name");
            }
        }
        if (mode == AuthMode.NONE && !loopback(bind)) {
            r.problem("auth.mode: none is allowed only on a loopback address (server.bind " + bind + "): choose users or header to listen on others");
        }
        Auth auth = new Auth(mode, users, r.text(h.get("user"), "auth.header.user"), r.text(h.get("roles"), "auth.header.roles"), proxies);

        Map<String, Object> ac = r.map(m.get("access"), "access");
        r.keys(ac, "access", "tables", "sutras", "default");
        Map<String, TableAccess> ta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : r.map(ac.get("tables"), "access.tables").entrySet()) {
            String k = "access.tables." + e.getKey();
            if (!tables.containsKey(e.getKey())) {
                r.problem(k + ": no table '" + e.getKey() + "' in tables");
            }
            Map<String, Object> one = r.map(e.getValue(), k);
            r.keys(one, k, "read", "unmask");
            Map<String, List<String>> unmask = new LinkedHashMap<>();
            r.map(one.get("unmask"), k + ".unmask").forEach((col, roles) -> unmask.put(col, r.texts(roles, k + ".unmask." + col)));
            ta.put(e.getKey(), new TableAccess(r.texts(one.get("read"), k + ".read"), unmask));
        }
        Map<String, List<String>> sa = new LinkedHashMap<>();
        r.map(ac.get("sutras"), "access.sutras").forEach((n, roles) -> sa.put(n, r.texts(roles, "access.sutras." + n)));
        String def = r.text(ac.getOrDefault("default", "signed-in"), "access.default");
        if (!"signed-in".equals(def) && !"closed".equals(def)) {
            r.problem("access.default: signed-in (a table or Sutra without an entry is open to every signed-in caller) or closed");
        }
        Access access = new Access(ta, sa, "signed-in".equals(def));
        if (!r.problems.isEmpty()) {
            throw new Invalid(r.problems);
        }
        return new ServerConfig(file, http, project, List.copyOf(libraries), runtime, List.copyOf(drivers), tables, auth, access);
    }

    /** Whether an address is a loopback one ({@code 127.0.0.1}, {@code ::1}, {@code localhost}). */
    static boolean loopback(String bind) {
        try {
            return bind != null && InetAddress.getByName(bind).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** The resolved settings, one per line, for the start-up log: paths as resolved, no secret. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add("config " + file);
        out.add("project " + project.dir() + " (level " + project.level() + (project.watch() ? ", watched" : "") + ")");
        libraries.forEach(l -> out.add("libraries " + l));
        if (runtime != null) {
            out.add("runtime " + runtime);
        }
        drivers.forEach(d -> out.add("driver " + d));
        tables.values().forEach(t -> out.add("table " + t.name() + ": " + t.source() + (t.refreshMs() > 0 ? ", every " + t.refreshMs() + " ms" : "")));
        out.add("auth " + auth.mode().name().toLowerCase(java.util.Locale.ROOT) + (auth.users() == null ? "" : " " + auth.users()));
        return out;
    }

    /** The checking reader: problems collected, never thrown one by one. */
    private static final class Reader {
        final Path configDir;
        Path projectDir;
        final List<String> problems = new ArrayList<>();

        Reader(Path configDir) {
            this.configDir = configDir;
        }

        void problem(String p) {
            problems.add(p);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map(Object v, String key) {
            if (v == null) {
                return new LinkedHashMap<>();
            }
            if (v instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            problem((key.isEmpty() ? "the file" : key) + ": a map of keys, not '" + v + "'");
            return new LinkedHashMap<>();
        }

        List<?> list(Object v, String key) {
            if (v == null) {
                return List.of();
            }
            if (v instanceof List<?> l) {
                return l;
            }
            problem(key + ": a list");
            return List.of();
        }

        void keys(Map<String, Object> m, String where, String... known) {
            Set<String> k = new LinkedHashSet<>(List.of(known));
            for (String key : m.keySet()) {
                if (!k.contains(key)) {
                    problem((where.isEmpty() ? "" : where + ".") + key + ": unknown key (known: " + String.join(", ", k) + ")");
                }
            }
        }

        String text(Object v, String key) {
            if (v == null) {
                return null;
            }
            if (v instanceof Map || v instanceof List) {
                problem(key + ": a text, not a " + (v instanceof Map ? "map" : "list"));
                return null;
            }
            return String.valueOf(v);
        }

        List<String> texts(Object v, String key) {
            if (v instanceof String s) {
                return List.of(s);
            }
            List<String> out = new ArrayList<>();
            for (Object o : list(v, key)) {
                String t = text(o, key);
                if (t != null) {
                    out.add(t);
                }
            }
            return out;
        }

        boolean bool(Object v, String key) {
            if (v instanceof Boolean b) {
                return b;
            }
            problem(key + ": true or false, not '" + v + "'");
            return false;
        }

        long number(Object v, String key, long min, long max) {
            if (v instanceof Long n && n >= min && n <= max) {
                return n;
            }
            problem(key + ": a whole number from " + min + " to " + max + ", not '" + v + "'");
            return min;
        }

        long duration(Object v, String key) {
            long ms = LiveTableSpec.durationMs(v);
            if (ms <= 0 && !(v instanceof Long l && l == 0) && !"0".equals(String.valueOf(v))) {
                problem(key + ": a duration such as 500ms, 5s, 10m, 2h (a bare number is seconds), not '" + v + "'");
            }
            return ms;
        }

        URI uri(String s, String key) {
            try {
                URI u = new URI(s);
                if (!"http".equals(u.getScheme()) && !"https".equals(u.getScheme()) || u.getHost() == null) {
                    problem(key + ": an http or https address, not '" + s + "'");
                    return null;
                }
                return u;
            } catch (java.net.URISyntaxException e) {
                problem(key + ": not an address: '" + s + "'");
                return null;
            }
        }

        /** An explicit path: absolute, or anchored at ${config} or ${project}; {@code mustExist} checks it is there. */
        Path path(Object v, String key, boolean mustExist) {
            String s = text(v, key);
            if (s == null) {
                return null;
            }
            Path out;
            if (s.startsWith("${config}")) {
                out = configDir.resolve(rest(s, "${config}"));
            } else if (s.startsWith("${project}")) {
                if (projectDir == null) {
                    problem(key + ": '" + s + "' names ${project}, which only paths after project.dir may use");
                    return null;
                }
                out = projectDir.resolve(rest(s, "${project}"));
            } else if (s.contains("${")) {
                problem(key + ": '" + s + "': the only anchors are ${config} and ${project}");
                return null;
            } else {
                Path p = Path.of(s);
                if (!p.isAbsolute()) {
                    problem(key + ": '" + s + "' is a bare relative path: write it absolute, or from ${config} (this file's folder) or ${project}");
                    return null;
                }
                out = p;
            }
            out = out.toAbsolutePath().normalize();
            if (mustExist && !Files.exists(out)) {
                problem(key + ": " + out + " does not exist");
            }
            return out;
        }

        private static String rest(String s, String anchor) {
            String r = s.substring(anchor.length());
            while (r.startsWith("/") || r.startsWith("\\")) {
                r = r.substring(1);
            }
            return r;
        }

        Table table(String name, Map<String, Object> m) {
            String k = "tables." + name;
            if (!name.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                problem(k + ": a table name is a letter, then letters, digits, '_', '.' or '-' (at most 64)");
            }
            keys(m, k, "title", "key", "file", "jdbc", "query", "params", "rest", "refresh", "timeout", "max-rows", "columns", "where");
            int sources = (m.containsKey("file") ? 1 : 0) + (m.containsKey("jdbc") ? 1 : 0) + (m.containsKey("rest") ? 1 : 0);
            if (sources != 1) {
                problem(k + ": exactly one source: file, jdbc or rest");
            }
            List<String> key = texts(m.get("key"), k + ".key");
            if (key.isEmpty()) {
                problem(k + ".key: a table needs its key (a column or a list): rows are matched by it when they change");
            }
            Path file = m.containsKey("file") ? path(m.get("file"), k + ".file", true) : null;
            if (file != null && !file.getFileName().toString().matches(".*\\.(csv|json|jsonl|ndjson|arrow)")) {
                problem(k + ".file: a .csv, .json, .jsonl, .ndjson or .arrow file");
            }
            Jdbc jdbc = null;
            String query = text(m.get("query"), k + ".query");
            Map<String, Object> params = map(m.get("params"), k + ".params");
            if (m.containsKey("jdbc")) {
                Map<String, Object> j = map(m.get("jdbc"), k + ".jdbc");
                keys(j, k + ".jdbc", "url", "user", "password-env", "password-file", "read-only");
                String url = text(j.get("url"), k + ".jdbc.url");
                if (url == null || !url.startsWith("jdbc:")) {
                    problem(k + ".jdbc.url: a jdbc: address");
                } else if (url.toLowerCase(java.util.Locale.ROOT).matches(".*[;?&:]password=.*")) {
                    problem(k + ".jdbc.url: no password in the address: use password-env or password-file");
                }
                if (j.containsKey("password-env") && j.containsKey("password-file")) {
                    problem(k + ".jdbc: password-env or password-file, not both");
                }
                jdbc = new Jdbc(url, text(j.get("user"), k + ".jdbc.user"), text(j.get("password-env"), k + ".jdbc.password-env"),
                        j.get("password-file") == null ? null : path(j.get("password-file"), k + ".jdbc.password-file", true),
                        bool(j.getOrDefault("read-only", true), k + ".jdbc.read-only"));
                if (query == null) {
                    problem(k + ".query: a jdbc table needs its query (a select; values as :name parameters from params)");
                } else {
                    JdbcSource.check(query, params.keySet(), msg -> problem(k + ".query: " + msg));
                }
                for (Map.Entry<String, Object> e : params.entrySet()) {
                    if (e.getValue() instanceof Map || e.getValue() instanceof List) {
                        problem(k + ".params." + e.getKey() + ": a single value");
                    }
                }
            } else if (query != null || !params.isEmpty()) {
                problem(k + ": query and params belong to a jdbc table");
            }
            Rest rest = null;
            if (m.containsKey("rest")) {
                Map<String, Object> rm = map(m.get("rest"), k + ".rest");
                keys(rm, k + ".rest", "url", "path", "headers", "header-env");
                URI u = uri(String.valueOf(rm.get("url")), k + ".rest.url");
                String path = text(rm.get("path"), k + ".rest.path");
                if (path != null && !path.matches("\\$(\\.[A-Za-z_][A-Za-z0-9_-]*)*")) {
                    problem(k + ".rest.path: a path such as $.items or $.data.rows (names only)");
                }
                Map<String, String> headers = new LinkedHashMap<>();
                map(rm.get("headers"), k + ".rest.headers").forEach((hk, hv) -> headers.put(hk, String.valueOf(hv)));
                for (String hk : headers.keySet()) {
                    if (hk.equalsIgnoreCase("authorization") || hk.equalsIgnoreCase("cookie") || hk.toLowerCase(java.util.Locale.ROOT).contains("token")) {
                        problem(k + ".rest.headers." + hk + ": a secret header is named by an environment variable (header-env), never written here");
                    }
                }
                Map<String, String> env = new LinkedHashMap<>();
                map(rm.get("header-env"), k + ".rest.header-env").forEach((hk, hv) -> env.put(hk, String.valueOf(hv)));
                rest = u == null ? null : new Rest(u, path, headers, env);
            }
            long refresh = m.containsKey("refresh") ? duration(m.get("refresh"), k + ".refresh") : 0;
            long timeout = duration(m.getOrDefault("timeout", "10s"), k + ".timeout");
            int maxRows = (int) number(m.getOrDefault("max-rows", 1_000_000L), k + ".max-rows", 1, 10_000_000);
            List<Map<String, Object>> columns = new ArrayList<>();
            for (Object c : list(m.get("columns"), k + ".columns")) {
                columns.add(map(c, k + ".columns"));
            }
            return new Table(name, text(m.get("title"), k + ".title"), key, file, jdbc, query, params, rest, refresh, timeout, maxRows,
                    columns, text(m.get("where"), k + ".where"));
        }
    }

    /** Leaves secrets out of addresses written to the log. */
    static final class Redact {
        private Redact() {
        }

        /** An address without its user information and query. */
        static String url(String u) {
            if (u == null) {
                return "";
            }
            String s = u.replaceAll("//[^/@]*@", "//***@");
            int q = s.indexOf('?');
            return q < 0 ? s : s.substring(0, q) + "?…";
        }
    }
}
