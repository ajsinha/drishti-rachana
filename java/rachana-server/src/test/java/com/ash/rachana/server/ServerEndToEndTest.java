/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The server end to end in Chromium: started on a free port, a reader signs in through the form, opens a Sutra's page, the view paints
 * from the Data Protocol, the table's file changes and the change arrives live, with no console error (the Content-Security-Policy
 * included). Runs Playwright through {@code uv run --with playwright==1.64.0}; skipped only when {@code uv} is not installed.
 */
class ServerEndToEndTest {

    @TempDir
    Path tmp;

    static Path uv() {
        for (String d : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            Path p = Path.of(d, "uv");
            if (Files.isExecutable(p)) {
                return p;
            }
        }
        return null;
    }

    @Test
    void aReaderSignsInSeesTheViewPaintAndALiveChangeArrive() throws Exception {
        Path uv = uv();
        org.junit.jupiter.api.Assertions.assertNotNull(uv, "uv is a prerequisite of the build (it runs the browser lane); nothing is skipped");
        Path script = Path.of(System.getProperty("rachana.repo.root"), "java/rachana-server/src/test/python/server_paint.py").normalize();
        Fixture fx = new Fixture(tmp);
        try (RachanaServer server = RachanaServer.start(ServerConfig.read(fx.users("")), fx.log)) {
            Process p = new ProcessBuilder(uv.toString(), "run", "-q", "--with", "playwright==1.64.0", "python", script.toString(), server.url(),
                    "alice", Fixture.PASSWORD, "Westfield").redirectErrorStream(true).start();
            List<String> out = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line = r.readLine(); line != null; line = r.readLine()) {
                    out.add(line);
                    if (line.startsWith("PAINTED")) {
                        Files.writeString(fx.dir.resolve("project/data/sales.csv"), Fixture.CSV.replace("North", "Westfield"));
                    }
                }
            }
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "the browser check did not finish");
            String all = String.join("\n", out);
            assertEquals(0, p.exitValue(), all + "\n--- server log ---\n" + fx.logText());
            assertTrue(all.contains("PAINTED") && all.contains("LIVE") && all.contains("OK"), all);
        }
    }
}
