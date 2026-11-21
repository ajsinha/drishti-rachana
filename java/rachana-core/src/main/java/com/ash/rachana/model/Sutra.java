/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.model;

import java.util.List;
import java.util.Map;

/**
 * A parsed, validated layout definition. Immutable; identified by {@code name@version}.
 *
 * @param name the Sutra name, for example {@code irs-vanilla}
 * @param version positive integer; old versions stay loadable
 * @param title the title line
 * @param strip header key figures (at most eight)
 * @param panels the panels in declaration order
 * @param keys function key to action ({@code link(...)}, {@code raw}, {@code impact}, or a panel id)
 * @param location the file it came from
 * @param description the author's one-paragraph plain text (never evaluated), or null
 * @param controls the controls the reader sets
 * @param layout the screen's grid, areas, split, tabs and sections (RACHANA_REFERENCE.md 13), or null: the panels flow
 * @param phone the screen's {@code phone:} block (docs/guides/BUILD_GUIDE.md section 5.2: the phone order, the default tier, the summary line), or
 *     null
 * @param extensions what the instance's profiles' top-level keys read, by key (split step A7: {@code domain} and {@code match} are the
 *     entity additions' keys, {@link com.ash.rachana.profile.Entities}); empty for Core alone
 */
public record Sutra(
        String name,
        int version,
        Title title,
        List<StripItem> strip,
        List<Panel> panels,
        Map<String, String> keys,
        SourceLocation location,
        String description,
        List<Control> controls,
        ScreenLayout layout,
        ScreenPhone phone,
        Map<String, Object> extensions) {

    public static final int MAX_STRIP = 8;

    /** A Sutra of a host whose profile reads {@code domain} and {@code match} (either may be null: not kept). */
    public Sutra(String name, int version, String domain, Match match, Title title, List<StripItem> strip, List<Panel> panels,
            Map<String, String> keys, SourceLocation location, String description, List<Control> controls, ScreenLayout layout, ScreenPhone phone) {
        this(name, version, title, strip, panels, keys, location, description, controls, layout, phone, entity(domain, match));
    }

    private static Map<String, Object> entity(String domain, Match match) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        if (domain != null) {
            m.put("domain", domain);
        }
        if (match != null) {
            m.put("match", match);
        }
        return m;
    }

    /** A Sutra without a phone block (docs/guides/BUILD_GUIDE.md section 5.2). */
    public Sutra(String name, int version, String domain, Match match, Title title, List<StripItem> strip, List<Panel> panels,
            Map<String, String> keys, SourceLocation location, String description, List<Control> controls, ScreenLayout layout) {
        this(name, version, domain, match, title, strip, panels, keys, location, description, controls, layout, null);
    }

    /** A Sutra without a layout: its panels flow (RACHANA_REFERENCE.md 13). */
    public Sutra(String name, int version, String domain, Match match, Title title, List<StripItem> strip, List<Panel> panels,
            Map<String, String> keys, SourceLocation location, String description, List<Control> controls) {
        this(name, version, domain, match, title, strip, panels, keys, location, description, controls, null);
    }

    /** A Sutra without a description. */
    public Sutra(String name, int version, String domain, Match match, Title title, List<StripItem> strip, List<Panel> panels,
            Map<String, String> keys, SourceLocation location) {
        this(name, version, domain, match, title, strip, panels, keys, location, null, List.of(), null);
    }

    /** A Sutra without controls. */
    public Sutra(String name, int version, String domain, Match match, Title title, List<StripItem> strip, List<Panel> panels,
            Map<String, String> keys, SourceLocation location, String description) {
        this(name, version, domain, match, title, strip, panels, keys, location, description, List.of(), null);
    }

    public Sutra {
        strip = List.copyOf(strip);
        panels = List.copyOf(panels);
        keys = Map.copyOf(keys);
        controls = controls == null ? List.of() : List.copyOf(controls);
        extensions = extensions == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(extensions));
    }

    /** What the profile key {@code key} read, or null. */
    public Object extension(String key) {
        return extensions.get(key);
    }

    /** The folder it belongs to ({@code domain}, an entity addition), or null when no profile reads it. */
    public String domain() {
        return extension("domain") instanceof String d ? d : null;
    }

    /** Which entities it applies to ({@code match}, an entity addition), or null when no profile reads it. */
    public Match match() {
        return extension("match") instanceof Match m ? m : null;
    }

    /** The control named {@code id}, if the Sutra declares one. */
    public java.util.Optional<Control> control(String id) {
        return controls.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public String id() {
        return name + "@" + version;
    }
}
