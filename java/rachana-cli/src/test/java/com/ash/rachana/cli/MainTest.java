/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The library commands end to end through the launcher: build, pack, verify, lock; usage and failure exit codes. */
class MainTest {
    static final String MANIFEST = """
            rachana: 1
            library: demo
            version: 1.2.0
            title: Demo
            summary: A tiny synthetic library for the tests.
            publisher: { name: Tests, key: ~ }
            licence: LicenseRef-Rachana-Test
            scope: public
            profiles: []
            depends: []
            """;
    static final String FIGURE = """
            rachana: 1.2
            fragment: figure
            version: 1
            doc: { summary: One figure. }
            params:
              value: { type: number, required: true, doc: The figure }
            panels:
              - { id: figure, kind: metric, label: Value, value: arg.value }
            """;

    @Test
    void buildPackVerifyLock(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("demo");
        Files.createDirectories(dir.resolve("fragments"));
        Files.writeString(dir.resolve("demo.rachana-lib.yaml"), MANIFEST);
        Files.writeString(dir.resolve("fragments/figure.v1.fragment.yaml"), FIGURE);
        Files.writeString(dir.resolve("LICENSE"), "Tests only.\n");
        assertEquals(0, Main.run(new String[] {"lib", "build", dir.toString()}));
        assertEquals(0, Main.run(new String[] {"lib", "verify", dir.toString()}));
        assertEquals(1, Main.run(new String[] {"lib", "verify", dir.toString(), "--require-signed"}), "unsigned is refused when required");
        assertEquals(0, Main.run(new String[] {"lib", "pack", dir.toString(), "--out", tmp.resolve("out").toString()}));
        Path rlib = tmp.resolve("out/demo-1.2.0.rlib");
        assertTrue(Files.exists(rlib));
        Path lock = tmp.resolve("rachana.lock");
        assertEquals(0, Main.run(new String[] {"lib", "lock", rlib.toString(), "--lock", lock.toString()}));
        assertTrue(Files.readString(lock).contains("demo"));
    }

    static final String CONTRACT = """
            { "contract": "desk-amounts", "title": "Amounts by desk", "grain": "one row per desk", "key": ["desk"],
              "columns": [ { "name": "desk", "type": "string", "doc": "The desk" },
                           { "name": "amount", "type": "decimal", "doc": "The amount" },
                           { "name": "note", "type": "string", "optional": true, "doc": "A remark" } ] }
            """;
    static final String BARS = """
            rachana: 1
            fragment: bars
            version: 1
            doc: { summary: Amounts by desk. }
            params:
              rows: { type: table, contract: desk-amounts, required: true, doc: One row per desk }
            panels:
              - { id: bars, kind: hbar, rows: arg.rows, label: desk, value: amount }
            """;

    /** A built library with one contract and the fragment that names it. */
    static Path contractLibrary(Path tmp) throws Exception {
        Path dir = tmp.resolve("demo");
        Files.createDirectories(dir.resolve("fragments"));
        Files.createDirectories(dir.resolve("contracts"));
        Files.writeString(dir.resolve("demo.rachana-lib.yaml"), MANIFEST);
        Files.writeString(dir.resolve("fragments/bars.v1.fragment.yaml"), BARS);
        Files.writeString(dir.resolve("contracts/desk-amounts.json"), CONTRACT);
        Files.writeString(dir.resolve("LICENSE"), "Tests only.\n");
        assertEquals(0, Main.run(new String[] {"lib", "build", dir.toString()}));
        return dir;
    }

    /** The exit status, a line break, then what the command printed. */
    static String out(String... args) {
        java.io.PrintStream was = System.out;
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(b, true, java.nio.charset.StandardCharsets.UTF_8));
        try {
            int code = Main.run(args);
            return code + "\n" + b.toString(java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            System.setOut(was);
        }
    }

    @Test
    void contractsListTheTablesAHostServes(@TempDir Path tmp) throws Exception {
        Path dir = contractLibrary(tmp);
        String text = out("lib", "contracts", dir.toString());
        assertTrue(text.startsWith("0\ndesk-amounts  Amounts by desk (one row per desk; key desk)  used by bars@1\n"), text);
        assertTrue(text.contains("  amount                   decimal    required  The amount"), text);
        assertTrue(text.contains("  note                     string     optional  A remark"), text);
        assertTrue(text.endsWith("1 contract\n"), text);
        String json = out("lib", "contracts", dir.toString(), "--json");
        assertTrue(json.contains("\"contract\":\"desk-amounts\"") && json.contains("\"usedBy\":[\"bars@1\"]"), json);
    }

    @Test
    void checkHostReportsTheFitColumnByColumn(@TempDir Path tmp) throws Exception {
        Path dir = contractLibrary(tmp);
        java.util.Map<String, String> schemas = java.util.Map.of(
                "/rdp/v1/tables/desk-amounts", "{\"name\":\"desk-amounts\",\"key\":[\"desk\"],\"columns\":[{\"name\":\"desk\",\"type\":\"string\"},"
                        + "{\"name\":\"amount\",\"type\":\"integer\"}]}",
                "/rdp/v1/tables/positions", "{\"name\":\"positions\",\"key\":[\"desk\"],\"columns\":[{\"name\":\"desk\",\"type\":\"string\"},"
                        + "{\"name\":\"amount\",\"type\":\"date\"}]}");
        try (Host s = new Host(schemas)) {
            String fits = out("lib", "check-host", dir.toString(), "--base", s.url + "/rdp/v1");
            assertTrue(fits.startsWith("0\n"), fits);
            assertTrue(fits.contains("absent ") && fits.endsWith("every contract fits\n"), fits);
            String type = out("lib", "check-host", dir.toString(), "--base", s.url + "/rdp/v1/", "--table", "desk-amounts=positions", "--json");
            assertTrue(type.startsWith("1\n") && type.contains("\"column\":\"amount\",\"needed\":\"decimal\",\"found\":\"date\",\"status\":\"type\""), type);
            String none = out("lib", "check-host", dir.toString(), "--base", s.url + "/rdp/v1", "--table", "desk-amounts=nope");
            assertTrue(none.startsWith("1\n") && none.contains("no-table"), none);
        }
        assertEquals(2, Main.run(new String[] {"lib", "check-host", dir.toString()}), "--base is required");
        assertEquals(2, Main.run(new String[] {"lib", "check-host", dir.toString(), "--base", "http://127.0.0.1:9", "--table", "x"}));
        assertEquals(1, Main.run(new String[] {"lib", "check-host", dir.toString(), "--base", "http://127.0.0.1:9"}), "no host answers");
    }

    @Test
    void usageAndFailures(@TempDir Path tmp) {
        assertEquals(2, Main.run(new String[] {}));
        assertEquals(2, Main.run(new String[] {"lib", "nope"}));
        assertEquals(2, Main.run(new String[] {"lib", "build"}));
        assertEquals(1, Main.run(new String[] {"lib", "verify", tmp.resolve("missing").toString()}));
    }

    /** A Data Protocol host reduced to its schema answers ({@code GET <path>}: the JSON, else 404), on a loopback port; java.base only. */
    static final class Host implements AutoCloseable {
        final java.net.ServerSocket socket;
        final String url;

        Host(java.util.Map<String, String> answers) throws java.io.IOException {
            socket = new java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
            url = "http://127.0.0.1:" + socket.getLocalPort();
            Thread t = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (java.net.Socket c = socket.accept()) {
                        var in = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                        String path = in.readLine().split(" ")[1];
                        for (String l = in.readLine(); l != null && !l.isEmpty(); l = in.readLine()) {
                            // the headers
                        }
                        String body = answers.getOrDefault(path, "{\"error\":{\"code\":\"rdp.table.unknown\"}}");
                        byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        String head = "HTTP/1.1 " + (answers.containsKey(path) ? "200 OK" : "404 Not Found") + "\r\nContent-Type: application/json\r\n"
                                + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
                        c.getOutputStream().write(head.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        c.getOutputStream().write(b);
                    } catch (java.io.IOException | RuntimeException e) {
                        // closed, or a client gone
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void close() throws java.io.IOException {
            socket.close();
        }
    }
}
