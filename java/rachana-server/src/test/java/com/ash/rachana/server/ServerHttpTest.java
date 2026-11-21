/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The server over HTTP: sign-in, sessions, CSRF and origin checks, rights, the Data Protocol and its live stream, pages and assets. */
class ServerHttpTest {

    @TempDir
    Path tmp;
    Fixture fx;
    RachanaServer server;
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void start() throws Exception {
        fx = new Fixture(tmp);
        server = RachanaServer.start(ServerConfig.read(fx.users("")), fx.log);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + path)).GET();
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, String cookie, String type, String body, String origin) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + path)).header("Content-Type", type)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        if (origin != null) {
            b.header("Origin", origin);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static String cookie(HttpResponse<?> r, String name) {
        for (String c : r.headers().allValues("Set-Cookie")) {
            if (c.startsWith(name + "=")) {
                return c.substring(0, c.indexOf(';'));
            }
        }
        return null;
    }

    static String field(String html, String name) {
        int i = html.indexOf("name=" + name + " value=\"");
        assertTrue(i >= 0, name + " in " + html);
        int s = i + ("name=" + name + " value=\"").length();
        return html.substring(s, html.indexOf('"', s));
    }

    /** Signs in through the form; answers the session cookie. */
    String signIn(String user, String password) throws Exception {
        HttpResponse<String> form = get("/signin", null);
        String pre = cookie(form, RachanaServer.SIGNIN_COOKIE);
        String body = "csrf=" + field(form.body(), "csrf") + "&next=%2Fv%2Fsales&user=" + user + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpResponse<String> r = post("/signin", pre, "application/x-www-form-urlencoded", body, server.url());
        if (r.statusCode() != 303) {
            return null;
        }
        assertEquals("/v/sales", r.headers().firstValue("Location").orElse(""));
        return cookie(r, RachanaServer.COOKIE);
    }

    @Test
    void signedOutCallersAreSentToSignInAndTheDataIsClosed() throws Exception {
        HttpResponse<String> page = get("/v/sales", null);
        assertEquals(303, page.statusCode());
        assertEquals("/signin?next=%2Fv%2Fsales", page.headers().firstValue("Location").orElse(""));
        HttpResponse<String> data = get(RachanaServer.DATA + "/tables", null);
        assertEquals(401, data.statusCode());
        assertTrue(data.body().contains("rdp.auth.required"));
        assertEquals(200, get("/healthz", null).statusCode());
        assertEquals(200, get("/assets/rachana/src/rachana_view.js", null).statusCode());
    }

    @Test
    void signInChecksThePasswordTheTokenAndTheOrigin() throws Exception {
        assertEquals(null, signIn("alice", "wrong"));
        assertEquals(null, signIn("nobody", Fixture.PASSWORD));
        HttpResponse<String> form = get("/signin", null);
        String body = "csrf=" + field(form.body(), "csrf") + "&user=alice&password=" + URLEncoder.encode(Fixture.PASSWORD, StandardCharsets.UTF_8);
        assertEquals(403, post("/signin", null, "application/x-www-form-urlencoded", body, server.url()).statusCode(), "no token cookie");
        assertEquals(403, post("/signin", cookie(form, RachanaServer.SIGNIN_COOKIE), "application/x-www-form-urlencoded", body,
                "https://evil.example").statusCode(), "another site");
        String session = signIn("alice", Fixture.PASSWORD);
        assertNotNull(session);
        String text = fx.logText();
        assertFalse(text.contains(Fixture.PASSWORD), "a password in the logs");
        assertFalse(text.contains(session.substring(session.indexOf('=') + 1)), "a session id in the logs");
        assertTrue(text.contains("\"event\":\"signin-failed\""));
    }

    @Test
    void aSignedInCallerReadsTheTablesTheirRolesAllow() throws Exception {
        String alice = signIn("alice", Fixture.PASSWORD);
        HttpResponse<String> list = get(RachanaServer.DATA + "/tables", alice);
        assertEquals(200, list.statusCode(), list.body());
        assertTrue(list.body().contains("\"sales\""), list.body());
        @SuppressWarnings("unchecked")
        Map<String, Object> rows = (Map<String, Object>) Json.read(get(RachanaServer.DATA + "/tables/sales/rows?shape=objects", alice).body());
        List<?> r = (List<?>) rows.get("rows");
        assertEquals(2, r.size());
        assertEquals(Map.of("$masked", true), ((Map<?, ?>) r.get(0)).get("email"), "email is unmasked for admins only");
        assertEquals("120.00", ((Map<?, ?>) r.get(0)).get("revenue"), "a decimal travels as its exact text");
        String bob = signIn("bob", Fixture.PASSWORD);
        HttpResponse<String> none = get(RachanaServer.DATA + "/tables/sales/rows", bob);
        assertEquals(404, none.statusCode(), "a table refused is answered as unknown");
        assertFalse(get(RachanaServer.DATA + "/tables", bob).body().contains("\"sales\""));
    }

    @Test
    void pagesListOnlyTheSutrasTheCallerMayOpen() throws Exception {
        String alice = signIn("alice", Fixture.PASSWORD);
        HttpResponse<String> index = get("/", alice);
        assertTrue(index.body().contains("/v/sales"));
        assertFalse(index.body().contains("/v/secret"));
        assertEquals(404, get("/v/secret", alice).statusCode());
        assertEquals(404, get("/sutras/secret.sutra.yaml", alice).statusCode());
        HttpResponse<String> page = get("/v/sales", alice);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<rachana-view src=\"/sutras/sales.sutra.yaml\" data=\"/data/v1\""));
        assertFalse(page.body().contains("<script>"), "no inline script");
        assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("script-src 'self'"));
        assertEquals("nosniff", page.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals(Fixture.SUTRA, get("/sutras/sales.sutra.yaml", alice).body());
        assertEquals("hello", get("/static/hello.txt", alice).body());
        assertEquals(400, get("/static/../users.yaml", alice).statusCode());
        assertEquals(400, get("/static/%2e%2e/users.yaml", alice).statusCode());
    }

    @Test
    void signOutNeedsTheSessionsToken() throws Exception {
        String alice = signIn("alice", Fixture.PASSWORD);
        assertEquals(403, post("/signout", alice, "application/x-www-form-urlencoded", "csrf=forged", server.url()).statusCode());
        String token = field(get("/", alice).body(), "csrf");
        assertEquals(303, post("/signout", alice, "application/x-www-form-urlencoded", "csrf=" + token, server.url()).statusCode());
        assertEquals(401, get(RachanaServer.DATA + "/tables", alice).statusCode(), "the session is gone");
    }

    @Test
    void queriesFromAnotherSiteAreRefused() throws Exception {
        String alice = signIn("alice", Fixture.PASSWORD);
        HttpResponse<String> q = post(RachanaServer.DATA + "/tables/sales/query", alice, "application/json", "{\"limit\":1}", "https://evil.example");
        assertEquals(403, q.statusCode());
        HttpResponse<String> ok = post(RachanaServer.DATA + "/tables/sales/query", alice, "application/json", "{\"limit\":1}", server.url());
        assertEquals(200, ok.statusCode(), ok.body());
    }

    @Test
    void aChangedFileArrivesOnTheChangeStream() throws Exception {
        String alice = signIn("alice", Fixture.PASSWORD);
        @SuppressWarnings("unchecked")
        Map<String, Object> page = (Map<String, Object>) Json.read(get(RachanaServer.DATA + "/tables/sales/rows", alice).body());
        Map<?, ?> v = (Map<?, ?>) page.get("version");
        HttpRequest req = HttpRequest.newBuilder(URI.create(server.url() + RachanaServer.DATA + "/changes?table=sales&after=" + v.get("epoch") + ":"
                + v.get("seq"))).header("Cookie", alice).GET().build();
        HttpResponse<InputStream> stream = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        Files.writeString(fx.dir.resolve("project/data/sales.csv"), Fixture.CSV.replace("120.00", "140.00"));
        StringBuilder got = new StringBuilder();
        try (InputStream in = stream.body()) {
            long until = System.currentTimeMillis() + 10_000;
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < until && !got.toString().contains("140.00")) {
                int n = in.read(buf);
                if (n < 0) {
                    break;
                }
                got.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        assertTrue(got.toString().contains("\"revenue\":\"140.00\""), got.toString());
        assertTrue(got.toString().contains("\"email\":{\"$masked\":true}"), "a patch is masked as the page is: " + got);
    }

    @Test
    void theProblemsPageListsASutraThatDoesNotCompile() throws Exception {
        Files.writeString(fx.dir.resolve("project/sutras/broken.sutra.yaml"), "rachana: 1\nsutra: broken\nversion: 1\npanels: 7\n");
        long until = System.currentTimeMillis() + 8_000;
        String alice = signIn("alice", Fixture.PASSWORD);
        String body = "";
        while (System.currentTimeMillis() < until && !body.contains("broken")) {
            body = get("/problems", alice).body();
            Thread.sleep(200);
        }
        assertTrue(body.contains("broken.sutra.yaml"), body);
        assertTrue(body.contains("RCH-"), body);
    }

    @Test
    void aLiveTableAnswersArrowPages() throws Exception {
        String alice = signIn("alice", Fixture.PASSWORD);
        HttpRequest req = HttpRequest.newBuilder(URI.create(server.url() + RachanaServer.DATA + "/tables/sales/rows")).header("Cookie", alice)
                .header("Accept", "application/vnd.apache.arrow.stream").GET().build();
        HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode(), new String(r.body(), StandardCharsets.UTF_8));
        assertEquals("application/vnd.apache.arrow.stream", r.headers().firstValue("Content-Type").orElse(""));
        Map<String, Object> page = com.ash.rachana.data.ArrowRows.read(new java.io.ByteArrayInputStream(r.body()));
        assertEquals(2, ((List<?>) page.get("values")).size());
    }

    @Test
    void liveYamlDeclarationsOfTheProjectAreServed() throws Exception {
        Files.writeString(fx.dir.resolve("project/sales.live.yaml"), """
                rachana: 1
                live-table: sales
                snapshot: sales
                key: [region, month]
                where: "@.region == 'North'"
                columns: [ { name: region }, { name: month, type: date }, { name: revenue, type: decimal }, { name: email } ]
                """);
        Files.writeString(fx.dir.resolve("project/orphan.live.yaml"), "rachana: 1\nlive-table: orphan\nsnapshot: nowhere\nkey: id\ncolumns: [ { name: id } ]\n");
        server.close();
        server = RachanaServer.start(ServerConfig.read(fx.config), fx.log);
        String alice = signIn("alice", Fixture.PASSWORD);
        @SuppressWarnings("unchecked")
        Map<String, Object> rows = (Map<String, Object>) Json.read(get(RachanaServer.DATA + "/tables/sales/rows?shape=objects", alice).body());
        assertEquals(1, ((List<?>) rows.get("rows")).size(), "the declaration's own condition applies");
        assertTrue(get("/problems", alice).body().contains("orphan.live.yaml"), "a declaration naming no configured table is listed");
        assertEquals(404, get(RachanaServer.DATA + "/tables/orphan/rows", alice).statusCode());
    }
}
