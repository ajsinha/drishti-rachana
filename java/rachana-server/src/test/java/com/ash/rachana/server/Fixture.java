/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A project folder for the tests: a sales Sutra over a CSV table, a users file, and a configuration around them. */
final class Fixture {

    static final String SUTRA = """
            rachana: 1.2
            sutra: sales
            version: 1
            data:
              sales: { table: sales, key: [region, month], live: true }
            panels:
              - { id: total, kind: metric, label: Revenue, value: "sum($.sales, 'revenue')", fmt: amount0 }
              - { id: rows, kind: table, title: Rows, rows: $.sales, columns: [ { label: Region, bind: "@.region" }, { label: Revenue, bind: "@.revenue" } ] }
            """;

    static final String CSV = """
            region,month,revenue,email
            North,2026-01-01,120.00,n@example.com
            South,2026-01-01,80.50,s@example.com
            """;

    final Path dir;
    final Path config;
    final ByteArrayOutputStream logs = new ByteArrayOutputStream();
    final Log log = new Log(new PrintStream(logs, true, StandardCharsets.UTF_8), null);
    static final String PASSWORD = "correct horse battery staple";

    Fixture(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir.resolve("project/sutras"));
        Files.createDirectories(dir.resolve("project/data"));
        Files.createDirectories(dir.resolve("project/static"));
        Files.writeString(dir.resolve("project/sutras/sales.sutra.yaml"), SUTRA);
        Files.writeString(dir.resolve("project/sutras/secret.sutra.yaml"), SUTRA.replace("sutra: sales", "sutra: secret"));
        Files.writeString(dir.resolve("project/data/sales.csv"), CSV);
        Files.writeString(dir.resolve("project/static/hello.txt"), "hello");
        Files.writeString(dir.resolve("users.yaml"), "users:\n"
                + "  alice: { password: \"" + Users.hash(PASSWORD.toCharArray(), 20_000) + "\", roles: [sales] }\n"
                + "  bob: { password: \"" + Users.hash(PASSWORD.toCharArray(), 20_000) + "\", roles: [] }\n");
        this.config = dir.resolve("rachana-server.yaml");
    }

    /** A configuration: users sign-in, the sales table refreshed every 200 ms, the email column unmasked for admins only. */
    Path users(String extra) throws IOException {
        Files.writeString(config, """
                rachana-server: 1
                server: { bind: 127.0.0.1, port: 0 }
                project: { dir: "${config}/project", watch: true }
                tables:
                  sales: { file: "${project}/data/sales.csv", key: [region, month], refresh: 200ms, title: Sales }
                auth: { mode: users, users: "${config}/users.yaml" }
                access:
                  tables: { sales: { read: [sales], unmask: { email: [admin] } } }
                  sutras: { secret: [admin] }
                """ + extra);
        return config;
    }

    String logText() {
        return logs.toString(StandardCharsets.UTF_8);
    }
}
