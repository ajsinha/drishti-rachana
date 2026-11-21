/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Writes through the standalone server (LIVE_TABLES 10, L10): a live table whose snapshot is a JDBC table with {@code write:} takes
 * edits from callers with an edit role, after the CSRF token, the Origin and the Host are checked; the outbox journals each change and the
 * JDBC writer applies it once (the change id in the journal table), with the version guard at the source; the edit shows when the
 * polling stream reads the source again.
 */
class ServerWriteTest {

    @TempDir
    Path tmp;
    Fixture fx;
    RachanaServer server;
    String url;
    Connection keep;
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void start() throws Exception {
        fx = new Fixture(tmp);
        url = "jdbc:h2:mem:writes" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        keep = new JdbcSource.Drivers(List.of()).driver(url).connect(url, new Properties());
        try (Statement st = keep.createStatement()) {
            st.execute("create table ORDERS (ID varchar(20) primary key, V bigint, QTY bigint, BY_USER varchar(40))");
            st.execute("create table RACHANA_CHANGES (change_id varchar(64) primary key, changed_at timestamp)");
            st.execute("insert into ORDERS values ('a', 1, 1, 'system')");
        }
        Files.writeString(tmp.resolve("project/orders.live.yaml"), """
                rachana: 1
                live-table: orders
                snapshot: orders
                stream: orders
                key: ID
                version: V
                changed-by: BY_USER
                columns: [ { name: ID }, { name: V, type: integer }, { name: QTY, type: integer }, { name: BY_USER } ]
                edit:
                  fields: [ { id: qty, bind: QTY, type: number, min: 0 } ]
                """);
        Files.writeString(fx.config, """
                rachana-server: 1
                server: { bind: 127.0.0.1, port: 0 }
                project: { dir: "${config}/project", watch: false }
                tables:
                  orders:
                    jdbc: { url: "%s" }
                    query: "select ID, V, QTY, BY_USER from ORDERS"
                    key: ID
                    refresh: 100ms
                    write: { table: ORDERS, journal-table: RACHANA_CHANGES }
                auth: { mode: users, users: "${config}/users.yaml" }
                access:
                  tables: { orders: { read: [], edit: { roles: [sales], columns: [QTY] } } }
                edits: { journal: "${config}/data/outbox.jsonl" }
                """.formatted(url));
        server = RachanaServer.start(ServerConfig.read(fx.config), fx.log);
    }

    @AfterEach
    void stop() throws Exception {
        server.close();
        keep.close();
    }

    String signIn(String user) throws Exception {
        HttpResponse<String> form = http.send(HttpRequest.newBuilder(URI.create(server.url() + "/signin")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String pre = ServerHttpTest.cookie(form, RachanaServer.SIGNIN_COOKIE);
        String body = "csrf=" + ServerHttpTest.field(form.body(), "csrf") + "&next=%2F&user=" + user + "&password="
                + java.net.URLEncoder.encode(Fixture.PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(server.url() + "/signin")).header("Cookie", pre)
                .header("Origin", server.url()).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(303, r.statusCode());
        return ServerHttpTest.cookie(r, RachanaServer.COOKIE);
    }

    String token(String cookie) throws Exception {
        return ServerHttpTest.field(http.send(HttpRequest.newBuilder(URI.create(server.url() + "/")).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body(), "csrf");
    }

    HttpResponse<String> write(String cookie, String csrf, Map<String, Object> change) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + RachanaServer.DATA + "/tables/orders/write"))
                .header("Cookie", cookie).header("Origin", server.url()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(change)));
        if (csrf != null) {
            b.header("X-Rachana-CSRF", csrf);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    long[] row() throws Exception {
        try (Statement st = keep.createStatement(); ResultSet rs = st.executeQuery("select V, QTY from ORDERS where ID = 'a'")) {
            rs.next();
            return new long[] {rs.getLong(1), rs.getLong(2)};
        }
    }

    long journalled() throws Exception {
        try (Statement st = keep.createStatement(); ResultSet rs = st.executeQuery("select count(*) from RACHANA_CHANGES")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> served(String cookie) throws Exception {
        String body = http.send(HttpRequest.newBuilder(URI.create(server.url() + RachanaServer.DATA + "/tables/orders/rows?shape=objects"))
                .header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        return (Map<String, Object>) ((List<?>) ((Map<String, Object>) Json.read(body)).get("rows")).get(0);
    }

    @Test
    void anEditorsChangeIsWrittenOnceAndArrivesOnTheStream() throws Exception {
        String alice = signIn("alice");
        String csrf = token(alice);
        Map<String, Object> c1 = Map.of("id", "c1", "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 5));
        HttpResponse<String> r = write(alice, csrf, c1);
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"state\":\"queued\""), r.body());
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until && !Long.valueOf(2).equals(served(alice).get("V"))) {
            Thread.sleep(50);
        }
        assertEquals(Map.of("ID", "a", "V", 2L, "QTY", 5L, "BY_USER", "alice"), served(alice));
        HttpResponse<String> again = write(alice, csrf, c1);                     // the same change: its first answer, never a second write
        assertTrue(again.body().contains("\"state\":\"sent\""), again.body());
        assertEquals(2, row()[0]);
        assertEquals(1, journalled());
        HttpResponse<String> stale = write(alice, csrf, Map.of("id", "c2", "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 6)));
        assertEquals(409, stale.statusCode(), stale.body());
        assertTrue(stale.body().contains("\"by\":\"alice\""), stale.body());
        assertTrue(Files.readString(tmp.resolve("data/outbox.jsonl")).contains("\"id\":\"c1\""), "journalled");
        assertTrue(fx.logText().contains("\"event\":\"write-submitted\""));
    }

    @Test
    void aWriteNeedsTheTokenTheRoleAndThisServersName() throws Exception {
        String alice = signIn("alice");
        Map<String, Object> c = Map.of("id", "c1", "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 5));
        assertEquals(403, write(alice, null, c).statusCode(), "no CSRF token");
        assertEquals(403, write(alice, "forged", c).statusCode(), "a wrong token");
        String bob = signIn("bob");
        HttpResponse<String> r = write(bob, token(bob), c);
        assertEquals(403, r.statusCode(), "a reader without the edit role");
        assertTrue(r.body().contains("rdp.write.forbidden"), r.body());
        String schema = http.send(HttpRequest.newBuilder(URI.create(server.url() + RachanaServer.DATA + "/tables/orders")).header("Cookie", bob)
                .GET().build(), HttpResponse.BodyHandlers.ofString()).body();
        assertTrue(schema.contains("\"write\"") && !schema.contains("\"columns\":{\"QTY\""), "can: write, but no write columns for bob: " + schema);
        assertEquals(403, rawWrite(alice, token(alice), "evil.example.com:" + server.port()), "another host name (DNS rebinding)");
        assertEquals(200, rawWrite(alice, token(alice), "127.0.0.1:" + server.port()));
    }

    /** A write with a Host header the client sets itself (the JDK client will not). */
    int rawWrite(String cookie, String csrf, String host) throws Exception {
        String body = Json.write(Map.of("id", "h" + host.length(), "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 3)));
        try (Socket s = new Socket("127.0.0.1", server.port())) {
            OutputStream out = s.getOutputStream();
            out.write(("POST " + RachanaServer.DATA + "/tables/orders/write HTTP/1.1\r\nHost: " + host + "\r\nCookie: " + cookie
                    + "\r\nX-Rachana-CSRF: " + csrf + "\r\nContent-Type: application/json\r\nContent-Length: "
                    + body.getBytes(StandardCharsets.UTF_8).length + "\r\nConnection: close\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = s.getInputStream();
            String status = new String(in.readNBytes(12), StandardCharsets.US_ASCII);
            return Integer.parseInt(status.substring(9, 12));
        }
    }
}
