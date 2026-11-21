/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.js;

import com.ash.rachana.conformance.Cases;
import com.ash.rachana.parse.Json;
import com.ash.rachana.table.LiveScreen;
import com.ash.rachana.table.TableSchema;
import com.ash.rachana.view.Frame;
import com.ash.rachana.view.ViewJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Open screens of the JavaScript runtime (B3: what {@code <rachana-view>} binds through): each a Core {@link LiveScreen} under a
 * number. JSON text in and out, as every function of the facade; the transport (fetch, paging, the change stream) is the caller's, every
 * meaning (the plan, the filter tree, decoding cells, keyed patches, binding, frames) is Core's.
 * <ul>
 *   <li>{@code open}: a request of the shape of a {@code sutra} case ({@code sutra}, {@code since}, {@code profile}, {@code given: {today,
 *       record, ctl, tables}}) → {@code {id, tables: [names], problems?}}</li>
 *   <li>{@code plan}: {@code {id, schemas: {name: schema}, ctl?, state?}} → the plan document</li>
 *   <li>{@code table}: {@code {id, name, schema?, rows}} → {@code {}}</li>
 *   <li>{@code view}: {@code {id, ctl?, state?}} (state: text per state id, null for a cleared one; level 1.2) → {@code {viewModel}}</li>
 *   <li>{@code patch}: {@code {id, table, patch}} → {@code {frames}} or, when the screen's shape changed, {@code {viewModel}}</li>
 *   <li>{@code close}: {@code {id}} → {@code {}}</li>
 * </ul>
 */
final class Screens {

    private static final Map<Integer, LiveScreen> OPEN = new LinkedHashMap<>();
    private static int next = 1;

    private Screens() {
    }

    static String call(String op, String json) {
        try {
            Map<String, Object> r = Cases.obj(Json.read(json));
            return switch (op) {
                case "open" -> open(r);
                case "plan" -> {
                    LiveScreen s = get(r);
                    if (r.get("ctl") instanceof Map<?, ?>) {
                        s.controls(Cases.obj(r.get("ctl")));
                    }
                    if (r.get("state") instanceof Map<?, ?>) {
                        s.state(Cases.obj(r.get("state")));
                    }
                    yield Json.write(s.plan(schemas(r.get("schemas"))));
                }
                case "table" -> {
                    List<Map<String, Object>> rows = rows(r.get("rows"));
                    get(r).table(String.valueOf(r.get("name")), r.get("schema") == null ? null : TableSchema.of(r.get("schema")), rows);
                    yield "{}";
                }
                case "missing" -> {                                    // the host has no such table (rdp.table.unknown): its entries fail
                    get(r).missing(String.valueOf(r.get("name")));
                    yield "{}";
                }
                case "view" -> {
                    LiveScreen s = get(r);
                    if (r.get("ctl") instanceof Map<?, ?>) {
                        s.controls(Cases.obj(r.get("ctl")));
                    }
                    if (r.get("state") instanceof Map<?, ?>) {
                        s.state(Cases.obj(r.get("state")));
                    }
                    yield "{\"viewModel\":" + ViewJson.write(s.view()) + "}";
                }
                case "patch" -> {
                    LiveScreen s = get(r);
                    List<Frame> frames = s.patch(String.valueOf(r.get("table")), Cases.obj(r.get("patch")));
                    yield frames == null ? "{\"viewModel\":" + ViewJson.write(s.last()) + "}" : "{\"frames\":" + ViewJson.write(frames) + "}";
                }
                case "close" -> {
                    OPEN.remove(id(r));
                    yield "{}";
                }
                default -> throw new IllegalArgumentException("no screen operation '" + op + "'");
            };
        } catch (com.ash.rachana.SutraException e) {
            List<Object> ps = new ArrayList<>();
            e.problems().forEach(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", p.code());
                m.put("severity", p.severity());
                m.put("message", p.message());
                m.put("line", (long) p.location().line());
                if (p.path() != null) {
                    m.put("path", p.path());
                }
                ps.add(m);
            });
            return "{\"error\":{\"code\":\"sutra\",\"problems\":" + Json.write(ps) + "}}";
        } catch (Throwable t) {
            return "{\"error\":{\"code\":\"runtime\",\"message\":" + Json.quote(t.getClass().getSimpleName() + ": " + t.getMessage()) + "}}";
        }
    }

    private static String open(Map<String, Object> r) {
        LiveScreen s = Cases.screen(r);
        int id = next++;
        OPEN.put(id, s);
        return "{\"id\":" + id + ",\"tables\":" + Json.write(s.tableNames()) + "}";
    }

    private static LiveScreen get(Map<String, Object> r) {
        LiveScreen s = OPEN.get(id(r));
        if (s == null) {
            throw new IllegalArgumentException("no open screen " + r.get("id"));
        }
        return s;
    }

    private static int id(Map<String, Object> r) {
        return r.get("id") instanceof Number n ? n.intValue() : -1;
    }

    private static Map<String, TableSchema> schemas(Object o) {
        if (!(o instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, TableSchema> out = new LinkedHashMap<>();
        Cases.obj(o).forEach((k, v) -> out.put(k, TableSchema.of(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            l.forEach(x -> out.add((Map<String, Object>) x));
        }
        return out;
    }
}
