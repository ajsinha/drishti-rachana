/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.profile;

import com.ash.rachana.model.Match;
import com.ash.rachana.model.PanelKind;
import com.ash.rachana.parse.PNode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import com.ash.rachana.model.Ordered;

/**
 * The additions of a host that serves <em>entities of kinds</em> (split step A7; docs/design/CORE_AND_PROFILES.md section 1): the
 * top-level keys {@code domain} (the folder a Sutra belongs to) and {@code match} (which entities it applies to, with the sections
 * modules contribute to a kind), the panel kinds {@code links} and {@code provenance}, the {@code kind} source form and
 * {@code form.save}. A profile takes them with {@link #addTo}; Core alone has none of them.
 *
 * <p>They are kept here, beside the profile mechanism, until the host's profile module takes them over (DRISHTI_MIGRATION_TODO 8.3d):
 * the conformance suite's {@code drishti} profile uses them, so the moved code keeps answering as the reference does.
 */
public final class Entities {

    /** The key that says which entities a Sutra applies to. */
    public static final String MATCH = "match";
    /** The key that names the folder a Sutra belongs to. */
    public static final String DOMAIN = "domain";

    private static final String EL = "Rachana-EL";

    private Entities() {}

    /** Adds every entity addition to {@code profile}; the {@code match.kind} of the schema is any text. */
    public static Profile.Builder addTo(Profile.Builder profile) {
        return addTo(profile, List::of);
    }

    /**
     * Adds every entity addition to {@code profile}.
     *
     * @param kinds the entity kinds served now, for the schema's {@code match.kind} list (empty: any text)
     */
    public static Profile.Builder addTo(Profile.Builder profile, Supplier<? extends Collection<String>> kinds) {
        return profile.keys(domain(), match(kinds, k -> List.of())).panelKinds(PanelKind.LINKS, PanelKind.PROVENANCE).sourceForms("kind").formSave();
    }

    /**
     * Adds every entity addition to {@code profile}, with {@code blocks} as its fragment resolver: a Sutra's {@code use:} finds that
     * library's blocks, and a Sutra that matches a kind gets the sections the library's modules contribute to that kind.
     *
     * @param kinds the entity kinds served now, for the schema's {@code match.kind} list (empty: any text)
     * @param blocks the instance's block library
     */
    public static Profile.Builder addTo(Profile.Builder profile, Supplier<? extends Collection<String>> kinds, com.ash.rachana.parse.BlockLibrary blocks) {
        Objects.requireNonNull(blocks, "a block library");
        return profile.keys(domain(), match(kinds, blocks::contributedUses)).panelKinds(PanelKind.LINKS, PanelKind.PROVENANCE).sourceForms("kind")
                .formSave().fragments(blocks);
    }

    /** {@code domain}: the folder it belongs to; the folder it was read from when it does not say. */
    public static SutraKey domain() {
        return new SutraKey() {
            @Override
            public String name() {
                return DOMAIN;
            }

            @Override
            public Object read(PNode node, Context context) {
                return node == null || node.text() == null ? context.folder() : node.text();
            }

            @Override
            public String after() {
                return "version";
            }

            @Override
            public Map<String, Object> schema() {
                return text("The folder it belongs to (defaults to its parent folder)");
            }
        };
    }

    /** {@code match: { kind, where, priority }}, required; a {@link Match}; no contributed sections. */
    public static SutraKey match(Supplier<? extends Collection<String>> kinds) {
        return match(kinds, k -> List.of());
    }

    /**
     * {@code match: { kind, where, priority }}, required; a {@link Match}.
     *
     * @param sections the {@code use:} nodes of the sections contributed to a kind's Sutras (module.yaml {@code sections:})
     */
    public static SutraKey match(Supplier<? extends Collection<String>> kinds, java.util.function.Function<String, List<PNode>> sections) {
        Objects.requireNonNull(sections, "the contributed sections");
        return new SutraKey() {
            @Override
            public String name() {
                return MATCH;
            }

            @Override
            public Object read(PNode n, Context c) {
                if (n == null || !n.isMap()) {
                    c.problem("RCH-2010", "missing 'match' mapping with at least 'kind'", n == null ? c.root() : n);
                    return new Match("?", null, 0);
                }
                n.map().forEach((k, v) -> {
                    if (!Ordered.set("kind", "where", "priority").contains(k)) {
                        c.problem("RCH-2011", "unknown key '" + k + "' in match", v);
                    }
                });
                String kind = null;
                PNode kn = n.map().get("kind");
                if (kn == null || kn.value() == null) {
                    c.problem("RCH-2010", "missing 'kind'", n);
                } else if (!kn.isScalar()) {
                    c.problem("RCH-2012", "'kind' must be text", kn);
                } else {
                    kind = kn.text();
                }
                if (kind != null && !(kn.value() instanceof String)) {
                    c.problem("RCH-2012", "'kind' in match is an entity kind written as text (such as trade), not '" + kind + "'", kn);
                }
                PNode where = n.map().get("where");
                if (where != null && where.value() != null && !(where.value() instanceof String)) {
                    c.problem("RCH-2012", "'where' in match is an expression written as text, not '" + plain(where) + "'", where);
                }
                PNode pr = n.map().get("priority");
                int priority = 0;
                if (pr != null && pr.value() != null) {
                    if (pr.value() instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        priority = l.intValue();
                    } else {
                        c.problem("RCH-2012", "'priority' in match must be a whole number (higher is tried first), not '" + plain(pr) + "'", pr);
                    }
                }
                return new Match(kind, where == null || where.text() == null ? null : where.text(), priority);
            }

            @Override
            public List<PNode> panels(Object value) {
                return value instanceof Match m && m.kind() != null ? sections.apply(m.kind()) : List.of();   // module.yaml sections: of the kind
            }

            @Override
            public boolean required() {
                return true;
            }

            @Override
            public String after() {
                return "doc";
            }

            @Override
            public Map<String, Object> schema() {
                Collection<String> served = kinds.get();
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("kind", served == null || served.isEmpty() ? text("The entity kind")
                        : Ordered.map("type", "string", "enum", List.copyOf(new TreeSet<>(served)), "description", "The entity kind"));
                props.put("where", text(EL + " that must be true for the Sutra to apply"));
                props.put("priority", Ordered.map("type", "integer", "description", "Higher wins when several Sutras match"));
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("type", "object");
                o.put("properties", props);
                o.put("additionalProperties", false);
                o.put("required", List.of("kind"));
                return o;
            }
        };
    }

    private static Map<String, Object> text(String description) {
        return Ordered.map("type", "string", "description", description);
    }

    private static Object plain(PNode n) {
        if (n.isMap()) {
            Map<String, Object> out = new LinkedHashMap<>();
            n.map().forEach((k, v) -> out.put(k, plain(v)));
            return out;
        }
        if (n.isList()) {
            return n.list().stream().map(Entities::plain).toList();
        }
        return n.value();
    }
}
