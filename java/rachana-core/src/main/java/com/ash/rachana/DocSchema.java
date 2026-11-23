/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana;

import com.ash.rachana.model.Ordered;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The doc definitions of the Sutra schema (SPEC 2.9): {@code doc} (the strict doc block of a fragment or block), {@code docLoose} and {@code docLooseOf} (the doc of a Sutra or a panel: the form 1.0.1 accepted),
 * {@code docLine}, {@code docOf} (a part's doc: one line or a block), and the typed fragment file with its parameter, column and slot
 * ({@code fragment}, {@code fragmentParam}, {@code fragmentColumn}, {@code fragmentSlot}). {@link RachanaSchema} puts them under
 * {@code $defs}; the GenUI kit's tool schemas embed the same definitions (tools/sajha/schemas/doc.schema.json, kept equal by its test).
 * The lint of a thin doc is {@link com.ash.rachana.parse.DocBlockLint} ({@code RCH-2093}).
 */
public final class DocSchema {

    private DocSchema() {}

    /** The definitions, by name. */
    public static Map<String, Object> defs() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("doc", Ordered.map(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("summary"),
            "description", "The doc block (SPEC 2.9) of a Sutra, fragment, block, parameter, slot or panel: what it is, when to use it and what data it takes, as data the compiler checks. People, component browsers, the GenUI catalogue and AI agents read it; nothing else describes a fragment. Missing: RCH-2055; thin: RCH-2093",
            "properties", Ordered.map(
                "summary", Ordered.map(
                    "type", "string",
                    "minLength", 1,
                    "maxLength", 120,
                    "pattern", "^[^\\n]+$",
                    "description", "One line of at most 120 characters: what it shows"),
                "description", Ordered.map("type", "string", "minLength", 1, "description", "What it shows and how, in a short paragraph"),
                "category", Ordered.map("type", "string", "description", "The group it is listed under (a category id of its library)"),
                "tags", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "minLength", 1),
                    "description", "Short words the library's index shows"),
                "use-when", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "minLength", 1),
                    "description", "Sentences: the situations it is the right choice for"),
                "avoid-when", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "minLength", 1),
                    "description", "Sentences: when not to use it, each naming what to use instead"),
                "data", Ordered.map(
                    "type", "object",
                    "additionalProperties", false,
                    "description", "The data it takes: the rows in words and one synthetic sample row",
                    "properties", Ordered.map(
                        "table", Ordered.map("type", "string", "description", "The table or record parameter the sample row is of"),
                        "in-words", Ordered.map(
                            "type", "string",
                            "minLength", 1,
                            "description", "One row per what, and the columns, in plain words"),
                        "sample-row", Ordered.map(
                            "type", "object",
                            "minProperties", 1,
                            "description", "One synthetic row, in the fragment's own column names"))),
                "example", Ordered.map(
                    "type", "object",
                    "required", List.of("use"),
                    "description", "A minimal use: { use: library/name@major, with: {...} }",
                    "properties", Ordered.map(
                        "use", Ordered.map("type", "string", "pattern", "^([a-z][a-z0-9-]{0,63}/)?[a-z][a-z0-9-]{1,63}(@[0-9]+)?$"),
                        "with", Ordered.map("type", "object"))),
                "see-also", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "pattern", "^([a-z][a-z0-9-]{0,63}/)?[a-z][a-z0-9-]{1,63}(@[0-9]+)?$"),
                    "description", "Related fragments: library/name or library/name@major"),
                "keywords", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "minLength", 1),
                    "description", "Search words: what a user would type to find it"),
                "intents", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "minLength", 1),
                    "description", "The AI kit's intents it answers (AI_GENUI 2.1)"),
                "params", Ordered.map(
                    "type", "object",
                    "description", "Older docstring form (RCH-2055): each parameter's type, default and meaning"),
                "emits", Ordered.map("type", "array", "description", "Older docstring form: the events it fires"),
                "expects", Ordered.map("type", "array", "description", "Older docstring form: what it reads from a parent"),
                "examples", Ordered.map("type", "array", "description", "Older docstring form: uses of it"),
                "since", Ordered.map("type", "string", "description", "The release it arrived in"),
                "uses", Ordered.map(
                    "type", "array",
                    "items", Ordered.map("type", "string", "minLength", 1),
                    "description", "A composite fragment's inner fragments, pinned (RCH-2055)"))));
        out.put("docLoose", Ordered.map(
            "type", "object",
            "description", "The doc of a Sutra or a panel (SPEC 2.9): an object whose keys are the doc block's properties (see doc); nothing is required, other keys are allowed and there is no length limit. A thin doc is RCH-2093 (a lint), never a reason a Sutra is refused",
            "properties", Ordered.map(
                "summary", Ordered.map("type", "string", "description", "One line: what it shows"),
                "description", Ordered.map("type", "string", "description", "What it shows and how"))));
        out.put("docLooseOf", Ordered.map(
            "description", "A panel's doc: one line, or the loose doc object",
            "oneOf", List.of(Ordered.map("$ref", "#/$defs/docLine"), Ordered.map("$ref", "#/$defs/docLoose"))));
        out.put("docLine", Ordered.map(
            "type", "string",
            "minLength", 1,
            "pattern", "^[^\\n]+$",
            "description", "A one-line doc: of a parameter, slot, column, event or panel"));
        out.put("docOf", Ordered.map(
            "description", "A part's doc: one line, or a whole doc block",
            "oneOf", List.of(Ordered.map("$ref", "#/$defs/docLine"), Ordered.map("$ref", "#/$defs/doc"))));
        out.put("fragment", Ordered.map(
            "type", "object",
            "required", List.of("rachana", "fragment", "version", "doc", "panels"),
            "description", "A typed fragment file, <name>.v<N>.fragment.yaml (LIBRARIES 4); a text-form block carries the same doc",
            "properties", Ordered.map(
                "rachana", new LinkedHashMap<String, Object>(),
                "fragment", Ordered.map("type", "string", "pattern", "^[a-z][a-z0-9-]{1,63}$"),
                "version", Ordered.map("type", "integer", "minimum", 1),
                "doc", Ordered.map("$ref", "#/$defs/doc"),
                "params", Ordered.map(
                    "type", "object",
                    "maxProperties", 40,
                    "additionalProperties", Ordered.map("$ref", "#/$defs/fragmentParam")),
                "slots", Ordered.map(
                    "type", "object",
                    "maxProperties", 8,
                    "additionalProperties", Ordered.map("$ref", "#/$defs/fragmentSlot")),
                "emits", Ordered.map(
                    "type", "array",
                    "items", Ordered.map(
                        "type", "object",
                        "required", List.of("name"),
                        "properties", Ordered.map(
                            "name", Ordered.map("type", "string"),
                            "payload", new LinkedHashMap<String, Object>(),
                            "doc", Ordered.map("$ref", "#/$defs/docOf")))),
                "panels", Ordered.map("type", "array"))));
        out.put("fragmentColumn", Ordered.map(
            "type", "object",
            "additionalProperties", false,
            "description", "A column of a table parameter (LIBRARIES 5.1)",
            "properties", Ordered.map(
                "type", Ordered.map("type", "string"),
                "doc", Ordered.map("$ref", "#/$defs/docOf"),
                "optional", Ordered.map("type", "boolean"))));
        out.put("fragmentParam", Ordered.map(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("type", "doc"),
            "description", "A fragment's parameter (LIBRARIES 4.2)",
            "properties", Ordered.map(
                "type", Ordered.map("type", "string"),
                "doc", Ordered.map("$ref", "#/$defs/docOf"),
                "default", new LinkedHashMap<String, Object>(),
                "required", Ordered.map("type", "boolean"),
                "optional", Ordered.map("type", "boolean"),
                "columns", Ordered.map("type", "object", "additionalProperties", Ordered.map("$ref", "#/$defs/fragmentColumn")),
                "contract", Ordered.map("type", "string"),
                "min", Ordered.map("type", "number"),
                "max", Ordered.map("type", "number"),
                "options", Ordered.map("type", "array"),
                "pattern", Ordered.map("type", "string"),
                "max-rows", Ordered.map("type", "integer"),
                "live", Ordered.map(
                    "enum", List.of("ok", "no"),
                    "description", "ok | no: whether the fragment redraws correctly from keyed patches (LIBRARIES 5)"),
                "key", new LinkedHashMap<String, Object>(),
                "window", Ordered.map(
                    "enum", List.of("ok", "no"),
                    "description", "ok | no: whether every read is push-down-able (LIBRARIES 5)"),
                "dynamic", Ordered.map("type", "boolean"),
                "of", Ordered.map("type", "string"))));
        out.put("fragmentSlot", Ordered.map(
            "type", "object",
            "required", List.of("accepts", "doc"),
            "description", "A fragment's slot (LIBRARIES 4.3)",
            "properties", Ordered.map(
                "accepts", Ordered.map("enum", List.of("panels", "columns", "controls", "fields", "actions", "menu", "layers")),
                "max", Ordered.map("type", "integer", "minimum", 1),
                "exposes", Ordered.map("type", "object"),
                "default", new LinkedHashMap<String, Object>(),
                "doc", Ordered.map("$ref", "#/$defs/docOf"))));
        return out;
    }
}
