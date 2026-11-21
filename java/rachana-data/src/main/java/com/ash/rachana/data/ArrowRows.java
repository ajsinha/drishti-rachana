/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.data;

import com.ash.rachana.parse.Json;
import com.ash.rachana.table.TableSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeStampMilliTZVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowStreamReader;
import org.apache.arrow.vector.ipc.ArrowStreamWriter;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * A page of rows as an Arrow IPC stream and back (PROTOCOL 5.2), through the official Apache Arrow Java library: the host writes the JSON
 * page it answers ({@code table}, {@code version}, {@code columns}, {@code values}, {@code total}, {@code next}, {@code totals}) as a stream
 * (the schema message with the protocol's metadata, then batches of at most {@code batchRows}); the client reads a stream back into the
 * same JSON page, so everything after the transport is the JSON path. Types (PROTOCOL 3.1): string Utf8, integer Int64 (Decimal(38, 0)
 * when a value is beyond a long), decimal Decimal128 (the schema's {@code scale}, else the largest scale in the page), number Float64,
 * boolean Bool, date Date32, timestamp Timestamp(ms, UTC), time Time32(ms), list and object Utf8 holding JSON; every field carries
 * {@code rachana.type}. A masked cell is a null slot plus {@code true} in a {@code <name>$masked} Bool column (PROTOCOL 3.2).
 * Stateless; each call has its own allocator.
 */
public final class ArrowRows {

    /** Rows per record batch (PROTOCOL 13 {@code arrowBatchRows}). */
    public static final int BATCH_ROWS = 65_536;
    public static final String MEDIA = "application/vnd.apache.arrow.stream";
    private static final String MASK = "$masked";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private ArrowRows() {}

    /** Writes a JSON page (columnar-by-row or objects) of a table as an Arrow IPC stream. */
    public static byte[] write(Map<String, Object> page, TableSchema schema, int batchRows) {
        List<String> cols = columns(page);
        List<List<Object>> values = values(page, cols);
        List<Field> fields = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<Integer> scales = new ArrayList<>();
        List<Boolean> masked = new ArrayList<>();
        for (int c = 0; c < cols.size(); c++) {
            String name = cols.get(c);
            String type = type(schema, name, values, c);
            int scale = 0;
            boolean big = false;
            boolean anyMask = schema.column(name) != null && schema.column(name).masked();
            for (List<Object> row : values) {
                Object v = row.get(c);
                anyMask |= isMasked(v);
                if (!isMasked(v) && v != null && "decimal".equals(type)) {
                    scale = Math.max(scale, Math.max(0, new BigDecimal(String.valueOf(v)).scale()));
                }
                if (!isMasked(v) && v != null && "integer".equals(type)) {
                    big |= !fitsLong(v);
                }
            }
            Integer declared = declaredScale(schema, name);
            scale = "decimal".equals(type) && declared != null ? declared : scale;
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("rachana.type", type);
            Map<?, ?> doc = columnDoc(schema, name);
            for (String k : List.of("fmt", "label", "unit")) {
                if (doc.get(k) instanceof String s) {
                    meta.put("rachana." + k, s);
                }
            }
            ArrowType at = switch (type) {
                case "integer" -> big ? new ArrowType.Decimal(38, 0, 128) : new ArrowType.Int(64, true);
                case "decimal" -> new ArrowType.Decimal(38, scale, 128);
                case "number" -> new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
                case "boolean" -> ArrowType.Bool.INSTANCE;
                case "date" -> new ArrowType.Date(DateUnit.DAY);
                case "timestamp" -> new ArrowType.Timestamp(TimeUnit.MILLISECOND, "UTC");
                case "time" -> new ArrowType.Time(TimeUnit.MILLISECOND, 32);
                default -> ArrowType.Utf8.INSTANCE;
            };
            fields.add(new Field(name, new FieldType(true, at, null, meta), null));
            types.add(big ? "integer.big" : type);
            scales.add(scale);
            masked.add(anyMask);
        }
        List<Integer> maskCols = new ArrayList<>();
        for (int c = 0; c < cols.size(); c++) {
            if (masked.get(c)) {
                fields.add(new Field(cols.get(c) + MASK, new FieldType(true, ArrowType.Bool.INSTANCE, null, Map.of("rachana.masked", cols.get(c))), null));
                maskCols.add(c);
            }
        }
        Schema arrow = new Schema(fields, metadata(page, schema));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BufferAllocator alloc = new RootAllocator(); VectorSchemaRoot root = VectorSchemaRoot.create(arrow, alloc);
             ArrowStreamWriter w = new ArrowStreamWriter(root, null, Channels.newChannel(out))) {
            w.start();
            int batch = Math.max(1, batchRows);
            for (int from = 0; from < values.size() || from == 0; from += batch) {
                int n = Math.min(batch, values.size() - from);
                root.allocateNew();
                for (int c = 0; c < cols.size(); c++) {
                    FieldVector v = root.getVector(c);
                    for (int i = 0; i < n; i++) {
                        set(v, types.get(c), scales.get(c), i, values.get(from + i).get(c));
                    }
                    v.setValueCount(n);
                }
                for (int m = 0; m < maskCols.size(); m++) {
                    BitVector v = (BitVector) root.getVector(cols.size() + m);
                    for (int i = 0; i < n; i++) {
                        v.setSafe(i, masked(values.get(from + i).get(maskCols.get(m)), schema, cols.get(maskCols.get(m))) ? 1 : 0);
                    }
                    v.setValueCount(n);
                }
                root.setRowCount(n);
                w.writeBatch();
                if (values.isEmpty()) {
                    break;
                }
            }
            w.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Reads an Arrow IPC stream into the JSON page (columnar-by-row) the host would have answered. */
    public static Map<String, Object> read(InputStream in) {
        Map<String, Object> page = new LinkedHashMap<>();
        try (BufferAllocator alloc = new RootAllocator(); ArrowStreamReader r = new ArrowStreamReader(in, alloc)) {
            VectorSchemaRoot root = r.getVectorSchemaRoot();
            Schema s = root.getSchema();
            Map<String, String> meta = s.getCustomMetadata() == null ? Map.of() : s.getCustomMetadata();
            page.put("table", meta.getOrDefault("rachana.table", ""));
            if (meta.containsKey("rachana.epoch")) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("epoch", meta.get("rachana.epoch"));
                v.put("seq", Long.parseLong(meta.getOrDefault("rachana.seq", "0")));
                page.put("version", v);
            }
            List<String> cols = new ArrayList<>();
            Map<String, Integer> maskOf = new LinkedHashMap<>();
            for (int i = 0; i < s.getFields().size(); i++) {
                Field f = s.getFields().get(i);
                String target = f.getMetadata() == null ? null : f.getMetadata().get("rachana.masked");
                if (target != null) {
                    maskOf.put(target, i);
                } else {
                    cols.add(f.getName());
                }
            }
            List<Object> values = new ArrayList<>();
            while (r.loadNextBatch()) {
                int n = root.getRowCount();
                for (int i = 0; i < n; i++) {
                    List<Object> row = new ArrayList<>(cols.size());
                    for (String c : cols) {
                        FieldVector v = root.getVector(c);
                        Integer mi = maskOf.get(c);
                        if (mi != null && !root.getVector(mi).isNull(i) && ((BitVector) root.getVector(mi)).get(i) == 1) {
                            row.add(Map.of("$masked", true));
                        } else {
                            String type = v.getField().getMetadata() == null ? null : v.getField().getMetadata().get("rachana.type");
                            row.add(cell(v, type, i));
                        }
                    }
                    values.add(row);
                }
            }
            page.put("columns", cols);
            page.put("values", values);
            if (meta.containsKey("rachana.total")) {
                page.put("total", Long.parseLong(meta.get("rachana.total")));
            }
            if (meta.containsKey("rachana.next")) {
                page.put("next", meta.get("rachana.next"));
            }
            if (meta.containsKey("rachana.totals")) {
                page.put("totals", Json.read(meta.get("rachana.totals")));
            }
            if (meta.containsKey("rachana.schema")) {
                page.put("schema", Json.read(meta.get("rachana.schema")));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return page;
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    private static Map<String, String> metadata(Map<String, Object> page, TableSchema schema) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("rachana.table", String.valueOf(page.getOrDefault("table", schema.name())));
        if (!schema.key().isEmpty()) {
            m.put("rachana.key", Json.write(schema.key()));
        }
        if (page.get("version") instanceof Map<?, ?> v) {
            m.put("rachana.epoch", String.valueOf(v.get("epoch")));
            m.put("rachana.seq", String.valueOf(v.get("seq")));
        }
        if (page.get("total") != null) {
            m.put("rachana.total", String.valueOf(page.get("total")));
        }
        if (page.get("next") != null) {
            m.put("rachana.next", String.valueOf(page.get("next")));
        }
        if (page.get("totals") != null) {
            m.put("rachana.totals", Json.write(page.get("totals")));
        }
        if (page.get("schema") != null) {
            m.put("rachana.schema", Json.write(page.get("schema")));
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    static List<String> columns(Map<String, Object> page) {
        if (page.get("columns") instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        List<String> out = new ArrayList<>();
        if (page.get("rows") instanceof List<?> rows && !rows.isEmpty() && rows.get(0) instanceof Map<?, ?> first) {
            ((Map<String, Object>) first).keySet().forEach(out::add);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<List<Object>> values(Map<String, Object> page, List<String> cols) {
        List<List<Object>> out = new ArrayList<>();
        if (page.get("values") instanceof List<?> l) {
            l.forEach(v -> out.add(new ArrayList<>((List<Object>) v)));
        } else if (page.get("rows") instanceof List<?> l) {
            for (Object o : l) {
                Map<String, Object> r = (Map<String, Object>) o;
                List<Object> row = new ArrayList<>();
                cols.forEach(c -> row.add(r.get(c)));
                out.add(row);
            }
        }
        return out;
    }

    private static Map<?, ?> columnDoc(TableSchema schema, String name) {
        if (schema.doc().get("columns") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m && name.equals(m.get("name"))) {
                    return m;
                }
            }
        }
        return Map.of();
    }

    private static Integer declaredScale(TableSchema schema, String name) {
        return columnDoc(schema, name).get("scale") instanceof Number n ? n.intValue() : null;
    }

    /** A column's type: the schema's, else (a grouped answer's computed columns) from its values. */
    private static String type(TableSchema schema, String name, List<List<Object>> values, int c) {
        TableSchema.Column col = schema.column(name);
        if (col != null) {
            return col.type();
        }
        String t = null;
        for (List<Object> row : values) {
            Object v = row.get(c);
            String vt = v == null || isMasked(v) ? null : v instanceof Boolean ? "boolean" : v instanceof Long || v instanceof Integer
                    || v instanceof BigInteger ? "integer" : v instanceof Number ? "number" : v instanceof String s && isDecimal(s) ? "decimal"
                    : v instanceof String ? "string" : "object";
            if (vt != null) {
                t = t == null || t.equals(vt) ? vt : t.equals("integer") && vt.equals("number") || t.equals("number") && vt.equals("integer")
                        ? "number" : "string";
            }
        }
        return t == null ? "string" : t;
    }

    private static boolean isDecimal(String s) {
        return s.matches("-?[0-9]+(\\.[0-9]+)?");
    }

    private static boolean isMasked(Object v) {
        return v instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("$masked"));
    }

    private static boolean masked(Object v, TableSchema schema, String col) {
        return isMasked(v) || schema.column(col) != null && schema.column(col).masked();
    }

    private static boolean fitsLong(Object v) {
        if (v instanceof Long || v instanceof Integer) {
            return true;
        }
        try {
            new BigDecimal(String.valueOf(v)).longValueExact();
            return true;
        } catch (ArithmeticException | NumberFormatException e) {
            return false;
        }
    }

    private static void set(FieldVector v, String type, int scale, int i, Object x) {
        if (x == null || isMasked(x)) {
            v.setNull(i);
            return;
        }
        String text = x instanceof String s ? s : null;
        switch (type) {
            case "integer" -> ((BigIntVector) v).setSafe(i, x instanceof Number n && !(x instanceof BigInteger) ? n.longValue() : Long.parseLong(String.valueOf(x)));
            case "integer.big" -> ((DecimalVector) v).setSafe(i, new BigDecimal(String.valueOf(x)));
            case "decimal" -> ((DecimalVector) v).setSafe(i, new BigDecimal(x instanceof Double d ? BigDecimal.valueOf(d).toPlainString()
                    : String.valueOf(x)).setScale(scale, RoundingMode.UNNECESSARY));
            case "number" -> {
                double d = x instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(x));
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    v.setNull(i);
                } else {
                    ((Float8Vector) v).setSafe(i, d);
                }
            }
            case "boolean" -> ((BitVector) v).setSafe(i, Boolean.TRUE.equals(x) ? 1 : 0);
            case "date" -> ((DateDayVector) v).setSafe(i, (int) LocalDate.parse(text).toEpochDay());
            case "timestamp" -> ((TimeStampMilliTZVector) v).setSafe(i, OffsetDateTime.parse(text).toInstant().toEpochMilli());
            case "time" -> ((TimeMilliVector) v).setSafe(i, (int) (LocalTime.parse(text).toNanoOfDay() / 1_000_000L));
            case "list", "object" -> ((VarCharVector) v).setSafe(i, Json.write(x).getBytes(StandardCharsets.UTF_8));
            default -> ((VarCharVector) v).setSafe(i, (text != null ? text : x instanceof Map || x instanceof List ? Json.write(x)
                    : String.valueOf(x)).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    private static Object cell(FieldVector v, String type, int i) {
        if (v.isNull(i)) {
            return null;
        }
        if (v instanceof VarCharVector s) {
            String text = new String(s.get(i), StandardCharsets.UTF_8);
            return "list".equals(type) || "object".equals(type) ? Json.read(text) : text;
        }
        if (v instanceof BigIntVector b) {
            long l = b.get(i);
            return Math.abs(l) <= (1L << 53) ? (Object) l : Long.toString(l);
        }
        if (v instanceof DecimalVector d) {
            BigDecimal x = d.getObject(i);
            if ("integer".equals(type)) {
                BigInteger bi = x.toBigIntegerExact();
                return bi.abs().compareTo(BigInteger.ONE.shiftLeft(53)) <= 0 ? (Object) bi.longValue() : bi.toString();
            }
            return x.toPlainString();
        }
        if (v instanceof Float8Vector f) {
            return f.get(i);
        }
        if (v instanceof BitVector b) {
            return b.get(i) == 1;
        }
        if (v instanceof DateDayVector d) {
            return LocalDate.ofEpochDay(d.get(i)).toString();
        }
        if (v instanceof TimeStampMilliTZVector t) {
            return TS.format(Instant.ofEpochMilli(t.get(i)));
        }
        if (v instanceof TimeMilliVector t) {
            LocalTime lt = LocalTime.ofNanoOfDay(t.get(i) * 1_000_000L);
            return lt.getNano() == 0 ? lt.format(DateTimeFormatter.ofPattern("HH:mm:ss")) : lt.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        }
        Object o = v.getObject(i);
        return o == null ? null : o.toString();
    }
}
