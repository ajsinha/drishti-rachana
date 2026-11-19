# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""The WebAssembly proof's Python host: the TeaVM WebAssembly GC build under wasmtime (the PyPI wheel), no JVM, no JavaScript.

  run_wasm.py cases <module.wasm> <cases.jsonl> <results.jsonl>     W4: the conformance cases, results in the canonical form
  run_wasm.py bench <module.wasm> <bench dir>                        W3, W6: instantiate, lint (parse + check) timings, memory

The module's boundary is numbers and linear memory (WasmMain.java): input as UTF-8 at an address from teavm.malloc, results copied
out the same way. Imports the build declares but this path never calls are defined as traps (wasmtime-py cannot describe GC
reference types, so those could not be written in Python anyway); the maths imports are Python's math module: a fourth
implementation of exp/log/pow, which is exactly what CoreMath (A5) removes.
"""
import json
import math
import os
import resource
import struct
import sys
import time

import wasmtime


def custom_section(b, want):
    i = 8
    while i < len(b):
        sid = b[i]; i += 1
        size = shift = 0
        while True:
            x = b[i]; i += 1; size |= (x & 0x7f) << shift; shift += 7
            if x < 0x80: break
        end = i + size
        if sid == 0:
            n = shift2 = 0; j = i
            while True:
                x = b[j]; j += 1; n |= (x & 0x7f) << shift2; shift2 += 7
                if x < 0x80: break
            if b[j:j + n].decode() == want:
                return b[j + n:end]
        i = end
    return None


def js_pow(x, y):  # JavaScript's x ** y semantics (Python's math.pow raises where JavaScript returns NaN or Infinity)
    try:
        return math.pow(x, y)
    except (ValueError, ZeroDivisionError):
        return float("nan") if not (x == 0 and y < 0) else math.copysign(float("inf"), x if y % 2 == 1 else 1.0)
    except OverflowError:
        return float("inf") if x > 0 or float(y).is_integer() and y % 2 == 0 else float("-inf")


def js_log(x):
    if x < 0 or math.isnan(x): return float("nan")
    if x == 0: return float("-inf")
    return math.log(x)


def js_exp(x):
    try:
        return math.exp(x)
    except OverflowError:
        return float("inf")


STACK = 2000 << 10     # wasm stack: the most wasmtime allows (below its 2 MiB async stack); the default is 512 KiB


def wrap(f):
    def g(*a):
        try:
            return f(*a)
        except (ValueError, OverflowError):
            return float("nan")
    return g


class Rachana:
    def __init__(self, path):
        t0 = time.perf_counter()
        raw = open(path, "rb").read()
        req = json.loads(custom_section(raw, "teavm.memoryRequirements") or b"{}")
        cfg = wasmtime.Config()
        cfg.max_wasm_stack = STACK
        self.engine = wasmtime.Engine(cfg)
        self.store = wasmtime.Store(self.engine)
        cache = os.environ.get("RACHANA_WASM_CACHE")      # a precompiled artifact (Module.serialize), made on first use
        if cache and os.path.exists(cache):
            module = wasmtime.Module.deserialize_file(self.engine, cache)
        else:
            module = wasmtime.Module(self.engine, raw)
            if cache:
                with open(cache, "wb") as f:
                    f.write(module.serialize())
        t1 = time.perf_counter()
        heap = ((req.get("dataSize", 0) + 7) >> 3) << 3
        pages = max(req.get("min", 0), 1)
        memory = wasmtime.Memory(self.store, wasmtime.MemoryType(wasmtime.Limits(pages, 32768)))
        linker = wasmtime.Linker(self.engine)
        linker.allow_shadowing = True
        linker.define_unknown_imports_as_traps(module)
        f64 = wasmtime.ValType.f64(); i32 = wasmtime.ValType.i32(); ext = wasmtime.ValType.externref()
        ft = lambda ps, rs: wasmtime.FuncType(ps, rs)
        maths = {"log": js_log, "exp": js_exp, "sqrt": wrap(math.sqrt), "floor": math.floor, "ceil": math.ceil, "sin": wrap(math.sin),
                 "cos": wrap(math.cos), "tan": wrap(math.tan), "asin": wrap(math.asin), "acos": wrap(math.acos), "atan": math.atan}
        for name, f in maths.items():
            linker.define_func("teavmMath", name, ft([f64], [f64]), (lambda f: lambda x: float(f(x)))(f))
        linker.define_func("teavmMath", "pow", ft([f64, f64], [f64]), lambda x, y: float(js_pow(x, y)))
        linker.define_func("teavmMath", "atan2", ft([f64, f64], [f64]), lambda y, x: math.atan2(y, x))
        linker.define_func("teavmDate", "currentTimeMillis", ft([], [f64]), lambda: float(int(time.time() * 1000)))
        linker.define_func("teavm", "takeStackTrace", ft([ext], [ext]), lambda name: None)
        linker.define_func("teavmMemory", "notifyHeapResized", ft([], []), lambda: None)
        linker.define_func("teavmJso", "stringBuiltinsSupported", ft([], [i32]), lambda: 0)
        # JavaScript strings, used only by TeaVM's own start-up (naming the JavaScript entry point): Python strings of UTF-16 units
        js = "wasm:js-string"
        linker.define_func(js, "fromCharCode", ft([i32], [ext]), lambda c: chr(c & 0xFFFF))
        linker.define_func(js, "concat", ft([ext, ext], [ext]), lambda a, b: (a or "") + (b or ""))
        linker.define_func(js, "substring", ft([ext, i32, i32], [ext]), lambda s, a, b: s[a:b])
        linker.define_func(js, "length", ft([ext], [i32]), lambda s: len(s))
        linker.define_func(js, "charCodeAt", ft([ext, i32], [i32]), lambda s, i: ord(s[i]))
        linker.define_func("teavmJso", "createFunction1", ft([ext, ext], [ext]), lambda a, b: ("function", a, b))
        # TeaVM's start-up also builds the JavaScript wrapper of the entry class; with no JavaScript these are inert records
        fn = wasmtime.ValType.funcref()
        jso = {"createClass": ([ext, ext, fn], [ext], lambda a, b, c: {"class": a}), "defineStaticMethod": ([ext, ext, fn, i32], [], lambda *a: None),
               "defineFunction": ([fn, i32], [ext], lambda f, v: {"function": f}), "global": ([ext], [ext], lambda n: {"global": n}),
               "getProperty": ([ext, ext], [ext], lambda o, n: None), "callFunction1": ([ext, ext], [ext], lambda f, a: None),
               "callMethod0": ([ext, ext], [ext], lambda o, n: None), "unwrapDouble": ([ext], [f64], lambda o: 0.0),
               "unwrapInt": ([ext], [i32], lambda o: 0), "wrapInt": ([i32], [ext], lambda v: v), "isUndefined": ([ext], [i32], lambda o: int(o is None)),
               "instanceOf": ([ext, ext], [i32], lambda o, c: 0)}
        for name, (ps, rs, f) in jso.items():
            linker.define_func("teavmJso", name, ft(ps, rs), f)
        linker.define(self.store, "env", "memory", memory)
        linker.define(self.store, "teavmMemory", "heapOffset", wasmtime.Global(self.store, wasmtime.GlobalType(i32, False), wasmtime.Val.i32(heap)))
        linker.define(self.store, "teavmMemory", "maxSize", wasmtime.Global(self.store, wasmtime.GlobalType(i32, False), wasmtime.Val.i32((1 << 31) - 1)))
        self.inst = linker.instantiate(self.store, module)
        ex = self.inst.exports(self.store)
        self.memory, self.call_, self.copy_ = memory, ex["call"], ex["copyResult"]
        self.malloc, self.free = ex["teavm.malloc"], ex["teavm.free"]
        t2 = time.perf_counter()
        self.compile_ms, self.instantiate_ms = (t1 - t0) * 1e3, (t2 - t1) * 1e3

    def call(self, op, text):
        b = text.encode("utf-8")
        p = self.malloc(self.store, max(len(b), 1))
        self.memory.write(self.store, b, p)
        n = self.call_(self.store, op, p, len(b))
        self.free(self.store, p)
        q = self.malloc(self.store, max(n, 1))
        self.copy_(self.store, q)
        out = bytes(self.memory.read(self.store, q, q + n))
        self.free(self.store, q)
        return out.decode("utf-8")


def main():
    mode, path = sys.argv[1], sys.argv[2]
    t0 = time.perf_counter()
    r = Rachana(path)
    if mode == "cases":
        lines = [l for l in open(sys.argv[3], encoding="utf-8").read().split("\n") if l.strip()]
        a = time.perf_counter(); parts = []; traps = 0
        for line in lines:          # one call per case: a trap (an uncatchable wasm error) loses one case, not the run
            try:
                parts.append(r.call(1, line))
            except wasmtime.Trap as t:
                traps += 1
                c = json.loads(line)
                msg = str(t).strip().splitlines()[-1].strip()
                parts.append(json.dumps({"case": c["case"], "kind": c["kind"], "status": "error", "actual": {"crash": msg}},
                                        separators=(",", ":"), ensure_ascii=False) + "\n")
                r = Rachana(path)
        out = "".join(parts)
        b = time.perf_counter()
        open(sys.argv[4], "w", encoding="utf-8", newline="\n").write(out)
        print(json.dumps({"wasmtime": wasmtime.__version__ if hasattr(wasmtime, "__version__") else "?", "compileMs": round(r.compile_ms),
                          "instantiateMs": round(r.instantiate_ms, 1), "casesMs": round((b - a) * 1e3), "lines": out.count("\n"), "traps": traps,
                          "maxRssMB": round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024)}))
    else:
        d = sys.argv[3]
        lines = [json.loads(l) for l in open(f"{d}/prep/cases.jsonl", encoding="utf-8")]
        typical = next(l["pnode"] for l in lines if l["case"].endswith("#typical"))
        large = next(l["pnode"] for l in lines if l["case"].endswith("#large"))
        a = time.perf_counter(); first = r.call(2, typical); first_ms = (time.perf_counter() - a) * 1e3
        for _ in range(10):
            r.call(2, typical); r.call(2, large)
        res = {"startToFirstCompileMs": round((time.perf_counter() - t0) * 1e3 - 0, 1), "moduleCompileMs": round(r.compile_ms, 1),
               "instantiateMs": round(r.instantiate_ms, 1), "firstCompileMs": round(first_ms, 1), "problems": first}
        for name, doc in (("typical", typical), ("large", large)):
            t = []
            for _ in range(30):
                a = time.perf_counter(); r.call(2, doc); t.append((time.perf_counter() - a) * 1e3)
            t.sort()
            res[name + "P50"], res[name + "P95"] = round(t[15], 2), round(t[28], 2)
        n = int(r.call(3, open(f"{d}/rows.json", encoding="utf-8").read()))
        t4 = []
        for e in ["@.amount", "@.price * @.qty - @.amount", "@.ccy == 'USD'", "round(@.price, 2)", "matches(@.book, '^BOOK-[0-9]+$')"]:
            r.call(4, "1\n" + e)
            a = time.perf_counter(); r.call(4, "3\n" + e); t4.append({"expr": e, "ns": round((time.perf_counter() - a) * 1e9 / (3 * n), 1)})
        res["t4"] = t4
        res["maxRssMB"] = round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024)
        print(json.dumps(res))


if __name__ == "__main__":
    import threading
    threading.stack_size(64 << 20)
    th = threading.Thread(target=main)
    th.start()
    th.join()
