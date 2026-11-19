/*
 * Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.proof;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** JVM figures for T3/T4/T5: {@code BenchMain <bench dir> <bench50k dir>}; the same workloads as bench.mjs, JSON on stdout. */
public final class BenchMain {
    private BenchMain() {
    }

    static final String[] TWENTY = {"@.amount", "@.amount * 2", "@.price * @.qty - @.amount", "@.amount > 1000000", "@.ccy == 'USD'",
        "@.ccy in ['USD', 'EUR', 'GBP']", "@.amount > 0 && @.status != 'CANCELLED'", "@.type == 'FX' ? @.amount : -@.amount",
        "'Leg ' + #index", "T:${@.book} / ${@.ccy}", "round(@.price, 2)", "abs(@.amount) / 1e6", "nonblank(@.desk, 'n/a')",
        "upper(left(@.book, 3))", "@.trade_date >= '2026-01-01'", "days_between(@.trade_date, '2026-11-17')",
        "reduce($.legs, 0, greatest(@acc, @.notional))", "size($.legs[?@.type == 'FLOAT'])", "map($.legs, @.notional / 1e6)",
        "matches(@.book, '^BOOK-[0-9]+$')"};

    static double median(List<Double> l) {
        List<Double> s = new ArrayList<>(l);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    static double p95(List<Double> l) {
        List<Double> s = new ArrayList<>(l);
        Collections.sort(s);
        return s.get(Math.min(s.size() - 1, (int) Math.ceil(0.95 * s.size()) - 1));
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        long m0 = System.nanoTime();
        String typical = null;
        String large = null;
        for (String line : Files.readAllLines(Path.of(args[0], "prep", "cases.jsonl"))) {
            Map<String, Object> c = (Map<String, Object>) Json.parse(line);
            if (((String) c.get("case")).endsWith("#typical")) {
                typical = (String) c.get("pnode");
            } else {
                large = (String) c.get("pnode");
            }
        }
        StringBuilder out = new StringBuilder("{\"runtime\":\"jvm " + System.getProperty("java.version") + "\"");
        long f0 = System.nanoTime();
        int problems = Bench.compileSutra(typical);
        double first = (System.nanoTime() - f0) / 1e6;
        out.append(",\"t3\":{\"firstCompileMs\":").append(first).append(",\"sinceStartMs\":").append((System.nanoTime() - m0) / 1e6)
                .append(",\"problems\":").append(problems);
        for (int k = 0; k < 300; k++) {
            Bench.compileSutra(typical);
            Bench.compileSutra(large);
        }
        for (String[] w : new String[][] {{"typical", typical}, {"large", large}}) {
            List<Double> t = new ArrayList<>();
            for (int k = 0; k < 30; k++) {
                long a = System.nanoTime();
                Bench.compileSutra(w[1]);
                t.add((System.nanoTime() - a) / 1e6);
            }
            out.append(",\"").append(w[0]).append("P50\":").append(median(t)).append(",\"").append(w[0]).append("P95\":").append(p95(t));
        }
        out.append("},\"t4\":[");
        int n = Bench.load(Files.readString(Path.of(args[0], "rows.json")));
        for (int x = 0; x < TWENTY.length; x++) {
            for (int k = 0; k < 30; k++) {
                Bench.evalLoop(TWENTY[x], 1);
            }
            List<Double> t = new ArrayList<>();
            for (int k = 0; k < 10; k++) {
                long a = System.nanoTime();
                Bench.evalLoop(TWENTY[x], 1);
                t.add((double) (System.nanoTime() - a) / n);
            }
            out.append(x == 0 ? "" : ",").append("{\"expr\":");
            Canon.string(TWENTY[x], out);
            out.append(",\"compiledNs\":").append(Math.round(median(t) * 10) / 10.0).append('}');
        }
        out.append("],\"t5\":{");
        Bench.load(Files.readString(Path.of(args[1], "rows.json")));
        String[] names = {"withTableCopy", "rowsOnly"};
        for (int w = 0; w < 2; w++) {
            List<Double> t = new ArrayList<>();
            for (int k = 0; k < 2000; k++) {
                long a = System.nanoTime();
                if (w == 0) {
                    Bench.liveChange("@.price * @.qty - @.amount", 100, k);
                } else {
                    Bench.liveChangeRowsOnly("@.price * @.qty - @.amount", 100, k);
                }
                if (k >= 1950) {
                    t.add((System.nanoTime() - a) / 1e6);
                }
            }
            out.append(w == 0 ? "" : ",").append('"').append(names[w]).append("\":{\"p50ms\":").append(median(t)).append(",\"p95ms\":").append(p95(t)).append('}');
        }
        out.append("}}");
        System.out.println(out);
    }
}
