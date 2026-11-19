# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""Fallback C of COMPILER_AND_RUNTIMES 5.2, measured: the JavaScript bundle (classic-script build) inside Python through a
JavaScript engine wheel, no JVM. Usage: run_pyjs.py <mini_racer|quickjs> <umd bundle> <cases.jsonl> <results.jsonl> [<bench dir>]"""
import json, resource, sys, time

engine, bundle, cases, out = sys.argv[1:5]
src = open(bundle, encoding="utf-8").read()
t0 = time.perf_counter()
if engine == "mini_racer":
    from py_mini_racer import MiniRacer
    ctx = MiniRacer(); ctx.eval("var self = globalThis;"); ctx.eval(src)
    call = lambda fn, *a: ctx.call(fn, *a)
else:
    import quickjs
    ctx = quickjs.Context(); ctx.set_max_stack_size(64 << 20); ctx.eval("var self = globalThis;"); ctx.eval(src)
    call = lambda fn, *a: ctx.get(fn)(*a)
t1 = time.perf_counter()
text = open(cases, encoding="utf-8").read()
res = call("runAll", text)
t2 = time.perf_counter()
open(out, "w", encoding="utf-8", newline="\n").write(res)
r = {"engine": engine, "loadMs": round((t1 - t0) * 1e3), "casesMs": round((t2 - t1) * 1e3), "lines": res.count("\n")}
if len(sys.argv) > 5:
    d = sys.argv[5]
    lines = [json.loads(l) for l in open(f"{d}/prep/cases.jsonl", encoding="utf-8")]
    large = next(l["pnode"] for l in lines if l["case"].endswith("#large"))
    typical = next(l["pnode"] for l in lines if l["case"].endswith("#typical"))
    for _ in range(10): call("compileSutra", typical); call("compileSutra", large)
    for name, doc in (("typical", typical), ("large", large)):
        t = []
        for _ in range(30):
            a = time.perf_counter(); call("compileSutra", doc); t.append((time.perf_counter() - a) * 1e3)
        t.sort(); r[name + "P50"], r[name + "P95"] = round(t[15], 2), round(t[28], 2)
r["maxRssMB"] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss // 1024
print(json.dumps(r))
