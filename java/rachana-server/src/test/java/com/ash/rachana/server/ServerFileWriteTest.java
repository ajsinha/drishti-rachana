/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ash.rachana.parse.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Edits of a file table through the standalone server (LIVE_TABLES 10): a CSV with {@code write: {journal}} takes an editor's change, the
 * file is replaced once and the change shows when the file is read again; with {@code edits.hold} the change waits in the tray and an
 * approver ({@code access.tables.<name>.approve}) releases it there, never their own. Configurations a file writer cannot serve are refused.
 */
class ServerFileWriteTest {

    @TempDir
    Path tmp;
    Fixture fx;
    RachanaServer server;
    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

    static final String DECL = """
            rachana: 1
            live-table: orders
            snapshot: orders
            stream: orders
            key: ID
            version: V
            changed-by: BY_USER
            tray: true
            columns: [ { name: ID }, { name: V, type: integer }, { name: QTY, type: integer }, { name: BY_USER } ]
            edit:
              fields: [ { id: qty, bind: QTY, type: number, min: 0 } ]
            """;

    void start(String edits, String approve) throws Exception {
        fx = new Fixture(tmp);
        Files.writeString(tmp.resolve("project/data/orders.csv"), "ID,V,QTY,BY_USER\na,1,1,system\n");
        Files.writeString(tmp.resolve("project/orders.live.yaml"), DECL);
        Files.writeString(tmp.resolve("users.yaml"), Files.readString(tmp.resolve("users.yaml"))
                + "  carol: { password: \"" + Users.hash(Fixture.PASSWORD.toCharArray(), 20_000) + "\", roles: [sales, checker] }\n");
        Files.writeString(fx.config, """
                rachana-server: 1
                server: { bind: 127.0.0.1, port: 0 }
                project: { dir: "${config}/project", watch: false }
                tables:
                  orders:
                    file: "${project}/data/orders.csv"
                    key: ID
                    refresh: 100ms
                    write: { journal: "${config}/data/orders.applied.jsonl" }
                auth: { mode: users, users: "${config}/users.yaml" }
                access:
                  tables: { orders: { read: [], edit: { roles: [sales] }%s } }
                edits: { journal: "${config}/data/outbox.jsonl"%s }
                """.formatted(approve, edits));
        server = RachanaServer.start(ServerConfig.read(fx.config), fx.log);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    ServerWriteTest client() {
        ServerWriteTest c = new ServerWriteTest();
        c.server = server;
        return c;
    }

    HttpResponse<String> post(String cookie, String csrf, String path, Object body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(server.url() + RachanaServer.DATA + path)).header("Cookie", cookie)
                .header("Origin", server.url()).header("Content-Type", "application/json").header("X-Rachana-CSRF", csrf)
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    String csv() throws Exception {
        return Files.readString(tmp.resolve("project/data/orders.csv"));
    }

    void waitFor(String text) throws Exception {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until && !csv().contains(text)) {
            Thread.sleep(50);
        }
        assertTrue(csv().contains(text), csv());
    }

    @Test
    void anEditorsChangeReplacesTheFileOnceAndShows() throws Exception {
        start("", "");
        ServerWriteTest c = client();
        String alice = c.signIn("alice");
        String csrf = c.token(alice);
        Map<String, Object> c1 = Map.of("id", "c1", "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 5));
        HttpResponse<String> r = post(alice, csrf, "/tables/orders/write", c1);
        assertEquals(200, r.statusCode(), r.body());
        waitFor("a,2,5,alice");
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until && !Long.valueOf(2).equals(c.served(alice).get("V"))) {
            Thread.sleep(50);
        }
        assertEquals(Map.of("ID", "a", "V", 2L, "QTY", 5L, "BY_USER", "alice"), c.served(alice));
        assertTrue(post(alice, csrf, "/tables/orders/write", c1).body().contains("\"state\":\"sent\""), "the same change: its first answer");
        assertEquals("ID,V,QTY,BY_USER\na,2,5,alice\n", csv());
        String journal = Files.readString(tmp.resolve("data/orders.applied.jsonl"));
        assertEquals(1, journal.split("\"state\":\"applied\"", -1).length - 1, journal);
    }

    @Test
    void aHeldChangeWaitsForAnotherApprover() throws Exception {
        start(", hold: [orders]", ", approve: [checker]");
        ServerWriteTest c = client();
        String alice = c.signIn("alice");
        HttpResponse<String> r = post(alice, c.token(alice), "/tables/orders/write",
                Map.of("id", "c1", "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 7)));
        assertTrue(r.body().contains("\"state\":\"held\""), r.body());
        String carol = c.signIn("carol");
        String csrf = c.token(carol);
        String tray = post(carol, csrf, "/tables/orders.changes/query", Map.of("columns", List.of("change", "state", "by", "summary"))).body();
        assertTrue(tray.contains("[\"c1\",\"held\",\"alice\",\"a: QTY = 7\"]"), tray);
        HttpResponse<String> own = post(carol, csrf, "/tables/orders/write", Map.of("id", "c2", "key", List.of("a"), "basedOn", 1, "set", Map.of("QTY", 8)));
        assertTrue(own.body().contains("\"state\":\"held\""), own.body());
        HttpResponse<String> self = post(carol, csrf, "/tables/orders.changes/write",
                Map.of("id", "d0", "key", List.of("c2"), "basedOn", "held", "set", Map.of("state", "released")));
        assertEquals(403, self.statusCode(), self.body());
        HttpResponse<String> bob = post(alice, c.token(alice), "/tables/orders.changes/write",
                Map.of("id", "d1", "key", List.of("c1"), "basedOn", "held", "set", Map.of("state", "released")));
        assertEquals(403, bob.statusCode(), "an editor without the approve right: " + bob.body());
        HttpResponse<String> ok = post(carol, csrf, "/tables/orders.changes/write",
                Map.of("id", "d2", "key", List.of("c1"), "basedOn", "held", "set", Map.of("state", "released")));
        assertEquals(200, ok.statusCode(), ok.body());
        waitFor("a,2,7,alice");
        assertTrue(fx.logText().contains("\"event\":\"write-released\""), fx.logText());
    }

    @Test
    void whatAFileWriterCannotServeIsRefused() throws Exception {
        fx = new Fixture(tmp);
        Files.writeString(tmp.resolve("project/data/prices.arrow"), "");
        Files.writeString(fx.config, """
                rachana-server: 1
                server: { bind: 127.0.0.1, port: 0 }
                project: { dir: "${config}/project", watch: false }
                tables:
                  prices: { file: "${project}/data/prices.arrow", key: ID, write: { journal: "${config}/data/p.jsonl" } }
                  sales: { file: "${project}/data/sales.csv", key: [region, month], write: { } }
                auth: { mode: users, users: "${config}/users.yaml" }
                access: { tables: { sales: { read: [] } } }
                edits: { journal: "${config}/data/outbox.jsonl", hold: [sales] }
                """);
        ServerConfig.Invalid bad = assertThrows(ServerConfig.Invalid.class, () -> ServerConfig.read(fx.config));
        String all = String.join("\n", bad.problems());
        assertTrue(all.contains("tables.prices.write: only a .csv, .json, .jsonl or .ndjson file is written to"), all);
        assertTrue(all.contains("tables.sales.write.journal: the file recording each applied change id"), all);
        assertTrue(all.contains("edits.hold: sales's changes wait for an approver, and access.tables.sales.approve names none"), all);
    }
}
