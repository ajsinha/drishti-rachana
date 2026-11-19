# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""W1's boundary report: reads a WebAssembly (GC) binary's type, import and export sections by hand (wasmtime-py cannot describe
GC reference types) and prints every import and export with its signature. Usage: wasm_boundary.py <module.wasm>"""
import sys

ABBR = {0x7f: "i32", 0x7e: "i64", 0x7d: "f32", 0x7c: "f64", 0x7b: "v128", 0x70: "funcref", 0x6f: "externref", 0x6e: "anyref", 0x6d: "eqref",
        0x6c: "i31ref", 0x6b: "structref", 0x6a: "arrayref", 0x71: "nullref", 0x72: "nullexternref", 0x73: "nullfuncref", 0x69: "exnref",
        0x74: "nullexnref", 0x78: "i8", 0x77: "i16"}
HEAP = {0x70: "func", 0x6f: "extern", 0x6e: "any", 0x6d: "eq", 0x6c: "i31", 0x6b: "struct", 0x6a: "array", 0x71: "none", 0x72: "noextern",
        0x73: "nofunc", 0x69: "exn", 0x74: "noexn"}


class R:
    def __init__(self, b, i=0):
        self.b, self.i = b, i

    def u8(self):
        v = self.b[self.i]; self.i += 1; return v

    def uleb(self):
        r = s = 0
        while True:
            x = self.u8(); r |= (x & 0x7f) << s; s += 7
            if x < 0x80: return r

    def sleb(self):
        r = s = 0
        while True:
            x = self.u8(); r |= (x & 0x7f) << s; s += 7
            if x < 0x80:
                if x & 0x40: r -= 1 << s
                return r

    def name(self):
        n = self.uleb(); v = self.b[self.i:self.i + n].decode(); self.i += n; return v

    def heap(self):
        x = self.b[self.i]
        if x in HEAP and x >= 0x69: self.i += 1; return HEAP[x]
        return "$" + str(self.sleb())

    def val(self):
        x = self.u8()
        if x in ABBR: return ABBR[x]
        if x in (0x63, 0x64): return f"(ref {'null ' if x == 0x63 else ''}{self.heap()})"
        raise ValueError(hex(x))


def parse(b):
    assert b[:4] == b"\0asm"
    r = R(b, 8); types, imports, exports, nfunc_imp = [], [], [], 0
    while r.i < len(b):
        sid = r.u8(); size = r.uleb(); end = r.i + size
        if sid == 1:
            for _ in range(r.uleb()):
                def comp():
                    k = r.u8()
                    if k in (0x50, 0x4f):
                        for _ in range(r.uleb()): r.uleb()
                        k = r.u8()
                    if k == 0x60:
                        ps = [r.val() for _ in range(r.uleb())]; rs = [r.val() for _ in range(r.uleb())]; return ("func", ps, rs)
                    if k == 0x5f:
                        fs = [];
                        for _ in range(r.uleb()): fs.append(r.val()); r.u8()
                        return ("struct", fs, [])
                    if k == 0x5e:
                        t = r.val(); r.u8(); return ("array", [t], [])
                    raise ValueError(hex(k))
                if r.b[r.i] == 0x4e:
                    r.u8()
                    for _ in range(r.uleb()): types.append(comp())
                else:
                    types.append(comp())
        elif sid == 2:
            for _ in range(r.uleb()):
                mod, nm, k = r.name(), r.name(), r.u8()
                if k == 0: imports.append((mod, nm, "func", types[r.uleb()]))
                elif k == 3: t = r.val(); r.u8(); imports.append((mod, nm, "global", t))
                elif k == 2:
                    fl = r.u8(); r.uleb(); (r.uleb() if fl & 1 else None); imports.append((mod, nm, "memory", None))
                elif k == 4: r.u8(); imports.append((mod, nm, "tag", types[r.uleb()]))
                else: r.u8(); r.val(); imports.append((mod, nm, "table?", None))
        elif sid == 7:
            for _ in range(r.uleb()):
                nm, k, ix = r.name(), r.u8(), r.uleb(); exports.append((nm, ["func", "table", "memory", "global", "tag"][k], ix))
        r.i = end
    return types, imports, exports


def main():
    types, imports, exports = parse(open(sys.argv[1], "rb").read())
    gc = lambda ts: any(t not in ("i32", "i64", "f32", "f64", "externref", "funcref") for t in ts)
    for mod, nm, k, t in imports:
        sig = f"({', '.join(t[1])}) -> ({', '.join(t[2])})" if k in ("func", "tag") else str(t)
        print(f"import {mod}.{nm} {k} {sig}" + ("   <- GC reference at the boundary" if k == "func" and gc(t[1] + t[2]) else ""))
    for nm, k, ix in exports:
        print(f"export {nm} {k} #{ix}")
    print(f"{len(types)} types, {len(imports)} imports, {len(exports)} exports")


main()
