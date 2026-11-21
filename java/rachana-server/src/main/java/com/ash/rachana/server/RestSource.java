/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import com.ash.rachana.parse.Json;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A table from a REST address answering JSON: a {@code GET} with the configured headers (secret ones from the environment), the rows at
 * {@code path} ({@code $.items}; the whole answer, or its {@code rows}, without one). Redirects are not followed; an answer larger than
 * 64 MB, a status other than 200 or a body that is not JSON is a failed read (the table pauses, the last rows stay).
 */
final class RestSource extends TableSource {

    static final int MAX_BYTES = 64 * 1024 * 1024;

    private final HttpClient http;

    RestSource(ServerConfig.Table t) {
        super(t);
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(Math.max(1000, t.timeoutMs()))).build();
    }

    @Override
    List<Map<String, Object>> load() throws Exception {
        ServerConfig.Rest r = table.rest();
        HttpRequest.Builder b = HttpRequest.newBuilder(r.url()).timeout(Duration.ofMillis(Math.max(1000, table.timeoutMs())))
                .header("Accept", "application/json").GET();
        r.headers().forEach(b::header);
        for (Map.Entry<String, String> e : r.headerEnv().entrySet()) {
            String v = System.getenv(e.getValue());
            if (v == null) {
                throw new ReadFailed(table.name() + ": the environment variable " + e.getValue() + " (header " + e.getKey() + ") is not set");
            }
            b.header(e.getKey(), v);
        }
        HttpResponse<InputStream> res;
        try {
            res = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ReadFailed(table.name() + ": the address could not be read (" + e.getClass().getSimpleName() + ")");
        }
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) {
                throw new ReadFailed(table.name() + ": the address answered " + res.statusCode());
            }
            byte[] body = in.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) {
                throw new ReadFailed(table.name() + ": the answer is larger than " + MAX_BYTES + " bytes");
            }
            Object doc;
            try {
                doc = Json.read(new String(body, StandardCharsets.UTF_8));
            } catch (Json.JsonException e) {
                throw new ReadFailed(table.name() + ": the answer is not JSON");
            }
            return fromJson(doc, r.path(), table.name());
        }
    }
}
