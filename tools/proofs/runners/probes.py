# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""P2's determinism probes: the stress list of docs/plans/JS_PROOF.md section 3 as conformance cases.

  probes.py draft <out.yaml>                       inputs only (no expect), for the prep step and the runners
  probes.py final <out.yaml> <jvm results.jsonl>   the case file with expect filled from the JVM reference's actual results

Every probe id keeps its stress-list id (d-n01-a is the first input of D-N01). Probes that need a host setting, a Sutra the P1
cut cannot bind, or a JVM started with another locale are listed in NOT_RUN with the reason (written into P2_RESULTS.md).
"""
import json
import sys

LONG_A = "a" * 8000
P = []  # (id, kind, text, extra)


def e(i, x, **kw):
    P.append((i, "expr", x, kw))


def t(i, x, **kw):
    P.append((i, "template", x, kw))


def err(i, x, **kw):
    P.append((i, "expr-error", x, kw))


# 3.1 number text
for k, x in enumerate(["0.1 + 0.2"]): e(f"d-n01-{'abc'[k]}", x)
for k, x in enumerate(["1 / 3"]): e(f"d-n02-{'abc'[k]}", x)
for k, x in enumerate(["2 / 3", "100 / 7", "1e-7 * 3"]): e(f"d-n03-{'abc'[k]}", x)
for k, x in enumerate(["1e15", "1e15 + 0.5", "999999999999999 + 0.5"]): e(f"d-n04-{'abc'[k]}", x)
for k, x in enumerate(["1e20", "1e21", "1e22", "123456789012345678901"]): e(f"d-n05-{'abcd'[k]}", x)
for k, x in enumerate(["-0 * 1", "0 * -1", "-0.0 + 0"]): e(f"d-n06-{'abc'[k]}", x)
for k, x in enumerate(["0 / 0", "sqrt(-1)", "ln(0)", "exp(1000)", "-exp(1000)"]): e(f"d-n07-{'abcde'[k]}", x)
for k, x in enumerate(["5e-324", "2.2250738585072014e-308", "1.7976931348623157e308"]): e(f"d-n08-{'abc'[k]}", x)
for k, x in enumerate(["0.000001", "0.0000001", "1234567.125"]): e(f"d-n09-{'abc'[k]}", x)
for k, x in enumerate(["round(2.5)", "round(-2.5)", "round(0.5)", "round(1.005, 2)", "round(2.675, 2)"]): e(f"d-n10-{'abcde'[k]}", x)
for k, x in enumerate(["trunc(1e300)", "int(-1e300)", "trunc(0/0)"]): e(f"d-n11-{'abc'[k]}", x)
for k, x in enumerate(["mod(-3, 2)", "-3 % 2", "5.5 % 2", "-5.5 % 2"]): e(f"d-n12-{'abcd'[k]}", x)
for k, x in enumerate(["floor(-0.5)", "ceiling(-0.5)"]): e(f"d-n13-{'ab'[k]}", x)
for k, x in enumerate(["power(2, 0.5)", "power(10, -5)", "power(1.1, 2)"]): e(f"d-n14-{'abc'[k]}", x)
for k, x in enumerate(["round_up(0.1 + 0.2, 1)", "sum([0.1, 0.2, 0.3])", "sum([1e16, 1, -1e16])"]): e(f"d-n20-{'abc'[k]}", x)
for k, x in enumerate(["sequence(3, 0.1, 0.1)", "yearfrac('2024-02-29', '2025-03-01', 'ACT/ACT')"]): e(f"d-n21-{'ab'[k]}", x)
for k, x in enumerate(["fmt(1234.5, 'amount0')", "fmt(-0.004, 'amount2')", "fmt(1e9, 'compact')", "fmt(0.125, 'pct1')"]): e(f"d-n22-{'abcd'[k]}", x)
for k, x in enumerate(["text(1234.5678, '#,##0.00')", "text(-1, '0;(0)')", "text(0, '0;-0;\"zero\"')", "text(12345, '0.00E+00')", "text(0.5, '0%')"]):
    e(f"d-n23-{'abcde'[k]}", x)
for k, x in enumerate(["value('1,234.5')", "value('1.234,5', ',', '.')"]): e(f"d-n24-{'ab'[k]}", x)
for k, x in enumerate(["percentile([1,2,3,4], 0.3)", "median([1, 2])", "stddev([1, 2, 3, 4])"]): e(f"d-n25-{'abc'[k]}", x)
# 3.2 number reading
for k, x in enumerate(["'5' * 2", "' 5 ' * 2", "'5\\t' * 2"]): e(f"d-r01-{'abc'[k]}", x.replace("\\t", "\t"))
for k, x in enumerate(["'1d' * 1", "'1f' * 1", "'0x1p3' * 1", "'1_000' * 1"]): e(f"d-r02-{'abcd'[k]}", x)
for k, x in enumerate(["'Infinity' * 1", "'NaN' * 1", "'-Infinity' > 0", "'inf' * 1"]): e(f"d-r03-{'abcd'[k]}", x)
for k, x in enumerate(["'9007199254740993' * 1", "'2.2250738585072012e-308' * 1", "'0.1e-400' * 1", "'1e400' * 1"]): e(f"d-r04-{'abcd'[k]}", x)
for k, x in enumerate(["'10' > '9'", "'1e3' == 1000", "'.5' == 0.5", "'+5' == 5", "'5.' == 5"]): e(f"d-r05-{'abcde'[k]}", x)
for k, x in enumerate(["٣ + 1", "１ + 1"]): e(f"d-r06-{'ab'[k]}", x)
# 3.3 text
for k, x in enumerate(["'Z' < 'a'", "'a' < 'B'", "'é' > 'z'"]): e(f"d-u01-{'abc'[k]}", x)
for k, x in enumerate(["'￿' < '\U0001F600'", "'\U0001F600' < '｡'"]): e(f"d-u02-{'ab'[k]}", x)
for k, x in enumerate(["len('\U0001F600')", "left('\U0001F600a', 1)", "mid('a\U0001F600b', 2, 1)", "reverse('a\U0001F600')"]): e(f"d-u03-{'abcd'[k]}", x)
for k, x in enumerate(["sort(['b', 'A', 'a', 'B', '\U0001F600', '￿'])", "unique(['a', 'A'])"]): e(f"d-u04-{'ab'[k]}", x)
for k, x in enumerate(["upper('ß')", "upper('ﬁ')", "lower('İ')", "lower('Σ')", "lower('AΣ')", "proper('ǆemal')"]):
    e(f"d-u05-{'abcdef'[k]}", x)
for k, x in enumerate(["upper('ꭰ')", "lower('Ა')", "upper('\U0001E922')"]): e(f"d-u06-{'abc'[k]}", x)
for k, x in enumerate(["$.ꭰ", "$.ࡰ", "$.\U00011F04"]): e(f"d-u07-{'abc'[k]}", x)
for k, x in enumerate(["trim(' x ')", "clean('a\u0007b')", "trim(' x ')", "1 + 1"]): e(f"d-u08-{'abcd'[k]}", x)
for k, x in enumerate(["contains('STRASSE', 'ß')", "matches('ǅ', '(?i)ǆ')", "matches('K', '(?i)k')"]): e(f"d-u09-{'abc'[k]}", x)
for k, x in enumerate(["char(128512)", "code('\U0001F600')", "char(55357)"]): e(f"d-u10-{'abc'[k]}", x)
# 3.4 dates
for k, x in enumerate(["edate('2024-01-31', 1)", "edate('2023-01-31', 1)", "edate('2024-02-29', 12)"]): e(f"d-t01-{'abc'[k]}", x)
for k, x in enumerate(["date(2026, 14, 1)", "date(2026, 0, 1)", "date(2026, 1, 0)", "date(2026, 2, 30)"]): e(f"d-t02-{'abcd'[k]}", x)
for k, x in enumerate(["week('2027-01-01')", "week('2026-12-31')", "week('2020-12-31')"]): e(f"d-t03-{'abc'[k]}", x)
for k, x in enumerate(["days_between('2024-02-28', '2024-03-01')", "'2026-11-17' - '2026-11-01'", "'2026-11-17T23:59:59Z' - '2026-11-16'"]):
    e(f"d-t04-{'abc'[k]}", x)
for k, b in enumerate(["30/360", "ACT/ACT", "ACT/360", "ACT/365", "30E/360"]): e(f"d-t05-{'abcde'[k]}", f"yearfrac('2024-02-29', '2025-02-28', '{b}')")
e("d-t05-f", "days360('2024-02-29', '2024-03-31', true)")
for k, x in enumerate(["datedif('2024-02-29', '2025-02-28', 'Y')", "datedif('2024-02-29', '2025-02-28', 'YD')", "datedif('2024-02-29', '2025-02-28', 'MD')"]):
    e(f"d-t06-{'abc'[k]}", x)
for k, x in enumerate(["date('0001-01-01')", "date('9999-12-31')", "date('1582-10-10')", "edate('9999-12-31', 1)"]): e(f"d-t07-{'abcd'[k]}", x)
for k, x in enumerate(["date('2026-02-29')", "date('2026-13-01')", "date('26-1-1')", "date('2026-1-1')"]): e(f"d-t08-{'abcd'[k]}", x)
for k, x in enumerate(["hour('2026-03-08T02:30:00-05:00')", "timestamp('2026-11-01T01:30:00Z')"]): e(f"d-t09-{'ab'[k]}", x)
for k, x in enumerate(["shift_date('2026-11-27', 1)", "business_days_between('2026-12-24', '2027-01-04')"]): e(f"d-t10-{'ab'[k]}", x)
for k, x in enumerate(["text('2026-11-17', 'dddd d mmmm yyyy')", "text('2026-11-17', 'ddd mmm')"]): e(f"d-t11-{'ab'[k]}", x)
e("d-t12-a", "fmt('2026-11-17', 'date')")
for k, x in enumerate(["asof()", "edate(asof(), -12)"]): e(f"d-t14-{'ab'[k]}", x, today="2024-02-29")
# 3.5 booleans
for k, x in enumerate(["true + 1", "1 + true", "true + true", "'x' + false"]): e(f"d-b01-{'abcd'[k]}", x)
for k, x in enumerate(["true * 1", "true - false", "-true", "true / 2"]): e(f"d-b02-{'abcd'[k]}", x)
for k, x in enumerate(["true == 1", "true == 'true'", "1 == '1.0'", "true == '1'"]): e(f"d-b03-{'abcd'[k]}", x)
for k, x in enumerate(["true > false", "true < 2", "'true' > 'false'"]): e(f"d-b04-{'abc'[k]}", x)
for k, x in enumerate(["0 < 5 < 10", "10 < 5 < 1"]): e(f"d-b05-{'ab'[k]}", x)
for k, x in enumerate(["!'0'", "!0", "!(0/0)", "![]", "!$.missing"]): e(f"d-b06-{'abcde'[k]}", x)
# 3.6 regular expressions
for k, x in enumerate(["regex_extract('aaa', 'a*?', 0)", "regex_extract('abab', '(ab)+', 1)", "regex_extract('ab', '(a|ab)(b?)', 2)"]):
    e(f"d-x01-{'abc'[k]}", x)
for k, x in enumerate(["matches('aa', '(a)\\\\1')", "matches('a', '(?=a)')", "matches('aa', 'a++')", "matches('a', '(?>a)')", "matches('x', '\\\\bx')"]):
    e(f"d-x02-{'abcde'[k]}", x)
for k, x in enumerate(["regex_replace('a.b', '.', '-')", "regex_replace('x', 'x', '$1')", "regex_replace('x', 'x', '\\\\$')"]): e(f"d-x03-{'abc'[k]}", x)
for k, x in enumerate(["matches('é', '\\\\w')", "matches('٣', '\\\\d')", "matches(' ', '\\\\s')"]): e(f"d-x04-{'abc'[k]}", x)
e("d-x05-a", "matches('a', '" + "a?" * 100 + "')")
e("d-x05-b", "matches('a', '" + "a?" * 100 + "a')")
e("d-x05-c", "matches($.long, 'b')", record={"long": "a" * 8193})
e("d-x06-a", "matches($.long, '(a|aa)*c')", record={"long": LONG_A})
for k, x in enumerate(["matches('A\\nB', '^B$')", "matches('a\\r\\n', 'a$')"]): e(f"d-x07-{'ab'[k]}", x.replace("\\n", "\n").replace("\\r", "\r"))
# 3.7 order, limits, errors
e("d-o02-a", "size(group($.rows, 'k', 'v'))", record={"rows": [{"k": "k%03d" % ((i * 7919) % 600), "v": i} for i in range(600)]})
e("d-o02-b", "group($.rows, 'k', 'v')", record={"rows": [{"k": "k%d" % ((i * 37) % 13), "v": i} for i in range(40)]})
err("d-o03-a", "sumx(1)")
for k, x in enumerate(["unique([1, 1.0, '1', 2, 0.1 + 0.2, 0.3])", "unique([0, -0, 0.0])"]): e(f"d-o05-{'ab'[k]}", x)
err("d-l1-a", "(" * 201 + "1" + ")" * 201)
e("d-l1-b", "(" * 199 + "1" + ")" * 199)
err("d-l2-a", "1" + " + 1" * 2500)
e("d-l2-b", "size(sequence(10001))")
e("d-l2-c", "size([" + ", ".join(["1"] * 1001) + "])")
for k, x in enumerate(["sqrt(-4)", "eomonth('soon')", "round_up('abc')"]): e(f"d-e1-{'abc'[k]}", x)
# 3.8 real-number functions
for k, x in enumerate(["exp(1)", "exp(-745)", "exp(709.78)", "ln(2)", "ln(1e-300)", "log(1000, 10)", "log(8, 2)", "log10(1000)"]): e(f"d-m01-{'abcdefgh'[k]}", x)
for k, x in enumerate(["power(2, 0.5)", "power(10, 0.1)", "power(1.0000001, 1e7)", "power(-8, 1/3)"]): e(f"d-m02-{'abcd'[k]}", x)
for k, x in enumerate(["norm_s_inv(0.99)", "norm_s_dist(1.96, true)", "norm_inv(0.025, 0, 1)", "t_inv(0.975, 10)", "chisq_inv(0.95, 3)", "gamma(0.5)",
                       "gamma_ln(100)", "erf(0.5)", "beta_inv(0.5, 2, 3)"]): e(f"d-m03-{'abcdefghi'[k]}", x)
for k, x in enumerate(["irr([-100, 60, 60])", "xirr([-1000, 600, 600], ['2026-01-01', '2026-07-01', '2027-01-01'])", "rate(10, -100, 800)",
                       "npv(0.1, [100, 100])", "pmt(0.05/12, 360, 200000)"]): e(f"d-m04-{'abcde'[k]}", x)
for k, x in enumerate(["fisher(0.5)", "fisher_inv(0.5)", "fisher_inv(400)", "tanh(20)", "tanh(400)"]): e(f"d-m05-{'abcde'[k]}", x)
# added by P2: TeaVM's log1p(x) = log(1 + x), expm1(x) = exp(x) - 1, tanh by exp (read from its class library)
for k, x in enumerate(["pmt(1e-12, 360, 200000)", "pv(1e-12, 360, -1000)", "fv(1e-12, 360, -1000)", "nper(1e-12, -1000, 200000)",
                       "effect(1e-10, 12)", "nominal(1e-10, 12)"]): e(f"d-m06-{'abcdef'[k]}", x)
for k, x in enumerate(["round(2.5)", "round(3.5)", "round(-0.5)", "round(1e300)", "trunc(1e19)", "int(-1e19)", "mod(1e19, 7)"]): e(f"d-m07-{'abcdefg'[k]}", x)

NOT_RUN = [
    ("D-T12 (a JVM started with -Duser.language=de)", "a JVM-against-JVM probe: run once by hand below; not a case"),
    ("D-T13 (form field zones America/Nuuk, Europe/Kyiv, US/Pacific-New)", "a Form model field, not an expression: needs the binder (A13)"),
    ("D-O01 (event cycles) and D-O04 (ten problems of mixed kinds)", "need SutraWiring over a bound Sutra and the full problem list of A2; not in the P1 cut"),
    ("D-L1 second half (a YAML document nested 1,000 deep)", "the YAML reader is A8's; the P1 cut reads the reference's tree"),
]


def case(i, kind, x, kw):
    c = {"id": i, "kind": "expr" if kind == "template" else kind, "tags": ["determinism"]}
    c["template" if kind == "template" else "expr"] = x
    given = {}
    if "record" in kw:
        given["record"] = kw["record"]
    given["today"] = kw.get("today", "2026-11-17")
    if c["kind"] != "expr-error":
        c["given"] = given
    return c


def printable(ch):
    o = ord(ch)
    return o in (9, 10, 13) or 0x20 <= o <= 0x7e or o == 0x85 or 0xa0 <= o <= 0xd7ff or 0xe000 <= o <= 0xfffd or o >= 0x10000


def yjson(v):
    """JSON (a YAML flow value), with every character YAML may not hold raw escaped as \\uXXXX."""
    return "".join(ch if printable(ch) else "\\u%04x" % ord(ch) for ch in json.dumps(v, ensure_ascii=False))


def dump(cases, out):
    lines = ["# Copyright (c) 2026 Ashutosh Sinha. Specification licence: ../../../LICENSE-SPEC.md (draft, not yet in force).",
             "# P2's determinism probes (docs/plans/JS_PROOF.md section 3; results in docs/plans/P2_RESULTS.md). expect is the JVM",
             "# reference's actual result (Drishti's compiler, Java 21), recorded by tools/proofs; verified stays false until H1 and a review.",
             "suite: el/determinism", 'since: "1.0"', "profile: core", "verified: false",
             'description: "Determinism probes: number text and reading, text order and case, dates, booleans, regular expressions, limits, real-number functions."',
             "cases:"]
    for c in cases:
        lines.append("")
        lines.append("  - " + "\n    ".join(f"{k}: {yjson(v)}" for k, v in c.items()))
    open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def main():
    mode, out = sys.argv[1], sys.argv[2]
    cases = [case(*p) for p in P]
    ids = [c["id"] for c in cases]
    assert len(ids) == len(set(ids)), "duplicate probe id"
    if mode == "final":
        actual = {}
        for line in open(sys.argv[3], encoding="utf-8"):
            r = json.loads(line)
            actual[r["case"].split("#", 1)[1]] = r["actual"]
        for c in cases:
            a = actual[c["id"]]
            if "crash" in a:
                raise SystemExit(f"{c['id']}: the reference crashed: {a}")
            c["expect"] = ({"problems": [{"code": x["code"]} for x in a["problems"]]} if c["kind"] == "expr-error" else
                           {"error": {"code": a["error"]["code"]}} if "error" in a else {"value": a["value"], "type": a["type"], "text": a["text"]})
    dump(cases, out)
    print(f"{len(cases)} probes written to {out}")


main()
