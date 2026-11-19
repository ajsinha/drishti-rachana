# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Synthetic bench inputs (deterministic): the typical Sutra (12 panels, about 60 expressions), the large one (50 panels, about
400) as a case file the prep step turns into parsed trees, and 10,000 rows plus a record with 10 legs for T4 and T5.

Usage: gen_bench.py <out dir> [rows]
"""
import json, pathlib, random, struct, sys

def f64(x):
    return {"$f64": "%016x" % struct.unpack(">Q", struct.pack(">d", x))[0]}

def metric(i):
    return (f"  - {{ id: m{i}, kind: metric, label: Metric {i}, value: \"sum($.sales, 'revenue') / {i + 1}\", fmt: amount0, span: 3,\n"
            f"      delta: \"sum($.sales[?@.month == '2026-06-01'], 'revenue') - sum($.sales[?@.month == '2026-05-01'], 'revenue')\",\n"
            f"      caption: \"${{size($.sales)}} rows, {i}\" }}\n")

def table(i, cols):
    s = (f"  - id: t{i}\n    kind: table\n    title: Table {i}\n"
         f"    rows: \"take(sort(group($.sales, @.region + ' / ' + @.product, 'revenue'), @.value, true), 8)\"\n    span: 12\n    columns:\n")
    binds = ["#index + 1", "@.label", "@.value", "@.count", "@.value / 1000", "upper(left(@.label, 3))", "round(@.value, 2)",
             "@.value > 1000 ? 'big' : 'small'", "len(@.label)", "@.count * 2", "abs(@.value)", "nonblank(@.label, 'n/a')"]
    for c in range(cols):
        s += f"      - {{ label: C{c}, bind: \"{binds[c % len(binds)]}\" }}\n"
    return s

def sutra(name, metrics, tables, cols):
    s = f"rachana: 1\nsutra: {name}\nversion: 1\ntitle: {{ id: \"'{name}'\" }}\nmatch: {{ kind: sales }}\npanels:\n"
    s += "".join(metric(i) for i in range(metrics)) + "".join(table(i, cols) for i in range(tables))
    return s

def main():
    out = pathlib.Path(sys.argv[1]); n = int(sys.argv[2]) if len(sys.argv) > 2 else 10000
    (out / "cases").mkdir(parents=True, exist_ok=True)
    typical = sutra("typical", 6, 6, 6)      # 6 x 3 + 6 x 7 = 60 expressions
    large = sutra("large", 25, 25, 12)       # 25 x 3 + 25 x 13 = 400 expressions
    lit = lambda t: "    sutra: |\n" + "".join("      " + l + "\n" for l in t.splitlines())
    (out / "cases" / "bench.yaml").write_text(
        "suite: bench/sutras\nsince: \"1.0\"\nprofile: core\ncases:\n"
        "  - id: typical\n    kind: problem\n" + lit(typical) + "    expect: { ok: true }\n"
        "  - id: large\n    kind: problem\n" + lit(large) + "    expect: { ok: true }\n", encoding="utf-8")
    r = random.Random(20261119)
    ccys = ["USD", "EUR", "GBP", "JPY", "CAD"]; types = ["FX", "IRS", "BOND"]; status = ["LIVE", "CANCELLED", "MATURED"]
    rows = []
    for i in range(n):
        rows.append({"amount": f64(round(r.uniform(-5e6, 5e6), 2)), "price": f64(round(r.uniform(0.5, 150), 4)), "qty": r.randint(1, 10000),
                     "ccy": r.choice(ccys), "trade_date": "2026-%02d-%02d" % (r.randint(1, 12), r.randint(1, 28)), "type": r.choice(types),
                     "desk": "" if i % 17 == 0 else "DESK-%d" % r.randint(1, 9), "book": "BOOK-%d" % r.randint(1, 500) if i % 11 else "bk-x",
                     "status": r.choice(status)})
    legs = [{"type": "FLOAT" if k % 2 else "FIXED", "notional": f64(1e6 * (k + 1) + 0.5)} for k in range(10)]
    (out / "rows.json").write_text(json.dumps({"record": {"legs": legs}, "rows": rows}, separators=(",", ":")), encoding="utf-8")
    plain = [{k: (struct.unpack(">d", struct.pack(">Q", int(v["$f64"], 16)))[0] if isinstance(v, dict) else v) for k, v in row.items()} for row in rows]
    plegs = [{"type": l["type"], "notional": 1e6 * (k + 1) + 0.5} for k, l in enumerate(legs)]
    (out / "rows.plain.json").write_text(json.dumps({"record": {"legs": plegs}, "rows": plain}, separators=(",", ":")), encoding="utf-8")
    print(f"bench: typical {typical.count(chr(10))} lines, large {large.count(chr(10))} lines, {n} rows")

main()
