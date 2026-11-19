# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""T2 weight by package: split an unminified TeaVM bundle into top-level definitions and sum bytes by name prefix."""
import collections, re, sys
NAMES = {"cadre": "rachana.el", "cadrec": "rachana.el.check", "cadrm": "rachana.model", "cadrp": "rachana.parse", "cadrf": "rachana.format",
         "cadc": "common (SafeRegex, BusinessCalendar)", "cada": "api (DataNode...)", "cadae": "api.error", "cap": "proof harness",
         "ju": "java.util", "jusi": "java.util.stream", "jur": "java.util.regex", "jt": "java.time", "jtf": "java.time.format",
         "jtt": "java.time.temporal", "jtc": "java.time.chrono", "jtz": "java.time.zone", "jm": "java.math", "jl": "java.lang",
         "otcit": "TeaVM text (locale data)", "otciu": "TeaVM unicode tables", "otcic": "TeaVM charset", "jnc": "java.nio.charset",
         "jnci": "nio charset impl", "jt_": "java.text", "jtx": "java.text"}
t = open(sys.argv[1], encoding="utf-8").read()
parts = re.split(r'\n(?=[A-Za-z_$][\w$]* = |function |let |const |class )', t)
agg = collections.Counter()
for p in parts:
    m = re.match(r'(?:let |const |function |class )?([A-Za-z_$][\w$]*)', p)
    n = m.group(1) if m else "?"
    k = re.match(r'([A-Za-z]+)_', n)
    agg[k.group(1) if k else "runtime/other"] += len(p.encode())
tot = sum(agg.values())
for k, v in agg.most_common(30):
    print(f"| {k} | {NAMES.get(k, '')} | {v:,} | {100 * v / tot:.1f}% |")
print("total", tot)
