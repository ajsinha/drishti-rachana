/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The page assets, from the jar (copied there from the repository at build time; never from a CDN): {@code <rachana-view>} and its
 * modules ({@code rachana/src/}), the runtime bundle ({@code rachana/rachana.runtime.js}, or the configured {@code assets.runtime}) and the
 * vendored Drishti Elements pack ({@code vendor/drishti-elements/<version>/}). The per-library stylesheets the Elements loader asks for by
 * address are answered with a stub naming where their rules are (the pack's {@code drishti-elements.css} holds them), as a built site does.
 * Also the content types of every file the server sends.
 */
final class Assets {

    /** One file to send. */
    record Asset(byte[] bytes, String type) {}

    static final String ROOT = "rachana-server/assets/";
    static final String STUB_CSS = "/* the rules of this sheet are in the pack's drishti-elements.css */\n";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_+@.-]+(/[A-Za-z0-9_+@.-]+)*");
    private static final Map<String, String> TYPES = Map.ofEntries(Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"), Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"), Map.entry("json", "application/json"), Map.entry("yaml", "text/plain; charset=utf-8"),
            Map.entry("yml", "text/plain; charset=utf-8"), Map.entry("svg", "image/svg+xml"), Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"), Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"), Map.entry("woff2", "font/woff2"), Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("md", "text/plain; charset=utf-8"), Map.entry("csv", "text/csv; charset=utf-8"), Map.entry("wasm", "application/wasm"));

    private final String version;
    private final String elements;
    private final Path runtime;
    private final Set<String> stubs;
    private final Map<String, Asset> cache = new ConcurrentHashMap<>();

    Assets(Path runtime) {
        Properties p = new Properties();
        try (InputStream in = Assets.class.getClassLoader().getResourceAsStream("rachana-server/server.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.version = p.getProperty("version", "dev");
        this.elements = p.getProperty("elements", "");
        this.runtime = runtime;
        Set<String> s = new TreeSet<>();
        byte[] js = resource(elementsDir() + "/drishti-elements.js");
        if (js != null) {
            Matcher m = Pattern.compile("\"((?:css|elements)/[\\w./-]+\\.css)\"").matcher(new String(js, StandardCharsets.UTF_8));
            while (m.find()) {
                s.add(elementsDir() + "/" + m.group(1));
            }
        }
        this.stubs = Set.copyOf(s);
    }

    String version() {
        return version;
    }

    /** The Elements pack's folder below the assets: {@code vendor/drishti-elements/<version>}. */
    String elementsDir() {
        return "vendor/drishti-elements/" + elements;
    }

    /** Whether the runtime bundle can be served (the jar's, or the configured file). */
    boolean hasRuntime() {
        return runtime != null ? Files.isRegularFile(runtime) : resource("rachana/rachana.runtime.js") != null;
    }

    /** Whether the Elements pack is in the jar. */
    boolean hasElements() {
        return !elements.isEmpty() && resource(elementsDir() + "/drishti-elements.js") != null;
    }

    /** An asset by its path below {@code /assets/}, or empty. */
    Optional<Asset> get(String path) {
        if (path == null || !SAFE.matcher(path).matches() || path.contains("..")) {
            return Optional.empty();
        }
        if (stubs.contains(path)) {
            return Optional.of(new Asset(STUB_CSS.getBytes(StandardCharsets.UTF_8), type(path)));
        }
        if (path.equals("rachana/rachana.runtime.js") && runtime != null) {
            try {
                return Optional.of(new Asset(Files.readAllBytes(runtime), type(path)));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        Asset a = cache.get(path);
        if (a == null) {
            byte[] b = resource(path);
            if (b == null) {
                return Optional.empty();
            }
            a = new Asset(b, type(path));
            cache.put(path, a);
        }
        return Optional.of(a);
    }

    private static byte[] resource(String path) {
        try (InputStream in = Assets.class.getClassLoader().getResourceAsStream(ROOT + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** The content type of a file name (by its extension; {@code application/octet-stream} for others). */
    static String type(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "application/octet-stream" : TYPES.getOrDefault(name.substring(dot + 1).toLowerCase(Locale.ROOT), "application/octet-stream");
    }
}
