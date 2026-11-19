# Copyright (c) 2026 Ashutosh Sinha. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""W1 workaround, measured: wasmtime-py cannot declare a host function returning a non-nullable reference ((ref extern)), which
TeaVM's string imports return. This rewrites, in place and byte for byte, the result type of the imported functions' types from
(ref extern) to (ref null extern) (0x64 0x6f -> 0x63 0x6f), so Python can supply them. Usage: wasm_patch.py <in.wasm> <out.wasm>"""
import sys
sys.argv, args = [sys.argv[0], sys.argv[1]], sys.argv[1:]
src = open(__file__.replace("wasm_patch.py", "wasm_boundary.py")).read().replace("\nmain()\n", "\n")
exec(src)

def offsets(b):
    r = R(b, 8); func_types = {}; imported = set(); idx = 0
    while r.i < len(b):
        sid = r.u8(); size = r.uleb(); end = r.i + size
        if sid == 1:
            for _ in range(r.uleb()):
                def comp():
                    nonlocal idx
                    k = r.u8()
                    if k in (0x50, 0x4f):
                        for _ in range(r.uleb()): r.uleb()
                        k = r.u8()
                    if k == 0x60:
                        for _ in range(r.uleb()): r.val()
                        res = []
                        for _ in range(r.uleb()):
                            res.append(r.i); r.val()
                        func_types[idx] = res
                    elif k == 0x5f:
                        for _ in range(r.uleb()): r.val(); r.u8()
                    elif k == 0x5e:
                        r.val(); r.u8()
                    idx += 1
                if r.b[r.i] == 0x4e:
                    r.u8()
                    for _ in range(r.uleb()): comp()
                else:
                    comp()
        elif sid == 2:
            for _ in range(r.uleb()):
                r.name(); r.name(); k = r.u8()
                if k == 0: imported.add(r.uleb())
                elif k == 3: r.val(); r.u8()
                elif k == 2:
                    fl = r.u8(); r.uleb(); (r.uleb() if fl & 1 else None)
                elif k == 4: r.u8(); r.uleb()
                else: r.u8(); r.val()
        r.i = end
    return [o for t in imported for o in func_types.get(t, [])]

b = bytearray(open(args[0], "rb").read())
n = 0
for o in offsets(bytes(b)):
    if b[o] == 0x64 and b[o + 1] == 0x6f:
        b[o] = 0x63; n += 1
open(args[1], "wb").write(b)
print(f"patched {n} result types")
