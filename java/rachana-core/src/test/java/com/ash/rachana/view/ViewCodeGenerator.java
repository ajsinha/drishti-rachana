/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.view;

import com.ash.rachana.model.PanelKind;
import com.ash.rachana.parse.Json;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The build-time generator of the view package's two generated files (split step A15, JS_PROOF J21): Core reads no record by
 * reflection, so that the JavaScript build (TeaVM) can take it.
 *
 * <ul>
 * <li>{@code ViewRecords.java}: one non-reflective writer per view record (its fields in declaration order, each with its
 * {@link Include} rule), used by {@link ViewJson};</li>
 * <li>{@code ViewModelSchemaText.java}: the ViewModel's JSON Schema as text, built here from the records by reflection and
 * read back by {@link ViewModelSchema#build()}.</li>
 * </ul>
 *
 * <p>{@code ViewCodeGeneratedTest} fails when either file is stale; {@code main} (run from java/rachana-core with the test class
 * path) rewrites both.
 */
public final class ViewCodeGenerator {

    static final Path SOURCE = Path.of("src/main/java/com/ash/rachana/view");

    private ViewCodeGenerator() {}

    public static void main(String[] args) throws Exception {
        Path dir = args.length > 0 ? Path.of(args[0]) : SOURCE;
        Files.writeString(dir.resolve("ViewRecords.java"), records(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ViewModelSchemaText.java"), schemaSource(), StandardCharsets.UTF_8);
    }

    private static final String HEADER = """
            /*
             * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
             * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
             */
            package com.ash.rachana.view;

            """;

    // ---------------------------------------------------------------- the records

    /** Every record of the view package (nested ones too) and every record their fields hold, by canonical name. */
    static List<Class<?>> viewRecords() throws IOException, URISyntaxException {
        Path classes = Path.of(ViewModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve("com/ash/rachana/view");
        Map<String, Class<?>> found = new TreeMap<>();
        try (Stream<Path> files = Files.list(classes)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String n = f.getFileName().toString();
                try {
                    walk(Class.forName("com.ash.rachana.view." + n.substring(0, n.length() - 6)), found);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(n, e);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void walk(Class<?> c, Map<String, Class<?>> found) {
        if (!c.isRecord() || c.getCanonicalName() == null || found.containsKey(c.getCanonicalName())) {
            return;
        }
        found.put(c.getCanonicalName(), c);
        for (RecordComponent rc : c.getRecordComponents()) {
            walkType(rc.getGenericType(), found);
        }
    }

    private static void walkType(Type t, Map<String, Class<?>> found) {
        if (t instanceof WildcardType w) {
            walkType(w.getUpperBounds()[0], found);
        } else if (t instanceof ParameterizedType p) {
            Arrays.stream(p.getActualTypeArguments()).forEach(a -> walkType(a, found));
        } else if (t instanceof Class<?> c) {
            walk(c, found);
        }
    }

    /** The rule of a record's field: its own, else the record's, else {@link Include.Rule#ALWAYS}. */
    static Include.Rule rule(Class<?> record, RecordComponent rc) {
        Include own = rc.getAccessor().getAnnotation(Include.class);
        if (own == null) {
            own = rc.getAnnotation(Include.class);
        }
        if (own != null) {
            return own.value();
        }
        Include all = record.getAnnotation(Include.class);
        return all == null ? Include.Rule.ALWAYS : all.value();
    }

    /** The text of ViewRecords.java. */
    static String records() throws IOException, URISyntaxException {
        StringBuilder b = new StringBuilder(HEADER);
        b.append("""
                import com.ash.rachana.view.Include.Rule;
                import java.util.LinkedHashMap;
                import java.util.Map;

                /**
                 * GENERATED by ViewCodeGenerator (test sources) from the view records: do not edit. One writer per record, its fields in
                 * declaration order with their {@link Include} rules, so that {@link ViewJson} reads no record by reflection.
                 */
                final class ViewRecords {

                    private ViewRecords() {}

                    /** The record as an object of its written fields, or null when the value is not a view record. */
                    static Map<String, Object> tree(Object value) {
                        return switch (value) {
                """);
        for (Class<?> c : viewRecords()) {
            b.append("            case ").append(c.getCanonicalName().replace("com.ash.rachana.view.", "")).append(" r -> {\n");
            b.append("                Map<String, Object> m = new LinkedHashMap<>();\n");
            for (RecordComponent rc : c.getRecordComponents()) {
                b.append("                ViewJson.put(m, \"").append(rc.getName()).append("\", r.").append(rc.getName()).append("(), Rule.")
                        .append(rule(c, rc).name()).append(");\n");
            }
            b.append("                yield m;\n            }\n");
        }
        b.append("""
                            default -> null;
                        };
                    }
                }
                """);
        return b.toString();
    }

    // ---------------------------------------------------------------- the schema

    /** The schema, built from the records: a ViewModel at the root, every record under {@code $defs} ({@code Frame} for the stream). */
    static Map<String, Object> schema() {
        Map<String, Object> defs = new LinkedHashMap<>();
        record(ViewModel.class, defs);
        record(Frame.class, defs);
        for (Class<?> c : PanelData.class.getPermittedSubclasses()) {
            record(c, defs);
        }
        defs.put("Tone", ordered("enum", ViewModelSchema.TONES, "description",
                "A theme tone; the console maps each to a colour token and pairs it with a sign or glyph"));
        defs.put("PanelData", ordered("description", "A panel's kind-specific content; which record depends on the panel's kind",
                "anyOf", Arrays.stream(PanelData.class.getPermittedSubclasses()).map(c -> ref(c.getSimpleName())).toList()));
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        s.put("$id", ViewModelSchema.ID);
        s.put("title", "ViewModel");
        s.put("description", "The JSON the server returns for every entity screen (GET /api/v1/views/{kind}/{id}, and the view event of its "
                + "stream). $defs.Frame is a frame event of the stream. See docs/architecture/VIEWMODEL.md.");
        s.put("$ref", "#/$defs/ViewModel");
        s.put("$defs", defs);
        return s;
    }

    /** The text of ViewModelSchemaText.java: the schema's JSON in chunks below the class file's constant limit. */
    static String schemaSource() {
        String json = Json.write(schema());
        StringBuilder b = new StringBuilder(HEADER);
        b.append("""
                /**
                 * GENERATED by ViewCodeGenerator (test sources) from the view records: do not edit. The ViewModel's JSON Schema as compact
                 * JSON, read by {@link ViewModelSchema#build()}.
                 */
                final class ViewModelSchemaText {

                    private ViewModelSchemaText() {}

                    static final String JSON = String.join("",
                """);
        int chunk = 8_000;
        for (int i = 0; i < json.length(); i += chunk) {
            String part = json.substring(i, Math.min(json.length(), i + chunk));
            b.append("            \"").append(javaEscape(part)).append('"').append(i + chunk < json.length() ? ",\n" : ");\n");
        }
        b.append("}\n");
        return b.toString();
    }

    private static String javaEscape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    /** A map in the given key order (the schema text is then stable from run to run). */
    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> ref(String name) {
        return ordered("$ref", "#/$defs/" + name);
    }

    /** Adds {@code c}'s definition, and those of the records it holds, to {@code defs}. */
    private static void record(Class<?> c, Map<String, Object> defs) {
        String name = c.getSimpleName();
        if (defs.containsKey(name)) {
            return;
        }
        Map<String, Object> def = new LinkedHashMap<>();
        defs.put(name, def);                             // reserved first: records may hold themselves (Row.children)
        if (ViewModelSchema.ABOUT.containsKey(name)) {
            def.put("description", ViewModelSchema.ABOUT.get(name));
        }
        def.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>(ViewModelSchema.REQUIRED.getOrDefault(name, List.of()));
        for (RecordComponent rc : c.getRecordComponents()) {
            boolean always = rule(c, rc) == Include.Rule.ALWAYS;
            String key = name + "." + rc.getName();
            Map<String, Object> type;
            if (ViewModelSchema.ENUMS.containsKey(key)) {
                type = ordered("enum", ViewModelSchema.ENUMS.get(key));
            } else if (ViewModelSchema.TONED.contains(key)) {
                type = ref("Tone");
            } else {
                type = type(rc.getGenericType(), defs);
            }
            boolean primitive = rc.getType().isPrimitive();
            if (always && !primitive) {
                type = nullable(type);
            }
            props.put(rc.getName(), type);
            if (always && !required.contains(rc.getName())) {
                required.add(rc.getName());
            }
        }
        def.put("properties", props);
        if (!required.isEmpty()) {
            def.put("required", required);
        }
        def.put("additionalProperties", false);
        if (c == ViewModel.PanelView.class) {
            Map<String, Object> panel = new LinkedHashMap<>(props);
            panel.put("kind", ordered("enum", Arrays.stream(PanelKind.values()).map(PanelKind::id).toList()));
            def.put("properties", panel);
            def.put("allOf", ViewModelSchema.DATA_BY_KIND.entrySet().stream().map(e -> ordered(
                    "if", ordered("properties", ordered("kind", ordered("const", e.getKey())), "required", List.of("kind")),
                    "then", ordered("properties", ordered("data", nullable(ref(e.getValue().getSimpleName()))))))
                    .toList());
        }
    }

    /**
     * A number: the server writes NaN and the infinities (a gauge whose document lacks the value, imperfect source data) as null,
     * so every number, a primitive one too, may be null.
     */
    private static Map<String, Object> number() {
        return ordered("anyOf", List.of(ordered("type", "number"), ordered("type", "null")));
    }

    private static Map<String, Object> nullable(Map<String, Object> type) {
        if (type.isEmpty() || type.equals(number())) {
            return type;                                  // anything, or a number: null included already
        }
        return ordered("anyOf", List.of(type, ordered("type", "null")));
    }

    private static Map<String, Object> type(Type t, Map<String, Object> defs) {
        if (t instanceof WildcardType w) {
            return type(w.getUpperBounds()[0], defs);
        }
        if (t instanceof ParameterizedType p) {
            Class<?> raw = (Class<?>) p.getRawType();
            Type[] args = p.getActualTypeArguments();
            if (List.class.isAssignableFrom(raw)) {
                Map<String, Object> item = type(args[0], defs);
                boolean boxedNumber = args[0] == Integer.class || args[0] == Long.class;
                boolean nested = args[0] instanceof ParameterizedType;
                return ordered("type", "array", "items", boxedNumber || nested ? nullable(item) : item);
            }
            if (Map.class.isAssignableFrom(raw)) {
                Map<String, Object> value = type(args[1], defs);
                return value.isEmpty() ? ordered("type", "object") : ordered("type", "object", "additionalProperties", value);
            }
            return type(raw, defs);
        }
        Class<?> c = (Class<?>) t;
        if (c == String.class) {
            return ordered("type", "string");
        }
        if (c == boolean.class || c == Boolean.class) {
            return ordered("type", "boolean");
        }
        if (c == int.class || c == Integer.class || c == long.class || c == Long.class) {
            return ordered("type", "integer");
        }
        if (c == double.class || c == Double.class) {
            return number();
        }
        if (c == Instant.class) {
            return ordered("type", "string", "format", "date-time");
        }
        if (c == Object.class) {
            return new LinkedHashMap<>();
        }
        if (c == PanelData.class) {
            return ref("PanelData");
        }
        if (c.isRecord()) {
            record(c, defs);
            return ref(c.getSimpleName());
        }
        throw new IllegalStateException("no JSON type for " + c);
    }
}
