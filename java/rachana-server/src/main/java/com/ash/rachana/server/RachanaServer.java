/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.data.ProtocolError;
import com.ash.rachana.data.ProtocolHost;
import com.ash.rachana.data.ProtocolHttp;
import com.ash.rachana.parse.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The standalone server (COMPILER_AND_RUNTIMES 10, MODES 4): one process serving a project folder of Sutras as pages, the Data Protocol
 * over the configured tables (kept live by {@code rachana-live}) and sign-in, on the JDK's HTTP server with virtual threads.
 *
 * <p>Routes: {@code /} the Sutras, {@code /v/<sutra>} a page, {@code /sutras/<sutra>.sutra.yaml}, {@code /libraries/<sutra>.libraries.json}
 * and {@code /v/rachana_libs/...} (the libraries a page uses), {@code /assets/...} (the element, the runtime, the Elements pack),
 * {@code /static/...} (the project's {@code static/}), {@value #DATA} (the Data Protocol, {@link ProtocolHttp}), {@code /problems},
 * {@code /signin}, {@code /signout}, {@code /healthz}, {@code /metrics}.
 *
 * <p>Security: the default address is loopback; {@code auth.mode: none} is refused anywhere else. Every {@code POST} whose
 * {@code Origin} is not this server's own (or listed in {@code server.origins}) is refused; sign-in and sign-out also need their CSRF
 * token. Sessions are HttpOnly, SameSite=Lax cookies (Secure with {@code cookie-secure}). Every answer has a strict
 * Content-Security-Policy, {@code nosniff}, {@code same-origin} referrers and {@code frame-ancestors 'none'}. Rights are {@link Access}'s,
 * fail-closed. Logs are {@link Log}'s and never carry a secret.
 */
public final class RachanaServer implements AutoCloseable {

    /** The Data Protocol's base address. */
    public static final String DATA = "/data/v1";
    static final String COOKIE = "rachana_session";
    static final String SIGNIN_COOKIE = "rachana_signin";
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
            + "connect-src 'self'; worker-src 'self' blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";

    private final ServerConfig config;
    private final Log log;
    private final HttpServer server;
    private final ProtocolHttp protocol;
    private final ServerTables tables;
    private final Project project;
    private final Assets assets;
    private final Access access;
    private final Users users;
    private final Sessions sessions;
    private final Set<String> origins;
    private final ScheduledExecutorService watch;
    private final long started = System.currentTimeMillis();
    private volatile boolean closed;

    /** Starts a server for a checked configuration; answers once it listens. */
    public static RachanaServer start(ServerConfig config, Log log) throws IOException, ServerConfig.Invalid {
        return new RachanaServer(config, log == null ? new Log(System.err, null) : log);
    }

    /** Starts a server logging to standard error. */
    public static RachanaServer start(ServerConfig config) throws IOException, ServerConfig.Invalid {
        return start(config, null);
    }

    private RachanaServer(ServerConfig config, Log log) throws IOException, ServerConfig.Invalid {
        this.config = config;
        this.log = log;
        config.describe().forEach(l -> log.info("config", "setting", l));
        this.assets = new Assets(config.runtime());
        if (!assets.hasElements()) {
            throw new ServerConfig.Invalid(List.of("this jar has no Drishti Elements pack (build rachana-server from the repository)"));
        }
        if (!assets.hasRuntime()) {
            throw new ServerConfig.Invalid(List.of("the runtime bundle is not in this jar: build it (python3 tools/js/build_js.py) before the "
                    + "server, or name it under assets.runtime"));
        }
        this.users = config.auth().mode() == ServerConfig.AuthMode.USERS ? new Users(config.auth().users()) : null;
        this.sessions = new Sessions(config.http().sessionIdleMs(), config.http().sessionMaxMs(), Clock.systemUTC());
        this.access = new Access(config.access(), config.edits().access(), this::declared).approvers(config.approvals().approve());
        this.project = new Project(config.project(), config.libraries(), log);
        this.tables = new ServerTables(config, access, log);
        this.protocol = new ProtocolHttp(new ProtocolHost(DATA, tables.provider()), () -> closed);
        this.origins = new LinkedHashSet<>(config.http().origins());
        this.server = HttpServer.create(new InetSocketAddress(config.http().bind(), config.http().port()), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::serve);
        server.start();
        this.watch = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rachana-server-watch");
            t.setDaemon(true);
            return t;
        });
        if (config.project().watch()) {
            watch.scheduleWithFixedDelay(() -> {
                try {
                    project.refresh();
                } catch (RuntimeException e) {
                    log.warn("project-reload-failed", "error", e.getClass().getSimpleName());
                }
            }, 2, 2, TimeUnit.SECONDS);
        }
        log.info("listening", "url", url(), "sutras", project.state().sutras().size(), "tables", config.tables().size(),
                "auth", config.auth().mode().name().toLowerCase(Locale.ROOT));
    }

    /** The address it listens on: {@code http://<bind>:<port>}. */
    public String url() {
        String host = config.http().bind().contains(":") ? "[" + config.http().bind() + "]" : config.http().bind();
        return "http://" + host + ":" + port();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        closed = true;
        watch.shutdownNow();
        protocol.closeStreams();
        server.stop(0);
        tables.close();
        log.info("stopped");
    }

    // ---- requests ----------------------------------------------------------------------------------------------------

    /** What one request carries after the checks: its caller (null when nobody is signed in) and the session, if any. */
    private record Who(Users.Principal principal, Sessions.Session session) {}

    private void serve(HttpExchange x) {
        long t0 = System.nanoTime();
        String path = "?";
        Who who = new Who(null, null);
        try (x) {
            secure(x);
            path = x.getRequestURI().getPath();
            String method = x.getRequestMethod();
            if (!hostAllowed(x)) {                  // DNS-rebinding defence, in every auth mode, before anything else is read or answered
                log.warn("host-refused", "path", path, "host", x.getRequestHeaders().getFirst("Host"));
                text(x, 403, "this server answers only under its own address (other names: server.origins)");
                return;
            }
            if (path == null || path.contains("\0") || path.contains("\\") || List.of(path.split("/")).contains("..")) {
                text(x, 400, "a bad address");
                return;
            }
            if (!method.equals("GET") && !method.equals("POST")) {
                x.getResponseHeaders().set("Allow", "GET, POST");
                text(x, 405, "GET or POST");
                return;
            }
            if (method.equals("POST") && !sameOrigin(x)) {
                log.warn("cross-origin-refused", "path", path, "origin", x.getRequestHeaders().getFirst("Origin"));
                error(x, 403, "rdp.forbidden", "this request comes from another site");
                return;
            }
            Object body = null;
            String form = null;
            if (method.equals("POST")) {
                byte[] in = read(x.getRequestBody(), config.http().maxBody());
                if (in == null) {
                    error(x, 413, "rdp.limit.rows", "the request is larger than server.max-body");
                    return;
                }
                String ct = String.valueOf(x.getRequestHeaders().getFirst("Content-Type")).toLowerCase(Locale.ROOT);
                if (ct.startsWith("application/x-www-form-urlencoded")) {
                    form = new String(in, StandardCharsets.UTF_8);
                } else if (in.length > 0) {
                    try {
                        body = Json.read(new String(in, StandardCharsets.UTF_8));
                    } catch (RuntimeException e) {
                        ProtocolHttp.json(x, 422, ProtocolError.of("rdp.query.invalid", 422, "the body is not JSON").body());
                        return;
                    }
                }
            }
            if (path.equals("/healthz")) {
                ProtocolHttp.json(x, 200, Map.of("status", "ok"));
                return;
            }
            if (path.startsWith("/assets/") && method.equals("GET")) {
                Optional<Assets.Asset> a = assets.get(path.substring("/assets/".length()));
                if (a.isEmpty()) {
                    text(x, 404, "not found");
                } else {
                    bytes(x, 200, a.get().type(), a.get().bytes(), "public, max-age=300");
                }
                return;
            }
            if (path.equals("/signin")) {
                signIn(x, method, form);
                return;
            }
            who = identify(x);
            if (path.equals("/signout") && method.equals("POST")) {
                signOut(x, who, form);
                return;
            }
            if (who.principal() == null) {
                if (path.equals(DATA) || path.startsWith(DATA + "/") || !method.equals("GET")) {
                    error(x, 401, "rdp.auth.required", "sign in first");
                } else if (config.auth().mode() == ServerConfig.AuthMode.USERS) {
                    redirect(x, "/signin?next=" + java.net.URLEncoder.encode(local(x.getRequestURI().getRawPath()), StandardCharsets.UTF_8));
                } else {
                    html(x, 401, Pages.message("Not signed in", "This server is reached through its sign-in proxy."));
                }
                return;
            }
            route(x, method, path, body, who);
        } catch (IOException e) {
            // the client went away
        } catch (RuntimeException e) {
            log.error("request-failed", "path", path, "error", e.getClass().getSimpleName());
            try {
                error(x, 500, "rdp.internal", "the server failed");
            } catch (IOException | RuntimeException ignored) {
                // the answer had started
            }
        } finally {
            if (!path.startsWith("/assets/")) {
                log.info("request", "method", x.getRequestMethod(), "path", path, "status", x.getResponseCode(),
                        "ms", (System.nanoTime() - t0) / 1_000_000, "user", who.principal() == null ? null : who.principal().toString());
            }
        }
    }

    private void route(HttpExchange x, String method, String path, Object body, Who who) throws IOException {
        Users.Principal p = who.principal();
        if (path.equals(DATA) || path.startsWith(DATA + "/")) {
            String rel = path.substring(DATA.length());
            if (method.equals("POST") && rel.startsWith("/tables/") && rel.endsWith("/write") && !writeAllowed(x, who, rel)) {
                return;
            }
            protocol.serve(x, rel.isEmpty() ? "/" : rel, ProtocolHttp.query(x.getRequestURI().getRawQuery()), body, p);
            return;
        }
        if (!method.equals("GET")) {
            text(x, 404, "not found");
            return;
        }
        Project.State st = project.state();
        boolean canSignOut = who.session() != null;
        String csrf = who.session() == null ? "" : who.session().csrf();
        if (path.equals("/")) {
            List<Project.Sutra> mine = st.sutras().values().stream().filter(s -> access.sutra(p, s.name())).toList();
            html(x, 200, Pages.index(mine, problemCount(st), p, csrf, canSignOut));
            return;
        }
        if (path.equals("/problems")) {
            Map<String, Project.Sutra> mine = new LinkedHashMap<>();
            st.sutras().forEach((n, s) -> {
                if (access.sutra(p, n)) {
                    mine.put(n, s);
                }
            });
            html(x, 200, Pages.problems(st.problems(), mine, tables.pending(), p, csrf, canSignOut));
            return;
        }
        if (path.equals("/metrics")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uptimeMs", System.currentTimeMillis() - started);
            m.put("sutras", (long) st.sutras().size());
            m.put("tables", (long) config.tables().size());
            m.put("tablesPending", (long) tables.pending().size());
            m.put("sessions", (long) sessions.size());
            ProtocolHttp.json(x, 200, m);
            return;
        }
        if (path.startsWith("/v/rachana_libs/") || path.startsWith("/rachana_libs/")) {
            String key = path.substring(path.indexOf("rachana_libs/"));
            byte[] b = st.libraryFiles().get(key);
            if (b == null) {
                text(x, 404, "not found");
            } else {
                bytes(x, 200, Assets.type(key), b, "no-store");
            }
            return;
        }
        String name;
        if ((name = between(path, "/v/", "")) != null) {
            Project.Sutra s = st.sutras().get(name);
            if (s == null || !access.sutra(p, name)) {
                html(x, 404, Pages.message("Not found", "No Sutra '" + name + "' here."));
                return;
            }
            boolean libs = !Project.needed(s.text(), st.libraryFiles()).isEmpty();
            html(x, 200, Pages.view(s, assets.elementsDir(), libs, p, csrf, canSignOut));
            return;
        }
        if ((name = between(path, "/sutras/", Project.SUFFIX)) != null) {
            Project.Sutra s = st.sutras().get(name);
            if (s == null || !access.sutra(p, name)) {
                text(x, 404, "not found");
            } else {
                bytes(x, 200, "text/plain; charset=utf-8", s.text().getBytes(StandardCharsets.UTF_8), "no-store");
            }
            return;
        }
        if ((name = between(path, "/libraries/", ".libraries.json")) != null) {
            Project.Sutra s = st.sutras().get(name);
            if (s == null || !access.sutra(p, name)) {
                text(x, 404, "not found");
            } else {
                ProtocolHttp.json(x, 200, Map.of("files", Project.needed(s.text(), st.libraryFiles())));
            }
            return;
        }
        if (path.startsWith("/static/")) {
            Path root = config.project().dir().resolve("static").toAbsolutePath().normalize();
            Path f = root.resolve(path.substring("/static/".length())).normalize();
            if (!f.startsWith(root) || !Files.isRegularFile(f) || hidden(root.relativize(f)) || !realInside(root, f)) {
                text(x, 404, "not found");
            } else {
                bytes(x, 200, Assets.type(f.getFileName().toString()), Files.readAllBytes(f), "no-store");
            }
            return;
        }
        text(x, 404, "not found");
    }

    private static int problemCount(Project.State st) {
        return st.problems().size() + st.sutras().values().stream().mapToInt(s -> s.problems().size()).sum();
    }

    /** A file whose real path (symbolic links followed) is still inside the real folder: a link in static/ cannot lead out of it. */
    private static boolean realInside(Path root, Path f) {
        try {
            return f.toRealPath().startsWith(root.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hidden(Path rel) {
        for (Path part : rel) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /** The name between a prefix and a suffix of a path, when it is one plain segment; else null. */
    private static String between(String path, String prefix, String suffix) {
        if (!path.startsWith(prefix) || !path.endsWith(suffix) || path.length() <= prefix.length() + suffix.length()) {
            return null;
        }
        String n = path.substring(prefix.length(), path.length() - suffix.length());
        return n.contains("/") ? null : n;
    }

    // ---- sign-in -------------------------------------------------------------------------------------------------------

    /** The caller of a request: a session's (users), the trusted proxy's header (header) or the anonymous caller (none). */
    private Who identify(HttpExchange x) {
        switch (config.auth().mode()) {
            case NONE -> {
                return new Who(Users.Principal.ANONYMOUS, null);
            }
            case HEADER -> {
                String from = x.getRemoteAddress().getAddress().getHostAddress();
                String user = x.getRequestHeaders().getFirst(config.auth().userHeader());
                if (!trusted(from) || user == null || user.isBlank() || user.length() > 200) {
                    return new Who(null, null);
                }
                Set<String> roles = new LinkedHashSet<>();
                String r = config.auth().rolesHeader() == null ? null : x.getRequestHeaders().getFirst(config.auth().rolesHeader());
                if (r != null) {
                    for (String s : r.split(",")) {
                        if (!s.isBlank()) {
                            roles.add(s.trim());
                        }
                    }
                }
                return new Who(new Users.Principal(user.trim(), roles), null);
            }
            default -> {
                Sessions.Session s = sessions.get(cookie(x, COOKIE));
                return s == null ? new Who(null, null) : new Who(s.principal(), s);
            }
        }
    }

    private boolean trusted(String address) {
        for (String t : config.auth().trustedProxies()) {
            try {
                if (java.net.InetAddress.getByName(t).getHostAddress().equals(address)) {
                    return true;
                }
            } catch (java.net.UnknownHostException e) {
                // checked at load: an address
            }
        }
        return false;
    }

    private void signIn(HttpExchange x, String method, String form) throws IOException {
        if (config.auth().mode() != ServerConfig.AuthMode.USERS) {
            redirect(x, "/");
            return;
        }
        Map<String, String> q = ProtocolHttp.query(x.getRequestURI().getRawQuery());
        if (method.equals("GET")) {
            String token = Sessions.token();
            x.getResponseHeaders().add("Set-Cookie", SIGNIN_COOKIE + "=" + token + "; Path=/signin; HttpOnly; SameSite=Strict" + secureFlag());
            html(x, 200, Pages.signIn(token, local(q.get("next")), null));
            return;
        }
        Map<String, String> f = ProtocolHttp.query(form);
        String address = x.getRemoteAddress().getAddress().getHostAddress();
        if (!Sessions.same(f.get("csrf"), cookie(x, SIGNIN_COOKIE))) {
            log.warn("signin-refused", "reason", "csrf", "from", address);
            html(x, 403, Pages.message("Sign-in refused", "The sign-in form expired: open it again."));
            return;
        }
        long wait = sessions.waitMs(address);
        if (wait > 0) {
            x.getResponseHeaders().set("Retry-After", String.valueOf((wait + 999) / 1000));
            html(x, 429, Pages.message("Too many tries", "Wait " + ((wait + 999) / 1000) + " seconds before the next try."));
            return;
        }
        String user = f.getOrDefault("user", "");
        char[] password = f.getOrDefault("password", "").toCharArray();
        Users.Principal p = users.check(user, password);
        java.util.Arrays.fill(password, '\0');
        if (p == null) {
            sessions.failed(address);
            log.warn("signin-failed", "user", user.length() > 64 ? user.substring(0, 64) : user, "from", address);
            String token = Sessions.token();
            x.getResponseHeaders().add("Set-Cookie", SIGNIN_COOKIE + "=" + token + "; Path=/signin; HttpOnly; SameSite=Strict" + secureFlag());
            html(x, 401, Pages.signIn(token, local(f.get("next")), "The name or the password is not right."));
            return;
        }
        sessions.succeeded(address);
        String old = cookie(x, COOKIE);
        sessions.close(old);
        Sessions.Session s = sessions.open(p);
        log.info("signin", "user", p.name(), "from", address);
        x.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + s.id() + "; Path=/; HttpOnly; SameSite=Lax" + secureFlag());
        x.getResponseHeaders().add("Set-Cookie", SIGNIN_COOKIE + "=; Path=/signin; Max-Age=0; HttpOnly; SameSite=Strict" + secureFlag());
        redirect(x, local(f.get("next")));
    }

    private void signOut(HttpExchange x, Who who, String form) throws IOException {
        if (who.session() == null) {
            redirect(x, "/");
            return;
        }
        Map<String, String> f = ProtocolHttp.query(form);
        String token = f.get("csrf") != null ? f.get("csrf") : x.getRequestHeaders().getFirst("X-Rachana-CSRF");
        if (!Sessions.same(token, who.session().csrf())) {
            log.warn("signout-refused", "reason", "csrf", "user", who.principal().name());
            error(x, 403, "rdp.forbidden", "the CSRF token is missing or wrong");
            return;
        }
        sessions.close(who.session().id());
        log.info("signout", "user", who.principal().name());
        x.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + secureFlag());
        redirect(x, config.auth().mode() == ServerConfig.AuthMode.USERS ? "/signin" : "/");
    }

    private String secureFlag() {
        return config.http().cookieSecure() ? "; Secure" : "";
    }

    /** A local address to return to after sign-in: a path on this server, else {@code /}. */
    static String local(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\") || next.length() > 2000
                || next.chars().anyMatch(c -> c < 0x20 || c == 0x7f) || next.startsWith("/signin") || next.startsWith("/signout")) {
            return "/";
        }
        return next;
    }

    /**
     * The check of a write beside the protocol's own (LIVE_TABLES 5; the {@code Host} check ran for every request, the {@code Origin} check
     * for every POST): the session's CSRF token as {@code X-Rachana-CSRF} (header mode: the header present, which a page of another site
     * cannot send without a preflight this server never answers). A refusal is answered {@code 403 rdp.write.forbidden} and logged.
     */
    private boolean writeAllowed(HttpExchange x, Who who, String rel) throws IOException {
        String token = x.getRequestHeaders().getFirst("X-Rachana-CSRF");
        boolean ok = who.session() != null ? Sessions.same(token, who.session().csrf()) : token != null && !token.isBlank();
        if (!ok) {
            log.warn("write-refused", "reason", "csrf", "user", who.principal() == null ? null : who.principal().name(), "path", rel);
            error(x, 403, "rdp.write.forbidden", "the CSRF token is missing or wrong");
            return false;
        }
        return true;
    }

    /** The columns a live table's declaration edits (for the access rules; none before the tables are up). */
    private List<String> declared(String table) {
        ServerTables t = tables;
        return t == null ? List.of() : t.declared(table);
    }

    /**
     * Whether the {@code Host} header names this server: its bind address (unless it is a wildcard), {@code localhost}, {@code 127.0.0.1} or
     * {@code [::1]}, each with the server's port, or the authority of one of {@code server.origins}. A page of another site that
     * re-points its name at this address (DNS rebinding) carries its own name in {@code Host} and is refused, whatever the auth mode.
     */
    private boolean hostAllowed(HttpExchange x) {
        String host = x.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        String bind = config.http().bind();
        boolean wildcard = bind.equals("0.0.0.0") || bind.equals("::") || bind.equals("[::]");
        for (String name : wildcard ? List.of("localhost", "127.0.0.1", "[::1]") : List.of(bind.contains(":") && !bind.startsWith("[") ? "[" + bind + "]" : bind,
                "localhost", "127.0.0.1", "[::1]")) {
            if (h.equals(name.toLowerCase(Locale.ROOT) + ":" + port())) {
                return true;
            }
        }
        for (String o : origins) {
            try {
                String a = new URI(o).getRawAuthority();
                if (a != null && h.equals(a.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            } catch (java.net.URISyntaxException e) {
                // a malformed origin never matches (the configuration refuses it at start)
            }
        }
        return false;
    }

    /**
     * Whether a {@code POST} comes from this server's own pages: its {@code Origin} (or, without one, {@code Sec-Fetch-Site}) is this
     * server's address as the browser reached it, or one of {@code server.origins}. A request with neither header is not a browser's
     * cross-site request, so it is allowed.
     */
    private boolean sameOrigin(HttpExchange x) {
        String origin = x.getRequestHeaders().getFirst("Origin");
        if (origin == null) {
            String site = x.getRequestHeaders().getFirst("Sec-Fetch-Site");
            return site == null || site.equals("same-origin") || site.equals("none");
        }
        if (origins.contains(origin)) {
            return true;
        }
        String host = x.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        try {
            URI o = new URI(origin);
            return ("http".equals(o.getScheme()) || "https".equals(o.getScheme())) && host.equalsIgnoreCase(o.getRawAuthority());
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    // ---- answers -------------------------------------------------------------------------------------------------------

    private static void secure(HttpExchange x) {
        var h = x.getResponseHeaders();
        h.set("Content-Security-Policy", CSP);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "same-origin");            // no-referrer would make a form's Origin "null"
        h.set("X-Frame-Options", "DENY");
        h.set("Cross-Origin-Opener-Policy", "same-origin");
    }

    private static String cookie(HttpExchange x, String name) {
        List<String> all = x.getRequestHeaders().get("Cookie");
        if (all == null) {
            return null;
        }
        for (String line : all) {
            for (String part : line.split(";")) {
                String p = part.trim();
                if (p.startsWith(name + "=")) {
                    return p.substring(name.length() + 1);
                }
            }
        }
        return null;
    }

    /** The body, or null when it is longer than {@code max}. */
    private static byte[] read(InputStream in, int max) throws IOException {
        byte[] b = in.readNBytes(max + 1);
        return b.length > max ? null : b;
    }

    private static void redirect(HttpExchange x, String to) throws IOException {
        x.getResponseHeaders().set("Location", to);
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(303, -1);
    }

    private static void html(HttpExchange x, int status, String page) throws IOException {
        bytes(x, status, "text/html; charset=utf-8", page.getBytes(StandardCharsets.UTF_8), "no-store");
    }

    private static void text(HttpExchange x, int status, String s) throws IOException {
        bytes(x, status, "text/plain; charset=utf-8", s.getBytes(StandardCharsets.UTF_8), "no-store");
    }

    private static void error(HttpExchange x, int status, String code, String message) throws IOException {
        ProtocolHttp.json(x, status, ProtocolError.of(code, status, message).body());
    }

    private static void bytes(HttpExchange x, int status, String type, byte[] b, String cache) throws IOException {
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", cache);
        x.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            x.getResponseBody().write(b);
        }
    }
}
