/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The configuration's checks, password hashes, sessions and the throttle, row shaping, rights and log redaction. */
class ServerPartsTest {

    @TempDir
    Path tmp;

    static final String MINIMAL = "rachana-server: 1\nproject: { dir: \"${config}\" }\n";

    ServerConfig.Invalid invalid(String yaml) {
        return assertThrows(ServerConfig.Invalid.class, () -> ServerConfig.parse(yaml, tmp.resolve("rachana-server.yaml")));
    }

    static boolean has(ServerConfig.Invalid e, String text) {
        return e.problems().stream().anyMatch(p -> p.contains(text));
    }

    @Test
    void aMinimalConfigurationListensOnLoopbackWithNoSignIn() throws Exception {
        ServerConfig c = ServerConfig.parse(MINIMAL, tmp.resolve("rachana-server.yaml"));
        assertEquals("127.0.0.1", c.http().bind());
        assertEquals(8080, c.http().port());
        assertEquals(ServerConfig.AuthMode.NONE, c.auth().mode());
        assertEquals(tmp.toAbsolutePath().normalize(), c.project().dir());
    }

    @Test
    void theDocumentedExampleIsAValidConfiguration() throws Exception {
        Path example = Path.of(System.getProperty("rachana.repo.root"), "java/rachana-server/rachana-server.example.yaml");
        Files.createDirectories(tmp.resolve("project/data"));
        Files.writeString(tmp.resolve("project/data/sales.csv"), "region,month,revenue\n");
        Files.writeString(tmp.resolve("users.yaml"), "users: {}\n");
        Files.writeString(tmp.resolve("rachana.runtime.js"), "");
        ServerConfig c = ServerConfig.parse(Files.readString(example), tmp.resolve("rachana-server.yaml"));
        assertEquals(List.of("sales", "orders", "quotes"), List.copyOf(c.tables().keySet()));
        assertEquals(ServerConfig.AuthMode.USERS, c.auth().mode());
        assertTrue(c.tables().get("orders").jdbc().readOnly());
        assertFalse(c.describe().toString().contains("ORDERS_DB_PASSWORD") && c.describe().toString().contains("password="));
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        ServerConfig.Invalid e = invalid("rachana-server: 1\nproject: { dir: data, colour: red }\nsurprise: 1\n"
                + "server: { bind: 0.0.0.0 }\n");
        assertTrue(has(e, "project.dir: 'data' is a bare relative path"), e.problems().toString());
        assertTrue(has(e, "project.colour: unknown key"));
        assertTrue(has(e, "surprise: unknown key"));
        assertTrue(has(e, "auth.mode: none is allowed only on a loopback address"));
        assertTrue(has(invalid("project: { dir: \"${config}\" }\n"), "rachana-server: this file starts with"));
        assertTrue(has(invalid(MINIMAL + "libraries: [ \"${home}/libs\" ]\n"), "the only anchors are ${config} and ${project}"));
    }

    @Test
    void aJdbcTableIsOneSelectWithBoundParametersAndNoPasswordInTheFile() {
        String base = MINIMAL + "tables:\n  t:\n    key: id\n    jdbc: { url: \"jdbc:h2:mem:x;PASSWORD=hunter2\" }\n";
        assertTrue(has(invalid(base + "    query: \"select * from t\"\n"), "no password in the address"));
        String ok = MINIMAL + "tables:\n  t:\n    key: id\n    jdbc: { url: \"jdbc:h2:mem:x\" }\n";
        assertTrue(has(invalid(ok + "    query: \"delete from t\"\n"), "a single select"));
        assertTrue(has(invalid(ok + "    query: \"select 1; drop table t\"\n"), "one statement only"));
        assertTrue(has(invalid(ok + "    query: \"select * from t where a = :a\"\n"), ":a has no value in params"));
        assertTrue(has(invalid(ok + "    query: \"select * from t\"\n    params: { b: 1 }\n"), "params.b is not used"));
        assertTrue(has(invalid(MINIMAL + "tables:\n  t: { key: id, rest: { url: \"https://x.example/a\", headers: { Authorization: abc } } }\n"),
                "a secret header is named by an environment variable"));
        assertTrue(has(invalid(MINIMAL + "tables:\n  t: { rest: { url: \"ftp://x.example/a\" } }\n"), "an http or https address"));
        assertTrue(has(invalid(MINIMAL + "tables:\n  t: { rest: { url: \"https://x.example/a\" } }\n"), "a table needs its key"));
    }

    @Test
    void namedParametersBecomePlaceholdersOutsideQuotes() {
        JdbcSource.Prepared p = JdbcSource.prepare("select a::text, ':skip' as s from t where r = :region and m >= :since -- :not\n");
        assertEquals("select a::text, ':skip' as s from t where r = ? and m >= ?", p.sql());
        assertEquals(List.of("region", "since"), p.names());
    }

    @Test
    void passwordsAreSaltedHashesComparedByKey() throws Exception {
        String h1 = Users.hash("pw".toCharArray(), 20_000);
        String h2 = Users.hash("pw".toCharArray(), 20_000);
        assertFalse(h1.equals(h2), "salted");
        assertTrue(Users.matches("pw".toCharArray(), h1));
        assertFalse(Users.matches("pW".toCharArray(), h1));
        assertFalse(Users.matches("pw".toCharArray(), "plain"));
        Path f = tmp.resolve("users.yaml");
        Files.writeString(f, "users:\n  ann: { password: \"" + h1 + "\", roles: [a, b] }\n");
        Users u = new Users(f);
        assertEquals(Set.of("a", "b"), u.check("ann", "pw".toCharArray()).roles());
        assertNull(u.check("ann", "nope".toCharArray()));
        assertNull(u.check("zed", "pw".toCharArray()));
        Files.writeString(f, "users:\n  ann: { password: plain-text }\n");
        assertThrows(ServerConfig.Invalid.class, () -> new Users(f));
    }

    static final class TestClock extends Clock {
        long ms = Instant.parse("2026-11-21T12:00:00Z").toEpochMilli();

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId z) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(ms);
        }
    }

    @Test
    void sessionsExpireAndSignInIsThrottled() {
        TestClock clock = new TestClock();
        Sessions s = new Sessions(60_000, 3_600_000, clock);
        Sessions.Session a = s.open(new Users.Principal("ann", Set.of()));
        assertNotNull(s.get(a.id()));
        clock.ms += 59_000;
        assertNotNull(s.get(a.id()), "seen again: idle clock reset");
        clock.ms += 61_000;
        assertNull(s.get(a.id()), "idle too long");
        for (int i = 0; i < Sessions.FREE_FAILURES; i++) {
            s.failed("10.0.0.1");
        }
        assertEquals(0, s.waitMs("10.0.0.1"));
        s.failed("10.0.0.1");
        assertEquals(1000, s.waitMs("10.0.0.1"));
        s.failed("10.0.0.1");
        assertEquals(2000, s.waitMs("10.0.0.1"));
        s.succeeded("10.0.0.1");
        assertEquals(0, s.waitMs("10.0.0.1"));
        assertTrue(Sessions.same("abc", "abc"));
        assertFalse(Sessions.same("abc", null));
    }

    @Test
    void csvRowsAreTypedAndShaped() {
        List<Map<String, Object>> rows = Rows.csv("\uFEFFa,b,c,d\r\n1,\"x, \"\"y\"\"\",2.50,2026-01-02\n2,\"multi\nline\",3,\n");
        assertEquals(2, rows.size());
        assertEquals("x, \"y\"", rows.get(0).get("b"));
        assertEquals("multi\nline", rows.get(1).get("b"));
        assertEquals("integer", Rows.infer(rows, "a"));
        assertEquals("string", Rows.infer(rows, "b"));
        assertEquals("decimal", Rows.infer(rows, "c"));
        assertEquals("date", Rows.infer(rows, "d"));
        Map<String, Object> shaped = Rows.shape(rows, Map.of("a", "integer", "c", "decimal", "d", "date")).get(0);
        assertEquals(1L, shaped.get("a"));
        assertEquals("2.50", shaped.get("c"));
        assertEquals("2026-01-02", shaped.get("d"));
        assertNull(Rows.shape(rows, Map.of("d", "date")).get(1).get("d"));
    }

    @Test
    void accessIsFailClosedAndMasksByRole() throws Exception {
        ServerConfig.Access cfg = new ServerConfig.Access(Map.of("t", new ServerConfig.TableAccess(List.of("sales"), Map.of("email", List.of("admin")))),
                Map.of("s", List.of("admin")), false);
        Access a = new Access(cfg);
        Users.Principal sales = new Users.Principal("ann", Set.of("sales"));
        Users.Principal admin = new Users.Principal("root", Set.of("sales", "admin"));
        assertThrows(com.ash.rachana.live.spi.Authoriser.Refusal.class, () -> a.view(null, "t"));
        assertThrows(com.ash.rachana.live.spi.Authoriser.Refusal.class, () -> a.view(new Users.Principal("x", Set.of()), "t"));
        assertThrows(com.ash.rachana.live.spi.Authoriser.Refusal.class, () -> a.view(admin, "other"), "closed default");
        assertEquals(Set.of("email"), a.view(sales, "t").maskedColumns());
        assertEquals(Set.of(), a.view(admin, "t").maskedColumns());
        assertFalse(a.sutra(sales, "s"));
        assertTrue(a.sutra(admin, "s"));
        assertFalse(a.sutra(admin, "unlisted"), "closed default");
    }

    @Test
    void theLogNeverWritesASecretField() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Log log = new Log(new PrintStream(out, true, StandardCharsets.UTF_8), null);
        log.info("x", "password", "hunter2", "sessionId", "abc", "user", "ann");
        String line = out.toString(StandardCharsets.UTF_8);
        assertFalse(line.contains("hunter2") || line.contains("\"abc\""), line);
        assertTrue(line.contains("\"user\":\"ann\""));
    }

    @Test
    void theReturnAddressIsAlwaysLocal() {
        assertEquals("/v/a", RachanaServer.local("/v/a"));
        assertEquals("/", RachanaServer.local("//evil.example/"));
        assertEquals("/", RachanaServer.local("https://evil.example/"));
        assertEquals("/", RachanaServer.local("/\\evil.example"));
        assertEquals("/", RachanaServer.local("/x\r\nSet-Cookie: a=b"));
        assertEquals("/", RachanaServer.local(null));
    }
}
